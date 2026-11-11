package io.github.haeyeonyou.familytrip.care

import io.github.haeyeonyou.familytrip.data.Child
import io.github.haeyeonyou.familytrip.data.PresetState
import java.time.LocalDate

// 수유 표시를 아이 월령으로 나눈다 (QD1, #217). 12개월 이하 = 수유 내용(Figma C01~C12), 넘으면 유아기(Figma ⑦ C05-T1, 수유 없음).

/** 수유 내용을 보이는 마지막 개월 수. 경계가 바뀌면 이 값만 고친다 (서버 `family.INFANT_MAX_MONTHS`와 같은 값). */
const val INFANT_MAX_MONTHS = 12

/**
 * 수유 내용을 보일 아이인지: 생후 [INFANT_MAX_MONTHS]개월 이하. 개월 수는 [Child.ageMonthsOn] 규칙(태어난 날을 알면 생일 전날까지 한 달 덜 셈).
 * 아이 정보가 없으면(null) 지금까지처럼 수유 내용을 보인다.
 */
fun isInfant(child: Child?, today: LocalDate = LocalDate.now()): Boolean =
    child == null || child.ageMonthsOn(today) <= INFANT_MAX_MONTHS

/** 아이 선택이 없는 화면(홈·코스 상세): 12개월 이하 아이가 한 명이라도 있거나 아이 정보가 없으면 보인다. */
fun anyInfant(children: List<Child>, today: LocalDate = LocalDate.now()): Boolean =
    children.isEmpty() || children.any { isInfant(it, today) }

/** 유아기 칩 글자 "서아 · 14개월 · 유아기". */
fun toddlerChipLabel(base: String): String = "$base · 유아기"

/** 이 아이에게 보일 모드 슬롯. 유아는 수유 정차 모드가 없다 (C08·웹 같은 규칙). */
fun presetsForAge(presets: List<PresetState>, infant: Boolean): List<PresetState> =
    if (infant) presets else presets.filter { it.slot != "feeding_stop" }
