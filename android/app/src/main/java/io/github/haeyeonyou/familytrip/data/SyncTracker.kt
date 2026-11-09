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

    fun accept(marks: SyncMarks) {
        accepted = marks
    }

    /** 로그인한 계정이 바뀌었을 때 처음부터 다시 비교한다. */
    fun reset() {
        accepted = null
    }
}
