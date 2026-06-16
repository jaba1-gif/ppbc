package com.cptc.cphr

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Logger {
    private const val SP = "cphr_log"
    private const val KEY = "lines"
    private val fmt = SimpleDateFormat("MM-dd HH:mm:ss", Locale.getDefault())

    fun add(c: Context, msg: String) {
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val old = sp.getString(KEY, "") ?: ""
        val line = "${fmt.format(Date())}  $msg"
        // 只保留最近 200 行
        val all = (line + "\n" + old).split("\n").take(200).joinToString("\n")
        sp.edit().putString(KEY, all).apply()
    }

    fun get(c: Context): String =
        c.getSharedPreferences(SP, Context.MODE_PRIVATE).getString(KEY, "") ?: ""

    fun clear(c: Context) {
        c.getSharedPreferences(SP, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
