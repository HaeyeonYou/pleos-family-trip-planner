package io.github.haeyeonyou.familytrip.voice

import android.os.Handler
import android.os.Looper
import android.util.Log
import io.github.haeyeonyou.familytrip.care.CareController
import io.github.haeyeonyou.familytrip.care.CareKind
import io.github.haeyeonyou.familytrip.care.CareTone
import io.github.haeyeonyou.familytrip.care.ToneState
import io.github.haeyeonyou.familytrip.data.CarePlace
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.data.VoiceInterpretRequest
import io.github.haeyeonyou.familytrip.data.VoiceScreen
import io.github.haeyeonyou.familytrip.data.clockLabel
import io.github.haeyeonyou.familytrip.data.durationLabel
import io.github.haeyeonyou.familytrip.vehicle.VehicleRepository
import io.github.haeyeonyou.familytrip.navi.NaviRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

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
    /** 찾는 중·해석 중 */
    val busy: Boolean = false,
    /** 마이크·인식을 못 쓸 때 안내 */
    val notice: String? = null,
    /** 되묻는 중인 동작 설명. 있으면 "응/아니야" 버튼을 보인다. */
    val confirm: String? = null,
    /** 주행 중 1곳 제안 (D01). 있으면 패널 대신 큰 버튼 2개짜리 카드를 보인다. */
    val proposal: DriveProposal? = null,
)

private const val CANCEL_WINDOW_MS = 3_000L
private const val IDLE_CLOSE_MS = 8_000L
private const val CLOSE_AFTER_CHOICE_MS = 3_000L
private const val CLOSE_AFTER_ACTION_MS = 3_500L
private const val INTERPRET_TIMEOUT_MS = 3_500L
private const val TAG = "VoiceController"

/**
 * 음성 명령 흐름: 한마디 → (1) 키워드 규칙으로 확실한 것은 바로 처리, (2) 아니면 서버(Haiku)가 앱이 할 수 있는
 * 동작 하나로 해석 → 실행기가 앱 기능에 연결 → 화면 문구와 읽어 주기로 답.
 * 서버가 3초 안에 못 오거나 해석하지 못하면 예시 문장 버튼으로 되돌아간다.
 * 위험할 수 있는 동작(운전 중 공조 변경 등)은 "응/아니야"로 되묻고 실행한다.
 * 말이 안 들리는 상황을 위해 문장 버튼·입력창이 같은 [submit]을 탄다.
 */
