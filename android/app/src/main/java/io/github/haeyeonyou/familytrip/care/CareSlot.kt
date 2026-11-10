package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.FeedingRoutine

/**
 * 케어 화면 오른쪽 가변 슬롯(392x370)과 수유 타이머 카드의 상태 판단 (순수 함수, 11/10 디자인 C01·C02·C05·C06).
 * 화면은 이 파일의 판단만 따른다. 우선순위·임박 기준 같은 임시 결정값은 여기 한 곳에서만 정의한다.
 */

/** 수유 타이머 단계. */
enum class FeedingPhase { NoRecord, Normal, Soon, Overdue }

/** 임박 기준(분): 다음 수유 15분 전부터 (C05). 임시 결정값. */
const val FEEDING_SOON_MINUTES = 15L

fun feedingPhase(routine: FeedingRoutine, now: Long): FeedingPhase {
    val remaining = routine.remainingMinutes(now) ?: return FeedingPhase.NoRecord
    return when {
        remaining < 0 -> FeedingPhase.Overdue
        remaining <= FEEDING_SOON_MINUTES -> FeedingPhase.Soon
        else -> FeedingPhase.Normal
    }
}

/** 지난 분(올림 없이 내림). 지나지 않았으면 0. C06 "+10분", "수유 시각이 10분 지났어요" */
fun overdueMinutes(routine: FeedingRoutine, now: Long): Long {
    val remaining = routine.remainingMinutes(now) ?: return 0
    return if (remaining < 0) -remaining else 0
}

/** 슬롯에 들어갈 수 있는 상태. D2 우선순위 순서 = [priority]가 작을수록 먼저. 이번 범위에 없는 종류는 나중에 여기에 추가한다. */
enum class CareSlot(val priority: Int) {
    /** 수유 중 (C03, 2차) */
    FeedingNow(0),
    /** 수유 시각 지남 (C06) */
    FeedingOverdue(1),
    /** 수유 15분 전 (C05) */
    FeedingSoon(2),
    /** 정체로 도착 지연 (C01) */
    Delay(3),
    /** 경유지 추가 완료 (C12, 2차) */
    Waypoint(4),
    /** 수면 (C04, 2차) */
    Sleep(5),
    /** 오늘 케어 기록 (C02, 다른 상태가 없을 때) */
    Today(6),
}

/**
 * 지금 슬롯에 보일 상태 하나. [dismissed]는 사용자가 "나중에"·"그대로 가기"로 닫은 슬롯이라 건너뛴다
 * (그 아래 우선순위가 대신 보인다). [today]는 마지막에 항상 가능한 기본이다.
 */
fun pickCareSlot(
    phase: FeedingPhase,
    delayActive: Boolean,
    dismissed: Set<CareSlot> = emptySet(),
): CareSlot {
    val candidates = buildList {
        if (phase == FeedingPhase.Overdue) add(CareSlot.FeedingOverdue)
        if (phase == FeedingPhase.Soon) add(CareSlot.FeedingSoon)
        if (delayActive) add(CareSlot.Delay)
    }.filter { it !in dismissed }
    return candidates.minByOrNull { it.priority } ?: CareSlot.Today
}

/** 마지막 수유를 "N분 전"에 맞춘 시각(epoch ms) (C02 빠른 칩: 방금·30분·1시간·2시간·직접). */
fun fedAtMinutesAgo(minutesAgo: Int, now: Long): Long = now - minutesAgo * FeedingRoutine.MS_PER_MINUTE

/** C02 빠른 칩. 직접 선택은 별도 입력이라 여기엔 없다. */
val QUICK_FED_CHOICES: List<Pair<String, Int>> = listOf("방금" to 0, "30분 전" to 30, "1시간 전" to 60, "2시간 전" to 120)

/** 경유지 후보 오른쪽 문구: 경로 위면 "경로 위 · +6분", 아니면 "경로에서 +3분", 우회 시간을 모르면 거리·시간. */
fun detourLabel(onRoute: Boolean, detourMinutes: Int?, driveMinutes: Int): String = when {
    detourMinutes == null -> "${driveMinutes}분"
    onRoute -> "경로 위 · +${detourMinutes}분"
    else -> "경로에서 +${detourMinutes}분"
}
