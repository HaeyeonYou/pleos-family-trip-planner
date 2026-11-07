package io.github.haeyeonyou.familytrip.guide

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import io.github.haeyeonyou.familytrip.FamilyTripApp
import io.github.haeyeonyou.familytrip.MainActivity
import io.github.haeyeonyou.familytrip.data.CourseStop
import io.github.haeyeonyou.familytrip.ui.ROLE_LABELS

/**
 * 내비가 화면 앞에 있을 때 도착과 다음 장소를 헤드업 알림으로 알린다.
 * Pleos에는 '다른 앱 위에 표시' 권한 설정이 없어 오버레이 대신 알림을 쓴다 (#38).
 */
class GuideNotifier(private val context: Context) {

    private val manager = context.getSystemService(NotificationManager::class.java)

    init {
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "코스 안내", NotificationManager.IMPORTANCE_HIGH).apply {
                description = "도착과 다음 장소 안내"
            },
        )
        manager.createNotificationChannel(
            NotificationChannel(QUIET_CHANNEL_ID, "코스 안내 (수면 모드)", NotificationManager.IMPORTANCE_LOW).apply {
                description = "아이가 잘 때: 소리와 팝업 없이 조용히 알려요"
                setSound(null, null)
                enableVibration(false)
            },
        )
    }

    /** 수면 모드에서는 소리·팝업 없는 채널로 알린다. */
    @Volatile
    var quiet: Boolean = false

    /** 수면 모드 중 도착 5분 전에 한 번, 조용히 알린다. */
    fun notifyArrivingSoon(minutes: Int) {
        manager.notify(
            NOTIFICATION_ID_SOON,
            base().setContentTitle("${minutes}분 뒤 도착해요").setContentText("아이가 자고 있어요. 도착 전에 조용히 알려 드려요.").build(),
        )
    }

    fun notifyArrived(stop: CourseStop, next: CourseStop?) {
        val builder = base()
            .setContentTitle("${stop.place.name}에 도착했어요")
            .setContentText(
                next?.let { "다음: ${ROLE_LABELS[it.role]} ${it.place.name} (이동 ${it.driveMinutes}분)" }
                    ?: "오늘 코스의 마지막 장소예요",
            )
        if (next != null) {
            builder.addAction(Notification.Action.Builder(null, "다음 안내 시작", nextIntent()).build())
        }
        builder.addAction(Notification.Action.Builder(null, "앱 열기", openAppIntent()).build())
        manager.notify(NOTIFICATION_ID, builder.build())
    }

    fun notifyFinished() {
        manager.notify(
            NOTIFICATION_ID,
            base().setContentTitle("오늘 나들이 끝!").setContentText("집에 도착했어요. 수고하셨어요.").build(),
        )
    }

    fun clear() = manager.cancel(NOTIFICATION_ID)

    private fun base() = Notification.Builder(context, if (quiet) QUIET_CHANNEL_ID else CHANNEL_ID)
        .setSmallIcon(android.R.drawable.ic_dialog_map)
        .setCategory(Notification.CATEGORY_NAVIGATION)
        .setContentIntent(openAppIntent())
        .setAutoCancel(true)

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        context,
        0,
        Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun nextIntent(): PendingIntent = PendingIntent.getBroadcast(
        context,
        1,
        Intent(context, GuideActionReceiver::class.java).setAction(ACTION_NEXT),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    companion object {
        const val CHANNEL_ID = "trip_guide"
        const val QUIET_CHANNEL_ID = "trip_guide_quiet"
        const val NOTIFICATION_ID = 1
        const val NOTIFICATION_ID_SOON = 2
        const val ACTION_NEXT = "io.github.haeyeonyou.familytrip.action.GUIDE_NEXT"
    }
}

/** 알림의 "다음 안내 시작" 버튼. 앱 화면을 열지 않고 다음 구간을 내비에 넘긴다. */
class GuideActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == GuideNotifier.ACTION_NEXT) {
            (context.applicationContext as FamilyTripApp).container.guide.goToNext()
        }
    }
}
