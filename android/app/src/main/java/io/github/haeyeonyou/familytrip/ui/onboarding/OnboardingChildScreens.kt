package io.github.haeyeonyou.familytrip.ui.onboarding

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.care.childKey
import io.github.haeyeonyou.familytrip.care.selectedChildIndex
import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.onboarding.BirthWheel
import io.github.haeyeonyou.familytrip.onboarding.ChildDraft
import io.github.haeyeonyou.familytrip.onboarding.NapChoice
import io.github.haeyeonyou.familytrip.onboarding.TagAlong
import io.github.haeyeonyou.familytrip.onboarding.birthLabel
import io.github.haeyeonyou.familytrip.onboarding.coursePreview
import io.github.haeyeonyou.familytrip.onboarding.monthsLabel
import io.github.haeyeonyou.familytrip.onboarding.napPreview
import io.github.haeyeonyou.familytrip.onboarding.tagAlongLabel
import io.github.haeyeonyou.familytrip.ui.SitterButton
import io.github.haeyeonyou.familytrip.ui.SitterButtonKind
import io.github.haeyeonyou.familytrip.ui.SitterChip
import io.github.haeyeonyou.familytrip.ui.SitterIcon
import io.github.haeyeonyou.familytrip.ui.SitterIconSize
import io.github.haeyeonyou.familytrip.ui.SitterIcons
import io.github.haeyeonyou.familytrip.ui.SitterPalette
import io.github.haeyeonyou.familytrip.ui.SitterShapes
import io.github.haeyeonyou.familytrip.ui.SitterSurface
import io.github.haeyeonyou.familytrip.ui.SitterText
import java.time.LocalDate

/** 아바타 색 (Figma O04·O05: 첫째 노랑, 둘째 하늘). 11/10 팔레트에 없는 아바타 전용 색이라 여기에만 둔다. */
private val AvatarColors = listOf(Color(0xFFFFE58A) to Color(0xFF4E3F00), Color(0xFFCDEBFF) to Color(0xFF14507A), Color(0xFFE6E8FF) to Color(0xFF3138A8))

@Composable
private fun Avatar(name: String, index: Int, size: Int) {
    val (fill, ink) = AvatarColors[index.coerceAtLeast(0) % AvatarColors.size]
    Box(Modifier.size(size.dp).clip(CircleShape).background(fill), contentAlignment = Alignment.Center) {
        Text(name.take(1).ifBlank { "?" }, style = if (size >= 64) SitterText.Title2 else SitterText.Title3, color = ink)
    }
}

// ───────────────────────── O04 아이 정보 입력 ─────────────────────────

