package io.github.haeyeonyou.familytrip.ui.onboarding

import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.haeyeonyou.familytrip.data.Account
import io.github.haeyeonyou.familytrip.data.CarLoginPoll
import io.github.haeyeonyou.familytrip.data.CarLoginStart
import io.github.haeyeonyou.familytrip.data.CarLoginStarted
import io.github.haeyeonyou.familytrip.data.ClimateSettings
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.PresetState
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.onboarding.ChildDraft
import io.github.haeyeonyou.familytrip.onboarding.ImportedSummary
import io.github.haeyeonyou.familytrip.onboarding.LinkChoices
import io.github.haeyeonyou.familytrip.onboarding.LinkItem
import io.github.haeyeonyou.familytrip.onboarding.LinkState
import io.github.haeyeonyou.familytrip.onboarding.OnboardingFlow
import io.github.haeyeonyou.familytrip.onboarding.OnboardingMode
import io.github.haeyeonyou.familytrip.onboarding.OnboardingStep
import io.github.haeyeonyou.familytrip.onboarding.linkState
import io.github.haeyeonyou.familytrip.ui.SitterButton
import io.github.haeyeonyou.familytrip.ui.SitterButtonKind
import io.github.haeyeonyou.familytrip.ui.SitterCanvas
import io.github.haeyeonyou.familytrip.ui.SitterIcon
import io.github.haeyeonyou.familytrip.ui.SitterIconSize
import io.github.haeyeonyou.familytrip.ui.SitterIcons
import io.github.haeyeonyou.familytrip.ui.SitterPalette
import io.github.haeyeonyou.familytrip.ui.SitterShapes
import io.github.haeyeonyou.familytrip.ui.SitterSurface
import io.github.haeyeonyou.familytrip.ui.SitterText
import io.github.haeyeonyou.familytrip.ui.SitterToggle
import io.github.haeyeonyou.familytrip.ui.windLabel
import io.github.haeyeonyou.familytrip.ui.volumeLabel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.OffsetDateTime

/** 온보딩이 밖(MainActivity)에 부탁하는 일. 화면은 저장소를 직접 만지지 않는다. */
class OnboardingActions(
    /** QR 로그인이 승인됐다: 계정을 저장하고 서버에서 가족·프리셋·케어 장소를 받아 온다 (끝나면 돌아온다). */
    val onLoggedIn: suspend (Account) -> Unit,
    /** 이 차가 공조 제어를 지원하는지 (모르면 null) */
    val climateSupported: suspend () -> Boolean?,
    /** O03 `다음`: 고른 항목에 필요한 앱 권한을 요청한다. 거절해도 진행한다 (QO6). */
    val requestPermissions: (LinkChoices, () -> Unit) -> Unit,
    val saveChoices: (LinkChoices) -> Unit,
    /** 아이 이름을 음성으로 듣는다 (들은 글을 돌려준다). */
    val listenName: ((String) -> Unit) -> Unit,
    val saveChild: (ChildDraft) -> Unit,
    /** O05에서 고른 아이 (null = 첫째, 케어 화면 C07과 같은 규칙) */
    val selectChild: (String?) -> Unit,
    /** O05 아이 카드에서 고친 그 아이의 케어 모드 값을 서버에 저장한다 (서버 아이 id, null = 가족 기본값). 끝나면 성공 여부. */
    val saveModes: (childId: String?, edits: Map<String, ClimateSettings>, onDone: (Boolean) -> Unit) -> Unit = { _, _, done -> done(false) },
    /** `홈으로`·`나중에 하기`: 완료로 표시하고 홈(H01)으로 */
    val finish: () -> Unit,
    /** 설정에서 연 경우 닫기 */
    val close: () -> Unit,
)

/**
 * 온보딩 O01~O06 (11/10 디자인, Figma `369:20599`~`369:21421`). 앱 셸의 음성 입력줄·칩 줄·바로가기 독이 없는 전용 레이아웃이다.
 * 주행 중에는 버튼·칩은 그대로 쓰고 키보드 입력(아이 이름)만 막는다 (QO8). 입력값은 화면을 돌려도 남는다.
 */
