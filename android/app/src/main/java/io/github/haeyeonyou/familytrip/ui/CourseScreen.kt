package io.github.haeyeonyou.familytrip.ui

import androidx.compose.runtime.Composable
import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseResponse
import io.github.haeyeonyou.familytrip.data.FeedingRoutine

/** 코스 편집 동작. 모두 서버에서 시간표를 다시 계산한다. */
data class CourseEditActions(
    val swap: (stopIndex: Int, place: CoursePlace) -> Unit,
    val move: (stopIndex: Int, delta: Int) -> Unit,
    val remove: (stopIndex: Int) -> Unit,
    val changeStay: (stopIndex: Int, deltaMinutes: Int) -> Unit,
    val add: (role: String, place: CoursePlace) -> Unit,
    /** H02-2 주차장 고르기 (#250). 고른 곳이 그 장소의 길안내 도착지가 된다. */
    val pickParking: (stopIndex: Int, lot: io.github.haeyeonyou.familytrip.data.StopParking) -> Unit = { _, _ -> },
)

/**
 * 코스 화면. 11/10 디자인 H02(상세)·H02-1(대체 장소·경유지 편집)은 [CourseOverview]가 그린다.
 * 옛 편집 화면(머무름 ±30분·장소 추가·상세 펼치기)은 H02-1 패널로 옮겼다.
 */
@Composable
fun CourseScreen(
    course: CourseResponse,
    isParked: Boolean,
    loading: Boolean,
    error: String?,
    message: String?,
    feeding: FeedingRoutine,
    edit: CourseEditActions,
    onSave: () -> Unit,
    onStartGuide: () -> Unit,
    onBack: () -> Unit,
    weather: io.github.haeyeonyou.familytrip.data.WeatherInfo? = null,
    going: Int = 0,
    origin: io.github.haeyeonyou.familytrip.data.LatLng? = null,
    /** 12개월 이하 아이가 있을 때만 수유 지점·수유 여유를 보인다 (#217). */
    showFeeding: Boolean = true,
) = DrivingAware(isParked) {
    CourseOverview(course, loading, error, message, feeding, edit, onStartGuide, onSave = onSave, onBack = onBack, weather = weather, going = going, origin = origin, showFeeding = showFeeding)
}
