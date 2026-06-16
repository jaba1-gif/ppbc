package com.cptc.cphr

import android.annotation.SuppressLint
import android.content.Context
import android.provider.Settings
import java.security.MessageDigest

/**
 * 离线卡密 / 使用期管理
 * - 首次安装给 1 天试用
 * - 设备码 = 基于 ANDROID_ID 生成的短码，发给作者
 * - 作者用同样算法对“设备码+天数”生成卡密，用户输入后增加到期时间
 * 说明：纯离线方案，无法100%防破解，但足够日常发卡使用。
 */
object License {
    private const val SP = "cphr_lic"
    private const val SECRET = "cphr@2026#postal"   // 发卡密钥，作者保管；改了它旧卡密全失效

    private const val TRIAL_DAYS = 1L
    private const val DAY_MS = 24L * 60 * 60 * 1000

    /** 取设备码（给用户看、发给作者）*/
    @SuppressLint("HardwareIds")
    fun deviceCode(c: Context): String {
        val aid = Settings.Secure.getString(c.contentResolver, Settings.Secure.ANDROID_ID) ?: "0000"
        val raw = md5(aid + SECRET)
        return raw.substring(0, 8).uppercase()   // 8位设备码
    }

    /** 到期时间戳（毫秒）。首次调用初始化为试用期 */
    fun expireAt(c: Context): Long {
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        if (!sp.contains("expire")) {
            val exp = System.currentTimeMillis() + TRIAL_DAYS * DAY_MS
            sp.edit().putLong("expire", exp).apply()
            return exp
        }
        return sp.getLong("expire", 0L)
    }

    /** 是否在有效期内 */
    fun isValid(c: Context): Boolean = System.currentTimeMillis() < expireAt(c)

    /** 剩余天数（向上取整，最少显示0） */
    fun remainDays(c: Context): Long {
        val left = expireAt(c) - System.currentTimeMillis()
        if (left <= 0) return 0
        return (left + DAY_MS - 1) / DAY_MS
    }

    /** 剩余时间文字 */
    fun remainText(c: Context): String {
        val left = expireAt(c) - System.currentTimeMillis()
        if (left <= 0) return "已到期"
        val days = left / DAY_MS
        val hours = (left % DAY_MS) / (60 * 60 * 1000)
        return if (days > 0) "剩余 ${days} 天 ${hours} 小时" else "剩余 ${hours} 小时"
    }

    /**
     * 输入卡密激活。卡密格式： 天数-校验码
     * 例：30-AB12CD34  表示绑定本机的30天卡
     * 校验码 = md5(设备码 + 天数 + SECRET) 取前8位
     */
    fun activate(c: Context, code: String): String {
        val input = code.trim().uppercase()
        val parts = input.split("-")
        if (parts.size != 2) return "卡密格式错误"
        val days = parts[0].toLongOrNull() ?: return "卡密格式错误"
        if (days <= 0 || days > 3650) return "卡密天数无效"
        val expectCheck = genCheck(c, days)
        if (parts[1] != expectCheck) return "卡密无效（与本机不匹配）"

        // 防重复使用同一张卡：记录用过的卡密
        val sp = c.getSharedPreferences(SP, Context.MODE_PRIVATE)
        val used = sp.getStringSet("used", HashSet()) ?: HashSet()
        if (used.contains(input)) return "该卡密已使用过"

        // 续期：在“当前到期时间”和“现在”里取较大者，再加天数
        val base = maxOf(expireAt(c), System.currentTimeMillis())
        val newExp = base + days * DAY_MS
        val newUsed = HashSet(used); newUsed.add(input)
        sp.edit().putLong("expire", newExp).putStringSet("used", newUsed).apply()
        return "激活成功，增加 ${days} 天"
    }

    /** 作者发卡用：根据设备码与天数生成卡密的校验码 */
    private fun genCheck(c: Context, days: Long): String {
        return md5(deviceCode(c) + days + SECRET).substring(0, 8).uppercase()
    }

    private fun md5(s: String): String {
        val d = MessageDigest.getInstance("MD5").digest(s.toByteArray())
        return d.joinToString("") { "%02x".format(it) }
    }
}
