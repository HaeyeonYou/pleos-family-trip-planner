package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.runBlocking
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class ServerEraseTest {
    private class Fake(
        val family: FamilyIds = FamilyIds(listOf(IdOnly("c1"), IdOnly("c2")), listOf(IdOnly("m1"))),
        val failOn: String? = null,
        val code: Int = 500,
    ) : EraseRemote {
        val calls = mutableListOf<String>()
        private fun call(name: String) {
            calls += name
            if (name == failOn) throw HttpException(Response.error<Unit>(code, "".toResponseBody("application/json".toMediaType())))
        }
        override suspend fun familyIds(): FamilyIds { call("family"); return family }
        override suspend fun eraseChild(id: String) = call("child:$id")
        override suspend fun eraseMember(id: String) = call("member:$id")
        override suspend fun eraseVisits() = call("visits")
    }

    @Test
    fun 방문_기록과_아이와_멤버를_모두_지운다() = runBlocking {
        val remote = Fake()
        assertNull(eraseServerData(remote))
        assertEquals(listOf("family", "visits", "child:c1", "child:c2", "member:m1"), remote.calls)
    }

    @Test
    fun 서버가_실패하면_안내를_돌려주고_더_진행하지_않는다() = runBlocking {
        val remote = Fake(failOn = "child:c1")
        val message = eraseServerData(remote)
        assertNotNull(message)
        assertTrue(message!!.contains("이 차의 데이터는 그대로"))
        assertTrue("c2는 건드리지 않는다", "child:c2" !in remote.calls)
    }

    @Test
    fun 로그인이_끊기면_다시_로그인하라고_안내한다() = runBlocking {
        val message = eraseServerData(Fake(failOn = "family", code = 401))
        assertTrue(message!!.contains("로그인"))
    }
}
