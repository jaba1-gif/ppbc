package com.cptc.cphr

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder

/** 前台常驻服务，保持进程存活、降低被杀概率 */
class KeepAliveService : Service() {
    companion object { const val CH = "cphr_keep" }

    override fun onCreate() {
        super.onCreate()
        createChannel()
        startForeground(1, buildNotif())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Scheduler.scheduleAll(this)
        return START_STICKY
    }

    private fun createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            val ch = NotificationChannel(CH, "定点打卡运行中", NotificationManager.IMPORTANCE_MIN)
            ch.setShowBadge(false)
            nm.createNotificationChannel(ch)
        }
    }

    private fun buildNotif(): Notification {
        val b = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            Notification.Builder(this, CH) else @Suppress("DEPRECATION") Notification.Builder(this)
        return b.setContentTitle("定点打卡得红包")
            .setContentText("正在守候打卡时间…")
            .setSmallIcon(android.R.drawable.ic_menu_my_calendar)
            .setOngoing(true)
            .build()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 被划掉最近任务时尝试重启自己
        val restart = Intent(applicationContext, KeepAliveService::class.java)
        restart.setPackage(packageName)
        startService(restart)
        super.onTaskRemoved(rootIntent)
    }
}
