package com.cptc.cphr

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class ClockService : AccessibilityService() {

    companion object {
        @Volatile var instance: ClockService? = null
        // 当前任务："in" 签到 / "out" 签退 / null 空闲
        @Volatile var currentTask: String? = null
    }

    private val h = Handler(Looper.getMainLooper())
    private var taskStartAt = 0L
    private var feedbackTried = false      // 范围外异常反馈是否已尝试过一次
    private var reEntered = false          // 是否已退出重进过一次

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
        Cfg.load(this)
        Logger.add(this, "无障碍服务已连接")
    }

    override fun onDestroy() {
        instance = null
        super.onDestroy()
    }

    /** 外部（定时器/手动按钮）调用：开始一次打卡任务 */
    fun startTask(task: String) {
        Cfg.load(this)
        currentTask = task
        taskStartAt = System.currentTimeMillis()
        feedbackTried = false
        reEntered = false
        Logger.add(this, "开始任务: ${if (task == "in") "签到" else "签退"}")
        wakeAndOpenTarget()
        // 卡死监控
        h.postDelayed(stuckChecker, Cfg.stuckTimeoutMs)
    }

    /** 唤醒屏幕并打开目标App */
    private fun wakeAndOpenTarget() {
        // 唤醒屏幕（无法解锁带密码的锁屏，仅点亮）
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        @Suppress("DEPRECATION")
        val wl = pm.newWakeLock(
            PowerManager.SCREEN_BRIGHT_WAKE_LOCK or PowerManager.ACQUIRE_CAUSES_WAKEUP,
            "cphr:wake"
        )
        wl.acquire(8000)
        h.postDelayed({ if (wl.isHeld) wl.release() }, 8000)

        val intent = packageManager.getLaunchIntentForPackage(Cfg.targetPkg)
        if (intent == null) {
            Logger.add(this, "未找到目标App，请检查包名: ${Cfg.targetPkg}")
            currentTask = null
            return
        }
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)
        startActivity(intent)
        // 等待目标App加载后开始点击
        h.postDelayed({ runFlow() }, Cfg.openWaitMs)
    }

    /** 重启目标App */
    private fun restartTarget() {
        Logger.add(this, "目标App无响应，退出重进")
        performGlobalAction(GLOBAL_ACTION_BACK)
        h.postDelayed({ performGlobalAction(GLOBAL_ACTION_HOME) }, 500)
        h.postDelayed({ wakeAndOpenTarget() }, 1500)
    }

    /** 卡死检测：超时仍未完成则重启一次 */
    private val stuckChecker = Runnable {
        if (currentTask != null) {
            Logger.add(this, "超时未完成，尝试重启目标App")
            if (!reEntered) {
                reEntered = true
                restartTarget()
                h.postDelayed(stuckChecker, Cfg.stuckTimeoutMs)
            } else {
                Logger.add(this, "重试后仍未完成，放弃本次，等待下次")
                currentTask = null
            }
        }
    }

    /** 主流程：在目标App界面内逐步操作 */
    private fun runFlow() {
        val task = currentTask ?: return
        val root = rootInActiveWindow ?: run {
            h.postDelayed({ runFlow() }, 800); return
        }

        // 1. 已完成则结束
        val doneTxt = if (task == "in") Cfg.txtDone else Cfg.txtDoneOut
        if (findByText(root, doneTxt) != null) {
            Logger.add(this, "检测到「$doneTxt」，本次已完成")
            finishTask(true)
            return
        }

        // 2. 范围外处理
        if (findByText(root, Cfg.txtOutOfRange) != null) {
            handleOutOfRange(root)
            return
        }

        // 3. 进入「工作」页
        val work = findClickableByText(root, Cfg.txtWork)
        if (work != null) {
            Logger.add(this, "点击「${Cfg.txtWork}」")
            work.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            h.postDelayed({ runFlow() }, 1500)
            return
        }

        // 4. 点击 签到 / 签退
        val btnTxt = if (task == "in") Cfg.txtCheckIn else Cfg.txtCheckOut
        val btn = findClickableByText(root, btnTxt)
        if (btn != null) {
            Logger.add(this, "点击「$btnTxt」")
            btn.performAction(AccessibilityNodeInfo.ACTION_CLICK)
            h.postDelayed({ runFlow() }, 2000)
            return
        }

        // 没找到任何目标，稍后重试
        h.postDelayed({ runFlow() }, 1000)
    }

    /** 范围外：填写定位异常反馈并提交，尝试一次；仍范围外则退出重进再试一次 */
    private fun handleOutOfRange(root: AccessibilityNodeInfo) {
        if (!feedbackTried) {
            feedbackTried = true
            Logger.add(this, "检测到范围外，进入异常反馈")
            val fb = findClickableByText(root, Cfg.txtFeedback)
            if (fb != null) {
                fb.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                h.postDelayed({ fillFeedbackAndSubmit() }, 1500)
                return
            }
            // 找不到反馈入口，退出重进
            if (!reEntered) { reEntered = true; restartTarget() }
            else finishTask(false)
        } else {
            // 已反馈过仍范围外
            if (!reEntered) {
                reEntered = true
                Logger.add(this, "反馈后仍范围外，退出重进再试一次")
                restartTarget()
            } else {
                Logger.add(this, "重进后仍范围外，放弃本次")
                finishTask(false)
            }
        }
    }

    private fun fillFeedbackAndSubmit() {
        val root = rootInActiveWindow ?: return
        val edit = findEditable(root)
        if (edit != null) {
            val args = android.os.Bundle()
            args.putCharSequence(
                AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                Cfg.feedbackReason
            )
            edit.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
            Logger.add(this, "已填写理由「${Cfg.feedbackReason}」")
        }
        h.postDelayed({
            val r2 = rootInActiveWindow ?: return@postDelayed
            val submit = findClickableByText(r2, Cfg.txtSubmit)
            if (submit != null) {
                submit.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                Logger.add(this, "已提交异常反馈")
            }
            // 提交后回主流程，等待下一次（范围外标志可能仍在，交由重进逻辑处理）
            h.postDelayed({ runFlow() }, 2000)
        }, 1000)
    }

    private fun finishTask(success: Boolean) {
        h.removeCallbacks(stuckChecker)
        currentTask = null
        Logger.add(this, if (success) "任务完成 ✓" else "任务结束（未确认成功）")
    }

    // —— 节点查找工具 ——
    private fun findByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        node ?: return null
        val list = node.findAccessibilityNodeInfosByText(text)
        return list?.firstOrNull()
    }

    private fun findClickableByText(node: AccessibilityNodeInfo?, text: String): AccessibilityNodeInfo? {
        node ?: return null
        val list = node.findAccessibilityNodeInfosByText(text) ?: return null
        for (n in list) {
            var cur: AccessibilityNodeInfo? = n
            var depth = 0
            while (cur != null && depth < 6) {
                if (cur.isClickable) return cur
                cur = cur.parent
                depth++
            }
        }
        return list.firstOrNull()
    }

    private fun findEditable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        node ?: return null
        if (node.isEditable) return node
        for (i in 0 until node.childCount) {
            val r = findEditable(node.getChild(i))
            if (r != null) return r
        }
        return null
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}
}
