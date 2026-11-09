package io.github.haeyeonyou.familytrip.data

import kotlinx.coroutines.CancellationException
import kotlinx.serialization.Serializable
import retrofit2.HttpException

/** 서버가 돌려주는 가족 목록에서 지울 때 필요한 id만 읽는다. */
@Serializable
data class IdOnly(val id: String)

@Serializable
data class FamilyIds(val children: List<IdOnly> = emptyList(), val members: List<IdOnly> = emptyList())

/** 설정 '데이터 전체 삭제'가 서버에 부르는 삭제 API (docs/research/web-server-contract.md 7-4). */
interface EraseRemote {
    suspend fun familyIds(): FamilyIds
    suspend fun eraseChild(id: String)
    suspend fun eraseMember(id: String)
    suspend fun eraseVisits()
}

class TripApiEraseRemote(private val api: TripApi) : EraseRemote {
    override suspend fun familyIds() = api.familyForErase()
    override suspend fun eraseChild(id: String) = api.deleteChild(id).requireDeleted()
    override suspend fun eraseMember(id: String) = api.eraseMember(id).requireDeleted()
    override suspend fun eraseVisits() = api.eraseVisits().requireDeleted()

    /** 이미 없는 것(404)은 지워진 것으로 본다. */
    private fun retrofit2.Response<Unit>.requireDeleted() {
        if (!isSuccessful && code() != 404) throw HttpException(this)
    }
}

/**
 * 로그인한 계정의 서버 데이터(방문 기록 전체, 아이와 그 수유 기록, 외출 멤버)를 지운다. 계정과 공조 프리셋은 남긴다.
 * 성공하면 null, 실패하면 화면에 보일 안내를 돌려준다. 중간에 실패하면 이미 지운 것은 되돌릴 수 없으므로 안내에 그 사실을 적는다.
 */
suspend fun eraseServerData(remote: EraseRemote): String? = try {
    val family = remote.familyIds()
    remote.eraseVisits()
    family.children.forEach { remote.eraseChild(it.id) }
    family.members.forEach { remote.eraseMember(it.id) }
    null
} catch (e: CancellationException) {
    throw e
} catch (e: HttpException) {
    if (e.code() == 401) "차의 로그인이 끊겨 서버 데이터를 지우지 못했어요. 다시 로그인한 뒤 시도해 주세요. 이 차의 데이터는 그대로예요."
    else "서버 오류(${e.code()})로 서버 데이터를 지우지 못했어요. 일부는 이미 지워졌을 수 있어요. 이 차의 데이터는 그대로예요."
} catch (e: Exception) {
    "서버에 연결하지 못해 서버 데이터를 지우지 못했어요. 이 차의 데이터는 그대로예요."
}
