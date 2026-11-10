package io.github.haeyeonyou.familytrip.grow

import io.github.haeyeonyou.familytrip.data.Visit
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/*
 * 그로우 젤리 우주의 순수 계산 (#164). 화면과 분리해 단위 테스트한다.
 *
 * 규칙 (젤리 우주 B안 374:6875 정리본 `docs/development-plan-1110.md` 3.5 + 이 작업에서 정한 정의):
 * - 경험 1개 = 나들이·식사·카페 방문 1회 중 10분 이상 머문 것. 아직 떠나지 않았으면 도착 후 10분이 지나면 센다.
 *   웹에서 받은 기록처럼 떠난 시각이 없는 지난 기록도 도착 후 10분이 지났으므로 센다.
 * - 경험은 12분류 중 하나로 나눈다. 서버 분야는 5종뿐이라 앱이 장소 이름·역할·서버 분야로 판정한다 ([classify]).
 * - 성장 단계는 분류별 누적 경험 수: 1~3 방울, 4~9 몽글, 10 이상 탄생. 한 번 태어난 캐릭터는 줄어들지 않는다.
 * - 조합 유형은 최근 6개월 경험으로 다시 센다: 1위 = 메인 캐릭터, 2위 = "한 스푼".
 * - 한 분류가 전체 경험의 절반을 넘으면(경험 4개 이상일 때) 비어 있는 별을 권한다.
 */

/**
 * 경험 분류 12종과 젤리 캐릭터. [chip]은 칩·짧은 표기, [spoon]은 "○○ 한 스푼"에 쓰는 이름.
 * 색은 Figma 몸통 기본색, 역량 문구는 유니콘만 Figma 원문(374:7878)이고 나머지는 Figma 원칙("~자랄 수 있어요")에 맞춰 이 앱이 썼다.
 */
enum class JellyKind(
    val label: String,
    val chip: String,
    val character: String,
    val typeName: String,
    val spoon: String,
    val power: String,
    val argb: Long,
    /** 원본 그림 파일 이름 (`res/drawable-nodpi/jelly_<art>_<drop|puff|born>.png`, Figma 371:6514) */
    val art: String,
) {
    ART("전시·미술", "전시", "유니콘", "상상의 유니콘형", "유니콘", "보고, 생각하고, 말로 꺼내는 힘과 나와 다른 관점을 받아들이는 마음이 자랄 수 있어요", 0xFFF58AD0, "unicorn"),
    SCIENCE("과학·박물관", "과학", "공룡", "호기심 공룡형", "공룡", "왜 그럴까 묻고 직접 확인하는 힘이 자랄 수 있어요", 0xFF4CC98A, "dinosaur"),
    NATURE("자연·숲", "자연", "곰", "푸근한 숲 곰형", "숲 곰", "계절과 자연을 몸으로 느끼는 힘이 자랄 수 있어요", 0xFF9BD14B, "bear"),
    ANIMAL("동물·교감", "동물", "강아지", "다정한 강아지형", "강아지", "다른 생명을 아끼고 마음을 나누는 힘이 자랄 수 있어요", 0xFFFF8FA3, "puppy"),
    WATER("물놀이", "물놀이", "카피바라", "느긋한 카피바라형", "카피바라", "새로운 감각을 즐기고 긴장을 푸는 힘이 자랄 수 있어요", 0xFF5AAEFF, "capybara"),
    MOVE("몸놀이", "몸놀이", "말", "씩씩한 달리는 말형", "말", "몸을 움직이고 균형을 잡는 힘이 자랄 수 있어요", 0xFFFF6B6B, "horse"),
    MUSIC("음악·공연", "음악", "고양이", "감성 고양이형", "고양이", "소리와 리듬으로 마음을 표현하는 힘이 자랄 수 있어요", 0xFFA07CFF, "cat"),
    BOOK("책·이야기", "책", "웜뱃", "똑똑한 웜뱃형", "웜뱃", "이야기를 듣고 상상하는 힘이 자랄 수 있어요", 0xFFC49A74, "wombat"),
    FOOD("맛 탐험", "맛", "햄스터", "미식가 햄스터형", "햄스터", "새로운 맛을 겁내지 않고 즐기는 힘이 자랄 수 있어요", 0xFFF7C548, "hamster"),
    FRIEND("친구·어울림", "친구", "펭귄", "골목대장 펭귄형", "펭귄", "친구와 어울리고 차례를 기다리는 힘이 자랄 수 있어요", 0xFF6672FF, "penguin"),
    MAKE("만들기", "만들기", "비버", "뚝딱이 비버형", "비버", "손으로 만들고 끝까지 해내는 힘이 자랄 수 있어요", 0xFFFF9A4D, "beaver"),
    TRAVEL("여행", "여행", "수달", "모험가 수달형", "수달", "낯선 곳에서도 씩씩하게 둘러보는 힘이 자랄 수 있어요", 0xFF2EC4C4, "otter"),
}

