package io.github.haeyeonyou.familytrip.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.haeyeonyou.familytrip.grow.CalendarDay
import io.github.haeyeonyou.familytrip.grow.Experience
import io.github.haeyeonyou.familytrip.grow.JellyStage
import io.github.haeyeonyou.familytrip.grow.KindGrowth
import io.github.haeyeonyou.familytrip.grow.PeriodSummary
import io.github.haeyeonyou.familytrip.grow.calendarCells
import io.github.haeyeonyou.familytrip.grow.calendarMonths
import io.github.haeyeonyou.familytrip.grow.dotDate
import io.github.haeyeonyou.familytrip.grow.monthSummary
import io.github.haeyeonyou.familytrip.grow.yearSummary
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** G03에서 보는 기간: 한 달 또는 올해 전체. */
internal sealed interface CalendarView {
    data class Month(val month: YearMonth) : CalendarView
    data class Year(val year: Int) : CalendarView
}

private val SundayRed = Color(0xFFE5484D)
private val SaturdayBlue = Color(0xFF3B6CFF)
private val BirthPink = Color(0xFFD6337F)

/**
 * 그로우 G03 월별 경험 젤리 캘린더 (387:8031, #216). 날짜 칸에 그날 자란 젤리 방울(분야 색), 태어난 날은 캐릭터 + "탄생".
 * 날짜를 누르면 그날 다녀온 곳 목록(QF3 제안, 사진 없음). ‹(제목 왼쪽)·뒤로 가기는 G01.
 */
@Composable
internal fun ExperienceCalendar(
    list: List<Experience>,
    today: LocalDate,
    view: CalendarView,
    avatarLabel: String?,
    weather: @Composable () -> Unit,
    onView: (CalendarView) -> Unit,
    onBack: () -> Unit,
    onRoute: (String, Double, Double) -> Unit,
) {
    val thisMonth = YearMonth.from(today)
    var day by remember { mutableStateOf<CalendarDay?>(null) }
    SitterVoiceBar(hint = "무엇을 볼까요?  “지난달 젤리 보여줘”", avatarLabel = avatarLabel, weather = weather)
    SitterChipRow {
        calendarMonths(today).forEach { m ->
            SitterChip("${m.monthValue}월", view == CalendarView.Month(m), onClick = { onView(CalendarView.Month(m)) })
        }
        SitterChip("올해 전체", view == CalendarView.Year(today.year), onClick = { onView(CalendarView.Year(today.year)) })
    }
    val summary = remember(list, view) {
        when (view) {
            is CalendarView.Month -> monthSummary(list, view.month)
            is CalendarView.Year -> yearSummary(list, view.year)
        }
    }
    val periodName = when (view) {
        is CalendarView.Month -> "${view.month.monthValue}월"
        is CalendarView.Year -> "${view.year}년"
    }
    SitterWideAndColumn(
        wide = {
            SitterCard(Modifier.fillMaxSize(), contentPadding = PaddingValues(start = 20.dp, top = 20.dp, end = 20.dp, bottom = 20.dp)) {
                CalendarHeader(view, thisMonth, onView, onBack)
                when (view) {
                    is CalendarView.Month -> MonthGrid(list, view.month, today, onDay = { day = it })
                    is CalendarView.Year -> YearGrid(list, view.year, today, onMonth = { onView(CalendarView.Month(it)) })
                }
            }
        },
        third = {
            SummaryCard(periodName, summary, Modifier.fillMaxWidth().height(200.dp))
            Spacer(Modifier.height(16.dp))
            GrowthCard(
                title = if (view == CalendarView.Month(thisMonth)) "이번 달 자란 젤리" else "$periodName 자란 젤리",
                rows = summary.growth,
                modifier = Modifier.weight(1f).fillMaxWidth(),
            )
            Spacer(Modifier.height(16.dp))
            ShortcutDock()
        },
    )
    day?.let { d ->
        BackHandler { day = null }
        DaySheet(d, onRoute = onRoute, onClose = { day = null })
    }
}

