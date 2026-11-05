package io.github.haeyeonyou.familytrip.dev

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.graphics.Color
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.Gravity
import android.view.WindowManager
import android.widget.TextView

/**
 * 앱이 백그라운드(내비가 앞)일 때 정보를 보여 줄 수 있는지 확인하는 스파이크 (#38).
 * 헤드업 알림과 '다른 앱 위에 표시' 오버레이를 시험한다.
 */
object BackgroundDisplayTest {
    private const val CHANNEL_ID = "trip_guide"
    private var overlay: TextView? = null

    fun postNotificationLater(context: Context, delayMs: Long = 4000) {
        val app = context.applicationContext
        val manager = app.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "코스 안내", NotificationManager.IMPORTANCE_HIGH),
        )
        Handler(Looper.getMainLooper()).postDelayed({
            val notification = Notification.Builder(app, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_map)
                .setContentTitle("공룡세상에 도착했어요")
                .setContentText("다음: 백미당 카페 (이동 3분)")
                .setCategory(Notification.CATEGORY_NAVIGATION)
                .build()
            manager.notify(1, notification)
        }, delayMs)
    }

    fun canDrawOverlays(context: Context) = Settings.canDrawOverlays(context)

    fun toggleOverlay(context: Context) {
        val app = context.applicationContext
        val wm = app.getSystemService(WindowManager::class.java)
        overlay?.let {
            wm.removeView(it)
            overlay = null
            return
        }
        val view = TextView(app).apply {
            text = "다음: 백미당 카페 · 3분\n16:05 귀가 예정"
            textSize = 28f
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.argb(220, 40, 40, 60))
            setPadding(40, 30, 40, 30)
        }
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.TOP or Gravity.END
            x = 40
            y = 160
        }
        wm.addView(view, params)
        overlay = view
    }
}