enum class JellyStage(val min: Int) {
    NONE(0), DROP(1), PUFF(4), BORN(10);

    companion object {
        fun of(count: Int): JellyStage = entries.last { count >= it.min }
    }
}

/** 단계 이름: "유니콘 방울" / "유니콘 몽글" / "상상의 유니콘형". 경험이 없으면 "비어 있는 별". */
fun stageName(kind: JellyKind, stage: JellyStage): String = when (stage) {
    JellyStage.NONE -> "비어 있는 별"
    JellyStage.DROP -> "${kind.character} 방울"
    JellyStage.PUFF -> "${kind.character} 몽글"
    JellyStage.BORN -> kind.typeName
}

const val MIN_STAY_MINUTES = 10L
const val TYPE_WINDOW_MONTHS = 6L
private val EXPERIENCE_ROLES = setOf("outing", "meal", "cafe")

/** 경험으로 세는 방문인가 (10분 이상 머묾). */
fun isExperience(v: Visit, now: LocalDateTime): Boolean {
    if (v.role !in EXPERIENCE_ROLES) return false
    val arrived = runCatching { LocalDateTime.parse(v.arrivedAt) }.getOrNull() ?: return false
    val end = v.departedAt?.let { runCatching { LocalDateTime.parse(it) }.getOrNull() } ?: now
    return ChronoUnit.MINUTES.between(arrived, end) >= MIN_STAY_MINUTES
}

// 앞에서부터 처음 맞는 분류를 쓴다. 더 좁은 말(북카페, 어린이 박물관 등)이 넓은 말보다 먼저 온다.
private val BOOK_WORDS = listOf("도서관", "북카페", "서점", "책방", "그림책", "동화")
private val WATER_WORDS = listOf("워터", "물놀이", "수영장", "해수욕", "풀장", "스파", "계곡")
private val ANIMAL_WORDS = listOf("동물원", "아쿠아리움", "수족관", "목장", "농장", "동물", "애견", "반려", "펫", "승마")
private val MUSIC_WORDS = listOf("공연", "극장", "아트홀", "콘서트", "뮤지컬", "인형극", "음악", "오페라", "국악")
private val ART_WORDS = listOf("미술관", "갤러리", "미술", "아트", "전시")
private val SCIENCE_WORDS = listOf("과학", "박물관", "천문대", "기념관", "역사관", "발명", "우주", "공룡")
private val FRIEND_WORDS = listOf("육아종합지원센터", "육아지원센터", "공동육아", "육아나눔", "문화센터", "어린이집", "키즈클럽")
private val MAKE_WORDS = listOf("클래스", "공방", "만들기", "쿠킹", "체험관", "체험장", "도예", "목공")
private val MOVE_WORDS = listOf("놀이터", "키즈카페", "트램폴린", "놀이공원", "테마파크", "랜드", "방방", "볼풀", "실내놀이", "놀이")
// 서버에서 받은 방문은 역할이 모두 나들이라 이름으로도 맛 탐험을 본다 (키즈카페·북카페는 앞에서 걸러짐)
private val FOOD_WORDS = listOf("카페", "식당", "베이커리", "레스토랑", "맛집", "브런치")
private val NATURE_WORDS = listOf("공원", "숲", "수목원", "생태", "휴양림", "정원", "식물원", "둘레길", "산림", "캠핑")
private val TRAVEL_WORDS = listOf("전망대", "마을", "레일바이크", "케이블카", "유적", "한옥", "관광", "항구", "기차", "섬")

/** 서버 분야(5종) → 이름으로 못 정했을 때 쓰는 분류. */
private val THEME_FALLBACK = mapOf(
    "nature" to JellyKind.NATURE,
    "exhibition" to JellyKind.SCIENCE,
    "experience" to JellyKind.MAKE,
    "play" to JellyKind.MOVE,
    "pet" to JellyKind.ANIMAL,
)

/** 방문 하나의 경험 분류. */
fun classify(v: Visit): JellyKind = classify(v.placeName, v.role, v.theme)

