package io.github.haeyeonyou.familytrip.voice

import android.os.Handler
import android.os.Looper
import io.github.haeyeonyou.familytrip.care.CareController
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.ToneState
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.durationLabel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** 음성 패널 상태 (피그마 VX01~VX04). */
data class VoiceUiState(
    /** 패널이 열려 있는지 */
    val open: Boolean = false,
    val listening: Boolean = false,
    /** "방금 말씀하신 것" */
    val heard: String = "",
    val situation: CareSituation? = null,
    /** 화면과 음성으로 알리는 답변 */
    val message: String = "",
    /** 번호로 고를 수 있는 후보 (최대 2곳) */
    val options: List<CarePlace> = emptyList(),
    /** 고른 번호 (1부터) */
    val chosen: Int? = null,
    /** 찾는 중 */
    val busy: Boolean = false,
    /** 마이크·인식을 못 쓸 때 안내 */
    val notice: String? = null,
)

private const val CANCEL_WINDOW_MS = 3_000L
private const val IDLE_CLOSE_MS = 8_000L
private const val CLOSE_AFTER_CHOICE_MS = 3_000L

/**
 * 음성 케어 흐름: 말 한마디 → 상황 판단 → 답변(화면 + 읽어 주기) + 화면 톤 전환 → "1번으로 가줘"로 경유지 추가.
 * 잘못 알아들었으면 답변 뒤 3초 안에 "아니야"로 취소하고, 고르지 않으면 8초 뒤 닫혀 원래 경로를 유지한다.
 * 말이 안 들리는 상황을 위해 문장 버튼·입력창이 같은 [submit]을 탄다.
 */