class VoiceController(
    private val io: VoiceIo,
    private val care: CareController,
    private val vehicle: VehicleRepository,
    private val navi: NaviRepository,
    private val api: TripApi,
    private val scope: CoroutineScope,
) {
    private val main = Handler(Looper.getMainLooper())
    private val closeTimer = AutoCloseTimer(object : TimerClock {
        override fun postDelayed(task: Runnable, delayMs: Long) {
            main.postDelayed(task, delayMs)
        }

        override fun cancel(task: Runnable) = main.removeCallbacks(task)
    })
    private val _state = MutableStateFlow(VoiceUiState())
    val state: StateFlow<VoiceUiState> = _state.asStateFlow()

    /** 화면 쪽 기능 통로. MainActivity가 꽂는다. */
    @Volatile
    var host: VoiceHost? = null

    private var answeredAt = 0L

    /** 되물은 동작. "응"이 오면 실행한다. */
    private var pending: (suspend () -> Unit)? = null

    /** 마이크 버튼: 패널을 열고 한 문장을 듣는다. 이미 열려 있으면 다시 듣는다. */
    fun startListening() {
        cancelClose()
        io.stopSpeaking()
        pending = null
        // 이전 답변·후보를 지우고 새로 듣는다 (이미 열려 있어도 같다).
        _state.value = VoiceUiState(open = true, listening = true)
        io.listen(onPartial = { partial -> _state.update { it.copy(heard = partial) } }) { result ->
            _state.update { it.copy(listening = false) }
            when (result) {
                is ListenResult.Heard -> submit(result.text)
                is ListenResult.Failed -> {
                    _state.update { it.copy(heard = "", notice = "${result.reason}. 아래 문장 버튼을 누르거나 다시 말씀해 주세요.") }
                    scheduleClose(IDLE_CLOSE_MS)
                }
            }
        }
    }

    /** 인식한 문장, 문장 버튼, 시연 입력창이 모두 여기로 들어온다. */
    fun submit(text: String) {
        // 주행 중 1곳 제안에 대한 답은 여기서 먼저 받는다. 관계없는 말은 무시해 8초 타이머를 그대로 둔다.
        val proposal = _state.value.proposal
        if (proposal != null && proposal.waiting) {
            when (classifyProposalReply(text)) {
                ProposalReply.ACCEPT -> {
                    cancelClose()
                    io.cancelListen()
                    acceptProposal()
                    return
                }
                ProposalReply.DECLINE -> {
                    cancelClose()
                    io.cancelListen()
                    declineProposal()
                    return
                }
                ProposalReply.OTHER -> if (parseUtterance(text) !is Utterance.Situation) return
            }
        }
        cancelClose()
        io.cancelListen()
        _state.update { it.copy(open = true, listening = false, notice = null, heard = text) }
        when (val u = parseUtterance(text)) {
            is Utterance.Situation -> handleSituation(text, u.situation)
            is Utterance.Choose -> handleChoose(u.number)
            Utterance.Cancel -> handleCancel()
            Utterance.Confirm -> handleConfirm()
            Utterance.KeepGoing -> close()
            Utterance.Unknown -> interpretRemotely(text)
        }
    }

    // 서버 해석

    private fun interpretRemotely(text: String) {
        val hasOptions = _state.value.options.isNotEmpty()
        _state.update { it.copy(busy = true, message = "", situation = null, confirm = null) }
        pending = null
        scope.launch {
            val screen = host?.screen()?.copy(hasOptions = hasOptions)
            val result = withTimeoutOrNull(INTERPRET_TIMEOUT_MS) {
                runCatching { api.interpretVoice(VoiceInterpretRequest(text, screen ?: VoiceScreen())) }
                    .onFailure { Log.w(TAG, "interpret failed", it) }
                    .getOrNull()
            }
            val step = result?.let(::toStep)
            if (step == null || step == Step.Unsupported) {
                _state.update {
                    it.copy(
                        busy = false,
                        message = "",
                        notice = if (result == null) "서버와 연결되지 않아 해석하지 못했어요. 아래 문장을 눌러 주세요." else "잘 못 알아들었어요. 아래처럼 말씀해 보세요.",
                    )
                }
                scheduleClose(IDLE_CLOSE_MS)
                return@launch
            }
            execute(step, result.reply)
        }
    }

    // 실행기: 동작 이름 → 앱 기능

    /** [step]을 실행한다. 되물어야 하면 되묻고 멈춘다. [reply]는 서버가 정한 짧은 답변(사실이 필요 없는 동작에만 쓴다). */
    internal suspend fun execute(step: Step, reply: String) {
        val driving = host?.driving ?: false
        confirmPrompt(step, driving)?.let { prompt ->
            pending = { run(step, reply) }
            _state.update { it.copy(busy = false, message = prompt, confirm = prompt, options = emptyList()) }
            io.speak(prompt, care.voiceVolume)
            scheduleClose(IDLE_CLOSE_MS * 2)
            return
        }
        run(step, reply)
    }

    private suspend fun run(step: Step, reply: String) {
        pending = null
        _state.update { it.copy(confirm = null) }
        when (step) {
            is Step.Situation -> handleSituation(_state.value.heard, step.situation)
            is Step.Sleep -> {
                care.setSleepMode(step.on)
                finish(if (step.on) "수면 모드를 켰어요. 안내 음성을 줄이고 알림은 조용히 할게요." else "수면 모드를 껐어요.", tone = if (step.on) CareTone.SLEEP else null)
            }
            is Step.BabyCare -> {
                care.setBabyCare(step.on).join()
                finish(care.state.value.climateMessage ?: reply.ifBlank { "베이비케어를 바꿨어요." })
            }
            is Step.Climate -> runClimate(step)
            Step.FeedLog -> {
                care.markFed()
                val next = care.state.value.feeding.nextAt?.let { "다음 수유는 ${clockLabel(it)}쯤이에요." }.orEmpty()
                finish("수유를 기록했어요. $next".trim(), tone = CareTone.FEED)
            }
            is Step.FeedInterval -> {
                care.setFeedingInterval(step.minutes)
                finish("수유 간격을 ${durationLabel(step.minutes.toLong())}으로 바꿨어요.")
            }
            is Step.Find -> if (host?.driving == true) {
                propose(step.kind, toneFor(step.kind))
            } else {
                val places = care.findNearby(step.kind, 2)
                answerPlaces(step.kind, places, toneFor(step.kind))
            }
            is Step.AddWaypoint -> {
                val place = care.findNearby(step.kind, 1)?.firstOrNull()
                finish(
                    place?.let { care.addWaypoint(it) } ?: "근처에서 ${step.kind.label}을 찾지 못했어요.",
                    tone = toneFor(step.kind),
                )
            }
            Step.RemoveWaypoint -> finish(if (navi.removeWaypoints()) "경유지를 뺐어요." else "안내 중인 경로가 없어요.")
            is Step.GoTab -> {
                host?.goTab(step.tab)
                finish("${step.tab.label} 화면으로 이동했어요.", closeMs = CLOSE_AFTER_ACTION_MS)
            }
            is Step.SetFilter -> {
                host?.setFilter(step.filter, step.on)
                finish(
                    "${step.filter.label} 조건을 ${if (step.on) "넣어" else "빼고"} 코스를 다시 추천해요.",
                    closeMs = CLOSE_AFTER_ACTION_MS,
                )
            }
            Step.AnotherCourses -> {
                host?.anotherCourses()
                finish("다른 코스를 찾아볼게요.", closeMs = CLOSE_AFTER_ACTION_MS)
            }
            is Step.OpenCourse -> finish(
                if (host?.openCourse(step.number) == true) "${step.number}번 코스를 열었어요." else "먼저 홈에서 추천 코스를 불러와 주세요.",
                closeMs = CLOSE_AFTER_ACTION_MS,
            )
            Step.StartCourse -> finish(
                if (host?.startCourse() == true) "코스 안내를 시작해요." else "안내할 코스가 아직 없어요. 홈에서 코스를 열어 주세요.",
                closeMs = CLOSE_AFTER_ACTION_MS,
            )
            is Step.Choose -> handleChoose(step.number)
            Step.Confirm -> handleConfirm()
            Step.Cancel -> handleCancel()
            Step.KeepGoing -> close()
            is Step.Query -> finish(answerQuery(step.topic))
            Step.Unsupported -> finish("이건 아직 할 수 없어요.")
        }
    }

    private suspend fun runClimate(step: Step.Climate) {
        val reading = vehicle.readClimate()
        if (!reading.supported) {
            finish("이 차에서는 공조를 제어할 수 없어요 (지원 차량에서만 · 시동이 켜져 있어야 해요).")
            return
        }
        val change = resolveClimate(step.request, reading)
        val result = vehicle.applyClimateChange(change)
        care.refreshClimate()
        finish(
            when {
                result.applied.isEmpty() -> "공조를 바꾸지 못했어요: ${result.failed.joinToString()}"
                result.failed.isEmpty() -> "공조를 바꿨어요: ${result.applied.joinToString()}"
                else -> "일부만 바꿨어요: ${result.applied.joinToString()} · 바꾸지 못한 항목: ${result.failed.joinToString()}"
            },
        )
    }

    /** 앱이 가진 정보로 하는 답변. 지어내지 않고 없으면 없다고 답한다. */
    private suspend fun answerQuery(topic: VoiceTopic): String = when (topic) {
        VoiceTopic.ETA -> host?.etaAnswer() ?: "안내 정보를 확인할 수 없어요."
        VoiceTopic.NEXT_FEED -> {
            val f = care.state.value.feeding
            val remaining = f.remainingMinutes(System.currentTimeMillis())
            when {
                remaining == null -> "수유 기록이 없어요. \"방금 수유했어\"라고 말씀해 주세요."
                remaining < 0 -> "수유 시간이 ${durationLabel(remaining)} 지났어요."
                else -> "다음 수유까지 ${durationLabel(remaining)} 남았어요. (${clockLabel(f.nextAt!!)})"
            }
        }
        VoiceTopic.LAST_FEED -> care.state.value.feeding.lastFedAt?.let {
            "마지막 수유는 ${clockLabel(it)}, ${durationLabel((System.currentTimeMillis() - it) / 60_000)} 전이에요."
        } ?: "수유 기록이 없어요."
        VoiceTopic.WEATHER -> host?.weatherAnswer() ?: "날씨 정보를 확인할 수 없어요."
        VoiceTopic.CLIMATE -> {
            val r = vehicle.readClimate()
            when {
                !r.supported -> "이 차에서는 공조를 읽을 수 없어요."
                r.power != true -> "공조가 꺼져 있어요."
                else -> "공조는 ${r.temperature?.let { "${it}도" } ?: "설정 온도 확인 불가"}, 풍량 ${r.fanSpeed ?: "?"}단이에요."
            }
        }
    }

    // 케어 상황 4가지

    private fun handleSituation(heard: String, situation: CareSituation) {
        // 주행 중 1곳 제안(D01)은 상황 톤을 입히지 않는다 (수면은 다크 그대로)
        if (situation == CareSituation.SLEEP || host?.driving != true) ToneState.voice = situation.tone
        _state.update { VoiceUiState(open = true, heard = heard, situation = situation, busy = situation != CareSituation.SLEEP) }
        scope.launch {
            if (situation != CareSituation.SLEEP && host?.driving == true) {
                propose(
                    when (situation) {
                        CareSituation.DIAPER -> CareKind.DIAPER
                        CareSituation.FEED -> CareKind.NURSING
                        else -> CareKind.REST
                    },
                    situation.tone,
                )
                return@launch
            }
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

    // 주행 중 1곳 제안 (D01)

    /** [kind]의 후보 중 1곳만 골라 말하고 "좋아"/"아니요"를 기다린다. 8초 안에 답이 없으면 닫힌다. */
    private suspend fun propose(kind: CareKind, @Suppress("UNUSED_PARAMETER") tone: CareTone?) {
        // D01은 상황 톤(옛 수유·기저귀 색)을 입히지 않는다: Figma 356:6661은 기본 밝은 화면 + 어두운 막 위 흰 카드
        val places = care.findNearby(kind, PROPOSAL_CANDIDATES)
        val best = places?.let(::pickBestPlace)
        if (best == null) {
            answer(noProposalMessage(kind, failed = places == null), emptyList())
            return
        }
        val message = proposalMessage(best)
        answeredAt = System.currentTimeMillis()
        pending = { acceptProposal() }
        _state.update {
            it.copy(busy = false, message = message, options = emptyList(), notice = null, confirm = null, proposal = DriveProposal(kind, best))
        }
        // 말이 끝난 때부터 8초를 센다. 말이 안 끝나는 경우를 위해 안전 타이머도 건다.
        scheduleClose(PROPOSAL_SAFETY_CLOSE_MS)
        io.speak(message, care.voiceVolume) { onProposalSpoken() }
    }

    private fun onProposalSpoken() {
        if (_state.value.proposal?.waiting != true) return
        scheduleClose(PROPOSAL_IDLE_CLOSE_MS)
        listenForProposalReply(retries = 2)
    }

    /** 제안 뒤 "좋아"를 말로 받는다. 못 알아들으면 몇 번 더 듣고, 그동안에도 버튼은 눌린다. */
    private fun listenForProposalReply(retries: Int) {
        io.listen { result ->
            if (_state.value.proposal?.waiting != true) return@listen
            when (result) {
                is ListenResult.Heard -> {
                    submit(result.text)
                    if (_state.value.proposal?.waiting == true && retries > 0) listenForProposalReply(retries - 1)
                }
                is ListenResult.Failed -> if (retries > 0) listenForProposalReply(retries - 1)
            }
        }
    }

    private fun acceptProposal() {
        val p = _state.value.proposal ?: return
        pending = null
        val result = care.addWaypoint(p.place)
        answeredAt = System.currentTimeMillis()
        _state.update { it.copy(proposal = p.copy(answer = ProposalAnswer.ACCEPTED), message = result, chosen = 1) }
        io.speak(result, care.voiceVolume)
        scheduleClose(CLOSE_AFTER_CHOICE_MS)
    }

    private fun declineProposal() {
        val p = _state.value.proposal ?: return
        pending = null
        val msg = "알겠어요, 추가하지 않을게요."
        _state.update { it.copy(proposal = p.copy(answer = ProposalAnswer.DECLINED), message = msg) }
        io.speak(msg, care.voiceVolume)
        scheduleClose(1_500L)
    }

    private fun toneFor(kind: CareKind): CareTone? = when (kind) {
        CareKind.NURSING -> CareTone.FEED
        CareKind.DIAPER -> CareTone.DIAPER
        CareKind.REST -> CareTone.SOOTHE
        CareKind.RESTROOM -> null
    }

    private fun answerPlaces(kind: CareKind, places: List<CarePlace>?, tone: CareTone?) {
        tone?.let { ToneState.voice = it }
        answer(
            when {
                places == null -> "${kind.label}을 찾지 못했어요. 연결을 확인해 주세요."
                places.isEmpty() -> "10km 안에서 ${kind.label}을 찾지 못했어요."
                else -> "근처 ${kind.label} ${places.size}곳 찾았어요. ${firstDetail(places)}"
            },
            places.orEmpty(),
        )
    }

    private fun firstDetail(places: List<CarePlace>): String {
        val first = places.first()
        val where = if (first.onRoute) "경로 위에 있어요" else "${first.driveMinutes}분 거리예요"
        return "1번은 ${first.name}, $where."
    }

    private fun answer(message: String, options: List<CarePlace>) {
        answeredAt = System.currentTimeMillis()
        _state.update { it.copy(busy = false, message = message, options = options, notice = null) }
        io.speak(message, care.voiceVolume)
        scheduleClose(IDLE_CLOSE_MS)
    }

    /** 후보 없이 한 문장으로 끝나는 답변. [tone]이 있으면 그 톤을 입히고, [closeMs] 뒤에 닫는다. */
    private fun finish(message: String, tone: CareTone? = null, closeMs: Long = CLOSE_AFTER_CHOICE_MS) {
        tone?.let { ToneState.voice = it.takeIf { t -> t != CareTone.SLEEP } }
        answeredAt = System.currentTimeMillis()
        _state.update { it.copy(busy = false, message = message, options = emptyList(), notice = null, confirm = null) }
        io.speak(message, care.voiceVolume)
        scheduleClose(closeMs)
    }

    // 번호 선택·취소·확인

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

    private fun handleConfirm() {
        val run = pending
        if (run == null) {
            _state.update { it.copy(message = "확인할 내용이 없어요.") }
            scheduleClose(CLOSE_AFTER_CHOICE_MS)
            return
        }
        pending = null
        scope.launch { run() }
    }

    /** 되묻는 중이면 그 동작을 취소한다. 아니면 답변 뒤 3초 안의 "아니야"만 취소로 받는다 (수면 모드를 켰다면 다시 끈다). */
    private fun handleCancel() {
        if (_state.value.proposal?.waiting == true) {
            declineProposal()
            return
        }
        if (pending != null) {
            pending = null
            val msg = "알겠어요, 하지 않을게요."
            io.speak(msg, care.voiceVolume)
            _state.update { it.copy(message = msg, confirm = null) }
            scheduleClose(1_500L)
            return
        }
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
        pending = null
        ToneState.voice = null
        _state.value = VoiceUiState()
    }

    private fun scheduleClose(delayMs: Long) {
        cancelClose()
        closeTimer.start(delayMs) { close() }
    }

    private fun cancelClose() {
        closeTimer.cancel()
    }
}
