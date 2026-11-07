package io.github.haeyeonyou.familytrip.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.CourseStop

/** 코스 편집 동작. 모두 서버에서 시간표를 다시 계산한다. */
data class CourseEditActions(
    val swap: (stopIndex: Int, place: CoursePlace) -> Unit,
    val move: (stopIndex: Int, delta: Int) -> Unit,
    val remove: (stopIndex: Int) -> Unit,
    val changeStay: (stopIndex: Int, deltaMinutes: Int) -> Unit,
    val add: (role: String, place: CoursePlace) -> Unit,
)

private const val STAY_STEP_MINUTES = 30

@Composable
fun CourseScreen(
    course: CourseResponse,
    isParked: Boolean,
    loading: Boolean,
    error: String?,
    message: String?,
    edit: CourseEditActions,
    onSave: () -> Unit,
    onStartGuide: () -> Unit,
    onBack: () -> Unit,
) = DrivingAware(isParked) {
    var editing by remember { mutableStateOf(false) }
    // 상세를 펼친 장소, 교체 후보를 펼친 장소 (각각 하나만)
    var detail by remember { mutableStateOf<Int?>(null) }
    var swapping by remember { mutableStateOf<Int?>(null) }
    val visits = course.stops.count { it.role != "home" }

    // 아래 버튼 줄은 스크롤과 상관없이 늘 보이게 고정한다.
    Column(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 40.dp, vertical = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(if (editing) "코스 편집" else "오늘의 코스", fontSize = 40.sp)
                    Text(
                        "${course.departureAt.ifBlank { "-" }} 출발 · ${course.returnAt} 귀가 · 이동 ${course.totalDriveMinutes}분" +
                            if (course.routeSource == "estimate") " (추정)" else "",
                        fontSize = 26.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                BigOutlinedButton(if (editing) "편집 끝내기" else "코스 편집", {
                    editing = !editing
                    swapping = null
                })
            }
            course.warnings.forEach { Text("⚠ $it", fontSize = 22.sp, color = MaterialTheme.colorScheme.tertiary) }

            course.stops.forEachIndexed { index, stop ->
                val isVisit = stop.role != "home"
                StopCard(
                    stop = stop,
                    next = course.stops.getOrNull(index + 1),
                    alternatives = if (isVisit) course.alternatives[stop.role].orEmpty() else emptyList(),
                    showDetail = detail == index && isVisit,
                    showSwap = swapping == index,
                    enabled = !loading,
                    onToggleDetail = { if (isVisit) detail = if (detail == index) null else index },
                    onToggleSwap = { swapping = if (swapping == index) null else index },
                    onPick = { place ->
                        swapping = null
                        edit.swap(index, place)
                    },
                    editControls = if (editing && isVisit) {
                        {
                            EditControls(
                                stop = stop,
                                canMoveUp = index > 0,
                                canMoveDown = index < visits - 1,
                                canRemove = visits > 1,
                                enabled = !loading,
                                onMove = { delta ->
                                    detail = null
                                    edit.move(index, delta)
                                },
                                onStay = { delta -> edit.changeStay(index, delta) },
                                onRemove = {
                                    detail = null
                                    edit.remove(index)
                                },
                            )
                        }
                    } else {
                        null
                    },
                )
            }

            if (editing) AddStopSection(course, enabled = !loading, onAdd = edit.add)
        }
        HorizontalDivider()
        Column(
            modifier = Modifier.padding(horizontal = 40.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (loading) CircularProgressIndicator()
            ErrorText(error)
            message?.let { Text(it, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary) }
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                BigButton("안내 시작", onStartGuide, modifier = Modifier.weight(1f), enabled = !loading)
                BigOutlinedButton("코스 저장", onSave)
                BigOutlinedButton("조건 바꾸기", onBack)
            }
        }
    }
}

