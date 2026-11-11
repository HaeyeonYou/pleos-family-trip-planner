package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.data.VoiceScreen
import io.github.haeyeonyou.familytrip.grow.Experience
import io.github.haeyeonyou.familytrip.grow.GrowView

/**
 * 음성 명령이 화면 쪽 기능(탭 이동, 코스 추천 조건 등)을 부르는 통로.
 * 화면과 내비게이션을 가진 MainActivity가 구현해서 [VoiceController.host]에 꽂는다.
 * 앱 단위인 음성 컨트롤러가 화면 객체를 직접 잡지 않게 하려는 것이다.
 */
interface VoiceHost {
    /** 지금 주행 중인지 (주차가 아니면 true) */
    val driving: Boolean

    /** 해석 맥락으로 서버에 보낼 현재 화면 요약 */
    fun screen(): VoiceScreen

    fun goTab(tab: VoiceTab)

    /** 홈 코스 추천 조건을 바꾸고 홈으로 간다. */
    fun setFilter(filter: VoiceFilter, on: Boolean)

    /** 홈의 '다른 코스 보기'. 홈으로 가서 새 후보를 받는다. */
    fun anotherCourses()

    /** 홈 추천 코스 [number]번(1부터)을 연다. 없으면 false. */
    fun openCourse(number: Int): Boolean

    /** 열려 있는 코스의 안내를 시작한다. 열린 코스가 없으면 false. */
    fun startCourse(): Boolean

    /** 도착 시각 등 길 안내 정보로 하는 답변 */
    fun etaAnswer(): String

    fun weatherAnswer(): String

    /** 그로우: 방문 기록에서 센 경험 (10분 이상 머문 방문, 분류는 grow/JellyModel). */
    fun growExperiences(): List<Experience> = emptyList()

    /** 그로우 탭으로 가서 [view](거르기·장소 팝업)를 보인다. */
    fun showGrow(view: GrowView) {}

    /** [placeName]이 들어간 저장 코스를 연다. 없으면 false. */
    fun openCourseWith(placeName: String): Boolean = false

    /**
     * 코스 진행 도착 화면(H03·H03-1, #218)의 답 ("다음 장소 가자"·"바꿔줘"·"집에 가자"·"한 곳 더").
     * 지금 도착 화면이 아니거나 답이 아니면 null, 처리했으면 읽어 줄 한 문장.
     */
    fun answerArrival(text: String): String? = null
}
