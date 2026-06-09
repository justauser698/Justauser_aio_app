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
import java.net.HttpURLConnection
import java.net.URL
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
fun EvilboiappApp(
    isDark: Boolean = isSystemInDarkTheme()
) {
    var currentDestination by rememberSaveable { mutableStateOf(AppDestinations.WEB) }
    val context = LocalContext.current
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

                    // For API 33+ some pages run very early; intercept the main document and inject a small script
                    // so prefers-color-scheme is present before page scripts execute.
                    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): android.webkit.WebResourceResponse? {
                        try {
                            if (request?.isForMainFrame == true) {
                                val reqUrl = request.url?.toString() ?: return null
                                if (!(reqUrl.startsWith("http://") || reqUrl.startsWith("https://"))) return null

                                val url = URL(reqUrl)
                                val conn = url.openConnection() as HttpURLConnection
                                conn.requestMethod = "GET"
                                conn.instanceFollowRedirects = true
                                // copy User-Agent so servers return same HTML
                                conn.setRequestProperty("User-Agent", settings.userAgentString ?: "")
                                conn.connectTimeout = 5000
                                conn.readTimeout = 5000

                                val contentType = conn.contentType ?: ""
                                val mime = contentType.substringBefore(';').trim().lowercase()

                                if (mime != "text/html") {
                                    return null
                                }

                                val charset = contentType.substringAfter("charset=", "utf-8")
                                val body = conn.inputStream.bufferedReader(Charsets.UTF_8).use { it.readText() }

                                // Build injection script reflecting the current theme stored in view.tag (updated when theme changes)
                                val curDark = (view?.tag as? Boolean) ?: false
                                val inject = """
                                    <script>
                                      (function(isDark){
                                        try{
                                          const origMatch = window.matchMedia.bind(window);
                                          function createMQL(query){
                                            const matches = query.indexOf('prefers-color-scheme') !== -1 && isDark === true;
                                            const listeners = [];
                                            const mql = {
                                              matches: matches,
                                              media: query,
                                              onchange: null,
                                              addListener: function(cb){ if(typeof cb === 'function') listeners.push(cb); },
                                              removeListener: function(cb){ const i = listeners.indexOf(cb); if(i>-1) listeners.splice(i,1); },
                                              addEventListener: function(type, cb){ if(type === 'change' && typeof cb === 'function') listeners.push(cb); },
                                              removeEventListener: function(type, cb){ if(type === 'change'){ const i = listeners.indexOf(cb); if(i>-1) listeners.splice(i,1); } },
                                              dispatchEvent: function(ev){ listeners.forEach(function(cb){ try{ cb(ev); }catch(e){} }); return true; }
                                            };
                                            return mql;
                                          }
                                          window.matchMedia = function(query){
                                            if (query && query.indexOf('prefers-color-scheme') !== -1) {
                                              return createMQL(query);
                                            }
                                            return origMatch(query);
                                          };
                                          try{ window.dispatchEvent(new Event('prefers-color-scheme-changed')); }catch(e){}
                                        }catch(e){}
                                      })(${curDark});
                                    </script>
                                """.trimIndent()

                                // Insert before </head> when possible, else prepend
                                val modified = if (body.contains("</head>", ignoreCase = true)) {
                                    body.replaceFirst("</head>", "${inject}</head>")
                                } else {
                                    inject + body
                                }

                                val bytes = modified.toByteArray(Charsets.UTF_8)
                                return android.webkit.WebResourceResponse("text/html", charset, java.io.ByteArrayInputStream(bytes))
                            }
                        } catch (_: Exception) {
                            // best-effort; fall back to default handling
                        }
                        return null
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
                    // Also it dosent matter that is andrioid 13 it will work on older versions also
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

    // Sync theme settings and refresh browser when it changes
    LaunchedEffect(isDark) {
        webViews.values.forEach { webView ->
            webView.applyTheme(isDark)
            // store current theme on the view so intercepted requests can use it
            webView.tag = isDark
            webView.injectPrefersColorScheme(isDark)
            webView.post {
                // Force a network reload so sites re-evaluate prefers-color-scheme
                try {
                    webView.settings.cacheMode = WebSettings.LOAD_NO_CACHE
                    webView.clearCache(true)
                    webView.reload()
                } catch (_: Exception) {}
                // Restore normal caching after reload
                try { webView.settings.cacheMode = WebSettings.LOAD_DEFAULT } catch (_: Exception) {}
            }
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

                    IconButton(onClick = { activeWebView?.post {
                        try { activeWebView.settings.cacheMode = WebSettings.LOAD_NO_CACHE } catch (_: Exception) {}
                        try { activeWebView.clearCache(true) } catch (_: Exception) {}
                        try { activeWebView.tag = isDark } catch (_: Exception) {}
                        try { activeWebView.injectPrefersColorScheme(isDark) } catch (_: Exception) {}
                        try { activeWebView.reload() } catch (_: Exception) {}
                        try { activeWebView.settings.cacheMode = WebSettings.LOAD_DEFAULT } catch (_: Exception) {}
                    } }) {
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
    // Force background color to prevent theme bleeding
    setBackgroundColor(if (isDark) Color.BLACK else Color.WHITE)

    // Use safe calls and guards so this runs on API 31..37 (and other runtimes)
    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING)) {
            WebSettingsCompat.setAlgorithmicDarkeningAllowed(settings, isDark)
        }
    } catch (_: Throwable) { /* best-effort */ }

    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK)) {
            @Suppress("DEPRECATION")
            val forceDark = if (isDark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDark(settings, forceDark)
        }
    } catch (_: Throwable) { /* best-effort */ }

    try {
        if (WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK_STRATEGY)) {
            @Suppress("DEPRECATION")
            val strategy = WebSettingsCompat.DARK_STRATEGY_PREFER_WEB_THEME_OVER_USER_AGENT_DARKENING
            @Suppress("DEPRECATION")
            WebSettingsCompat.setForceDarkStrategy(settings, strategy)
        }
    } catch (_: Throwable) { /* best-effort */ }
}

