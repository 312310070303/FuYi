package com.fuyi.overlay

import android.Manifest
import android.app.AlertDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText

class MainActivity : AppCompatActivity() {

    private lateinit var prefs: Prefs
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvA11yStatus: TextView
    private lateinit var tvTest: TextView
    private lateinit var etBase: TextInputEditText
    private lateinit var etKey: TextInputEditText
    private lateinit var etModel: TextInputEditText
    private lateinit var etGlossary: TextInputEditText
    private lateinit var btnShot: MaterialButton

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        prefs = Prefs(this)
        setContentView(R.layout.activity_main)
        bindViews()
        loadPrefs()
        wireClicks()
        askNotificationPermission()
    }

    override fun onResume() {
        super.onResume()
        refreshStatus()
    }

    override fun onPause() {
        savePrefs()
        super.onPause()
    }

    private fun bindViews() {
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvA11yStatus = findViewById(R.id.tvA11yStatus)
        tvTest = findViewById(R.id.tvTest)
        etBase = findViewById(R.id.etBase)
        etKey = findViewById(R.id.etKey)
        etModel = findViewById(R.id.etModel)
        etGlossary = findViewById(R.id.etGlossary)
        btnShot = findViewById(R.id.btnShot)
    }

    private fun wireClicks() {
        findViewById<MaterialButton>(R.id.btnOverlay).setOnClickListener {
            startActivity(
                Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
            )
        }

        findViewById<MaterialButton>(R.id.btnA11y).setOnClickListener {
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
        }

        btnShot.setOnClickListener { testCapture() }

        findViewById<MaterialButton>(R.id.chipDeepSeek).setOnClickListener {
            etBase.setText("https://api.deepseek.com/v1")
            etModel.setText("deepseek-chat")
        }
        findViewById<MaterialButton>(R.id.chipOpenAI).setOnClickListener {
            etBase.setText("https://api.openai.com/v1")
            etModel.setText("gpt-4o-mini")
        }
        findViewById<MaterialButton>(R.id.chipGemini).setOnClickListener {
            etBase.setText("https://generativelanguage.googleapis.com/v1beta/openai")
            etModel.setText("gemini-2.0-flash")
        }
        findViewById<MaterialButton>(R.id.chipSilicon).setOnClickListener {
            etBase.setText("https://api.siliconflow.cn/v1")
            etModel.setText("Qwen/Qwen2.5-7B-Instruct")
        }

        findViewById<MaterialButton>(R.id.btnTest).setOnClickListener {
            savePrefs()
            tvTest.text = "测试中…"
            Translator.translate(
                prefs,
                "こんにちは、旅人さん。今日はいい天気ですね。"
            ) { result, error ->
                runOnUiThread {
                    tvTest.text = if (error != null) "❌ $error" else "✅ $result"
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnStart).setOnClickListener {
            startOverlay()
        }

        findViewById<MaterialButton>(R.id.btnStop).setOnClickListener {
            stopService(Intent(this, OverlayService::class.java))
            toast("已停止悬浮球")
        }
    }

    private fun startOverlay() {
        savePrefs()
        if (!Settings.canDrawOverlays(this)) {
            toast("请先授予「显示悬浮窗」权限")
            return
        }
        if (!CaptureService.isReady) {
            toast("请先开启无障碍服务")
            startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            return
        }
        if (prefs.apiKey.isBlank()) {
            toast("请先填写 API Key")
            return
        }
        ContextCompat.startForegroundService(this, Intent(this, OverlayService::class.java))
        toast("悬浮球已启动，回到游戏点一下")
    }

    private fun testCapture() {
        if (!CaptureService.isReady) {
            toast("先开启无障碍服务，再测试截屏")
            return
        }
        btnShot.isEnabled = false
        val started = CaptureService.captureScreen { bitmap, error ->
            runOnUiThread {
                btnShot.isEnabled = true
                if (bitmap == null) {
                    toast(error ?: "截屏失败")
                } else {
                    showShotDialog(bitmap)
                }
            }
        }
        if (!started) {
            btnShot.isEnabled = true
            toast("无障碍服务未连接")
        }
    }

    private fun showShotDialog(bitmap: Bitmap) {
        val image = ImageView(this).apply {
            scaleType = ImageView.ScaleType.FIT_CENTER
            setBackgroundColor(0xFF111111.toInt())
            setImageBitmap(bitmap)
        }
        val container = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(12), dp(12), dp(12), dp(4))
            addView(
                image,
                LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    dp(420)
                )
            )
            addView(TextView(this@MainActivity).apply {
                text = "分辨率 ${bitmap.width} × ${bitmap.height}\n如果这里全黑，说明该界面禁止截屏（FLAG_SECURE）。"
                textSize = 12f
                setPadding(0, dp(8), 0, 0)
            })
        }
        AlertDialog.Builder(this)
            .setTitle("截屏测试")
            .setView(container)
            .setPositiveButton("关闭", null)
            .show()
    }

    private fun refreshStatus() {
        val overlay = Settings.canDrawOverlays(this)
        tvOverlayStatus.text = if (overlay) "悬浮窗：✅ 已授予" else "悬浮窗：❌ 未授予"

        val accessibility = CaptureService.isReady
        tvA11yStatus.text =
            if (accessibility) "无障碍服务：✅ 已连接" else "无障碍服务：❌ 未开启（点下面按钮）"
    }

    private fun loadPrefs() {
        etBase.setText(prefs.apiBase)
        etKey.setText(prefs.apiKey)
        etModel.setText(prefs.model)
        etGlossary.setText(prefs.glossary)
    }

    private fun savePrefs() {
        prefs.apiBase = etBase.text?.toString()?.trim().orEmpty()
            .ifBlank { Prefs.DEFAULT_BASE }
        prefs.apiKey = etKey.text?.toString()?.trim().orEmpty()
        prefs.model = etModel.text?.toString()?.trim().orEmpty()
            .ifBlank { Prefs.DEFAULT_MODEL }
        prefs.glossary = etGlossary.text?.toString().orEmpty()
    }

    private fun askNotificationPermission() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            ActivityCompat.requestPermissions(
                this,
                arrayOf(Manifest.permission.POST_NOTIFICATIONS),
                100
            )
        }
    }
}
