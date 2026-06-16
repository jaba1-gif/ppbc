package com.cptc.cphr

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import java.util.Calendar

/** 定时调度：用精确闹钟在签到/签退时间触发 */
object Scheduler {

    const val ACTION_ALARM = "com.cptc.cphr.ALARM"
    const val EXTRA_TASK = "task"

    fun scheduleAll(c: Context) {
        Cfg.load(c)
        if (!Cfg.autoEnabled) { cancelAll(c); return }
        if (Cfg.checkInEnabled) schedule(c, "in", Cfg.checkInTime)
        if (Cfg.checkOutEnabled) schedule(c, "out", Cfg.checkOutTime)
        Logger.add(c, "已安排定时：签到${Cfg.checkInTime} 签退${Cfg.checkOutTime}")
    }

    private fun schedule(c: Context, task: String, hhmm: String) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val parts = hhmm.split(":")
        val cal = Calendar.getInstance().apply {
            set(Calendar.HOUR_OF_DAY, parts[0].toInt())
            set(Calendar.MINUTE, parts.getOrElse(1){"0"}.toInt())
            set(Calendar.SECOND, 0)
            if (timeInMillis <= System.currentTimeMillis()) add(Calendar.DAY_OF_MONTH, 1)
        }
        val pi = pending(c, task)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !am.canScheduleExactAlarms()) {
                // 没有精确闹钟权限，降级为非精确
                am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            } else {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
            }
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.timeInMillis, pi)
        }
    }

    private fun pending(c: Context, task: String): PendingIntent {
        val i = Intent(c, AlarmReceiver::class.java).apply {
            action = ACTION_ALARM
            putExtra(EXTRA_TASK, task)
        }
        val code = if (task == "in") 1001 else 1002
        return PendingIntent.getBroadcast(
            c, code, i,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    fun cancelAll(c: Context) {
        val am = c.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        am.cancel(pending(c, "in"))
        am.cancel(pending(c, "out"))
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val task = intent.getStringExtra(Scheduler.EXTRA_TASK) ?: return
        Logger.add(context, "到点触发：${if (task=="in") "签到" else "签退"}")
        val svc = ClockService.instance
        if (svc != null) {
            svc.startTask(task)
        } else {
            Logger.add(context, "无障碍服务未开启，无法执行，请在设置中开启")
        }
        // 安排下一天
        Scheduler.scheduleAll(context)
    }
}
