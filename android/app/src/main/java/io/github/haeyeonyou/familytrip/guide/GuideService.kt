package io.github.haeyeonyou.familytrip.guide

import io.github.haeyeonyou.familytrip.data.AppLog
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import io.github.haeyeonyou.familytrip.MainActivity

/**
 * 안내 중에만 도는 포그라운드 서비스. 내비가 앞에 있으면 우리 앱은 화면에서 사라지고
 * Android 14가 프로세스를 얼려(freeze) FusedLocation 근접 이벤트를 받지 못한다.
 * 상시 알림으로 현재 목적지를 보여 주는 역할도 한다.
 */
class GuideService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "코스 안내 중"
        try {
            startForeground(NOTIFICATION_ID, notification(text), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
        } catch (e: SecurityException) {
            // Android 위치 권한을 거부하면 위치 포그라운드 서비스를 못 띄운다. 앱이 앞에 있을 때만 도착 판정이 된다.
            AppLog.w(TAG, "foreground service not allowed", e)
            stopSelf()
        }
        return START_NOT_STICKY
    }

    private fun notification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "안내 진행 상태", NotificationManager.IMPORTANCE_LOW),
        )
        val open = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        return Notification.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_map)
            .setContentTitle("차곡 코스 안내 중")
            .setContentText(text)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
    }

    companion object {
        private const val TAG = "GuideService"
        private const val CHANNEL_ID = "trip_guide_status"
        private const val NOTIFICATION_ID = 2
        private const val EXTRA_TEXT = "text"

        /** 시작하거나, 이미 돌고 있으면 상시 알림 문구만 바꾼다. */
        fun update(context: Context, text: String) {
            context.startForegroundService(Intent(context, GuideService::class.java).putExtra(EXTRA_TEXT, text))
        }

        fun stop(context: Context) {
            context.stopService(Intent(context, GuideService::class.java))
        }
    }
}
