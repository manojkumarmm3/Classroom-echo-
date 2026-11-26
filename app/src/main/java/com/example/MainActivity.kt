package com.example

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.view.WindowManager
import android.webkit.JavascriptInterface
import android.webkit.PermissionRequest
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.io.ByteArrayInputStream

class MainActivity : ComponentActivity() {

  companion object {
    const val VIRTUAL_APP_URL = "https://appassets.androidplatform.net/assets/index.html"
    private const val VIRTUAL_HOST = "appassets.androidplatform.net"
    private const val ASSET_PATH = "/assets/index.html"
  }

  private var webView: WebView? = null
  private var pendingPermissionRequest: PermissionRequest? = null
  private var pendingCsvContent: String? = null
  private var showMicRationaleBanner by mutableStateOf(false)

  private val requestMicPermissionLauncher =
    registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
      val request = pendingPermissionRequest
      pendingPermissionRequest = null
      if (granted) {
        showMicRationaleBanner = false
        webView?.evaluateJavascript("window.dispatchEvent(new Event('androidMicGranted'))", null)
        if (request != null) {
          val audioResources =
            request.resources.filter { it == PermissionRequest.RESOURCE_AUDIO_CAPTURE }.toTypedArray()
          if (audioResources.isNotEmpty()) {
            request.grant(audioResources)
          } else {
            request.deny()
          }
        }
      } else {
        request?.deny()
        showMicRationaleBanner = true
      }
    }

  private val createCsvDocumentLauncher =
    registerForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
      val content = pendingCsvContent
      pendingCsvContent = null
      if (uri != null && content != null) {
        try {
          contentResolver.openOutputStream(uri)?.use { stream ->
            stream.write(content.toByteArray(Charsets.UTF_8))
          }
          Toast.makeText(this, "CSV report exported successfully", Toast.LENGTH_SHORT).show()
        } catch (err: Exception) {
          Toast.makeText(this, "Failed to save CSV: ${err.message}", Toast.LENGTH_LONG).show()
        }
      }
    }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge(
      statusBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
      navigationBarStyle = SystemBarStyle.dark(AndroidColor.TRANSPARENT),
    )

    setContent {
      MaterialTheme {
        ClassroomWebShell(
          showRationale = showMicRationaleBanner,
          onRequestMic = {
            showMicRationaleBanner = false
            requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
          },
          onOpenSettings = {
            val intent =
              Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                data = Uri.fromParts("package", packageName, null)
              }
            startActivity(intent)
          },
          onWebViewCreated = { created -> webView = created },
        )
      }
    }
  }

  @SuppressLint("SetJavaScriptEnabled")
  private fun configureWebView(wv: WebView) {
    wv.setBackgroundColor(AndroidColor.parseColor("#050A18"))
    wv.settings.apply {
      javaScriptEnabled = true
      domStorageEnabled = true
      mediaPlaybackRequiresUserGesture = false
      allowFileAccess = false
      allowContentAccess = false
      useWideViewPort = true
      loadWithOverviewMode = true
      builtInZoomControls = false
      displayZoomControls = false
      cacheMode = WebSettings.LOAD_DEFAULT
    }

    wv.addJavascriptInterface(AndroidWebBridge(), "AndroidBridge")

    wv.webViewClient =
      object : WebViewClient() {
        override fun shouldInterceptRequest(
          view: WebView?,
          request: WebResourceRequest?,
        ): WebResourceResponse? {
          val url = request?.url ?: return null
          if (url.host == VIRTUAL_HOST && url.path == ASSET_PATH) {
            return try {
              val stream = assets.open("index.html")
              WebResourceResponse("text/html", "UTF-8", stream)
            } catch (_: Exception) {
              WebResourceResponse(
                "text/plain",
                "UTF-8",
                ByteArrayInputStream("Asset not found".toByteArray()),
              )
            }
          }
          return super.shouldInterceptRequest(view, request)
        }
      }

    wv.webChromeClient =
      object : WebChromeClient() {
        override fun onPermissionRequest(request: PermissionRequest?) {
          if (request == null) return
          runOnUiThread {
            val wantsAudio =
              request.resources.contains(PermissionRequest.RESOURCE_AUDIO_CAPTURE)
            if (!wantsAudio) {
              request.deny()
              return@runOnUiThread
            }
            val granted =
              ContextCompat.checkSelfPermission(
                this@MainActivity,
                Manifest.permission.RECORD_AUDIO,
              ) == PackageManager.PERMISSION_GRANTED
            if (granted) {
              request.grant(arrayOf(PermissionRequest.RESOURCE_AUDIO_CAPTURE))
            } else {
              pendingPermissionRequest?.deny()
              pendingPermissionRequest = request
              requestMicPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
            }
          }
        }
      }

    if (wv.url == null) {
      wv.loadUrl(VIRTUAL_APP_URL)
    }
  }

  @Composable
  private fun ClassroomWebShell(
    showRationale: Boolean,
    onRequestMic: () -> Unit,
    onOpenSettings: () -> Unit,
    onWebViewCreated: (WebView) -> Unit,
  ) {
    BackHandler(enabled = webView?.canGoBack() == true) {
      webView?.goBack()
    }

    Box(
      modifier =
        Modifier.fillMaxSize()
          .background(Color(0xFF050A18))
          .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
      AndroidView(
        factory = { context ->
          WebView(context).apply {
            configureWebView(this)
            onWebViewCreated(this)
          }
        },
        modifier = Modifier.fillMaxSize().testTag("classroom_webview"),
      )

      AnimatedVisibility(
        visible = showRationale,
        modifier = Modifier.align(Alignment.BottomCenter).padding(16.dp),
      ) {
        Surface(
          color = Color(0xFF0C172E),
          shape = RoundedCornerShape(16.dp),
          tonalElevation = 8.dp,
          modifier = Modifier.fillMaxWidth().testTag("mic_rationale_banner"),
        ) {
          Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
          ) {
            Text(
              text = stringResource(R.string.mic_permission_rationale),
              color = Color.White,
              fontSize = 13.sp,
            )
            Row(
              horizontalArrangement = Arrangement.spacedBy(10.dp),
              modifier = Modifier.fillMaxWidth(),
            ) {
              Button(
                onClick = onRequestMic,
                colors =
                  ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF00E5FF),
                    contentColor = Color(0xFF050A18),
                  ),
                modifier = Modifier.weight(1f).testTag("retry_mic_button"),
              ) {
                Text(
                  text = stringResource(R.string.grant_microphone_permission),
                  fontWeight = FontWeight.Bold,
                  fontSize = 12.sp,
                )
              }
              OutlinedButton(
                onClick = onOpenSettings,
                modifier = Modifier.weight(1f).testTag("open_settings_button"),
              ) {
                Text(
                  text = stringResource(R.string.open_app_settings),
                  color = Color(0xFF9FB2CC),
                  fontSize = 12.sp,
                )
              }
            }
          }
        }
      }
    }
  }

  inner class AndroidWebBridge {
    @JavascriptInterface
    fun isAndroidWebView(): Boolean = true

    @JavascriptInterface
    fun getNativePermissionState(): String {
      val granted =
        ContextCompat.checkSelfPermission(this@MainActivity, Manifest.permission.RECORD_AUDIO) ==
          PackageManager.PERMISSION_GRANTED
      return if (granted) "granted" else "prompt"
    }

    @JavascriptInterface
    fun setKeepScreenOn(enabled: Boolean) {
      runOnUiThread {
        if (enabled) {
          window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
          window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
      }
    }

    @JavascriptInterface
    fun saveCsvFile(filename: String, content: String) {
      runOnUiThread {
        pendingCsvContent = content
        val safeName = filename.ifBlank { "classroom_sound_report.csv" }
        createCsvDocumentLauncher.launch(safeName)
      }
    }
  }

  override fun onResume() {
    super.onResume()
    webView?.onResume()
  }

  override fun onPause() {
    webView?.onPause()
    super.onPause()
  }

  override fun onDestroy() {
    pendingPermissionRequest?.deny()
    pendingPermissionRequest = null
    webView?.let { wv ->
      wv.stopLoading()
      wv.destroy()
    }
    webView = null
    super.onDestroy()
  }
}