@Composable
internal fun BoxScope.ChildStep(
    draft: ChildDraft,
    isParked: Boolean,
    babyCare: String,
    today: LocalDate,
    onChange: (ChildDraft) -> Unit,
    onPickBirth: () -> Unit,
    onListen: () -> Unit,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
) {
    val s = SitterSurface.current
    val months = draft.ageMonths(today)
    StepTitle("아이를 알려주세요", "월령에 맞춰 케어 모드와 추천 코스를 준비해요")

    // 왼쪽 폼 (684,266 → 32,190 · 740 폭). 수유 간격 칸은 QO3로 뺐다.
    Column(Modifier.offset(32.dp, 190.dp).width(740.dp).clip(SitterShapes.Card).background(s.card).padding(28.dp)) {
        FieldLabel("이름 또는 애칭")
        NameField(draft.name, isParked, onChange = { onChange(draft.copy(name = it.take(ChildDraft.NAME_MAX))) }, onListen = onListen)

        FieldLabel("생년월일", top = 26)
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
            BirthTile(draft.year?.toString(), "년", onPickBirth)
            BirthTile(draft.month?.toString(), "월", onPickBirth)
            BirthTile(draft.day?.toString(), "일", onPickBirth)
            months?.let { AgePill(monthsLabel(it)) }
        }

        FieldLabel("낮잠 시간", optional = true, top = 26)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            NapChoice.entries.forEach { nap ->
                SitterChip(nap.label, selected = draft.nap == nap, onClick = { onChange(draft.copy(nap = if (draft.nap == nap) null else nap)) })
            }
        }

        FieldLabel("함께 챙길 것", optional = true, top = 26)
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            TagAlong.entries.forEach { tag ->
                SitterChip(tag.label, selected = tag in draft.tags, onClick = { onChange(draft.copy(tags = if (tag in draft.tags) draft.tags - tag else draft.tags + tag)) })
            }
        }
    }

    // 오른쪽 미리보기 (1440,176 → 788,100 · 436 폭)
    Column(Modifier.offset(788.dp, 100.dp).width(436.dp).clip(SitterShapes.Card).background(s.card).padding(24.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(draft.name, (draft.editing?.let { 0 } ?: 0), 64)
            Column(Modifier.padding(start = 16.dp)) {
                Text(draft.name.ifBlank { "이름" }, style = SitterText.Title2, color = if (draft.name.isBlank()) s.inkMute else s.ink)
                Text(
                    draft.birthDate?.let { "${monthsLabel(months ?: 0)} · ${it.year}.${it.monthValue}.${it.dayOfMonth}" } ?: "생년월일을 골라 주세요",
                    style = SitterText.Caption,
                    color = s.inkMute,
                )
            }
        }
        Text("이렇게 맞춰 드려요", style = SitterText.Body1.copy(fontWeight = FontWeight.Bold), color = s.primary, modifier = Modifier.padding(top = 18.dp, bottom = 12.dp))
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            PreviewRow(OnboardingIcons.Fan, "베이비케어 기본", babyCare)
            PreviewRow(SitterIcons.Moon, "낮잠 · 수면 모드", napPreview(draft.nap))
            PreviewRow(SitterIcons.Sprout, "추천 코스", coursePreview(draft.tags, months))
        }
        Text("아이 정보는 이 차와 내 계정에만 저장돼요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 16.dp))
    }

    OnboardingFooter(
        hint = when {
            draft.name.isBlank() -> "이름을 넣어 주세요"
            draft.birthDate == null -> "생년월일을 골라 주세요"
            else -> null
        },
        onBack = onBack,
        onNext = onNext,
        nextEnabled = draft.isComplete(today),
    )
}

@Composable
private fun FieldLabel(text: String, optional: Boolean = false, top: Int = 0) {
    val s = SitterSurface.current
    Row(Modifier.padding(top = top.dp, bottom = 12.dp), verticalAlignment = Alignment.Bottom) {
        Text(text, style = SitterText.Body1.copy(fontWeight = FontWeight.Bold), color = s.ink)
        if (optional) Text("선택", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(start = 8.dp))
    }
}

/**
 * 이름 입력 (684x66, 최대 10자, 오른쪽 글자 수와 마이크). 주행 중에는 키보드를 막고 마이크만 쓴다 (QO8).
 * 마이크는 이름 한 마디를 듣고 칸을 채운다.
 */
@Composable
private fun NameField(value: String, isParked: Boolean, onChange: (String) -> Unit, onListen: () -> Unit) {
    val s = SitterSurface.current
    Row(
        Modifier.fillMaxWidth().height(66.dp).clip(RoundedCornerShape(16.dp)).background(s.card).border(1.5.dp, if (isParked) s.primary else s.line, RoundedCornerShape(16.dp)).padding(start = 22.dp, end = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.weight(1f)) {
            if (isParked) {
                BasicTextField(
                    value = value,
                    onValueChange = { onChange(it.replace("\n", "")) },
                    singleLine = true,
                    textStyle = SitterText.Title3.copy(fontWeight = FontWeight.Medium, color = s.ink),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (value.isEmpty()) Text("예: 서아", style = SitterText.Title3.copy(fontWeight = FontWeight.Medium), color = s.inkMute)
            } else {
                Text(
                    value.ifEmpty { "주행 중에는 마이크로 말해 주세요" },
                    style = SitterText.Title3.copy(fontWeight = FontWeight.Medium),
                    color = if (value.isEmpty()) s.inkMute else s.ink,
                    maxLines = 1,
                )
            }
        }
        Text("${value.length}/${ChildDraft.NAME_MAX}", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(horizontal = 12.dp))
        Box(
            Modifier.size(44.dp).clip(CircleShape).background(s.primarySoft).clickable(role = Role.Button, onClick = onListen),
            contentAlignment = Alignment.Center,
        ) { SitterIcon(SitterIcons.Mic, s.primary, size = SitterIconSize.Small) }
    }
}

@Composable
private fun BirthTile(value: String?, unit: String, onClick: () -> Unit) {
    val s = SitterSurface.current
    Row(
        Modifier.width(180.dp).height(64.dp).clip(RoundedCornerShape(16.dp)).background(s.tile).clickable(role = Role.Button, onClick = onClick).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(value ?: "-", style = SitterText.Title3.copy(fontWeight = FontWeight.Medium), color = if (value == null) s.inkMute else s.ink, modifier = Modifier.weight(1f))
        Text(unit, style = SitterText.Caption, color = s.inkMute)
    }
}

@Composable
private fun AgePill(text: String) {
    val s = SitterSurface.current
    Text(text, style = SitterText.Label, color = s.primary, modifier = Modifier.clip(SitterShapes.Pill).background(s.primarySoft).padding(horizontal = 12.dp, vertical = 6.dp))
}

@Composable
private fun PreviewRow(icon: ImageVector, title: String, value: String) {
    val s = SitterSurface.current
    Row(Modifier.fillMaxWidth().clip(SitterShapes.Item).background(s.tile).padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(s.card), contentAlignment = Alignment.Center) { SitterIcon(icon, s.primary, size = SitterIconSize.Small) }
        Column(Modifier.padding(start = 14.dp)) {
            Text(title, style = SitterText.Body1, color = s.ink)
            Text(value, style = SitterText.Caption, color = s.inkSoft, maxLines = 2)
        }
    }
}

