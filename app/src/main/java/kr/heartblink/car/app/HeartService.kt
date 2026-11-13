package kr.heartblink.car.app

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import android.util.Log

/**
 * Keeps the car signals, the couple and the message opener alive while the car is on, also when the
 * navigation is in front or the gear is in D (listeners held by an activity stop there).
 */
class HeartService : Service() {
    override fun onCreate() {
        super.onCreate()
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(NotificationChannel(CHANNEL, "하트깜빡이", NotificationManager.IMPORTANCE_MIN))
        val note = Notification.Builder(this, CHANNEL)
            .setSmallIcon(kr.heartblink.car.R.mipmap.ic_launcher)
            .setContentTitle("하트깜빡이")
            .setContentText("연인이 남긴 메시지를 기다리고 있어요")
            .build()
        runCatching { startForeground(1, note, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE) }
            .onFailure { Log.w(TAG, "foreground failed: ${it.message}") }
        CarSignals.start(this)
        Session.start(this)
        MessageOpener.start(this)
        NaviWatch.start(this)
        DriveTalk.start(this)
        TogetherDistance.start(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_STICKY

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        private const val TAG = "HeartService"
        private const val CHANNEL = "heart"

        fun start(context: Context) {
            runCatching { context.startForegroundService(Intent(context, HeartService::class.java)) }
                .onFailure { Log.w(TAG, "start failed: ${it.message}") }
        }
    }
}

/** The car turned on (or the app was updated): start watching again. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        HeartService.start(context)
    }
}
