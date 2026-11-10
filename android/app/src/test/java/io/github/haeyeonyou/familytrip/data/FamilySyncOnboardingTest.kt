package io.github.haeyeonyou.familytrip.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 온보딩 O04(#184)가 저장하는 생년월일(일)·유모차·카시트·낮잠 시간대의 서버 맞춤. */
class FamilySyncOnboardingTest {
    private fun sc(
        id: String,
        birth: String = "2025-09",
        date: String? = null,
        stroller: Boolean? = null,
        seat: Boolean? = null,
        nap: String? = null,
    ) = ServerChild(
        id, "서아", birth, "mixed", 180, 2, 14,
        birthDate = date, rearFacingSeat = seat, strollerUse = stroller, napTimes = nap,
        temperament = listOf("animal"), heatSensitivity = "cold", motionSickness = true,
    )

    private fun local(vararg c: Child) = FamilyProfile(children = c.toList())

    @Test
    fun 새_아이는_일까지와_온보딩_항목을_함께_올린다() {
        val child = Child(2025, 9, "서아", birthDay = 3, stroller = true, rearFacingSeat = true, napTimes = "오후 1시쯤")
        val body = planFamily(local(child), 180, emptyList(), emptyList(), { "n1" }).puts.single().second
        assertEquals("2025-09", body.birthMonth)
        assertEquals("2025-09-03", body.birthDate)
        assertEquals(true, body.strollerUse)
        assertEquals(true, body.rearFacingSeat)
        assertEquals("오후 1시쯤", body.napTimes)
    }

    @Test
    fun 고쳐_올릴_때_웹에서_넣은_항목을_지우지_않는다() {
        val s = sc("a", date = "2025-09-03")
        val child = Child(2025, 9, "서아", "a", birthDay = 3, rearFacingSeat = true)
        val body = planFamily(local(child), 180, listOf(s), listOf(s), { "x" }).puts.single().second
        assertEquals(true, body.rearFacingSeat)
        assertEquals("2025-09-03", body.birthDate)
        assertEquals(listOf("animal"), body.temperament)  // 차가 모르는 값도 그대로 보낸다
        assertEquals("cold", body.heatSensitivity)
        assertEquals(true, body.motionSickness)
        assertEquals("mixed", body.feedingType)
    }

    @Test
    fun 차_값이_모름이면_고친_것으로_보지_않는다() {
        val s = sc("a", date = "2025-09-03", stroller = true, seat = false, nap = "오전 10시쯤")
        // 예전 화면(ProfileScreen)이 온보딩 항목을 모르고 저장한 아이
        val plan = planFamily(local(Child(2025, 9, "서아", "a")), 180, listOf(s), listOf(s), { "x" })
        assertTrue(plan.puts.isEmpty())
    }

    @Test
    fun 일만_고쳐도_올리고_달을_바꾸면_일까지_보낸다() {
        val s = sc("a", date = "2025-09-03")
        val day = planFamily(local(Child(2025, 9, "서아", "a", birthDay = 4)), 180, listOf(s), listOf(s), { "x" }).puts.single().second
        assertEquals("2025-09-04", day.birthDate)
        val month = planFamily(local(Child(2025, 8, "서아", "a")), 180, listOf(s), listOf(s), { "x" }).puts.single().second
        assertEquals("2025-08", month.birthMonth)
        assertNull(month.birthDate)  // 일을 모르면 달만 → 서버가 맞지 않는 일을 버린다
    }

    @Test
    fun 서버_값은_차_아이에_일과_온보딩_항목으로_내려온다() {
        val kids = serverChildrenToLocal(listOf(sc("a", date = "2025-09-03", stroller = true, seat = true, nap = "오후 1시쯤"), sc("b", birth = "2023-09")))
        assertEquals(Child(2025, 9, "서아", "a", birthDay = 3, stroller = true, rearFacingSeat = true, napTimes = "오후 1시쯤"), kids[0])
        assertNull(kids[1].birthDay)
        // 달과 안 맞는 날짜는 쓰지 않는다
        assertNull(serverChildrenToLocal(listOf(sc("c", birth = "2025-08", date = "2025-09-03"))).single().birthDay)
    }
}
