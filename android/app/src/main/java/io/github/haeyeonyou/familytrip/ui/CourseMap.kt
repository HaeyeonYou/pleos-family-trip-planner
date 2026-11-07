package io.github.haeyeonyou.familytrip.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import kotlinx.coroutines.delay
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.kakao.vectormap.KakaoMap
import com.kakao.vectormap.KakaoMapReadyCallback
import com.kakao.vectormap.MapLifeCycleCallback
import com.kakao.vectormap.MapView
import com.kakao.vectormap.camera.CameraUpdateFactory
import com.kakao.vectormap.label.LabelOptions
import com.kakao.vectormap.label.LabelStyle
import com.kakao.vectormap.label.LabelStyles
import com.kakao.vectormap.label.LabelTextBuilder
import com.kakao.vectormap.route.RouteLineOptions
import com.kakao.vectormap.route.RouteLineSegment
import com.kakao.vectormap.route.RouteLineStyle
import com.kakao.vectormap.route.RouteLineStyles
import io.github.haeyeonyou.familytrip.BuildConfig
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.data.LatLng
import com.kakao.vectormap.LatLng as MapLatLng

private const val TAG = "CourseMap"
private const val CAMERA_FIT_DELAY_MS = 400L

/**
 * 코스 경로 미리보기 (#40). 카카오맵에 장소 번호 핀과 실제 도로 경로(서버가 준 path)를 그린다.
 * 키가 없거나 지도를 띄우지 못하면 간이 지도([CourseMapLayer])로 대신 보여 준다 (시연장 통신 장애 대비).
 */
@Composable
fun CourseMap(stops: List<CourseStop>, path: List<LatLng>, modifier: Modifier = Modifier) {
    var failed by remember { mutableStateOf(BuildConfig.KAKAO_NATIVE_APP_KEY.isBlank()) }
    if (failed) {
        CourseMapLayer(stops = stops, modifier = modifier)
        return
    }
    var map by remember { mutableStateOf<KakaoMap?>(null) }
    val lifecycle = LocalLifecycleOwner.current.lifecycle
    var view by remember { mutableStateOf<MapView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { context ->
            MapView(context).also { mapView ->
                view = mapView
                mapView.start(
                    object : MapLifeCycleCallback() {
                        override fun onMapDestroy() = Unit

                        override fun onMapError(error: Exception) {
                            Log.w(TAG, "kakao map error", error)
                            failed = true
                        }
                    },
                    object : KakaoMapReadyCallback() {
                        override fun onMapReady(kakaoMap: KakaoMap) {
                            map = kakaoMap
                        }
                    },
                )
            }
        },
        // 지도 엔진은 뷰가 화면에서 완전히 없어질 때만 끈다 (다시 그려질 때 끄면 엔진이 시작되지 않는다).
        onRelease = { it.finish() },
    )

    // 화면이 가려지면 지도를 멈추고, 다시 보이면 이어서 그린다.
    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> view?.resume()
                Lifecycle.Event.ON_PAUSE -> view?.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose { lifecycle.removeObserver(observer) }
    }

    LaunchedEffect(map, stops, path) {
        val kakaoMap = map ?: return@LaunchedEffect
        val visits = stops.filter { it.role != "home" }
        draw(kakaoMap, visits, path)
        // 지도 크기가 정해진 뒤에 맞춰야 경로가 꽉 차게 보인다.
        delay(CAMERA_FIT_DELAY_MS)
        view?.let { v ->
            // 지도가 처음 잡은 화면 크기가 카드 크기와 다를 수 있어 카드 크기로 맞춘다.
            if (v.width > 0 && v.height > 0) kakaoMap.setViewport(v.width, v.height)
        }
        fitCamera(kakaoMap, visits, path)
    }
}

private fun draw(map: KakaoMap, stops: List<CourseStop>, path: List<LatLng>) {
    val labels = map.labelManager?.layer ?: return
    val routes = map.routeLineManager?.layer ?: return
    labels.removeAll()
    routes.removeAll()

    val line = path.map { MapLatLng.from(it.latitude, it.longitude) }
    if (line.size >= 2) {
        val style = RouteLineStyles.from(RouteLineStyle.from(14f, TripColors.Route.toArgb(), 3f, android.graphics.Color.WHITE))
        routes.addRouteLine(RouteLineOptions.from(RouteLineSegment.from(line, style)))
    }
    stops.forEachIndexed { i, stop ->
        val style = LabelStyle.from(pinBitmap(i + 1)).setTextStyles(30, TripColors.Ink.toArgb(), 4, android.graphics.Color.WHITE)
        labels.addLabel(
            LabelOptions.from(MapLatLng.from(stop.place.latitude, stop.place.longitude))
                .setStyles(LabelStyles.from(style))
                .setTexts(LabelTextBuilder().setTexts(stop.place.name.take(12))),
        )
    }

}

/** 경로와 장소가 모두 보이게 카메라를 맞춘다. */
private fun fitCamera(map: KakaoMap, stops: List<CourseStop>, path: List<LatLng>) {
    val points = (path.map { MapLatLng.from(it.latitude, it.longitude) } +
        stops.map { MapLatLng.from(it.place.latitude, it.place.longitude) }).toTypedArray()
    if (points.isNotEmpty()) map.moveCamera(CameraUpdateFactory.fitMapPoints(points, 60))
}

/** 초록 원 안에 번호를 그린 핀. */
private fun pinBitmap(number: Int, size: Int = 64): Bitmap {
    val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(bitmap)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    paint.color = android.graphics.Color.WHITE
    canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint)
    paint.color = TripColors.Green.toArgb()
    canvas.drawCircle(size / 2f, size / 2f, size / 2f - 5, paint)
    paint.color = android.graphics.Color.WHITE
    paint.textSize = size * 0.5f
    paint.typeface = Typeface.DEFAULT_BOLD
    paint.textAlign = Paint.Align.CENTER
    val y = size / 2f - (paint.descent() + paint.ascent()) / 2
    canvas.drawText(number.toString(), size / 2f, y, paint)
    return bitmap
}