// ───────────────────────── O04-1 생년월일 휠 시트 ─────────────────────────

/**
 * 생년월일 휠 (Figma 시트 `369:21297`, 700x564). 앱 영역만 어둡게 덮고, 키보드 없이 손가락 스크롤만 쓴다.
 * 고르는 즉시 "생후 N개월"이 바뀐다. 미래 날짜는 고를 수 없다 (출산 예정일 모드는 QO2로 범위 밖).
 */
@Composable
internal fun BoxScope.BirthSheet(draft: ChildDraft, today: LocalDate, onCancel: () -> Unit, onConfirm: (Int, Int, Int) -> Unit) {
    val s = SitterSurface.current
    val start = BirthWheel.clamp(draft.year ?: (today.year - 1), draft.month ?: today.monthValue, draft.day ?: 1, today)
    var year by remember { mutableIntStateOf(start.first) }
    var month by remember { mutableIntStateOf(start.second) }
    var day by remember { mutableIntStateOf(start.third) }
    fun set(y: Int, m: Int, d: Int) {
        val (cy, cm, cd) = BirthWheel.clamp(y, m, d, today)
        year = cy; month = cm; day = cd
    }
    val months = ChildDraft(year = year, month = month, day = day).ageMonths(today) ?: 0

    Box(
        Modifier.fillMaxSize().background(SitterPalette.Scrim.copy(alpha = 0.45f))
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onCancel),
    )
    Column(
        Modifier.offset(278.dp, 161.dp).width(700.dp).clip(SitterShapes.Sheet).background(s.card)
            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {}
            .padding(36.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text("생년월일", style = SitterText.Title1, color = s.ink)
                Text("날짜를 고르면 월령이 바로 계산돼요", style = SitterText.Body2, color = s.inkSoft, modifier = Modifier.padding(top = 8.dp))
            }
            AgePill(monthsLabel(months))
        }
        Row(
            Modifier.padding(top = 24.dp).fillMaxWidth().height(250.dp).clip(SitterShapes.Card).background(s.tile),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Wheel(BirthWheel.years(today), year, { "${it}년" }, { "$it" }, { set(it, month, day) }, Modifier.weight(1f))
            Wheel(BirthWheel.months(year, today), month, { "${it}월" }, { "$it" }, { set(year, it, day) }, Modifier.weight(1f))
            Wheel(BirthWheel.days(year, month, today), day, { "${it}일" }, { "$it" }, { set(year, month, it) }, Modifier.weight(1f))
        }
        Row(Modifier.padding(top = 20.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("오늘보다 늦은 날짜는 고를 수 없어요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.weight(1f))
            SitterButton("취소", onCancel, Modifier.width(110.dp).height(68.dp), kind = SitterButtonKind.Soft)
            Spacer(Modifier.width(12.dp))
            SitterButton("확인", { onConfirm(year, month, day) }, Modifier.width(110.dp).height(68.dp))
        }
    }
}

private const val WHEEL_ITEM_DP = 50
private const val WHEEL_VISIBLE = 5