class VoiceController(
    private val io: VoiceIo,
    private val care: CareController,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    private var answeredAt = 0L
    private var closeTask: Runnable? = null

    /** 마이크 버튼: 패널을 열고 한 문장을 듣는다. 이미 열려 있으면 다시 듣는다. */
    fun startListening() {
        cancelClose()
        io.stopSpeaking()
        _state.update { it.copy(open = true, listening = true, notice = null) }
        io.listen { result ->
            _state.update { it.copy(listening = false) }
            when (result) {
                is ListenResult.Heard -> submit(result.text)
                is ListenResult.Failed -> {
                    _state.update { it.copy(notice = "${result.reason}. 아래 문장 버튼을 눌러 말씀해 주세요.") }
                    scheduleClose(IDLE_CLOSE_MS)
                }
            }
        }
    }

    /** 인식한 문장, 문장 버튼, 시연 입력창이 모두 여기로 들어온다. */
    fun submit(text: String) {
        cancelClose()
        io.cancelListen()
        _state.update { it.copy(open = true, listening = false, notice = null) }
        when (val u = parseUtterance(text)) {
            is Utterance.Situation -> handleSituation(text, u.situation)
            is Utterance.Choose -> handleChoose(u.number)
            Utterance.Cancel -> handleCancel()
            Utterance.KeepGoing -> close()
            Utterance.Unknown -> {
                _state.update { it.copy(heard = text, message = "잘 이해하지 못했어요. \"애 잔다\", \"애 쌌다\", \"애 배고프다\", \"애 운다\" 중에서 말씀해 주세요.") }
                scheduleClose(IDLE_CLOSE_MS)
            }
        }
    }

    private fun handleSituation(heard: String, situation: CareSituation) {
        ToneState.voice = situation.tone
        _state.update { VoiceUiState(open = true, heard = heard, situation = situation, busy = situation != CareSituation.SLEEP) }
        scope.launch {
            when (situation) {
                CareSituation.SLEEP -> {
                    care.setSleepMode(true)
                    answer("수면 모드를 켰어요. 안내 음성을 줄이고 알림은 조용히 할게요.", emptyList())
                }
                CareSituation.DIAPER -> {
                    val places = care.findNearby(CareKind.DIAPER, 2)
                    answer(
                        when {
                            places == null -> "기저귀 교환대를 찾지 못했어요. 연결을 확인해 주세요."
                            places.isEmpty() -> "10km 안에서 기저귀 교환대를 찾지 못했어요."
                            else -> "근처 기저귀 교환대 ${places.size}곳 찾았어요. ${firstDetail(places)}"
                        },
                        places.orEmpty(),
                    )
                }
                CareSituation.FEED -> {
                    val places = care.findNearby(CareKind.NURSING, 2)
                    val feeding = care.state.value.feeding
                    val since = feeding.lastFedAt?.let { "마지막 수유는 ${durationLabel((System.currentTimeMillis() - it) / 60_000)} 전이에요. " }
                        ?: "마지막 수유 기록이 없어요. "
                    answer(
                        since + when {
                            places == null -> "수유실을 찾지 못했어요. 연결을 확인해 주세요."
                            places.isEmpty() -> "10km 안에서 수유실을 찾지 못했어요."
                            else -> "근처 수유실 ${places.size}곳 찾았어요. ${firstDetail(places)}"
                        },
                        places.orEmpty(),
                    )
                }
                CareSituation.SOOTHE -> {
                    val places = care.findNearby(CareKind.REST, 2)
                    answer(
                        when {
                            places == null -> "정차할 곳을 찾지 못했어요. 연결을 확인해 주세요."
                            places.isEmpty() -> "10km 안에서 안전하게 세울 곳을 찾지 못했어요."
                            else -> "안전하게 세울 수 있는 곳 ${places.size}곳 찾았어요. ${firstDetail(places)}"
                        },
                        places.orEmpty(),
                    )
                }
            }
        }
    }

    private fun firstDetail(places: List<CarePlace>): String {
        val first = places.first()
        val where = if (first.onRoute) "경로 위에 있어요" else "${first.driveMinutes}분 거리예요"
        return "1번은 ${first.name}, $where."
    }

    private fun answer(message: String, options: List<CarePlace>) {
        answeredAt = System.currentTimeMillis()
        _state.update { it.copy(busy = false, message = message, options = options) }
        io.speak(message, care.voiceVolume)
        scheduleClose(IDLE_CLOSE_MS)
    }

    private fun handleChoose(number: Int) {
        val s = _state.value
        val place = s.options.getOrNull(number - 1)
        if (place == null) {
            val msg = if (s.options.isEmpty()) "먼저 상황을 말씀해 주세요." else "${number}번은 없어요. 1번이나 2번으로 말씀해 주세요."
            _state.update { it.copy(message = msg) }
            io.speak(msg, care.voiceVolume)
            scheduleClose(IDLE_CLOSE_MS)
            return
        }
        val result = care.addWaypoint(place)
        _state.update { it.copy(chosen = number, message = result) }
        io.speak(result, care.voiceVolume)
        scheduleClose(CLOSE_AFTER_CHOICE_MS)
    }

    /** 답변 뒤 3초 안의 "아니야"만 취소로 받는다. 수면 모드를 켰다면 다시 끈다. */
    private fun handleCancel() {
        val s = _state.value
        val within = System.currentTimeMillis() - answeredAt <= CANCEL_WINDOW_MS
        if (!within || s.situation == null) {
            _state.update { it.copy(message = "취소할 내용이 없어요.") }
            scheduleClose(CLOSE_AFTER_CHOICE_MS)
            return
        }
        if (s.situation == CareSituation.SLEEP) care.setSleepMode(false)
        val msg = "알겠어요, 취소했어요."
        io.speak(msg, care.voiceVolume)
        _state.update { it.copy(message = msg, options = emptyList(), chosen = null) }
        scheduleClose(1_500L)
    }

    /** 패널을 닫고 톤을 되돌린다 (수면 모드가 켜져 있으면 다크는 남는다). 경로는 그대로다. */
    fun close() {
        cancelClose()
        io.cancelListen()
        io.stopSpeaking()
        ToneState.voice = null
        _state.value = VoiceUiState()
    }

    private fun scheduleClose(delayMs: Long) {
        cancelClose()
        closeTask = Runnable { close() }.also { main.postDelayed(it, delayMs) }
    }

    private fun cancelClose() {
        closeTask?.let(main::removeCallbacks)
        closeTask = null
    }
}