@Composable
fun OnboardingScreen(
    mode: OnboardingMode,
    api: TripApi,
    profile: FamilyProfile?,
    isParked: Boolean,
    presets: List<PresetState>,
    carePlaceNames: List<String>,
    /** 가족 기본값과 아이마다의 케어 모드 값 (O05 카드마다, QO5). 로그인 전이면 빈 목록. */
    allPresets: List<PresetState> = emptyList(),
    selectedChildId: String?,
    choices: LinkChoices,
    firstRecommendation: String?,
    actions: OnboardingActions,
) {
    val hasChildren = !profile?.children.isNullOrEmpty()
    var viaQr by rememberSaveable { mutableStateOf(false) }
    var loggedIn by rememberSaveable { mutableStateOf(false) }
    val path = OnboardingFlow.path(mode, viaQr, hasChildren && (mode == OnboardingMode.EDIT_CHILDREN || loggedIn))
    var step by rememberSaveable { mutableStateOf(path.first()) }
    var draft by remember { mutableStateOf<ChildDraft?>(null) }
    var picking by rememberSaveable { mutableStateOf(false) }
    val today = LocalDate.now()

    fun go(next: OnboardingStep?) {
        when {
            next != null -> step = next
            mode == OnboardingMode.EDIT_CHILDREN -> actions.close()
            else -> actions.finish()
        }
    }
    val stepLabel = OnboardingFlow.stepLabel(step, path)
    val back: (() -> Unit)? = OnboardingFlow.previous(step, path)?.let { prev -> { step = prev } }
    val later = if (mode == OnboardingMode.EDIT_CHILDREN) "닫기" else "나중에 하기"
    val onLater = if (mode == OnboardingMode.EDIT_CHILDREN) actions.close else actions.finish
    val baby = babyCareSummary(presets)

    SitterCanvas(ownsDock = false) {
        Box(Modifier.fillMaxSize().background(SitterSurface.current.app)) {
            OnboardingTopbar(stepLabel = stepLabel.takeIf { step != OnboardingStep.START && step != OnboardingStep.READY }, later = later.takeIf { step != OnboardingStep.START && step != OnboardingStep.READY }, onLater = onLater)
            when (step) {
                OnboardingStep.START -> StartStep(
                    onStart = { viaQr = false; step = OnboardingStep.PERMISSION },
                    onQr = { viaQr = true; step = OnboardingStep.QR },
                )
                OnboardingStep.QR -> QrStep(
                    api = api,
                    loggedIn = loggedIn,
                    imported = io.github.haeyeonyou.familytrip.onboarding.importedSummary(profile, presets.map { presetName(it) }, carePlaceNames, today),
                    onLoggedIn = { account ->
                        actions.onLoggedIn(account)
                        loggedIn = true
                    },
                    onNext = { step = OnboardingStep.PERMISSION },
                    onWithoutAccount = { viaQr = false; step = OnboardingStep.PERMISSION },
                    onBack = back,
                )
                OnboardingStep.PERMISSION -> PermissionStep(
                    initial = choices,
                    climateSupported = actions.climateSupported,
                    onBack = back,
                    onNext = { picked ->
                        actions.saveChoices(picked)
                        actions.requestPermissions(picked) { go(OnboardingFlow.next(OnboardingStep.PERMISSION, path)) }
                    },
                )
                OnboardingStep.CHILD -> {
                    val current = draft ?: ChildDraft().also { draft = it }
                    ChildStep(
                        draft = current,
                        isParked = isParked,
                        babyCare = baby,
                        today = today,
                        onChange = { draft = it },
                        onPickBirth = { picking = true },
                        onListen = { actions.listenName { heard -> draft = (draft ?: ChildDraft()).copy(name = heard.trim().take(ChildDraft.NAME_MAX)) } },
                        onBack = back ?: { step = OnboardingStep.CONFIRM }.takeIf { hasChildren },
                        onNext = {
                            actions.saveChild(current)
                            draft = null
                            step = OnboardingStep.CONFIRM
                        },
                    )
                    if (picking) {
                        BirthSheet(
                            draft = current,
                            today = today,
                            onCancel = { picking = false },
                            onConfirm = { y, m, d ->
                                draft = current.copy(year = y, month = m, day = d)
                                picking = false
                            },
                        )
                    }
                }
                OnboardingStep.CONFIRM -> ConfirmStep(
                    profile = profile,
                    selectedChildId = selectedChildId,
                    allPresets = allPresets,
                    isParked = isParked,
                    onSaveModes = actions.saveModes,
                    today = today,
                    onSelect = actions.selectChild,
                    onEdit = { child ->
                        draft = ChildDraft.from(child, profile?.petAlong == true)
                        step = OnboardingStep.CHILD
                    },
                    onAdd = {
                        draft = ChildDraft(tags = if (profile?.petAlong == true) setOf(io.github.haeyeonyou.familytrip.onboarding.TagAlong.PET) else emptySet())
                        step = OnboardingStep.CHILD
                    },
                    onBack = back,
                    onNext = { go(OnboardingFlow.next(OnboardingStep.CONFIRM, path)) },
                    nextLabel = if (mode == OnboardingMode.EDIT_CHILDREN) "완료" else "다음",
                )
                OnboardingStep.READY -> ReadyStep(
                    profile = profile,
                    selectedChildId = selectedChildId,
                    babyCare = baby,
                    firstRecommendation = firstRecommendation,
                    onHome = actions.finish,
                )
            }
        }
    }
}