@Composable
private fun StopCard(
    stop: CourseStop,
    next: CourseStop?,
    alternatives: List<CoursePlace>,
    showDetail: Boolean,
    showSwap: Boolean,
    enabled: Boolean,
    onToggleDetail: () -> Unit,
    onToggleSwap: () -> Unit,
    onPick: (CoursePlace) -> Unit,
    editControls: (@Composable () -> Unit)?,
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(
                    modifier = Modifier.weight(1f).clickable(onClick = onToggleDetail),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.width(140.dp)) {
                        Text(stop.arriveAt, fontSize = 30.sp)
                        Text(ROLE_LABELS[stop.role] ?: stop.role, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    }
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stop.place.name, fontSize = 28.sp)
                        val detail = buildList {
                            add("이동 ${stop.driveMinutes}분")
                            if (stop.stayMinutes > 0) add("머무름 ${stop.stayMinutes}분")
                            stop.place.shortCategory()?.let(::add)
                        }
                        Text(detail.joinToString(" · "), fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        AmenityBadges(stop.place.amenities)
                    }
                }
                if (alternatives.isNotEmpty()) {
                    OutlinedButton(onClick = onToggleSwap, enabled = enabled, modifier = Modifier.heightIn(min = 64.dp)) {
                        Text(if (showSwap) "닫기" else "다른 곳", fontSize = 22.sp)
                    }
                }
            }
            editControls?.invoke()
            if (showDetail) PlaceDetail(stop, next)
            if (showSwap) {
                HorizontalDivider()
                Text("이 장소 대신 갈 수 있는 곳", fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                alternatives.forEach { place -> PlaceRow(place, "이곳으로", enabled) { onPick(place) } }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EditControls(
    stop: CourseStop,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    canRemove: Boolean,
    enabled: Boolean,
    onMove: (Int) -> Unit,
    onStay: (Int) -> Unit,
    onRemove: () -> Unit,
) {
    // 세로 창에서는 버튼이 다음 줄로 넘어간다.
    FlowRow(
        verticalArrangement = Arrangement.spacedBy(8.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SmallAction("↑ 위로", enabled && canMoveUp) { onMove(-1) }
        SmallAction("↓ 아래로", enabled && canMoveDown) { onMove(1) }
        Text("머무름", fontSize = 20.sp, modifier = Modifier.align(Alignment.CenterVertically).padding(start = 24.dp))
        SmallAction("−$STAY_STEP_MINUTES", enabled) { onStay(-STAY_STEP_MINUTES) }
        Text("${stop.stayMinutes}분", fontSize = 24.sp, modifier = Modifier.align(Alignment.CenterVertically))
        SmallAction("+$STAY_STEP_MINUTES", enabled) { onStay(STAY_STEP_MINUTES) }
        SmallAction("빼기", enabled && canRemove, onRemove)
    }
}

@Composable
private fun SmallAction(text: String, enabled: Boolean, onClick: () -> Unit) {
    OutlinedButton(onClick = onClick, enabled = enabled, modifier = Modifier.heightIn(min = 64.dp)) {
        Text(text, fontSize = 22.sp)
    }
}

/** 장소 상세: 사진, 주소, 편의 정보 원문, 코스 안에서의 위치 (혜연 P04). */
@Composable
private fun PlaceDetail(stop: CourseStop, next: CourseStop?) {
    val place = stop.place
    HorizontalDivider()
    Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
        if (place.imageUrl.isNotBlank()) {
            AsyncImage(
                // TourAPI 이미지는 http로 오지만 같은 주소가 https도 지원한다 (평문 통신은 막혀 있음).
                model = place.imageUrl.replaceFirst("http://", "https://"),
                contentDescription = place.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.width(320.dp).height(200.dp).clip(RoundedCornerShape(12.dp)),
            )
        }
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (place.address.isNotBlank()) DetailLine("주소", place.address)
            if (place.source == "kakao" && place.category.isNotBlank()) DetailLine("분류", place.category)
            val position = buildList {
                add("${stop.arriveAt} 도착")
                if (stop.stayMinutes > 0) add("${stop.stayMinutes}분 머무름")
                next?.let { add("다음 장소까지 ${it.driveMinutes}분") }
            }
            DetailLine("코스에서", position.joinToString(" · "))
            place.amenityNotes.forEach { (key, note) ->
                DetailLine(AMENITY_LABELS[key] ?: if (key == "infant_etc") "유아 기타" else key, note)
            }
            if (place.amenities.values.any { it != "available" }) {
                Text("'정보 없음'은 시설이 없다는 뜻이 아니에요. 방문 전에 확인해 주세요.", fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
        Text(label, fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
        Text(value, fontSize = 20.sp, modifier = Modifier.weight(1f))
    }
}

/** 편집 중 코스에 넣을 수 있는 후보 (빠진 카페, 근처 식당 등). */
@Composable
private fun AddStopSection(course: CourseResponse, enabled: Boolean, onAdd: (String, CoursePlace) -> Unit) {
    val candidates = listOf("meal", "outing", "cafe").mapNotNull { role ->
        course.alternatives[role]?.takeIf { it.isNotEmpty() }?.let { role to it }
    }
    if (candidates.isEmpty()) return
    // 후보가 많아 기본은 접어 둔다.
    var open by remember { mutableStateOf(false) }
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text("장소 추가", fontSize = 28.sp)
                    Text("코스 끝(귀가 전)에 들어가요. 위아래 버튼으로 순서를 옮길 수 있어요.", fontSize = 20.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                SmallAction(if (open) "접기" else "후보 보기", enabled = true) { open = !open }
            }
            if (open) {
                candidates.forEach { (role, places) ->
                    Text(ROLE_LABELS[role] ?: role, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                    places.forEach { place -> PlaceRow(place, "추가", enabled) { onAdd(role, place) } }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(place: CoursePlace, action: String, enabled: Boolean, onClick: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(place.name, fontSize = 24.sp)
                listOfNotNull(place.shortCategory(), place.address.takeIf { it.isNotBlank() })
                    .takeIf { it.isNotEmpty() }
                    ?.let { Text(it.joinToString(" · "), fontSize = 18.sp, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                AmenityBadges(place.amenities)
            }
            BigButton(action, onClick, enabled = enabled)
        }
    }
}

/** 카카오 분류는 "음식점 > 한식"처럼 오므로 마지막 단계만 쓴다. TourAPI 분류코드는 보여 주지 않는다. */
private fun CoursePlace.shortCategory(): String? =
    category.substringAfterLast(" > ").takeIf { it.isNotBlank() && source == "kakao" }

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AmenityBadges(amenities: Map<String, String>) {
    if (amenities.isEmpty()) return
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        amenities.forEach { (key, value) ->
            val label = AMENITY_LABELS[key] ?: key
            // '정보 없음'은 없다는 뜻이 아니므로 그대로 표시한다.
            AssistChip(onClick = {}, label = { Text(if (value == "available") "$label 있음" else "$label 정보 없음", fontSize = 18.sp) })
        }
    }
}