/** 휠 한 열. 가운데 줄이 고른 값이고, 손을 떼면 가장 가까운 줄에 맞춰 멈춘다. */
@Composable
private fun Wheel(values: List<Int>, selected: Int, selectedLabel: (Int) -> String, label: (Int) -> String, onSelect: (Int) -> Unit, modifier: Modifier) {
    val s = SitterSurface.current
    val state = rememberLazyListState(initialFirstVisibleItemIndex = values.indexOf(selected).coerceAtLeast(0))
    val fling = rememberSnapFlingBehavior(lazyListState = state)
    val itemPx = with(LocalDensity.current) { WHEEL_ITEM_DP.dp.toPx() }
    val center by remember(values) {
        derivedStateOf { (state.firstVisibleItemIndex + if (state.firstVisibleItemScrollOffset > itemPx / 2) 1 else 0).coerceIn(0, values.lastIndex) }
    }
    val latestSelected by rememberUpdatedState(selected)
    val latestOnSelect by rememberUpdatedState(onSelect)
    LaunchedEffect(values) {
        snapshotFlow { center to state.isScrollInProgress }.collect { (at, moving) ->
            if (!moving) values.getOrNull(at)?.let { if (it != latestSelected) latestOnSelect(it) }
        }
    }
    // 다른 열 때문에 값이 당겨지면(예: 31일 → 30일) 그 줄로 옮긴다.
    LaunchedEffect(values, selected) {
        val at = values.indexOf(selected)
        if (at >= 0 && at != center && !state.isScrollInProgress) state.scrollToItem(at)
    }
    Box(modifier.height((WHEEL_ITEM_DP * WHEEL_VISIBLE).dp), contentAlignment = Alignment.Center) {
        Box(Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(WHEEL_ITEM_DP.dp).clip(SitterShapes.Item).background(s.card))
        LazyColumn(
            state = state,
            flingBehavior = fling,
            contentPadding = PaddingValues(vertical = (WHEEL_ITEM_DP * (WHEEL_VISIBLE / 2)).dp),
            modifier = Modifier.fillMaxSize(),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            items(values) { v ->
                val on = v == values.getOrNull(center)
                Box(Modifier.height(WHEEL_ITEM_DP.dp).fillMaxWidth().clickable { onSelect(v) }, contentAlignment = Alignment.Center) {
                    Text(
                        if (on) selectedLabel(v) else label(v),
                        style = if (on) SitterText.Title2 else SitterText.Title3.copy(fontWeight = FontWeight.Normal),
                        color = if (on) s.ink else s.inkMute,
                        textAlign = TextAlign.Center,
                    )
                }
            }
        }
    }
}

// ───────────────────────── O05 아이 확인 · 추가 ─────────────────────────

/**
 * 등록된 아이 카드·선택·편집·추가. 고른 아이는 케어 화면 아이 선택(C07)과 같은 값이라 케어 화면이 그 아이로 시작한다.
 * 카드의 베이비케어 줄은 **가족 공통 값**이다 (QO5 보류, 아이별 모드 값이 정해지면 바꾼다).
 */
