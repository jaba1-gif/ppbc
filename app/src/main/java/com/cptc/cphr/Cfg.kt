package com.cptc.cphr

import android.content.Context

/** 全局配置，全部可在设置页修改，改完即时生效，无需重新编译 */
object Cfg {
    private const val SP = "cphr_cfg"

    // —— 目标 App ——
    var targetPkg = "com.cptc.cphr"      // 邮政自助包名，可在设置页改
    // —— 界面文字（按邮政自助实际显示填，可改）——
    var txtWork = "工作"
    var txtCheckIn = "签到"               // 蓝色可点：未签到
    var txtCheckOut = "签退"              // 蓝色可点：未签退
    var txtDone = "已签到"                // 灰色：已完成签到
    var txtDoneOut = "已签退"             // 灰色：已完成签退
    var txtMorning = "上午"               // 上午那一行的标识文字
    var txtAfternoon = "下午"             // 下午那一行的标识文字
    var txtOutOfRange = "范围外"          // 不在打卡范围的提示关键字
    var txtFeedback = "定位异常反馈"
    var txtSubmit = "提交"
    var feedbackReason = "定位"           // 异常反馈里要填的理由

    // —— 时间设置 ——
    var checkInTime = "08:30"        // 自动签到时间（上午签到）
    var checkOutTime = "18:00"       // 自动签退时间（下午签退）
    var openWaitMs = 4000L           // 打开App后等待多久再点击
    var stuckTimeoutMs = 10000L      // 超过多久没反应就重启目标App

    // —— 开关 ——
    var autoEnabled = true
    var checkInEnabled = true
    var checkOutEnabled = true

    fun load(c: Context) {
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        targetPkg = sp.getString("targetPkg", targetPkg)!!
        txtWork = sp.getString("txtWork", txtWork)!!
        txtCheckIn = sp.getString("txtCheckIn", txtCheckIn)!!
        txtCheckOut = sp.getString("txtCheckOut", txtCheckOut)!!
        txtDone = sp.getString("txtDone", txtDone)!!
        txtDoneOut = sp.getString("txtDoneOut", txtDoneOut)!!
        txtMorning = sp.getString("txtMorning", txtMorning)!!
        txtAfternoon = sp.getString("txtAfternoon", txtAfternoon)!!
        txtOutOfRange = sp.getString("txtOutOfRange", txtOutOfRange)!!
        txtFeedback = sp.getString("txtFeedback", txtFeedback)!!
        txtSubmit = sp.getString("txtSubmit", txtSubmit)!!
        feedbackReason = sp.getString("feedbackReason", feedbackReason)!!
        checkInTime = sp.getString("checkInTime", checkInTime)!!
        checkOutTime = sp.getString("checkOutTime", checkOutTime)!!
        openWaitMs = sp.getLong("openWaitMs", openWaitMs)
        stuckTimeoutMs = sp.getLong("stuckTimeoutMs", stuckTimeoutMs)
        autoEnabled = sp.getBoolean("autoEnabled", autoEnabled)
        checkInEnabled = sp.getBoolean("checkInEnabled", checkInEnabled)
        checkOutEnabled = sp.getBoolean("checkOutEnabled", checkOutEnabled)
    }

    fun save(c: Context) {
        c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().apply {
            putString("targetPkg", targetPkg)
            putString("txtWork", txtWork)
            putString("txtCheckIn", txtCheckIn)
            putString("txtCheckOut", txtCheckOut)
            putString("txtDone", txtDone)
            putString("txtDoneOut", txtDoneOut)
            putString("txtMorning", txtMorning)
            putString("txtAfternoon", txtAfternoon)
            putString("txtOutOfRange", txtOutOfRange)
            putString("txtFeedback", txtFeedback)
            putString("txtSubmit", txtSubmit)
            putString("feedbackReason", feedbackReason)
            putString("checkInTime", checkInTime)
            putString("checkOutTime", checkOutTime)
            putLong("openWaitMs", openWaitMs)
            putLong("stuckTimeoutMs", stuckTimeoutMs)
            putBoolean("autoEnabled", autoEnabled)
            putBoolean("checkInEnabled", checkInEnabled)
            putBoolean("checkOutEnabled", checkOutEnabled)
            apply()
        }
    }
}
