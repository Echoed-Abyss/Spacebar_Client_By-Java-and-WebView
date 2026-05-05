package com.rech.app

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    companion object {
        private const val CHANNEL_ID = "rech_foreground"
        private const val NOTIFICATION_ID = 1001
        const val APP_VERSION = "1.0.1"
        const val VERSION_CHECK_URL = "http://110.42.50.148:1155/api/image/openown.json"
    }

    private lateinit var webView: WebView
    private lateinit var logFile: File

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        logFile = initLogFile()
        log("Rech App启动 v$APP_VERSION")

        window.statusBarColor = Color.TRANSPARENT
        window.navigationBarColor = Color.TRANSPARENT

        createNotificationChannel()
        showNotification()

        webView = WebView(this).apply {
            setBackgroundColor(Color.TRANSPARENT)
            setLayerType(WebView.LAYER_TYPE_HARDWARE, null)

            settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                allowContentAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
                cacheMode = WebSettings.LOAD_DEFAULT
                mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                allowFileAccessFromFileURLs = true
                allowUniversalAccessFromFileURLs = true
            }

            addJavascriptInterface(RechBridge(this@MainActivity), "RechNative")

            webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    log("页面加载完成: $url")
                }

                override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean {
                    val url = request?.url?.toString() ?: return false
                    if (url.startsWith("http://") || url.startsWith("https://")) {
                        if (!url.contains("assets") && !url.startsWith("file://")) {
                            startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                            return true
                        }
                    }
                    return false
                }
            }

            webChromeClient = WebChromeClient()
        }

        setContentView(webView)

        requestNotificationPermission()
    }

    private val requestPermissionLauncher =
        registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted: Boolean ->
            if (isGranted) {
                log("通知权限已授权")
            } else {
                log("通知权限被拒绝")
            }
            initApp()
        }

    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        initApp()
    }

    private fun initApp() {
        log("初始化APP，加载auth页面")
        loadPage("auth.html")
    }

    fun loadPage(page: String) {
        runOnUiThread {
            webView.loadUrl("file:///android_asset/$page")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "Rech服务",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "保持应用运行"
                setShowBadge(false)
            }
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(channel)
        }
    }

    private fun showNotification() {
        val intent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Rech")
            .setContentText("正在运行中...")
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .build()

        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.notify(NOTIFICATION_ID, notification)
    }

    override fun onDestroy() {
        super.onDestroy()
        try {
            val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.cancel(NOTIFICATION_ID)
        } catch (_: Exception) {}
    }

    private fun initLogFile(): File {
        val dir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Rech")
        if (!dir.exists()) dir.mkdirs()
        return File(dir, "rech_debug.log")
    }

    private fun log(msg: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        android.util.Log.d("RechDebug", msg)
        try {
            FileWriter(logFile, true).use { it.write("[$time] $msg\n") }
        } catch (_: Exception) {}
    }
}

/**
 * JavaScript 桥接类
 */
class RechBridge(private val activity: MainActivity) {

    @JavascriptInterface
    fun navigate(page: String) {
        activity.runOnUiThread { activity.loadPage(page) }
    }

    @JavascriptInterface
    fun toast(msg: String) {
        activity.runOnUiThread {
            Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show()
        }
    }

    @JavascriptInterface
    fun openUrl(url: String) {
        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    }

    @JavascriptInterface
    fun saveToken(token: String) {
        activity.getSharedPreferences("rech_prefs", Context.MODE_PRIVATE)
            .edit().putString("auth_token", token).apply()
    }

    @JavascriptInterface
    fun getToken(): String {
        return activity.getSharedPreferences("rech_prefs", Context.MODE_PRIVATE)
            .getString("auth_token", "") ?: ""
    }

    @JavascriptInterface
    fun saveCredentials(email: String, password: String, apiUrl: String) {
        activity.getSharedPreferences("rech_prefs", Context.MODE_PRIVATE).edit()
            .putString("saved_email", email)
            .putString("saved_password", password)
            .putString("saved_api_url", apiUrl)
            .apply()
    }

    @JavascriptInterface
    fun getSavedCredentials(): String {
        val prefs = activity.getSharedPreferences("rech_prefs", Context.MODE_PRIVATE)
        val email = prefs.getString("saved_email", "") ?: ""
        val password = prefs.getString("saved_password", "") ?: ""
        val apiUrl = prefs.getString("saved_api_url", "") ?: ""
        return """{"email":"$email","password":"$password","apiUrl":"$apiUrl"}"""
    }

    @JavascriptInterface
    fun clearCredentials() {
        activity.getSharedPreferences("rech_prefs", Context.MODE_PRIVATE).edit().clear().apply()
    }

    @JavascriptInterface
    fun getAppVersion(): String {
        return MainActivity.APP_VERSION
    }

    @JavascriptInterface
    fun getVersionCheckUrl(): String {
        return MainActivity.VERSION_CHECK_URL
    }

    @JavascriptInterface
    fun log(msg: String) {
        android.util.Log.d("RechJS", msg)
    }
}