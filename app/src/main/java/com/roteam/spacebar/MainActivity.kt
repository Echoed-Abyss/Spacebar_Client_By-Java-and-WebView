package com.roteam.spacebar

import android.annotation.SuppressLint
import android.os.Bundle
import android.os.Environment
import android.webkit.ConsoleMessage
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    @SuppressLint("SetJavaScriptEnabled")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val logFile = initLogFile()
        log(logFile, "App启动 (ComponentActivity)")

        try {
            val webView = WebView(this)
            window.statusBarColor = android.graphics.Color.TRANSPARENT
            window.navigationBarColor = android.graphics.Color.TRANSPARENT
            setContentView(webView)
            log(logFile, "WebView创建成功，已设置ContentView")

            webView.settings.apply {
                javaScriptEnabled = true
                domStorageEnabled = true
                allowFileAccess = true
                useWideViewPort = true
                loadWithOverviewMode = true
                builtInZoomControls = false
                displayZoomControls = false
            }
            log(logFile, "WebView设置完成")

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    super.onPageFinished(view, url)
                    log(logFile, "页面加载完成: $url")
                    //Toast.makeText(this@MainActivity, "页面加载完成", Toast.LENGTH_SHORT).show()
                }

                override fun onReceivedError(
                    view: WebView?,
                    errorCode: Int,
                    description: String?,
                    failingUrl: String?
                ) {
                    super.onReceivedError(view, errorCode, description, failingUrl)
                    log(logFile, "加载错误: code=$errorCode, desc=$description, url=$failingUrl")
                   // Toast.makeText(this@MainActivity, "加载失败: $description", Toast.LENGTH_LONG).show()
                }
            }

            webView.webChromeClient = object : WebChromeClient() {
                override fun onConsoleMessage(msg: ConsoleMessage?): Boolean {
                    msg?.let {
                        log(logFile, "JS: [${it.messageLevel()}] ${it.message()} (行${it.lineNumber()})")
                    }
                    return true
                }
            }

            // 检查assets中的文件
            try {
                val files = assets.list("") ?: emptyArray()
                log(logFile, "assets文件列表: ${files.joinToString()}")
            } catch (e: Exception) {
                log(logFile, "读取assets失败: ${e.message}")
            }

            // 加载HTML
            log(logFile, "开始加载 prism_ui.html")
            webView.loadUrl("file:///android_asset/prism_ui.html")

        } catch (e: Exception) {
            log(logFile, "致命错误: ${e.message}")
            Toast.makeText(this, "错误: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun initLogFile(): File {
        val logDir = File(getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS), "Prism")
        if (!logDir.exists()) logDir.mkdirs()
        return File(logDir, "prism_debug.log")
    }

    private fun log(logFile: File, message: String) {
        val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date())
        val line = "[$time] $message\n"
        android.util.Log.d("PrismDebug", message)
        try {
            FileWriter(logFile, true).use { it.write(line) }
        } catch (_: Exception) {}
    }
}