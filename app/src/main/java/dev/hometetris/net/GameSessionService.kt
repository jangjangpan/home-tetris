package dev.hometetris.net

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import dev.hometetris.R

/**
 * 멀티 게임이 붙어 있는 동안 앱을 "실행 중"으로 붙잡아 두는 포그라운드 서비스.
 *
 * 이게 없으면 앱을 잠시 내리는 순간 **이미 연결된 TCP 소켓이 시스템에 의해 끊긴다.**
 * (서버 소켓과 프로세스는 멀쩡한데 established 연결만 RST 로 정리된다. 삼성 기기가 특히 그렇다.)
 * 그래서 방장이 홈 버튼만 눌러도 참가자들이 전부 튕겨 나갔다.
 *
 * 알림을 띄우는 것 자체가 목적이 아니라, 알림은 포그라운드 서비스의 필수 조건이라 함께 있는 것이다.
 */
class GameSessionService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val text = intent?.getStringExtra(EXTRA_TEXT) ?: "게임 연결을 유지하는 중"
        startForeground(NOTIFICATION_ID, buildNotification(text))
        // 시스템이 죽였다가 되살려도 세션은 이미 사라졌으니 다시 시작하지 않는다.
        return START_NOT_STICKY
    }

    private fun buildNotification(text: String): Notification {
        val manager = getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "게임 연결",
                NotificationManager.IMPORTANCE_LOW, // 소리·진동 없이 조용히
            ).apply {
                description = "같은 Wi-Fi의 다른 기기와 연결을 유지하는 동안 표시됩니다"
                setShowBadge(false)
            }
            manager.createNotificationChannel(channel)
        }

        val open = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        val pending = PendingIntent.getActivity(
            this,
            0,
            open,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        return Notification.Builder(this, CHANNEL_ID)
            .setContentTitle("집안 테트리스")
            .setContentText(text)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentIntent(pending)
            .setOngoing(true)
            .build()
    }

    companion object {
        private const val CHANNEL_ID = "game_session"
        private const val NOTIFICATION_ID = 1
        private const val EXTRA_TEXT = "text"

        /** 멀티 세션을 열 때 호출. 앱이 화면에 떠 있는 동안 불러야 한다(안드로이드 12+ 제한). */
        fun start(context: Context, text: String) {
            val intent = Intent(context, GameSessionService::class.java).putExtra(EXTRA_TEXT, text)
            runCatching { context.startForegroundService(intent) }
        }

        fun stop(context: Context) {
            runCatching { context.stopService(Intent(context, GameSessionService::class.java)) }
        }
    }
}