private fun WebView.injectPrefersColorScheme(isDark: Boolean) {
    // More robust JS shim for window.matchMedia to help pages honour the spoofed system theme.
    val boolStr = if (isDark) "true" else "false"
    val js = """
        (function(isDark){
          try{
            const origMatch = window.matchMedia.bind(window);

            function createMQL(query){
              const matches = query.indexOf('prefers-color-scheme') !== -1 && isDark === true;
              const listeners = [];
              const mql = {
                matches: matches,
                media: query,
                onchange: null,
                addListener: function(cb){ if(typeof cb === 'function') listeners.push(cb); },
                removeListener: function(cb){ const i = listeners.indexOf(cb); if(i>-1) listeners.splice(i,1); },
                addEventListener: function(type, cb){ if(type === 'change' && typeof cb === 'function') listeners.push(cb); },
                removeEventListener: function(type, cb){ if(type === 'change'){ const i = listeners.indexOf(cb); if(i>-1) listeners.splice(i,1); } },
                dispatchEvent: function(ev){ listeners.forEach(function(cb){ try{ cb(ev); }catch(e){} }); return true; }
              };
              return mql;
            }

            window.matchMedia = function(query){
              if (query && query.indexOf('prefers-color-scheme') !== -1) {
                return createMQL(query);
              }
              return origMatch(query);
            };

            // Notify any listeners on the document/window that theme changed
            try{ window.dispatchEvent(new Event('prefers-color-scheme-changed')); }catch(e){}
          }catch(e){}
        })($boolStr);
    """.trimIndent()

    try {
        post { evaluateJavascript(js, null) }
    } catch (_: Throwable) { /* ignore */ }
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
