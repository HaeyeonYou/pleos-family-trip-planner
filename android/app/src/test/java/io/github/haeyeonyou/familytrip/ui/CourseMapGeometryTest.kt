package io.github.haeyeonyou.familytrip.ui

import io.github.haeyeonyou.familytrip.data.CoursePlace
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.cos

class CourseMapGeometryTest {
    private fun stop(role: String, name: String, lat: Double, lng: Double) =
        CourseStop(role = role, place = CoursePlace(name = name, latitude = lat, longitude = lng, source = "kakao", sourceId = name), arriveAt = "10:00", driveMinutes = 0, driveDistanceMeters = 0)

    // 판교 일대 실제 좌표 (홈 화면 캡처의 코스와 같은 동네)
    private val home = stop("home", "집", 37.3947, 127.1112)
    private val meal = stop("meal", "신신원", 37.4040, 127.1180)
    private val park = stop("outing", "고산근린공원", 37.4052, 127.1150)
    private val cafe = stop("cafe", "이디야커피", 37.4020, 127.1210)

    @Test
    fun `서버 경로가 있으면 그대로 경로선으로 쓰고 첫 점을 출발지로 둔다`() {
        val path = listOf(LatLng(37.3947, 127.1112), LatLng(37.3990, 127.1150), LatLng(37.4040, 127.1180))
        val shape = CourseMapGeometry.shape(listOf(meal, park, cafe, home), path)
        assertEquals(path, shape.line)
        assertEquals(path.first(), shape.origin)
        assertEquals(listOf("신신원", "고산근린공원", "이디야커피"), shape.visits.map { it.place.name })
        assertEquals(LatLng(37.3947, 127.1112), shape.home)
    }

    @Test
    fun `경로가 비면 장소 순서대로 직선으로 잇고 귀가 지점에서 끝낸다`() {
        val shape = CourseMapGeometry.shape(listOf(meal, park, cafe, home), emptyList())
        assertEquals(
            listOf(LatLng(37.4040, 127.1180), LatLng(37.4052, 127.1150), LatLng(37.4020, 127.1210), LatLng(37.3947, 127.1112)),
            shape.line,
        )
        assertNull(shape.origin)
    }

    @Test
    fun `경로가 한 점뿐이거나 좌표 없는 점만 있으면 직선으로 대신한다`() {
        val shape = CourseMapGeometry.shape(listOf(meal, park), listOf(LatLng(37.4, 127.1), LatLng(0.0, 0.0)))
        assertEquals(listOf(LatLng(37.4040, 127.1180), LatLng(37.4052, 127.1150)), shape.line)
        assertNull(shape.origin)
    }

    @Test
    fun `좌표가 없는 장소는 핀과 선에서 뺀다`() {
        val shape = CourseMapGeometry.shape(listOf(meal, stop("outing", "수동 입력", 0.0, 0.0), cafe), emptyList())
        assertEquals(listOf("신신원", "이디야커피"), shape.visits.map { it.place.name })
        assertEquals(2, shape.line.size)
    }

    @Test
    fun `북쪽은 위, 동쪽은 오른쪽이고 여백 안에 모두 들어간다`() {
        val points = listOf(home, meal, park, cafe).map { LatLng(it.place.latitude, it.place.longitude) }
        val proj = CourseMapGeometry.fit(points, width = 800f, height = 600f, padLeft = 50f, padTop = 40f, padRight = 50f, padBottom = 100f)
        val screen = points.map(proj::toScreen)
        screen.forEach {
            assertTrue("x=${it.x}", it.x in 49.9f..750.1f)
            assertTrue("y=${it.y}", it.y in 39.9f..500.1f)
        }
        val north = proj.toScreen(LatLng(37.4052, 127.1150))
        val south = proj.toScreen(LatLng(37.3947, 127.1112))
        assertTrue(north.y < south.y)
        val east = proj.toScreen(LatLng(37.4020, 127.1210))
        assertTrue(east.x > south.x)
        // 한 축은 여백 안 영역을 꽉 채운다 (맞춤)
        val xs = screen.map { it.x }
        val ys = screen.map { it.y }
        val filledX = abs((xs.max() - xs.min()) - 700f) < 0.5f
        val filledY = abs((ys.max() - ys.min()) - 460f) < 0.5f
        assertTrue("가로나 세로 한쪽은 꽉 차야 한다", filledX || filledY)
    }

    @Test
    fun `가로 세로 축척이 같아 모양이 찌그러지지 않는다`() {
        // 위도 0.01도와 같은 실제 거리만큼 동쪽으로 간 점은 화면에서도 같은 길이여야 한다
        val lat = 37.4
        val dLng = 0.01 / cos(Math.toRadians(lat))
        val a = LatLng(lat, 127.0)
        val b = LatLng(lat + 0.01, 127.0)
        val c = LatLng(lat, 127.0 + dLng)
        val proj = CourseMapGeometry.fit(listOf(a, b, c), width = 1000f, height = 300f)
        val pa = proj.toScreen(a)
        val up = pa.y - proj.toScreen(b).y
        val right = proj.toScreen(c).x - pa.x
        assertEquals(up, right, up * 0.01f)
    }

    @Test
    fun `점이 하나면 가운데에 놓는다`() {
        val proj = CourseMapGeometry.fit(listOf(LatLng(37.4, 127.1)), width = 400f, height = 300f, padBottom = 100f)
        val p = proj.toScreen(LatLng(37.4, 127.1))
        assertEquals(200f, p.x, 0.01f)
        assertEquals(100f, p.y, 0.01f)
    }

    @Test
    fun `가로로만 늘어선 장소도 0으로 나누지 않고 가로를 채운다`() {
        val a = LatLng(37.4, 127.10)
        val b = LatLng(37.4, 127.12)
        val proj = CourseMapGeometry.fit(listOf(a, b), width = 500f, height = 300f, padLeft = 50f, padRight = 50f)
        assertEquals(50f, proj.toScreen(a).x, 0.01f)
        assertEquals(450f, proj.toScreen(b).x, 0.01f)
        assertEquals(150f, proj.toScreen(a).y, 0.01f)
    }

    @Test
    fun `겹친 핀은 앞 핀을 두고 뒤 핀만 밀어낸다`() {
        val out = CourseMapGeometry.spread(listOf(MapPoint(100f, 100f), MapPoint(100f, 100f), MapPoint(300f, 100f)), minGap = 30f)
        assertEquals(MapPoint(100f, 100f), out[0])
        assertTrue(kotlin.math.hypot(out[1].x - 100f, out[1].y - 100f) >= 29.9f)
        assertEquals(MapPoint(300f, 100f), out[2])
    }
}