@Composable
private fun CalendarHeader(view: CalendarView, thisMonth: YearMonth, onView: (CalendarView) -> Unit, onBack: () -> Unit) {
    val s = SitterSurface.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(CircleShape).clickable(role = Role.Button, onClick = onBack),
            contentAlignment = Alignment.Center,
        ) { SitterIcon(SitterIcons.ChevronLeft, s.ink) }
        Column(Modifier.weight(1f).padding(start = 4.dp)) {
            Text(
                when (view) {
                    is CalendarView.Month -> "${view.month.year}년 ${view.month.monthValue}월"
                    is CalendarView.Year -> "${view.year}년 전체"
                },
                style = SitterText.Title2,
                color = s.ink,
            )
            Text(if (view is CalendarView.Month) "경험 젤리로 보는 한 달" else "달을 누르면 그 달 캘린더를 열어요", style = SitterText.Caption, color = s.inkMute)
        }
        if (view is CalendarView.Month) {
            NavArrow(SitterIcons.ChevronLeft, true) { onView(CalendarView.Month(view.month.minusMonths(1))) }
            NavArrow(SitterIcons.ChevronRight, view.month < thisMonth) { onView(CalendarView.Month(view.month.plusMonths(1))) }
        }
    }
}

@Composable
private fun NavArrow(icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    val s = SitterSurface.current
    Box(
        Modifier.size(48.dp).clip(CircleShape).then(if (enabled) Modifier.clickable(role = Role.Button, onClick = onClick) else Modifier),
        contentAlignment = Alignment.Center,
    ) { SitterIcon(icon, if (enabled) s.inkSoft else s.line, size = SitterIconSize.Medium) }
}

@Composable
private fun ColumnScope.MonthGrid(list: List<Experience>, month: YearMonth, today: LocalDate, onDay: (CalendarDay) -> Unit) {
    val s = SitterSurface.current
    val cells = remember(list, month) { calendarCells(list, month) }
    Row(Modifier.fillMaxWidth().padding(top = 10.dp, bottom = 6.dp)) {
        listOf("일", "월", "화", "수", "목", "금", "토").forEachIndexed { i, w ->
            Text(
                w,
                style = SitterText.Label,
                color = when (i) { 0 -> SundayRed; 6 -> SaturdayBlue; else -> s.inkMute },
                textAlign = TextAlign.Center,
                modifier = Modifier.weight(1f),
            )
        }
    }
    Column(Modifier.weight(1f).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        cells.chunked(7).forEach { week ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                week.forEach { c ->
                    if (c == null) Spacer(Modifier.weight(1f)) else DayCell(c, c.date == today, Modifier.weight(1f).fillMaxHeight(), onDay)
                }
            }
        }
    }
}

