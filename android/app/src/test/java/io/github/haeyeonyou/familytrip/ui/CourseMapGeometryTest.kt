package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.hypot

class CourseMapGeometryTest {
    private fun stop(role: String, name: String, lat: Double, lng: Double) =
        CourseStop(
            role = role,
            place = CoursePlace(name = name, latitude = lat, longitude = lng, source = "kakao", sourceId = name),
            arriveAt = "10:00", driveMinutes = 0, driveDistanceMeters = 0,
        )

    // 판교 일대 실제 좌표 (홈 화면 캡처의 코스와 같은 동네)
    private val home = stop("home", "집", 37.3947, 127.1112)
    private val meal = stop("meal", "신신원", 37.4040, 127.1180)
    private val park = stop("outing", "고산근린공원", 37.4052, 127.1150)
    private val cafe = stop("cafe", "이디야커피", 37.4020, 127.1210)
    private val start = LatLng(37.3947, 127.1112)

    @Test
    fun `출발 지점 다음 장소 번호 순서로 고르고 귀가 지점은 넣지 않는다`() {
        val roadPath = listOf(start, LatLng(37.3990, 127.1150), LatLng(37.4040, 127.1180))
        val a = CourseMapGeometry.anchors(listOf(meal, park, cafe, home), roadPath)
        assertEquals(listOf(null, 1, 2, 3), a.map { it.number })
        assertEquals(listOf("", "신신원", "고산근린공원", "이디야커피"), a.map { it.label })
        // 도로 경로의 중간 점은 쓰지 않는다 (출발 위치만)
        assertEquals(start, a.first().at)
    }

    @Test
    fun `출발 위치를 모르면 1번 장소에서 시작한다`() {
        val a = CourseMapGeometry.anchors(listOf(meal, cafe, home), emptyList())
        assertEquals(listOf(1, 2), a.map { it.number })
    }

    @Test
    fun `수유실이 확인된 장소는 이름 뒤에 수유실을 붙인다`() {
        val withRoom = meal.place.copy(amenities = mapOf("nursing_room" to "available"))
        assertEquals("신신원 · 수유실", CourseMapGeometry.placeLabel(withRoom))
        assertEquals("신신원", CourseMapGeometry.placeLabel(meal.place.copy(amenities = mapOf("nursing_room" to "unknown"))))
    }

    @Test
    fun `좌표가 없는 장소는 빼고 번호를 이어 붙인다`() {
        val a = CourseMapGeometry.anchors(listOf(meal, stop("outing", "수동 입력", 0.0, 0.0), cafe), emptyList())
        assertEquals(listOf("신신원", "이디야커피"), a.map { it.label })
        assertEquals(listOf(1, 2), a.map { it.number })
    }

    @Test
    fun `북쪽은 위 동쪽은 오른쪽 순서를 지키고 여백 안 영역을 끝까지 쓴다`() {
        val pts = listOf(start, meal.latLng(), park.latLng(), cafe.latLng())
        val s = CourseMapGeometry.place(pts, width = 800f, height = 600f, padLeft = 50f, padTop = 40f, padRight = 50f, padBottom = 100f)
        // 남쪽 끝(출발)이 가장 아래, 북쪽 끝(공원)이 가장 위
        assertEquals(500f, s[0].y, 0.01f)
        assertEquals(40f, s[2].y, 0.01f)
        // 서쪽 끝(출발)이 가장 왼쪽, 동쪽 끝(카페)이 가장 오른쪽
        assertEquals(50f, s[0].x, 0.01f)
        assertEquals(750f, s[3].x, 0.01f)
        // 남북 순서 유지: 공원(37.4052) > 식당(37.4040) > 카페(37.4020)
        assertTrue(s[2].y < s[1].y && s[1].y < s[3].y)
    }

    @Test
    fun `한곳에 몰린 장소도 순위 간격을 섞어 서로 떨어뜨린다`() {
        // 출발지는 멀고 세 장소는 수십 m 안에 몰려 있다
        val pts = listOf(LatLng(37.30, 127.00), LatLng(37.4000, 127.1000), LatLng(37.4001, 127.1001), LatLng(37.4002, 127.1002))
        val s = CourseMapGeometry.place(pts, width = 400f, height = 400f)
        // 실제 비율만 쓰면 세 장소가 1px 안에 겹치지만, 섞으면 이웃끼리 순위 간격의 절반쯤(400/3/2) 떨어진다
        val d = hypot(s[1].x - s[2].x, s[1].y - s[2].y)
        assertTrue("d=$d", d > 60f)
    }

    @Test
    fun `점이 하나거나 값이 모두 같으면 가운데에 놓는다`() {
        val s = CourseMapGeometry.place(listOf(start), width = 400f, height = 300f, padBottom = 100f)
        assertEquals(MapPoint(200f, 100f), s.single())
        assertEquals(listOf(0.5f, 0.5f), CourseMapGeometry.spreadAxis(listOf(3.0, 3.0)))
    }

    @Test
    fun `곡선은 모든 지점을 순서대로 지난다`() {
        val pts = listOf(MapPoint(0f, 100f), MapPoint(50f, 20f), MapPoint(120f, 80f), MapPoint(200f, 10f))
        val c = CourseMapGeometry.smoothCurve(pts)
        assertEquals(3, c.size)
        assertEquals(pts.dropLast(1), c.map { it.from })
        assertEquals(pts.drop(1), c.map { it.to })
        // 이어지는 토막의 접선 방향이 같아 꺾이지 않는다 (앞 토막 c2→to 와 뒤 토막 from→c1 이 한 직선)
        c.zipWithNext().forEach { (a, b) ->
            val cross = (a.to.x - a.c2.x) * (b.c1.y - b.from.y) - (a.to.y - a.c2.y) * (b.c1.x - b.from.x)
            assertEquals(0f, cross, 0.01f)
        }
    }

    @Test
    fun `되돌아오는 토막은 서로 반대쪽으로 휘어 겹치지 않는다`() {
        // 출발(0,0) → ①(100,0) → ②(50,0): ②가 출발과 ① 사이에 있어 되돌아온다
        val c = CourseMapGeometry.smoothCurve(listOf(MapPoint(0f, 0f), MapPoint(100f, 0f), MapPoint(50f, 0f)))
        val goMid = (c[0].c1.y + c[0].c2.y) / 2
        val backMid = (c[1].c1.y + c[1].c2.y) / 2
        assertTrue("go=$goMid back=$backMid", goMid * backMid < 0f)
        assertEquals(MapPoint(100f, 0f), c[0].to)
        assertEquals(MapPoint(50f, 0f), c[1].to)
    }

    @Test
    fun `지점이 하나면 곡선이 없다`() {
        assertTrue(CourseMapGeometry.smoothCurve(listOf(MapPoint(1f, 1f))).isEmpty())
    }

    @Test
    fun `겹친 핀은 앞 핀을 두고 뒤 핀만 밀어낸다`() {
        val out = CourseMapGeometry.spread(listOf(MapPoint(100f, 100f), MapPoint(100f, 100f), MapPoint(300f, 100f)), minGap = 30f)
        assertEquals(MapPoint(100f, 100f), out[0])
        assertTrue(hypot(out[1].x - 100f, out[1].y - 100f) >= 29.9f)
        assertEquals(MapPoint(300f, 100f), out[2])
    }

    private fun CourseStop.latLng() = LatLng(place.latitude, place.longitude)
}
