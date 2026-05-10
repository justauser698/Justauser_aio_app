package com.example.evilboiapp

import android.content.Intent
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.tooling.preview.PreviewScreenSizes
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature
import com.example.evilboiapp.ui.theme.EvilboiappTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            EvilboiappTheme {
                EvilboiappApp()
            }
        }
    }
}

@PreviewScreenSizes
@Composable
fun EvilboiappApp() {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.WEB) }
    val context = LocalContext.current
    val isDark = isSystemInDarkTheme()

    // Create and remember WebViews for each tab to keep state (login, scroll, cookies)
    val webViews = remember {
        AppDestinations.entries.associateWith { destination ->
            WebView(context).apply {
                // Remove explicit layer type to let system decide (fixes white screen on some devices)
                
                // Enable full cookie support for logins
                val cookieManager = CookieManager.getInstance()
                cookieManager.setAcceptCookie(true)
                cookieManager.setAcceptThirdPartyCookies(this, true)
                
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.MATCH_PARENT
                )
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        // Save cookies to disk so login persists after app restart
                        cookieManager.flush()
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
                        
                        // Handle non-http schemes (intent://, market://, etc.)
                        if (!url.startsWith("http://") && !url.startsWith("https://")) {
                            try {
                                val intent = Intent.parseUri(url, Intent.URI_INTENT_SCHEME)
                                if (intent != null) {
                                    val fallbackUrl = intent.getStringExtra("browser_fallback_url")
                                    if (fallbackUrl != null) {
                                        view?.loadUrl(fallbackUrl)
                                        return true
                                    }
                                    if (context.packageManager.resolveActivity(intent, 0) != null) {
                                        context.startActivity(intent)
                                        return true
                                    }
                                }
                            } catch (e: Exception) {
                                // Ignore parsing errors
                            }
                            return true
                        }
                        return false
                    }
                }
                webChromeClient = WebChromeClient() 
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    databaseEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW
                    
                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = true
                    
                    // Use a very standard Mobile User Agent that Instagram trusts
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; SM-S901B) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/116.0.0.0 Mobile Safari/537.36"
                }
                applyTheme(isDark)
                loadUrl(destination.url)
            }
        }
    }

    // Sync theme when it changes
    LaunchedEffect(isDark) {
        webViews.values.forEach { it.applyTheme(isDark) }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = {
                        Icon(
                            painterResource(it.icon),
                            contentDescription = it.label
                        )
                    },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                webViews.forEach { (destination, webView) ->
                    // Show only the selected WebView
                    if (destination == currentDestination) {
                        AndroidView(
                            factory = { webView },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
            }
        }
    }
}

private fun WebView.applyTheme(isDark: Boolean) {
    // Set background to black in dark mode to avoid white flashes
    if (isDark) {
        setBackgroundColor(Color.BLACK)
    } else {
        setBackgroundColor(Color.WHITE)
    }
    
    // Use WebSettingsCompat for maximum compatibility across Android versions (Android 7 to 17)
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
    } else if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        @Suppress("DEPRECATION")
        val forceDark = if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
        WebSettingsCompat.setForceDark(settings, forceDark)
    }
}

enum class AppDestinations(
    val label: String,
    val icon: Int,
    val url: String,
) {
    WEB("Browser", R.drawable.ic_web, "https://www.google.com"),
    YOUTUBE("YouTube", R.drawable.ic_youtube, "https://www.youtube.com"),
    INSTAGRAM("Instagram", R.drawable.ic_instagram, "https://www.instagram.com"),
}