@Composable
private fun DayCell(c: CalendarDay, isToday: Boolean, modifier: Modifier, onDay: (CalendarDay) -> Unit) {
    val s = SitterSurface.current
    val shape = RoundedCornerShape(14.dp)
    val dayColor = when (c.date.dayOfWeek) {
        DayOfWeek.SUNDAY -> SundayRed
        DayOfWeek.SATURDAY -> SaturdayBlue
        else -> s.ink
    }
    Box(
        modifier.clip(shape).background(if (isToday) s.primarySoft else s.card)
            .border(if (isToday) 2.dp else 1.dp, if (isToday) s.primary else s.line, shape)
            .then(if (c.visits.isNotEmpty()) Modifier.clickable(role = Role.Button) { onDay(c) } else Modifier)
            .padding(8.dp),
    ) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text("${c.date.dayOfMonth}", style = if (isToday) SitterText.Title3 else SitterText.Label, color = dayColor, modifier = Modifier.weight(1f))
            when {
                c.births.isNotEmpty() -> Text(
                    "탄생",
                    style = SitterText.Caption,
                    color = Color.White,
                    modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(BirthPink).padding(horizontal = 6.dp, vertical = 1.dp),
                )
                isToday -> Text("오늘", style = SitterText.Caption, color = s.primary)
            }
        }
        Box(Modifier.fillMaxSize().padding(top = 22.dp), contentAlignment = Alignment.Center) {
            if (c.births.isNotEmpty()) {
                JellyIcon(c.births.first(), JellyStage.BORN, 64.dp)
            } else if (c.kinds.isNotEmpty()) {
                Row(horizontalArrangement = Arrangement.spacedBy((-4).dp), verticalAlignment = Alignment.CenterVertically) {
                    c.kinds.take(3).forEach { JellyIcon(it, JellyStage.DROP, 40.dp) }
                    if (c.kinds.size > 3) Text("+${c.kinds.size - 3}", style = SitterText.Caption, color = s.inkMute)
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.YearGrid(list: List<Experience>, year: Int, today: LocalDate, onMonth: (YearMonth) -> Unit) {
    val s = SitterSurface.current
    val thisMonth = YearMonth.from(today)
    Column(Modifier.weight(1f).fillMaxWidth().padding(top = 14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        (1..12).chunked(4).forEach { row ->
            Row(Modifier.weight(1f).fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                row.forEach { m ->
                    val ym = YearMonth.of(year, m)
                    val sum = remember(list, ym) { monthSummary(list, ym) }
                    val future = ym > thisMonth
                    val shape = RoundedCornerShape(16.dp)
                    Column(
                        Modifier.weight(1f).fillMaxHeight().clip(shape)
                            .background(if (ym == thisMonth) s.primarySoft else s.card)
                            .border(if (ym == thisMonth) 2.dp else 1.dp, if (ym == thisMonth) s.primary else s.line, shape)
                            .then(if (!future) Modifier.clickable(role = Role.Button) { onMonth(ym) } else Modifier)
                            .padding(12.dp),
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${m}월", style = SitterText.Label, color = if (future) s.inkMute else s.ink, modifier = Modifier.weight(1f))
                            if (sum.births.isNotEmpty()) {
                                Text("탄생", style = SitterText.Caption, color = Color.White, modifier = Modifier.clip(RoundedCornerShape(6.dp)).background(BirthPink).padding(horizontal = 6.dp, vertical = 1.dp))
                            }
                        }
                        Text(if (sum.places == 0) "" else "${sum.places}곳 · ${sum.days}일", style = SitterText.Caption, color = s.inkMute)
                        Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Row(horizontalArrangement = Arrangement.spacedBy((-4).dp)) {
                                sum.births.lastOrNull()?.let { JellyIcon(it.first, JellyStage.BORN, 52.dp) }
                                sum.growth.filter { g -> sum.births.none { it.first == g.kind } }.take(if (sum.births.isEmpty()) 4 else 2)
                                    .forEach { JellyIcon(it.kind, JellyStage.DROP, 36.dp) }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** 오른쪽 위: "11월 요약 / 5곳 · 4일 / 젤리 5개 자람 · 새로 태어난 젤리 1 / 11.07 상상의 유니콘 탄생". */
@Composable
private fun SummaryCard(periodName: String, sum: PeriodSummary, modifier: Modifier) {
    val s = SitterSurface.current
    val hero = sum.births.lastOrNull()?.let { it.first to JellyStage.BORN } ?: sum.growth.firstOrNull()?.let { it.kind to it.stage.atLeastDrop() }
    SitterCard(modifier) {
        Row(Modifier.fillMaxSize()) {
            Column(Modifier.weight(1f)) {
                Text("$periodName 요약", style = SitterText.Caption, color = s.inkSoft)
                Text("${sum.places}곳 · ${sum.days}일", style = SitterText.Number, color = s.ink, maxLines = 1)
                Text("젤리 ${sum.grown}개 자람 · 새로 태어난 젤리 ${sum.births.size}", style = SitterText.Caption, color = s.primary, maxLines = 1)
                Spacer(Modifier.weight(1f))
                sum.births.lastOrNull()?.let { (k, d) ->
                    Text(
                        "${dotDate(d)} ${k.typeName} 탄생",
                        style = SitterText.Caption,
                        color = BirthPink,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.clip(SitterShapes.Pill).background(BirthPink.copy(alpha = 0.10f)).padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
                if (sum.grown == 0) Text("이 기간에는 다녀온 곳이 없어요", style = SitterText.Caption, color = s.inkMute)
            }
            hero?.let { (k, st) -> JellyIcon(k, st, 104.dp, Modifier.align(Alignment.CenterVertically)) }
        }
    }
}

/** 오른쪽 아래: 분야별로 이 기간에 자란 만큼 (+N)과 누적 진행 막대 (N/10). */
@Composable
private fun GrowthCard(title: String, rows: List<KindGrowth>, modifier: Modifier) {
    val s = SitterSurface.current
    SitterCard(modifier) {
        Text(title, style = SitterText.Title3, color = s.ink, modifier = Modifier.padding(bottom = 10.dp))
        if (rows.isEmpty()) {
            Text("나들이 장소에 10분 이상 머물면 그 분야 젤리가 자라요", style = SitterText.Body2, color = s.inkMute)
            return@SitterCard
        }
        Column(Modifier.weight(1f).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            rows.forEach { g ->
                val n = g.total.coerceAtMost(JellyStage.BORN.min)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    JellyIcon(g.kind, g.stage.atLeastDrop(), 48.dp)
                    Column(Modifier.weight(1f).padding(start = 10.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("${g.kind.label} · ${stageShort(g.stage)}", style = SitterText.Label, color = s.ink, modifier = Modifier.weight(1f), maxLines = 1)
                            Text("+${g.added}", style = SitterText.Label, color = s.primary)
                        }
                        Box(Modifier.padding(top = 4.dp).fillMaxWidth().height(6.dp).clip(SitterShapes.Pill).background(s.line)) {
                            Box(Modifier.fillMaxHeight().fillMaxWidth(n / JellyStage.BORN.min.toFloat()).clip(SitterShapes.Pill).background(Color(g.kind.argb)))
                        }
                        Text("$n/${JellyStage.BORN.min} (+${g.added})", style = SitterText.Caption, color = s.inkMute)
                    }
                }
            }
        }
    }
}

internal fun stageShort(stage: JellyStage): String = when (stage) {
    JellyStage.NONE -> "비어 있음"
    JellyStage.DROP -> "방울"
    JellyStage.PUFF -> "몽글"
    JellyStage.BORN -> "캐릭터"
}

/** 날짜 탭 (QF3 제안): 그날 다녀온 곳 이름·분야·젤리. 사진은 나중. */
@Composable
private fun DaySheet(d: CalendarDay, onRoute: (String, Double, Double) -> Unit, onClose: () -> Unit) {
    val s = SitterSurface.current
    SitterScrim(onClick = onClose)
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.width(720.dp).clip(SitterShapes.Sheet).background(s.card).pointerInput(Unit) { detectTapGestures { } }.padding(28.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("${d.date.monthValue}월 ${d.date.dayOfMonth}일 다녀온 곳", style = SitterText.Title2, color = s.ink)
                    Text(
                        "젤리 ${d.visits.size}개 자람" + if (d.births.isNotEmpty()) " · ${d.births.joinToString(", ") { it.character }} 탄생" else "",
                        style = SitterText.Body2,
                        color = s.primary,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                SitterButton("닫기", onClick = onClose, kind = SitterButtonKind.Soft)
            }
            Spacer(Modifier.height(18.dp))
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                d.visits.forEach { e ->
                    val v = e.visit
                    SitterRow(contentPadding = PaddingValues(start = 14.dp, top = 10.dp, end = 12.dp, bottom = 10.dp)) {
                        JellyIcon(e.kind, JellyStage.DROP, 48.dp)
                        Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                            Text(v.placeName, style = SitterText.Body1, color = s.ink, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("${e.kind.label} · ${e.kind.character} 젤리 · ${v.arrivedAt.substringAfter('T').take(5)} 도착", style = SitterText.Caption, color = s.inkMute, maxLines = 1)
                        }
                        val lat = v.latitude
                        val lon = v.longitude
                        if (lat != null && lon != null) SitterButton("다시 안내", onClick = { onRoute(v.placeName, lat, lon) }, icon = SitterIcons.Turn)
                    }
                }
            }
        }
    }
}
