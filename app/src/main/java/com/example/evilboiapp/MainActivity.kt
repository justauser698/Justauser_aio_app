package com.example.evilboiapp

import android.content.Intent
import android.graphics.Color
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
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.adaptive.navigationsuite.NavigationSuiteScaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    val focusManager = LocalFocusManager.current

    // Store the current URL for the browser bar
    val currentUrls = remember { mutableStateMapOf<AppDestinations, String>().apply {
        AppDestinations.entries.forEach { put(it, it.url) }
    } }
    
    var canGoBack by remember { mutableStateOf(false) }

    // Create and remember WebViews for each tab
    val webViews = remember {
        AppDestinations.entries.associateWith { destination ->
            WebView(context).apply {
                // Re-enable hardware acceleration (critical for video playback like Reels/YouTube)
                setLayerType(View.LAYER_TYPE_HARDWARE, null)
                
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
                        cookieManager.flush()
                        url?.let { currentUrls[destination] = it }
                        if (destination == currentDestination) {
                            canGoBack = view?.canGoBack() == true
                        }
                    }

                    override fun shouldOverrideUrlLoading(
                        view: WebView?,
                        request: WebResourceRequest?
                    ): Boolean {
                        val url = request?.url?.toString() ?: return false
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
                            } catch (_: Exception) {}
                            return true
                        }
                        return false
                    }
                }
                webChromeClient = WebChromeClient()
                @Suppress("SetJavaScriptEnabled")
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    mediaPlaybackRequiresUserGesture = false
                    mixedContentMode = WebSettings.MIXED_CONTENT_ALWAYS_ALLOW

                    loadWithOverviewMode = true
                    useWideViewPort = true
                    javaScriptCanOpenWindowsAutomatically = true
                    setSupportMultipleWindows(false) // Better stability for Instagram/YouTube

                    // Chrome-like User Agent that triggers dark mode on Google, YouTube, and Instagram
                    // Added a specific version and platform that Google Search honors for dark mode
                    // noinspection SpellCheckingInspection
                    userAgentString = "Mozilla/5.0 (Linux; Android 13; Pixel 7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Mobile Safari/537.36"
                }
                applyTheme(isDark)
                loadUrl(destination.url)
            }
        }
    }

    val activeWebView = webViews[currentDestination]

    // Handle physical back button
    BackHandler(enabled = canGoBack) {
        activeWebView?.goBack()
    }

    LaunchedEffect(currentDestination) {
        canGoBack = activeWebView?.canGoBack() == true
    }

    // Sync theme
    LaunchedEffect(isDark) {
        webViews.values.forEach { 
            it.applyTheme(isDark)
            it.reload()
        }
    }

    NavigationSuiteScaffold(
        navigationSuiteItems = {
            AppDestinations.entries.forEach {
                item(
                    icon = { Icon(painterResource(it.icon), it.label) },
                    label = { Text(it.label) },
                    selected = it == currentDestination,
                    onClick = { currentDestination = it }
                )
            }
        }
    ) {
        Scaffold(
            topBar = {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { activeWebView?.goBack() },
                        enabled = canGoBack
                    ) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back")
                    }

                    // URL Bar
                    var textState by remember(currentUrls[currentDestination]) { 
                        mutableStateOf(currentUrls[currentDestination] ?: "") 
                    }
                    
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 4.dp)
                            .clip(RoundedCornerShape(24.dp))
                            .background(MaterialTheme.colorScheme.surfaceVariant)
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        BasicTextField(
                            value = textState,
                            onValueChange = { textState = it },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            textStyle = TextStyle(
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                fontSize = 14.sp
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Uri,
                                imeAction = ImeAction.Go
                            ),
                            keyboardActions = KeyboardActions(
                                onGo = {
                                    val finalUrl = if (!textState.startsWith("http")) {
                                        "https://$textState"
                                    } else textState
                                    activeWebView?.loadUrl(finalUrl)
                                    focusManager.clearFocus()
                                }
                            )
                        )
                        if (textState.isEmpty()) {
                            Text("Enter URL", color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f), fontSize = 14.sp)
                        }
                    }

                    IconButton(onClick = { activeWebView?.reload() }) {
                        Icon(Icons.Default.Refresh, "Reload")
                    }
                }
            }
        ) { innerPadding ->
            Box(modifier = Modifier.padding(innerPadding).fillMaxSize()) {
                webViews.forEach { (destination, webView) ->
                    if (destination == currentDestination) {
                        AndroidView(factory = { webView }, modifier = Modifier.fillMaxSize())
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
    // For Android 13 (Tiramisu) and above
    if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
        WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
    }
    
    // For Android 10 to 12
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
        @Suppress("DEPRECATION")
        val forceDark = if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
        @Suppress("DEPRECATION")
        WebSettingsCompat.setForceDark(settings, forceDark)
    }

    // "Real Browser" behavior: 
    // This tells websites (Google/YouTube) to honor their dark media queries.
    if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
        @Suppress("DEPRECATION")
        val strategy = if (isDark) {
            WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
        } else {
            WebSettingsCompat.DARK_STRATEGY_USER_AGENT_DARKENING_ONLY // Fallback for light mode
        }
        @Suppress("DEPRECATION")
        WebSettingsCompat.setForceDarkStrategy(settings, strategy)
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