@Composable
internal fun BoxScope.ConfirmStep(
    profile: FamilyProfile?,
    selectedChildId: String?,
    babyCare: String,
    today: LocalDate,
    onSelect: (String?) -> Unit,
    onEdit: (Child) -> Unit,
    onAdd: () -> Unit,
    onBack: (() -> Unit)?,
    onNext: () -> Unit,
    nextLabel: String,
) {
    val s = SitterSurface.current
    val children = profile?.children.orEmpty()
    val selectedIndex = if (children.isEmpty()) -1 else selectedChildIndex(children, selectedChildId)
    StepTitle("함께 다니는 아이를 선택해 주세요", "고른 아이로 케어 화면이 시작해요 · 케어 모드 값은 가족이 함께 써요")

    Row(
        Modifier.offset(32.dp, 204.dp).width(1192.dp).height(277.dp).horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        children.forEachIndexed { index, child ->
            val key = childKey(children, index)
            // 둘째부터는 서버 id가 있어야 고를 수 있다 (로그인 전에는 첫째만, 케어 화면 C07과 같은 규칙)
            val selectable = index == 0 || key != null
            ChildCard(
                child = child,
                index = index,
                selected = index == selectedIndex,
                babyCare = babyCare,
                today = today,
                onClick = { if (index == selectedIndex || !selectable) onEdit(child) else onSelect(key) },
                onEdit = { onEdit(child) },
            )
        }
        AddChildCard(onAdd)
    }
    val second = children.getOrNull(1)?.name?.takeIf { it.isNotBlank() }
    Row(
        Modifier.offset(32.dp, 504.dp).width(1192.dp).height(56.dp).clip(RoundedCornerShape(16.dp)).background(s.primarySoft).padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        SitterIcon(SitterIcons.Info, s.primary, size = SitterIconSize.Small)
        Text(
            if (second != null) "형제가 함께 타면 케어 화면의 아이 버튼으로 바꿔요 · 주행 중에는 \"${callName(second)} 바꿔줘\"" else "형제 · 자매도 따로 등록하면 케어 화면에서 아이를 바꿔 쓸 수 있어요",
            style = SitterText.Label,
            color = s.primary,
            modifier = Modifier.padding(start = 10.dp),
        )
    }
    OnboardingFooter(
        hint = if (children.isEmpty()) "아이를 추가하면 월령에 맞춰 준비해요 · 지금 안 해도 돼요" else "카드를 한 번 더 누르면 고칠 수 있어요",
        onBack = onBack,
        onNext = onNext,
        nextLabel = nextLabel,
    )
}

/** "도윤" → "도윤이로", "서아" → "서아로" (받침이 있으면 '이로'). */
private fun callName(name: String): String {
    val ch = name.last()
    val final = ch in '가'..'힣' && (ch - '가') % 28 != 0
    return if (final) "${name}이로" else "${name}로"
}

@Composable
private fun ChildCard(child: Child, index: Int, selected: Boolean, babyCare: String, today: LocalDate, onClick: () -> Unit, onEdit: () -> Unit) {
    val s = SitterSurface.current
    Column(
        Modifier.width(386.dp).height(277.dp).clip(SitterShapes.Card).background(s.card)
            .then(if (selected) Modifier.border(2.dp, s.primary, SitterShapes.Card) else Modifier)
            .clickable(role = Role.Button, onClick = onClick)
            .padding(28.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Avatar(child.name, index, 72)
            Column(Modifier.padding(start = 16.dp).weight(1f)) {
                Text(child.name.ifBlank { "아이 ${index + 1}" }, style = SitterText.Title2, color = s.ink)
                Text("${monthsLabel(child.ageMonthsOn(today))} · ${birthLabel(child)}", style = SitterText.Body2, color = s.inkMute)
            }
            if (selected) {
                Box(Modifier.size(44.dp).clip(CircleShape).background(s.primary), contentAlignment = Alignment.Center) {
                    SitterIcon(SitterIcons.Check, Color.White, size = SitterIconSize.Medium)
                }
            } else {
                Box(Modifier.size(44.dp).clip(CircleShape).background(s.tile).clickable(role = Role.Button, onClick = onEdit), contentAlignment = Alignment.Center) {
                    SitterIcon(SitterIcons.Pencil, s.inkSoft, size = SitterIconSize.Small)
                }
            }
        }
        Column(
            Modifier.padding(top = 18.dp).fillMaxWidth().clip(SitterShapes.Item).background(s.tile).padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            CardLine(SitterIcons.Moon, child.napTimes?.let { "낮잠 $it" } ?: "낮잠 정보 없음")
            CardLine(SitterIcons.Baby, tagAlongLabel(child))
            CardLine(OnboardingIcons.Fan, "베이비케어 $babyCare")
        }
    }
}

@Composable
private fun CardLine(icon: ImageVector, text: String) {
    val s = SitterSurface.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        SitterIcon(icon, s.primary, size = SitterIconSize.Small)
        Text(text, style = SitterText.Body2, color = s.inkSoft, maxLines = 1, modifier = Modifier.padding(start = 10.dp))
    }
}