fun classify(placeName: String, role: String, theme: String?): JellyKind {
    val name = placeName.replace(" ", "")
    fun has(words: List<String>) = words.any { it in name }
    return when {
        has(BOOK_WORDS) -> JellyKind.BOOK
        role == "meal" || role == "cafe" -> JellyKind.FOOD
        has(WATER_WORDS) -> JellyKind.WATER
        has(ANIMAL_WORDS) -> JellyKind.ANIMAL
        has(MUSIC_WORDS) -> JellyKind.MUSIC
        has(ART_WORDS) -> JellyKind.ART
        has(SCIENCE_WORDS) -> JellyKind.SCIENCE
        has(FRIEND_WORDS) -> JellyKind.FRIEND
        has(MAKE_WORDS) -> JellyKind.MAKE
        has(MOVE_WORDS) -> JellyKind.MOVE
        has(FOOD_WORDS) -> JellyKind.FOOD
        has(NATURE_WORDS) -> JellyKind.NATURE
        has(TRAVEL_WORDS) -> JellyKind.TRAVEL
        else -> THEME_FALLBACK[theme] ?: JellyKind.TRAVEL
    }
}

/** 경험 하나 (분류가 붙은 방문). */
class Experience(val visit: Visit, val kind: JellyKind) {
    val date: LocalDate get() = visit.date
}

fun experiences(visits: List<Visit>, now: LocalDateTime = LocalDateTime.now()): List<Experience> =
    visits.filter { isExperience(it, now) }.sortedBy { it.arrivedAt }.map { Experience(it, classify(it)) }

/** 분류 하나의 상태. [count]는 누적 경험 수, [recent]는 최근 6개월 경험 수, [places]는 다녀온 장소(같은 곳 1개). */
class JellyState(val kind: JellyKind, val count: Int, val recent: Int, val places: Int, val first: LocalDate?, val last: LocalDate?) {
    val stage: JellyStage get() = JellyStage.of(count)
}

fun jellyStates(list: List<Experience>, today: LocalDate): List<JellyState> {
    val from = today.minusMonths(TYPE_WINDOW_MONTHS)
    return JellyKind.entries.map { kind ->
        val mine = list.filter { it.kind == kind }
        JellyState(
            kind = kind,
            count = mine.size,
            recent = mine.count { it.date > from },
            places = mine.map { it.visit.placeName }.distinct().size,
            first = mine.minOfOrNull { it.date },
            last = mine.maxOfOrNull { it.date },
        )
    }
}

/** 조합 유형: 최근 6개월 1위 메인 + 2위 한 스푼. 최근 경험이 없으면 null. 같은 수면 더 최근에 간 쪽이 앞선다. */
class JellyType(val main: JellyKind, val spoon: JellyKind?) {
    val line: String get() = main.typeName + (spoon?.let { " + ${it.spoon} 한 스푼" } ?: "")
}

fun jellyType(states: List<JellyState>): JellyType? {
    val ranked = states.filter { it.recent > 0 }.sortedWith(compareByDescending<JellyState> { it.recent }.thenByDescending { it.last })
    val main = ranked.firstOrNull() ?: return null
    return JellyType(main.kind, ranked.getOrNull(1)?.kind)
}

/** 아직 비어 있는 별을 고를 때의 순서 (자연·과학·책처럼 아이 첫 나들이로 고르기 쉬운 것 먼저). */
private val SUGGEST_ORDER = listOf(
    JellyKind.NATURE, JellyKind.SCIENCE, JellyKind.BOOK, JellyKind.MAKE, JellyKind.ANIMAL, JellyKind.MUSIC,
    JellyKind.WATER, JellyKind.MOVE, JellyKind.ART, JellyKind.FRIEND, JellyKind.TRAVEL, JellyKind.FOOD,
)

/** 다음에 넓어질 영역: 한 번도 안 간 분류 중 첫째, 모두 가 봤으면 가장 적게 간 분류. */
fun suggestEmpty(states: List<JellyState>): JellyKind {
    val byKind = states.associateBy { it.kind }
    return SUGGEST_ORDER.firstOrNull { (byKind[it]?.count ?: 0) == 0 }
        ?: SUGGEST_ORDER.minBy { byKind[it]?.count ?: 0 }
}

/** 한 분류가 절반을 넘었으면 그 분류. 경험이 4개 미만이면 판단하지 않는다. */
fun dominant(states: List<JellyState>): JellyKind? {
    val total = states.sumOf { it.count }
    if (total < 4) return null
    return states.firstOrNull { it.count * 2 > total }?.kind
}

