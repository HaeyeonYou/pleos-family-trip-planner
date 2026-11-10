package io.github.haeyeonyou.familytrip.voice

import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.data.CarePlace

/**
 * 주행 중 음성 1곳 제안 (11/10 디자인 D01). 목록 대신 최우선 1곳만 말하고 "좋아 · 추가"/"아니요"로 답받는다.
 * 규칙: docs/design/pleos-1110/05-gaps-and-open-questions.md Q23.
 */

/** 무응답으로 닫히기까지 (D01 안내 글 "8초 안에 답이 없으면 닫혀요"). 제안 말이 끝난 때부터 센다. */
const val PROPOSAL_IDLE_CLOSE_MS = 8_000L

/** 제안 말이 끝나지 않아도(음성 출력 실패 등) 이 시간이 지나면 닫는다. */
const val PROPOSAL_SAFETY_CLOSE_MS = 20_000L

/** 1곳을 고르려고 서버에서 받아 오는 후보 수 */
const val PROPOSAL_CANDIDATES = 3

enum class ProposalAnswer { ACCEPTED, DECLINED }

/** 지금 제안 중인 1곳. [answer]가 null이면 아직 답을 기다리는 중이다. */
data class DriveProposal(
    val kind: CareKind,
    val place: CarePlace,
    val answer: ProposalAnswer? = null,
) {
    val waiting: Boolean get() = answer == null
}

/**
 * 후보 중 1곳 고르기 (Q23 ②): 경로에서 더 가는 시간이 가장 적은 곳. 경로 위(onRoute)는 0분으로 본다.
 * 우회 시간을 모르면(가는 곳 없음) 그 뒤로 보내고, 같으면 이동 시간 → 거리 순으로 가깝고 빠른 곳.
 */
fun pickBestPlace(places: List<CarePlace>): CarePlace? =
    places.minWithOrNull(
        compareBy<CarePlace> { if (it.onRoute) 0 else it.detourMinutes ?: Int.MAX_VALUE }
            .thenBy { it.driveMinutes }
            .thenBy { it.distanceM },
    )

/** 화면에 보이고 읽어 주는 제안 문장 (디자인 문구: "가장 가까운 OO몰 2F 수유실이에요. 경로에서 3분 더 걸려요. 경유지로 추가할까요?"). */
fun proposalMessage(place: CarePlace): String {
    val how = when {
        place.onRoute -> "경로 위에 있어요."
        place.detourMinutes != null -> "경로에서 ${place.detourMinutes}분 더 걸려요."
        else -> "${place.driveMinutes}분 거리예요."
    }
    return "가장 가까운 ${place.name}이에요. $how 경유지로 추가할까요?"
}

/** 후보가 없거나 찾지 못했을 때의 한 문장 */
fun noProposalMessage(kind: CareKind, failed: Boolean): String =
    if (failed) "${kind.label}을 찾지 못했어요. 연결을 확인해 주세요." else "10km 안에서 ${kind.label}을 찾지 못했어요."

enum class ProposalReply { ACCEPT, DECLINE, OTHER }

private val ACCEPT_WORDS = listOf("좋아", "추가", "그래", "갈게","응", "네", "예", "어", "맞아", "해줘", "해주세요")
private val DECLINE_WORDS = listOf("아니", "아냐", "싫", "취소", "그만", "됐", "패스", "괜찮", "그대로", "닫아", "안할", "안해")

/**
 * 제안에 대한 답을 가른다. 짧게 말하는 상황이라 한두 마디 키워드로 본다.
 * "좋아", "좋아 추가", "첫 번째", "응"은 수락, "아니요", "싫어", "그대로 가기"는 거절, 그 밖의 말은 [ProposalReply.OTHER].
 * 거절이 먼저다 ("좋아 아니야"는 거절).
 */
fun classifyProposalReply(text: String): ProposalReply {
    val compact = text.filter { it.isLetterOrDigit() }
    if (compact.isEmpty()) return ProposalReply.OTHER
    if (DECLINE_WORDS.any { compact.contains(it) }) return ProposalReply.DECLINE
    if (parseUtterance(text).let { it is Utterance.Choose && it.number == 1 }) return ProposalReply.ACCEPT
    // "응", "네"처럼 한 글자 대답은 문장 속 글자와 헷갈리지 않게 통째로 같을 때만 수락으로 본다.
    val short = ACCEPT_WORDS.filter { it.length == 1 }
    if (compact in short) return ProposalReply.ACCEPT
    if (ACCEPT_WORDS.filter { it.length > 1 }.any { compact.contains(it) }) return ProposalReply.ACCEPT
    return ProposalReply.OTHER
}

/** 시간 예약 통로. 앱에서는 Handler, 테스트에서는 가짜 시계가 맡는다. */
interface TimerClock {
    fun postDelayed(task: Runnable, delayMs: Long)
    fun cancel(task: Runnable)
}

/**
 * 한 번에 하나만 걸리는 자동 닫기 타이머. [start]를 다시 부르면 앞의 예약은 취소되고 새로 센다.
 * 음성 패널의 모든 "몇 초 뒤 닫기"와 D01의 8초 무응답 닫기가 이것을 쓴다.
 */
class AutoCloseTimer(private val clock: TimerClock) {
    private var task: Runnable? = null

    fun start(delayMs: Long, onFire: () -> Unit) {
        cancel()
        val t = Runnable {
            task = null
            onFire()
        }
        task = t
        clock.postDelayed(t, delayMs)
    }

    fun cancel() {
        task?.let(clock::cancel)
        task = null
    }

    val pending: Boolean get() = task != null
}