/** 베이비케어 기본 한 줄 ("24°C · 미풍 · 음량 낮게"). 폰에서 정한 탑승 준비 프리셋이 있으면 그 값, 없으면 차의 베이비케어 기본값. */
fun babyCareSummary(presets: List<PresetState>): String {
    val s = presets.firstOrNull { it.slot == "boarding" && it.id != null }?.settings
        ?: ClimateSettings(temperature = io.github.haeyeonyou.familytrip.care.BABY_TARGET_TEMPERATURE, fanSpeed = 1)
    val temp = if (s.temperature % 1f == 0f) s.temperature.toInt().toString() else "%.1f".format(s.temperature)
    return listOfNotNull(if (s.power) "${temp}°C" else null, windLabel(s), volumeLabel(s.mediaVolume)).joinToString(" · ")
}

/**
 * O05 아이 카드의 베이비케어 줄 (QO5: 모드 값은 아이별). 서버에서 받은 그 아이의 탑승 준비 값(자기 값이 없으면 가족 기본값)으로
 * "24°C · 약풍 · 음량 낮게", 받은 값이 없으면(로그인 전) "기본값".
 */
fun childBabyCareLine(allPresets: List<PresetState>, child: io.github.haeyeonyou.familytrip.data.Child): String {
    val boarding = io.github.haeyeonyou.familytrip.data.presetsForChild(allPresets, child.serverId).firstOrNull { it.slot == "boarding" }
        ?: return "기본값"
    return io.github.haeyeonyou.familytrip.ui.modeSummary(boarding.settings)
}

private fun presetName(p: PresetState): String = when (p.slot) {
    "boarding" -> if (p.id != null) "베이비케어 ${p.settings.temperature.toInt()}°C" else "베이비케어 기본"
    "nap" -> "낮잠"
    "feeding_stop" -> "수유 정차"
    else -> p.label
}

// ───────────────────────── 공통 틀: Topbar · Footer ─────────────────────────

