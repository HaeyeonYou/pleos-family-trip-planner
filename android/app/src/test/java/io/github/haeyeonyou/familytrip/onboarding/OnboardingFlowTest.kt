package io.github.haeyeonyou.familytrip.onboarding

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.NapWindow
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.CHILD
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.CONFIRM
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.PERMISSION
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.QR
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.READY
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep.START
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class OnboardingFlowTest {
    private val today = LocalDate.of(2026, 11, 11)

    // 단계 경로 (QO1)

    @Test
    fun 시작하기는_권한_아이정보_확인_완료_순서이고_세_단계로_센다() {
        val path = OnboardingFlow.path(OnboardingMode.FIRST_RUN, viaQr = false, hasChildren = false)
        assertEquals(listOf(START, PERMISSION, CHILD, CONFIRM, READY), path)
        assertEquals("1 / 3", OnboardingFlow.stepLabel(PERMISSION, path))
        assertEquals("3 / 3", OnboardingFlow.stepLabel(CONFIRM, path))
        assertNull(OnboardingFlow.stepLabel(START, path))
        assertNull(OnboardingFlow.stepLabel(READY, path))
    }

    @Test
    fun QR은_네_단계이고_아이를_불러오면_권한은_거치고_아이정보는_건너뛴다() {
        val before = OnboardingFlow.path(OnboardingMode.FIRST_RUN, viaQr = true, hasChildren = false)
        assertEquals(listOf(START, QR, PERMISSION, CHILD, CONFIRM, READY), before)
        assertEquals("1 / 4", OnboardingFlow.stepLabel(QR, before))
        assertEquals("3 / 4", OnboardingFlow.stepLabel(CHILD, before))

        val loaded = OnboardingFlow.path(OnboardingMode.FIRST_RUN, viaQr = true, hasChildren = true)
        assertEquals(listOf(START, QR, PERMISSION, CONFIRM, READY), loaded)
        assertEquals(CONFIRM, OnboardingFlow.next(PERMISSION, loaded))
        assertEquals("2 / 3", OnboardingFlow.stepLabel(PERMISSION, loaded))
        assertEquals(PERMISSION, OnboardingFlow.previous(CONFIRM, loaded))
        assertNull(OnboardingFlow.next(READY, loaded))
    }

    @Test
    fun 설정에서_열면_아이_단계만_돈다() {
        assertEquals(listOf(CHILD, CONFIRM), OnboardingFlow.path(OnboardingMode.EDIT_CHILDREN, viaQr = false, hasChildren = false))
        val edit = OnboardingFlow.path(OnboardingMode.EDIT_CHILDREN, viaQr = false, hasChildren = true)
        assertEquals(listOf(CONFIRM), edit)
        assertNull(OnboardingFlow.stepLabel(CONFIRM, edit))
        assertNull(OnboardingFlow.next(CONFIRM, edit))
    }

    // 시작 화면 분기

    @Test
    fun 처음_실행만_온보딩부터이고_안내중이거나_마쳤거나_쓰던_차면_아니다() {
        assertTrue(OnboardingFlow.startsWithOnboarding(guideActive = false, done = false, loggedIn = false, profile = null))
        assertTrue(OnboardingFlow.startsWithOnboarding(false, false, false, FamilyProfile()))
        assertFalse(OnboardingFlow.startsWithOnboarding(guideActive = true, done = false, loggedIn = false, profile = null))
        assertFalse(OnboardingFlow.startsWithOnboarding(false, done = true, loggedIn = false, profile = null))  // 나중에 하기도 완료
        assertFalse(OnboardingFlow.startsWithOnboarding(false, false, loggedIn = true, profile = null))
        assertFalse(OnboardingFlow.startsWithOnboarding(false, false, false, FamilyProfile(children = listOf(Child(2025, 9)))))
    }

    // 월령 계산 (QO2: 일까지)

    @Test
    fun 월령은_생일_전날까지_한_달_덜_센다() {
        val seoa = Child(2025, 9, "서아", birthDay = 3)
        assertEquals(14, seoa.ageMonthsOn(LocalDate.of(2026, 11, 3)))
        assertEquals(13, seoa.ageMonthsOn(LocalDate.of(2026, 11, 2)))
        assertEquals(14, Child(2025, 9).ageMonthsOn(LocalDate.of(2026, 11, 2)))  // 일을 모르면 달 기준
        assertEquals("생후 14개월", monthsLabel(14))
        assertEquals("38개월", monthsLabel(38))
        assertEquals("2025.9.3", birthLabel(seoa))
        assertEquals("2023.9", birthLabel(Child(2023, 9)))
    }

    @Test
    fun 휠은_미래_날짜를_고를_수_없고_없는_날은_당긴다() {
        assertEquals(2026, BirthWheel.years(today).last())
        assertEquals(11, BirthWheel.months(2026, today).last())
        assertEquals(12, BirthWheel.months(2025, today).last())
        assertEquals(11, BirthWheel.days(2026, 11, today).last())
        assertEquals(29, BirthWheel.days(2024, 2, today).last())
        assertEquals(Triple(2026, 11, 11), BirthWheel.clamp(2026, 12, 31, today))
        assertEquals(Triple(2025, 2, 28), BirthWheel.clamp(2025, 2, 31, today))
    }

    // 입력 → 저장값 (QO3·QO4)

    @Test
    fun 이름과_오늘까지의_생년월일이_있어야_다음으로_간다() {
        val d = ChildDraft(name = "서아", year = 2025, month = 9, day = 3)
        assertTrue(d.isComplete(today))
        assertFalse(d.copy(name = " ").isComplete(today))
        assertFalse(d.copy(day = null).isComplete(today))
        assertFalse(d.copy(year = 2026, month = 11, day = 12).isComplete(today))
        assertFalse(d.copy(month = 2, day = 30).isComplete(today))
        assertEquals(14, d.ageMonths(today))
    }

    @Test
    fun 칩은_아이값과_가족값으로_나뉘어_저장된다() {
        val draft = ChildDraft(name = "서아이름이아주아주길어요", year = 2025, month = 9, day = 3, nap = NapChoice.AFTERNOON, tags = setOf(TagAlong.STROLLER, TagAlong.REAR_FACING_SEAT, TagAlong.PET))
        val profile = (null as FamilyProfile?).withChild(draft)
        val child = profile.children.single()
        assertEquals("서아이름이아주아주길", child.name)  // 10자
        assertEquals(Triple(2025, 9, 3), Triple(child.birthYear, child.birthMonth, child.birthDay))
        assertEquals(true, child.stroller)
        assertEquals(true, child.rearFacingSeat)
        assertEquals("오후 1시쯤", child.napTimes)
        assertTrue(profile.petAlong)  // 반려견은 가족 값
        assertEquals(NapWindow(13 * 60, 15 * 60), NapChoice.AFTERNOON.window)
        assertNull(NapChoice.IRREGULAR.window)
    }

    @Test
    fun 안_고른_칩은_모름이고_원래_켜져_있던_것만_끈다() {
        val fresh = ChildDraft(name = "도윤", year = 2023, month = 9, day = 1).toChild()
        assertNull(fresh.stroller)
        assertNull(fresh.rearFacingSeat)
        assertNull(fresh.napTimes)

        val old = Child(2023, 9, "도윤", serverId = "s-2", stroller = true, rearFacingSeat = null, napTimes = "오전 10시쯤")
        val profile = FamilyProfile(children = listOf(Child(2025, 9, "서아"), old), petAlong = true)
        val edit = ChildDraft.from(old, petAlong = true)
        assertEquals(NapChoice.MORNING, edit.nap)
        assertEquals(setOf(TagAlong.STROLLER, TagAlong.PET), edit.tags)
        val next = profile.withChild(edit.copy(day = 1, tags = emptySet()))
        val saved = next.children[1]
        assertEquals("s-2", saved.serverId)  // 고쳐도 서버 id는 지킨다
        assertEquals(false, saved.stroller)
        assertNull(saved.rearFacingSeat)
        assertEquals("오전 10시쯤", saved.napTimes)
        assertEquals(2, next.children.size)
        assertFalse(next.petAlong)
    }

    @Test
    fun 미리보기_문구는_입력에_따라_바뀐다() {
        assertEquals("유모차 가능 · 수유실 있는 곳 위주", coursePreview(setOf(TagAlong.STROLLER), 14))
        assertEquals("월령에 맞는 곳 위주", coursePreview(emptySet(), 40))
        assertEquals("반려견 동반 위주", coursePreview(setOf(TagAlong.PET, TagAlong.REAR_FACING_SEAT), null))
        assertEquals("오후 1시쯤 출발이면 미리 제안", napPreview(NapChoice.AFTERNOON))
        assertEquals("유모차 · 카시트 뒤보기", tagAlongLabel(Child(2025, 9, stroller = true, rearFacingSeat = true)))
        assertEquals("함께 챙길 것 없음", tagAlongLabel(Child(2025, 9, stroller = false)))
    }

    // O02 불러온 항목 · O03 권한

    @Test
    fun 불러온_항목_요약은_아이와_케어장소를_외_N으로_줄인다() {
        val profile = FamilyProfile(children = listOf(Child(2025, 9, "서아", birthDay = 3), Child(2023, 9, "도윤")))
        val s = importedSummary(profile, listOf("베이비케어 기본", "낮잠", ""), listOf("구리시 육아종합지원센터", "키즈카페"), today)
        assertEquals("서아 · 생후 14개월 외 1명", s.children)
        assertEquals("베이비케어 기본 · 낮잠", s.careModes)
        assertEquals("구리시 육아종합지원센터 외 1곳", s.carePlaces)
        val empty = importedSummary(null, emptyList(), emptyList(), today)
        assertNull(empty.children)
        assertNull(empty.careModes)
        assertNull(empty.carePlaces)
    }

    @Test
    fun 캘린더는_준비중이고_공조를_못하는_차는_안내만() {
        val on = LinkChoices()
        assertEquals(LinkState.COMING_SOON, linkState(LinkItem.CALENDAR, on, climateSupported = true))
        assertEquals(LinkState.GUIDE_ONLY, linkState(LinkItem.CLIMATE, on, climateSupported = false))
        assertEquals(LinkState.ON, linkState(LinkItem.CLIMATE, on, climateSupported = null))  // 모르면 켠 상태로 둔다
        assertEquals(LinkState.OFF, linkState(LinkItem.VOICE, on.copy(voice = false), climateSupported = true))
        assertEquals(LinkState.ON, linkState(LinkItem.LOCATION, on, climateSupported = false))
        assertEquals(3, LinkItem.entries.count { it.required })
    }
}