/** 우주 아래 안내 한 줄. */
fun universeHint(states: List<JellyState>): String {
    if (states.all { it.count == 0 }) return "나들이 장소에 10분 이상 머물면 첫 별이 생겨요"
    val top = dominant(states) ?: return "별을 누르면 다녀온 기록과 다시 가는 길을 볼 수 있어요"
    return "${top.label} 경험이 절반을 넘었어요 · 비어 있는 ${suggestEmpty(states).label} 별도 키워 볼까요?"
}

// ───────────────────────── 오른쪽 타일 ─────────────────────────

/** 이번 달 다녀온 장소 수와 지난달 대비. */
class MonthTile(val places: Int, val delta: Int)

fun monthTile(list: List<Experience>, today: LocalDate): MonthTile {
    val now = YearMonth.from(today)
    fun placesIn(m: YearMonth) = list.filter { YearMonth.from(it.date) == m }.map { it.visit.placeName }.distinct().size
    val cur = placesIn(now)
    return MonthTile(cur, cur - placesIn(now.minusMonths(1)))
}

fun monthDeltaText(delta: Int): String = when {
    delta > 0 -> "지난달보다 +${delta}곳"
    delta < 0 -> "지난달보다 ${delta}곳"
    else -> "지난달과 같아요"
}

/** 새로 해본 분야: 처음 경험한 날이 이번 달인 분류. */
fun newKindsThisMonth(states: List<JellyState>, today: LocalDate): List<JellyKind> {
    val now = YearMonth.from(today)
    return states.filter { it.first != null && YearMonth.from(it.first) == now }.map { it.kind }
}

// ───────────────────────── 다시 가볼까요? ─────────────────────────

class Revisit(
    val placeName: String,
    val kind: JellyKind,
    val count: Int,
    val last: LocalDate,
    val latitude: Double?,
    val longitude: Double?,
    val nursingRoom: Boolean,
)

/** 다시 가 볼 곳: 마지막 방문이 7일 이상 지난 장소를 많이 간 순(같으면 최근 순)으로 [limit]개. */
fun revisits(list: List<Experience>, today: LocalDate, limit: Int = 3): List<Revisit> =
    list.groupBy { it.visit.placeName }.mapNotNull { (name, xs) ->
        val last = xs.maxBy { it.visit.arrivedAt }
        if (ChronoUnit.DAYS.between(last.date, today) < 7) return@mapNotNull null
        val located = xs.lastOrNull { it.visit.latitude != null && it.visit.longitude != null }?.visit
        Revisit(
            placeName = name,
            kind = last.kind,
            count = xs.size,
            last = last.date,
            latitude = located?.latitude,
            longitude = located?.longitude,
            nursingRoom = xs.any { "nursing_room" in it.visit.amenities },
        )
    }.sortedWith(compareByDescending<Revisit> { it.count }.thenByDescending { it.last }).take(limit)

/** "3일 전" / "3주 전" / "10.19" */
fun agoText(date: LocalDate, today: LocalDate): String {
    val days = ChronoUnit.DAYS.between(date, today)
    return when {
        days <= 0 -> "오늘"
        days < 7 -> "${days}일 전"
        days < 35 -> "${days / 7}주 전"
        else -> "${date.monthValue}.${date.dayOfMonth.toString().padStart(2, '0')}"
    }
}

fun revisitCaption(r: Revisit, today: LocalDate): String =
    agoText(r.last, today) + " · " + if (r.nursingRoom) "수유실 있음" else r.kind.label

// ───────────────────────── 탄생 ─────────────────────────

/** 아직 축하하지 않은 탄생 (누적 10회 이상). [celebrated]는 축하한 분류 이름(enum name) 모음. */
fun pendingBirths(states: List<JellyState>, celebrated: Set<String>): List<JellyKind> =
    states.filter { it.stage == JellyStage.BORN && it.kind.name !in celebrated }.map { it.kind }

/** 주행 중 음성 안내 문구 (B안: 화면 팝업 없이 1회). */
fun birthSpeech(childName: String, kinds: List<JellyKind>): String {
    val who = childName.ifBlank { "우리 아이" }
    val names = kinds.joinToString(", ") { it.character }
    return "${who}의 $names 캐릭터가 태어났어요"
}

/** 방문 기록에서 아직 축하하지 않은 탄생. */
fun birthsIn(visits: List<Visit>, celebrated: Set<String>, now: LocalDateTime = LocalDateTime.now()): List<JellyKind> =
    pendingBirths(jellyStates(experiences(visits, now), now.toLocalDate()), celebrated)