/** 로고 44 (그라데이션 둥근 사각 + "Sn") + `시터노트`, 오른쪽 단계 `N / M`·`나중에 하기` (Figma Topbar 1192x44, 프레임 (32,24)). */
@Composable
private fun OnboardingTopbar(stepLabel: String?, later: String?, onLater: () -> Unit) {
    val s = SitterSurface.current
    Row(
        modifier = Modifier.offset(32.dp, 24.dp).width(1192.dp).height(44.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppLogo()
        Text("시터노트", style = SitterText.Body1, color = s.ink, modifier = Modifier.padding(start = 12.dp))
        Spacer(Modifier.weight(1f))
        stepLabel?.let {
            Box(Modifier.clip(SitterShapes.Pill).background(s.card).padding(horizontal = 12.dp, vertical = 5.dp)) {
                Text(it, style = SitterText.Label, color = s.ink)
            }
        }
        later?.let {
            Text(
                it,
                style = SitterText.Body2,
                color = s.inkMute,
                modifier = Modifier.padding(start = 6.dp).clip(SitterShapes.Pill).clickable(role = Role.Button, onClick = onLater).padding(horizontal = 8.dp, vertical = 10.dp),
            )
        }
    }
}

@Composable
private fun AppLogo() {
    Box(
        modifier = Modifier.size(44.dp).clip(RoundedCornerShape(12.dp)).background(SitterPalette.Gradient),
        contentAlignment = Alignment.Center,
    ) {
        Text("Sn", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)
    }
}

/** 아래 줄 (Figma Footer 1192x68, 프레임 y=786): 왼쪽 안내 글, 오른쪽 `이전`(110x68)·`다음`(110x68). */
@Composable
internal fun BoxScope.OnboardingFooter(
    hint: String?,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String = "다음",
    nextEnabled: Boolean = true,
) {
    val s = SitterSurface.current
    Row(
        modifier = Modifier.align(Alignment.TopStart).offset(32.dp, 786.dp).width(1192.dp).height(68.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(hint.orEmpty(), style = SitterText.Caption, color = s.inkMute, modifier = Modifier.weight(1f))
        onBack?.let { SitterButton("이전", it, Modifier.width(110.dp).height(68.dp), kind = SitterButtonKind.Soft) }
        SitterButton(nextLabel, onNext, Modifier.width(if (nextLabel.length > 2) 129.dp else 110.dp).height(68.dp), enabled = nextEnabled)
    }
}

/** 아이콘 원 (O02 48 · O03 52, Soft 바탕 + Primary 아이콘). */
@Composable
internal fun IconBubble(icon: ImageVector, size: Int = 52, tint: Color = SitterSurface.current.primary) {
    Box(Modifier.size(size.dp).clip(CircleShape).background(SitterSurface.current.primarySoft), contentAlignment = Alignment.Center) {
        SitterIcon(icon, tint, size = (size * 0.46f).dp)
    }
}

// ───────────────────────── O01 시작 ─────────────────────────

@Composable
private fun BoxScope.StartStep(onStart: () -> Unit, onQr: () -> Unit) {
    val s = SitterSurface.current
    Column(Modifier.offset(72.dp, 180.dp).width(520.dp)) {
        Text(
            "우리 아이 경험 내비게이션",
            style = SitterText.Label,
            color = s.primary,
            modifier = Modifier.clip(SitterShapes.Tag).background(s.primarySoft).padding(horizontal = 12.dp, vertical = 5.dp),
        )
        Text("아이와의 외출,\n출발 전부터 함께할게요", style = SitterText.Display.copy(fontSize = 46.sp, lineHeight = 66.sp), color = s.ink, modifier = Modifier.padding(top = 22.dp))
        Text(
            "일정을 보고 미리 준비하고, 이동 중엔 함께 돌보고,\n다녀온 경험은 아이의 경험 젤리로 키워요",
            style = SitterText.Body1.copy(fontWeight = FontWeight.Normal, lineHeight = 31.sp),
            color = s.inkSoft,
            modifier = Modifier.padding(top = 26.dp),
        )
    }
    Column(Modifier.offset(72.dp, 560.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        SitterButton("시작하기", onStart, Modifier.fillMaxWidth().height(68.dp))
        SitterButton("웹앱 계정으로 시작 · QR", onQr, Modifier.fillMaxWidth().height(68.dp), kind = SitterButtonKind.Line)
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 6.dp)) {
            SitterIcon(SitterIcons.Info, s.inkMute, size = SitterIconSize.Small)
            Text("주행 중에는 이름을 입력할 수 없어요 · 정차하면 이어서 진행돼요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(start = 8.dp))
        }
    }
    Column(Modifier.offset(672.dp, 120.dp).width(520.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        ValueCard("READY", "미리 준비", "일정에 맞춰 연료·타이어를 챙기고, 경로 위 수유실을 먼저 찾아둬요", OnboardingIcons.Car, 180)
        ValueCard("CARE", "함께 돌봄", "\"애 잔다\" \"수유할게\" 한마디로 온도·바람·음량을 아이에게 맞춰요", SitterIcons.Heart, 155)
        ValueCard("GROW", "같이 성장", "다녀온 곳이 경험 젤리로 자라고, 다음 외출을 추천해요", SitterIcons.Sprout, 155)
    }
}

@Composable
private fun ValueCard(kicker: String, title: String, body: String, icon: ImageVector, height: Int) {
    val s = SitterSurface.current
    Column(Modifier.fillMaxWidth().height(height.dp).clip(SitterShapes.Card).background(s.card).padding(horizontal = 28.dp, vertical = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconBubble(icon, size = 56)
            Column(Modifier.padding(start = 16.dp)) {
                Text(kicker, style = SitterText.Label, color = s.primary)
                Text(title, style = SitterText.Title2, color = s.ink)
            }
        }
        Text(body, style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(top = 14.dp))
    }
}

// ───────────────────────── O02 웹앱 계정 연결 (QR) ─────────────────────────

private const val TAG = "OnboardingQr"
private const val POLL_MS = 2_000L
private const val RETRY_MS = 5_000L

/**
 * QR 로그인 (기존 `LoginScreen.kt`의 요청·폴링 로직을 옮김): 서버에 요청을 만들고 QR을 띄운 뒤 폰에서 승인할 때까지 폴링한다.
 * 만료되거나 `새 QR 받기`를 누르면 새 요청을 만든다. 승인되면 가족·프리셋·케어 장소를 받아 오른쪽 목록에 체크한다.
 */
@Composable
private fun BoxScope.QrStep(
    api: TripApi,
    loggedIn: Boolean,
    imported: ImportedSummary,
    onLoggedIn: suspend (Account) -> Unit,
    onNext: () -> Unit,
    onWithoutAccount: () -> Unit,
    onBack: (() -> Unit)?,
) {
    val s = SitterSurface.current
    var started by remember { mutableStateOf<CarLoginStarted?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var round by remember { mutableStateOf(0) }
    var importing by remember { mutableStateOf(false) }
    var leftSec by remember { mutableStateOf<Long?>(null) }
    var totalSec by remember { mutableStateOf(300L) }

    LaunchedEffect(round, loggedIn) {
        if (loggedIn) return@LaunchedEffect
        while (true) {
            try {
                started = null
                val request = api.startCarLogin(CarLoginStart(deviceName = "Pleos Connect 차량"))
                started = request
                error = null
                totalSec = secondsUntil(request.expiresAt)?.coerceAtLeast(1) ?: 300L
                while (true) {
                    delay(POLL_MS)
                    val result = api.pollCarLogin(request.requestId, CarLoginPoll(request.pollSecret))
                    if (result.status == "approved" && result.token != null && result.user != null) {
                        importing = true
                        try {
                            onLoggedIn(Account(result.token, result.user))
                        } finally {
                            importing = false
                        }
                        return@LaunchedEffect
                    }
                    if (result.status != "pending") break // 만료되면 새 QR
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "car login failed", e)
                started = null
                error = "서버에 연결하지 못했어요. 잠시 후 다시 시도할게요."
                delay(RETRY_MS)
            }
        }
    }
    // 남은 시간 카운트다운 (서버가 준 만료 시각 기준)
    LaunchedEffect(started) {
        val req = started ?: run { leftSec = null; return@LaunchedEffect }
        while (true) {
            leftSec = secondsUntil(req.expiresAt)?.coerceAtLeast(0)
            delay(1_000)
        }
    }

    // 왼쪽 QR 카드 (684,180 → 프레임 32,104 · 560x585)
    Column(Modifier.offset(32.dp, 104.dp).width(560.dp).height(585.dp).clip(SitterShapes.Card).background(s.card).padding(36.dp)) {
        Text(if (loggedIn) "연결됐어요" else "휴대폰으로 스캔해 주세요", style = SitterText.Title2, color = s.ink)
        Text(
            if (loggedIn) "웹앱 계정으로 로그인했어요. 오른쪽에서 불러온 것을 확인해 주세요" else "카메라로 QR을 비추면 웹앱 계정으로 로그인돼요",
            style = SitterText.Body2,
            color = s.inkSoft,
            modifier = Modifier.padding(top = 14.dp),
        )
        Box(
            Modifier.padding(top = 22.dp).align(Alignment.CenterHorizontally).size(351.dp).clip(SitterShapes.Card).border(1.5.dp, s.line, SitterShapes.Card),
            contentAlignment = Alignment.Center,
        ) {
            val request = started
            when {
                loggedIn -> SitterIcon(SitterIcons.Check, s.primary, size = 96.dp)
                importing -> Text("불러오는 중…", style = SitterText.Body1, color = s.inkSoft)
                request != null -> {
                    AsyncImage(model = request.qrUrl, contentDescription = "로그인 QR", modifier = Modifier.size(316.dp))
                    Box(Modifier.size(56.dp).clip(RoundedCornerShape(14.dp)).background(Color.White).padding(3.dp)) { AppLogo() }
                }
                error != null -> Text(error!!, style = SitterText.Body2, color = SitterPalette.Danger, modifier = Modifier.padding(24.dp))
                else -> Text("QR을 만드는 중…", style = SitterText.Body1, color = s.inkSoft)
            }
        }
        Spacer(Modifier.weight(1f))
        if (!loggedIn) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val left = leftSec
                Text(
                    "남은 시간 ${left?.let { "%d:%02d".format(it / 60, it % 60) } ?: "-:--"}",
                    style = SitterText.Label,
                    color = s.inkSoft,
                    modifier = Modifier.clip(SitterShapes.Pill).background(s.tile).padding(horizontal = 12.dp, vertical = 6.dp),
                )
                Box(Modifier.padding(horizontal = 14.dp).weight(1f).height(4.dp).clip(SitterShapes.Pill).background(s.tile)) {
                    val ratio = ((left ?: 0L).toFloat() / totalSec).coerceIn(0f, 1f)
                    Box(Modifier.fillMaxWidth(ratio).height(4.dp).background(s.primary))
                }
                Text(
                    "새 QR 받기",
                    style = SitterText.Button.copy(fontSize = 18.sp),
                    color = s.primary,
                    modifier = Modifier.clip(SitterShapes.Pill).clickable(role = Role.Button) { round++ }.padding(horizontal = 6.dp, vertical = 8.dp),
                )
            }
        }
    }

    // 오른쪽 '로그인하면 함께 가져와요' (1276,180 → 624,104 · 600x514). 수유 기록 줄은 QO3로 뺐다.
    Column(Modifier.offset(624.dp, 104.dp).width(600.dp).clip(SitterShapes.Card).background(s.card).padding(32.dp), verticalArrangement = Arrangement.spacedBy(22.dp)) {
        Text("로그인하면 함께 가져와요", style = SitterText.Title3, color = s.ink)
        ImportRow(OnboardingIcons.Smile, "아이 정보", imported.children, loggedIn, "웹앱에서 넣은 아이 이름 · 생년월일")
        ImportRow(OnboardingIcons.Fan, "케어 모드 값", imported.careModes, loggedIn, "베이비케어 · 낮잠 · 수유 정차")
        ImportRow(SitterIcons.Star, "자주 가는 케어 장소", imported.carePlaces, loggedIn, "자주 들른 수유실 · 케어 장소")
        Row(
            Modifier.fillMaxWidth().clip(SitterShapes.Row).background(s.primarySoft).padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            SitterIcon(OnboardingIcons.Car, s.primary, size = SitterIconSize.Small)
            Text("이 차에 쌓인 방문·수유 기록도 계정으로 합쳐져요", style = SitterText.Label, color = s.primary, modifier = Modifier.padding(start = 10.dp))
        }
    }

    if (loggedIn) {
        OnboardingFooter(hint = null, onBack = null, onNext = onNext)
    } else {
        Row(Modifier.offset(624.dp, 800.dp).width(600.dp), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("계정이 없나요?", style = SitterText.Body2, color = s.inkMute)
            Text(
                "이 차에서 바로 시작 ›",
                style = SitterText.Body1.copy(fontWeight = FontWeight.Bold),
                color = s.ink,
                modifier = Modifier.padding(start = 6.dp).clip(SitterShapes.Pill).clickable(role = Role.Button, onClick = onWithoutAccount).padding(8.dp),
            )
        }
        onBack?.let {
            Text(
                "‹ 처음으로",
                style = SitterText.Body2,
                color = s.inkMute,
                modifier = Modifier.offset(32.dp, 800.dp).clip(SitterShapes.Pill).clickable(role = Role.Button, onClick = it).padding(8.dp),
            )
        }
    }
}

private fun secondsUntil(iso: String?): Long? = iso?.let {
    runCatching { OffsetDateTime.parse(it).toInstant().epochSecond - Instant.now().epochSecond }.getOrNull()
}

@Composable
private fun ImportRow(icon: ImageVector, title: String, value: String?, loggedIn: Boolean, placeholder: String) {
    val s = SitterSurface.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconBubble(icon, size = 48)
        Column(Modifier.padding(start = 16.dp).weight(1f)) {
            Text(title, style = SitterText.Body1, color = s.ink)
            Text(
                when {
                    value != null -> value
                    loggedIn -> "계정에 아직 없어요"
                    else -> placeholder
                },
                style = SitterText.Caption,
                color = s.inkMute,
                maxLines = 1,
            )
        }
        if (loggedIn && value != null) SitterIcon(SitterIcons.Check, s.primary, size = SitterIconSize.Medium)
    }
}

// ───────────────────────── O03 차량 · 앱 권한 ─────────────────────────

@Composable
private fun BoxScope.PermissionStep(
    initial: LinkChoices,
    climateSupported: suspend () -> Boolean?,
    onBack: (() -> Unit)?,
    onNext: (LinkChoices) -> Unit,
) {
    val s = SitterSurface.current
    var picked by remember { mutableStateOf(initial) }
    var climateOk by remember { mutableStateOf<Boolean?>(null) }
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { scope.launch { climateOk = runCatching { climateSupported() }.getOrNull() } }

    StepTitle("차와 연결할 항목을 확인해 주세요", "정차 중에만 화면을 조작하고, 주행 중에는 음성으로만 움직여요")
    Column(Modifier.offset(32.dp, 204.dp).width(588.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        listOf(LinkItem.VEHICLE, LinkItem.DOOR, LinkItem.LOCATION).forEach { item ->
            LinkRow(item, icon = linkIcon(item)) {
                Text("필수", style = SitterText.Label, color = s.inkSoft, modifier = Modifier.clip(SitterShapes.Pill).background(s.tile).padding(horizontal = 12.dp, vertical = 5.dp))
            }
        }
    }
    Column(Modifier.offset(636.dp, 204.dp).width(588.dp), verticalArrangement = Arrangement.spacedBy(13.dp)) {
        listOf(LinkItem.CLIMATE, LinkItem.VOICE, LinkItem.CALENDAR).forEach { item ->
            val state = linkState(item, picked, climateOk)
            LinkRow(item, icon = linkIcon(item), dimmed = state == LinkState.COMING_SOON, note = when (state) {
                LinkState.GUIDE_ONLY -> "이 차는 안내만 해요"
                LinkState.COMING_SOON -> "준비 중이에요"
                else -> null
            }) {
                when (state) {
                    LinkState.GUIDE_ONLY -> Text("안내만", style = SitterText.Label, color = s.inkSoft, modifier = Modifier.clip(SitterShapes.Pill).background(s.tile).padding(horizontal = 12.dp, vertical = 5.dp))
                    LinkState.COMING_SOON -> Text("준비 중", style = SitterText.Label, color = s.inkMute, modifier = Modifier.clip(SitterShapes.Pill).background(s.tile).padding(horizontal = 12.dp, vertical = 5.dp))
                    else -> SitterToggle(checked = state == LinkState.ON, onCheckedChange = { on ->
                        picked = if (item == LinkItem.CLIMATE) picked.copy(climate = on) else picked.copy(voice = on)
                    })
                }
            }
        }
    }
    OnboardingFooter(
        hint = "공조 제어를 지원하지 않는 차종은 해당 항목만 안내로 바뀌어요 · 나중에 설정에서 바꿀 수 있어요",
        onBack = onBack,
        onNext = { onNext(picked) },
    )
}

private fun linkIcon(item: LinkItem): ImageVector = when (item) {
    LinkItem.VEHICLE -> OnboardingIcons.Car
    LinkItem.DOOR -> OnboardingIcons.Door
    LinkItem.LOCATION -> SitterIcons.Nav
    LinkItem.CLIMATE -> OnboardingIcons.Fan
    LinkItem.VOICE -> SitterIcons.Mic
    LinkItem.CALENDAR -> SitterIcons.Clock
}

@Composable
private fun LinkRow(item: LinkItem, icon: ImageVector, dimmed: Boolean = false, note: String? = null, trailing: @Composable () -> Unit) {
    val s = SitterSurface.current
    Row(
        Modifier.fillMaxWidth().height(92.dp).clip(SitterShapes.Row).background(s.card).padding(horizontal = 24.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconBubble(icon, tint = if (dimmed) s.inkMute else s.primary)
        Column(Modifier.padding(start = 18.dp).weight(1f)) {
            Text(item.title, style = SitterText.Title3, color = if (dimmed) s.inkMute else s.ink)
            Text(note ?: item.description, style = SitterText.Caption, color = s.inkMute, maxLines = 1)
        }
        trailing()
    }
}

@Composable
internal fun StepTitle(title: String, subtitle: String) {
    val s = SitterSurface.current
    Column(Modifier.offset(32.dp, 102.dp)) {
        Text(title, style = SitterText.Title1, color = s.ink)
        Text(subtitle, style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(top = 12.dp))
    }
}
