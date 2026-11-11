package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.CourseRequest
import io.github.haeyeonyou.familytrip.data.FamilyProfile
import io.github.haeyeonyou.familytrip.data.LatLng
import io.github.haeyeonyou.familytrip.data.TripApi
import io.github.haeyeonyou.familytrip.data.TripConditions

/**
 * "경험젤리 만들러 갈까요?"의 안가봄 줄 (QF1 ③, #216): 아직 0개인 분야 근처 장소를 서버 추천(`course/suggest`)으로 1번 찾는다.
 * 첫 분야의 서버 분야로 거르고, 결과(코스 후보·대안 장소)에서 [targets] 분야 장소를 고른다([pickUnvisited]).
 * 실패하면 null (화면은 분야만 보이고 홈 추천으로 보낸다).
 */
suspend fun searchUnvisited(
    api: TripApi,
    origin: LatLng,
    profile: FamilyProfile?,
    targets: List<JellyKind>,
    visitedNames: Set<String>,
): UnvisitedPick? {
    val first = targets.firstOrNull() ?: return null
    val base = TripConditions(themes = listOfNotNull(searchTheme(first)))
    val conditions = profile?.applyTo(base) ?: base
    val res = try {
        api.suggest(CourseRequest(conditions = conditions, origin = origin))
    } catch (e: kotlinx.coroutines.CancellationException) {
        throw e
    } catch (e: Exception) {
        return null
    }
    return pickUnvisited(res, targets, visitedNames)
}
