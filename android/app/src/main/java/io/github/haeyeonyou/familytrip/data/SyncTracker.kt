package io.github.haeyeonyou.familytrip.data

import kotlinx.serialization.Serializable

/** 서버 `GET /me/sync`의 영역별 변경 표시 (12자 해시). 내용은 없고, 달라졌는지만 비교한다. */
@Serializable
data class SyncMarks(
    val presets: String = "",
    val feedings: String = "",
    val visits: String = "",
    val family: String = "",
    val cars: String = "",
    val all: String = "",
)

/** 이전 표시와 비교해 달라진 영역. */
data class SyncChange(
    val presets: Boolean = false,
    val feedings: Boolean = false,
    val visits: Boolean = false,
    val family: Boolean = false,
    val cars: Boolean = false,
) {
    val any: Boolean get() = presets || feedings || visits || family || cars
}

/**
 * 마지막으로 **반영을 마친** 표시를 기억하고 새 표시와 비교한다.
 * 반영(다시 받기)이 실패하면 [accept]를 부르지 않아, 다음 주기에 같은 변경이 다시 잡힌다.
 * 처음 보는 표시(또는 [reset] 뒤)는 모든 영역이 바뀐 것으로 본다. 그 사이에 바뀐 것을 놓치지 않기 위해서다.
 */
class SyncTracker {
    private var accepted: SyncMarks? = null

    fun diff(marks: SyncMarks): SyncChange {
        val last = accepted ?: return SyncChange(presets = true, feedings = true, visits = true, family = true, cars = true)
        if (last.all == marks.all) return SyncChange()
        return SyncChange(
            presets = last.presets != marks.presets,
            feedings = last.feedings != marks.feedings,
            visits = last.visits != marks.visits,
            family = last.family != marks.family,
            cars = last.cars != marks.cars,
        )
    }

    /**
     * [marks]를 반영을 마친 것으로 기억한다. [areas]에 true로 든 영역만 기억하고 나머지는 이전 값을 유지해,
     * 일부 영역만 실패했을 때 성공한 영역은 다시 받지 않고 실패한 영역만 다음 주기에 다시 받는다.
     */
    fun accept(marks: SyncMarks, areas: SyncChange = SyncChange(presets = true, feedings = true, visits = true, family = true, cars = true)) {
        val base = accepted ?: SyncMarks()
        val everything = areas.presets && areas.feedings && areas.visits && areas.family && areas.cars
        accepted = SyncMarks(
            presets = if (areas.presets) marks.presets else base.presets,
            feedings = if (areas.feedings) marks.feedings else base.feedings,
            visits = if (areas.visits) marks.visits else base.visits,
            family = if (areas.family) marks.family else base.family,
            cars = if (areas.cars) marks.cars else base.cars,
            // 모두 반영했을 때만 전체 표시를 기억한다 (같으면 아무것도 안 바뀐 것으로 보는 지름길)
            all = if (everything) marks.all else base.all,
        )
    }

    /** 로그인한 계정이 바뀌었을 때 처음부터 다시 비교한다. */
    fun reset() {
        accepted = null
    }
}