@Composable
private fun AddChildCard(onAdd: () -> Unit) {
    val s = SitterSurface.current
    val dash = with(LocalDensity.current) { 8.dp.toPx() }
    val stroke = with(LocalDensity.current) { 2.dp.toPx() }
    val radius = with(LocalDensity.current) { 24.dp.toPx() }
    val line = s.primary
    Column(
        Modifier.width(386.dp).height(277.dp)
            .drawBehind {
                drawRoundRect(color = line, style = Stroke(width = stroke, pathEffect = PathEffect.dashPathEffect(floatArrayOf(dash, dash / 1.5f))), cornerRadius = CornerRadius(radius))
            }
            .clip(SitterShapes.Card)
            .clickable(role = Role.Button, onClick = onAdd),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(64.dp).clip(CircleShape).background(s.card), contentAlignment = Alignment.Center) { SitterIcon(SitterIcons.Plus, s.primary, size = 30.dp) }
        Text("아이 추가", style = SitterText.Title3, color = s.primary, modifier = Modifier.padding(top = 18.dp))
        Text("형제 · 자매도 따로 등록해요", style = SitterText.Caption, color = s.inkMute, modifier = Modifier.padding(top = 6.dp))
    }
}

// ───────────────────────── O06 준비 완료 ─────────────────────────

/**
 * 완료 요약 3칸과 `홈으로`. 디자인의 '마지막 수유는 언제였나요?' 카드는 수유 기록 기능을 지울 예정이라(QO3) 넣지 않았다.
 * 첫 추천은 홈이 이미 받아 둔 코스가 있으면 그 이름, 없으면 홈에서 고른다는 안내다 (가짜 이름을 쓰지 않는다).
 */
@Composable
internal fun BoxScope.ReadyStep(
    profile: FamilyProfile?,
    selectedChildId: String?,
    babyCare: String,
    firstRecommendation: String?,
    onHome: () -> Unit,
) {
    val s = SitterSurface.current
    val children = profile?.children.orEmpty()
    val child = children.getOrNull(if (children.isEmpty()) 0 else selectedChildIndex(children, selectedChildId))
    val nap = NapChoice.of(child?.napTimes)
    val tags = buildSet {
        if (child?.stroller == true) add(TagAlong.STROLLER)
        if (profile?.petAlong == true) add(TagAlong.PET)
    }
    val months = child?.ageMonthsOn(LocalDate.now())
    val parts = babyCare.split(" · ")

    Column(Modifier.offset(0.dp, 96.dp).fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(Modifier.size(88.dp).clip(CircleShape).background(s.primarySoft), contentAlignment = Alignment.Center) {
            SitterIcon(SitterIcons.Check, s.primary, size = 44.dp)
        }
        Text(
            child?.name?.takeIf { it.isNotBlank() }?.let { "$it 맞춤 준비가 끝났어요" } ?: "준비가 끝났어요",
            style = SitterText.Title1,
            color = s.ink,
            modifier = Modifier.padding(top = 26.dp),
        )
        Text("다음에 탑승하면 베이비케어 기본값이 바로 적용돼요", style = SitterText.Body1.copy(fontWeight = FontWeight.Normal), color = s.inkSoft, modifier = Modifier.padding(top = 14.dp))
    }
    Row(Modifier.offset(32.dp, 330.dp).width(1192.dp).height(201.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        SummaryCard(OnboardingIcons.Fan, "베이비케어 기본", parts.take(2).joinToString(" · "), (parts.drop(2) + "탑승하면 바로").joinToString(" · "))
        SummaryCard(SitterIcons.Moon, "낮잠 · 수면", nap?.takeIf { it != NapChoice.IRREGULAR }?.label ?: "\"애 잔다\"", napPreview(nap))
        SummaryCard(SitterIcons.Sprout, "첫 추천", firstRecommendation ?: "홈에서 골라 드려요", coursePreview(tags, months))
    }
    OnboardingFooter(hint = "아이 정보는 설정 > 아이 정보에서 언제든 바꿀 수 있어요", onBack = null, onNext = onHome, nextLabel = "홈으로")
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.SummaryCard(icon: ImageVector, label: String, value: String, sub: String) {
    val s = SitterSurface.current
    Column(Modifier.weight(1f).height(201.dp).clip(SitterShapes.Card).background(s.card).padding(24.dp)) {
        IconBubble(icon)
        Text(label, style = SitterText.Body2, color = s.inkMute, modifier = Modifier.padding(top = 22.dp))
        Text(value, style = SitterText.Title2, color = s.ink, maxLines = 1, modifier = Modifier.padding(top = 4.dp))
        Text(sub, style = SitterText.Caption, color = s.inkMute, maxLines = 1, modifier = Modifier.padding(top = 6.dp))
    }
}
