package com.fuyi.overlay

import android.accessibilityservice.AccessibilityService
import android.graphics.Bitmap
import android.os.Handler
import android.os.Looper
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import java.lang.ref.WeakReference

/**
 * 只做一件事：通过无障碍服务抓取当前屏幕的一帧。
 * 不监听内容、不读界面节点。
 */
class CaptureService : AccessibilityService() {

    override fun onServiceConnected() {
        instance = WeakReference(this)
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance?.get() === this) instance = null
        super.onDestroy()
    }

    private fun grab(retryLeft: Int, callback: (Bitmap?, String?) -> Unit) {
        try {
            takeScreenshot(
                Display.DEFAULT_DISPLAY,
                mainExecutor,
                object : AccessibilityService.TakeScreenshotCallback {
                    override fun onSuccess(screenshot: AccessibilityService.ScreenshotResult) {
                        val buffer = screenshot.hardwareBuffer
                        var bitmap: Bitmap? = null
                        try {
                            val wrapped = Bitmap.wrapHardwareBuffer(buffer, screenshot.colorSpace)
                            if (wrapped != null) {
                                bitmap = wrapped.copy(Bitmap.Config.ARGB_8888, false)
                            }
                        } catch (t: Throwable) {
                            bitmap = null
                        } finally {
                            try {
                                buffer.close()
                            } catch (_: Throwable) {
                            }
                        }
                        val result = bitmap
                        if (result != null) {
                            callback(result, null)
                        } else {
                            callback(null, "无法读取截图数据")
                        }
                    }

                    override fun onFailure(errorCode: Int) {
                        if (errorCode == ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT && retryLeft > 0) {
                            Handler(Looper.getMainLooper()).postDelayed({
                                grab(retryLeft - 1, callback)
                            }, 450)
                        } else {
                            callback(null, describe(errorCode))
                        }
                    }
                }
            )
        } catch (t: Throwable) {
            callback(null, "截屏调用失败：${t.message ?: t.javaClass.simpleName}")
        }
    }

    private fun describe(code: Int): String = when (code) {
        ERROR_TAKE_SCREENSHOT_INTERVAL_TIME_SHORT -> "截屏太频繁，请稍后再点一次"
        ERROR_TAKE_SCREENSHOT_NO_ACCESSIBILITY_ACCESS -> "无障碍权限已失效，请回设置重新开启"
        ERROR_TAKE_SCREENSHOT_INVALID_DISPLAY -> "显示器无效"
        ERROR_TAKE_SCREENSHOT_INTERNAL_ERROR -> "系统截屏内部错误"
        else -> "截屏失败（错误码 $code）"
    }

    companion object {
        private var instance: WeakReference<CaptureService>? = null

        val isReady: Boolean
            get() = instance?.get() != null

        /** 返回 false 表示无障碍服务没连上。回调在主线程。 */
        fun captureScreen(callback: (Bitmap?, String?) -> Unit): Boolean {
            val service = instance?.get() ?: return false
            service.grab(1, callback)
            return true
        }
    }
}
