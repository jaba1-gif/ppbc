package com.cptc.cphr

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.text.TextUtils
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.cptc.cphr.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var b: ActivityMainBinding
    private val h = Handler(Looper.getMainLooper())

    override fun onCreate(s: Bundle?) {
        super.onCreate(s)
        b = ActivityMainBinding.inflate(layoutInflater)
        setContentView(b.root)
        Cfg.load(this)
        bindViews()
        startKeepAlive()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
        refreshLicense()
        refreshLog()
    }

    private fun refreshLicense() {
        b.tvRemain.text = "使用期限：" + License.remainText(this)
        b.tvDevice.text = "设备码：" + License.deviceCode(this)
    }

    private fun bindViews() {
        // 载入现有配置
        b.etPkg.setText(Cfg.targetPkg)
        b.etInTime.setText(Cfg.checkInTime)
        b.etOutTime.setText(Cfg.checkOutTime)
        b.etOpenWait.setText((Cfg.openWaitMs / 1000).toString())
        b.swAuto.isChecked = Cfg.autoEnabled
        b.swIn.isChecked = Cfg.checkInEnabled
        b.swOut.isChecked = Cfg.checkOutEnabled
        b.etWork.setText(Cfg.txtWork)
        b.etMorning.setText(Cfg.txtMorning)
        b.etAfternoon.setText(Cfg.txtAfternoon)
        b.etIn.setText(Cfg.txtCheckIn)
        b.etOut.setText(Cfg.txtCheckOut)

        b.btnSave.setOnClickListener { saveCfg() }
        b.btnTestIn.setOnClickListener { manualTest("in") }
        b.btnTestOut.setOnClickListener { manualTest("out") }
        b.btnAcc.setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        b.btnBattery.setOnClickListener { reqIgnoreBattery() }
        b.btnExactAlarm.setOnClickListener { reqExactAlarm() }
        b.btnClearLog.setOnClickListener { Logger.clear(this); refreshLog() }

        // 卡密
        b.btnCopyDevice.setOnClickListener { copyDevice() }
        b.btnActivate.setOnClickListener { activateCard() }
    }

    private fun copyDevice() {
        val code = License.deviceCode(this)
        val cm = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        cm.setPrimaryClip(android.content.ClipData.newPlainText("device", code))
        Toast.makeText(this, "设备码已复制：$code", Toast.LENGTH_SHORT).show()
    }

    private fun activateCard() {
        val code = b.etCard.text.toString().trim()
        if (code.isEmpty()) { Toast.makeText(this, "请输入卡密", Toast.LENGTH_SHORT).show(); return }
        val msg = License.activate(this, code)
        Toast.makeText(this, msg, Toast.LENGTH_LONG).show()
        refreshLicense()
    }

    private fun saveCfg() {
        Cfg.targetPkg = b.etPkg.text.toString().trim()
        Cfg.checkInTime = b.etInTime.text.toString().trim()
        Cfg.checkOutTime = b.etOutTime.text.toString().trim()
        Cfg.openWaitMs = (b.etOpenWait.text.toString().toLongOrNull() ?: 4) * 1000
        Cfg.autoEnabled = b.swAuto.isChecked
        Cfg.checkInEnabled = b.swIn.isChecked
        Cfg.checkOutEnabled = b.swOut.isChecked
        Cfg.txtWork = b.etWork.text.toString().trim()
        Cfg.txtMorning = b.etMorning.text.toString().trim()
        Cfg.txtAfternoon = b.etAfternoon.text.toString().trim()
        Cfg.txtCheckIn = b.etIn.text.toString().trim()
        Cfg.txtCheckOut = b.etOut.text.toString().trim()
        Cfg.save(this)
        Scheduler.scheduleAll(this)
        Toast.makeText(this, "已保存并重新安排定时", Toast.LENGTH_SHORT).show()
        refreshLog()
    }

    private fun manualTest(task: String) {
        if (!License.isValid(this)) {
            Toast.makeText(this, "使用期已到期，请先激活卡密", Toast.LENGTH_LONG).show()
            return
        }
        val svc = ClockService.instance
        if (svc == null) {
            Toast.makeText(this, "请先开启无障碍服务", Toast.LENGTH_LONG).show()
            return
        }
        saveCfg()
        Toast.makeText(this, "开始测试…", Toast.LENGTH_SHORT).show()
        svc.startTask(task)
        h.postDelayed({ refreshLog() }, 3000)
    }

    private fun startKeepAlive() {
        val svc = Intent(this, KeepAliveService::class.java)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
            startForegroundService(svc) else startService(svc)
    }

    private fun refreshStatus() {
        val accOn = isAccessibilityOn()
        b.tvAccStatus.text = if (accOn) "无障碍服务：已开启 ✓" else "无障碍服务：未开启 ✗（必须开启）"
    }

    private fun refreshLog() {
        b.tvLog.text = Logger.get(this)
    }

    private fun isAccessibilityOn(): Boolean {
        val expected = ComponentName(this, ClockService::class.java).flattenToString()
        val enabled = Settings.Secure.getString(
            contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val sp = TextUtils.SimpleStringSplitter(':')
        sp.setString(enabled)
        while (sp.hasNext()) if (sp.next().equals(expected, true)) return true
        return false
    }

    private fun reqIgnoreBattery() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val i = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
            i.data = Uri.parse("package:$packageName")
            try { startActivity(i) } catch (e: Exception) {
                startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
            }
        }
    }

    private fun reqExactAlarm() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            try {
                startActivity(Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
                    Uri.parse("package:$packageName")))
            } catch (e: Exception) {
                Toast.makeText(this, "请在系统设置→闹钟和提醒中允许本应用", Toast.LENGTH_LONG).show()
            }
        } else Toast.makeText(this, "当前系统无需此权限", Toast.LENGTH_SHORT).show()
    }
}
