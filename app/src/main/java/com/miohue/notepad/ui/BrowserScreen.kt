package com.miohue.notepad.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.*
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R

/**
 * 内置浏览器：地址栏 + 前进/后退/刷新 + 嗅探（识别页面资源链接）+ 下载。
 */
@SuppressLint("SetJavaScriptEnabled")
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BrowserScreen(url: String, onBack: () -> Unit) {
    val context = androidx.compose.ui.platform.LocalContext.current
    var pageUrl by remember { mutableStateOf(url) }
    var webView by remember { mutableStateOf<WebView?>(null) }
    var snifferLinks by remember { mutableStateOf<List<String>>(emptyList()) }
    var showSniffer by remember { mutableStateOf(false) }
    var currentTitle by remember { mutableStateOf("") }
    // 驱动「后退/前进」按钮的可用状态刷新
    var navTick by remember { mutableStateOf(0) }
    val canBack = remember(navTick, webView) { webView?.canGoBack() == true }
    val canForward = remember(navTick, webView) { webView?.canGoForward() == true }

    Scaffold(

        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = Color.Transparent,
        topBar = {
            TopAppBar(
                title = { Text(currentTitle.ifBlank { stringResource(R.string.web_001) }, maxLines = 1) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent),
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                },
                actions = {
                    IconButton(onClick = { showSniffer = !showSniffer }) { Icon(Icons.Default.TravelExplore, contentDescription = stringResource(R.string.web_003)) }
                    IconButton(enabled = canBack, onClick = { webView?.goBack() }) { Icon(Icons.Default.ArrowBackIosNew, contentDescription = stringResource(R.string.web_004)) }
                    IconButton(enabled = canForward, onClick = { webView?.goForward() }) { Icon(Icons.Default.ArrowForwardIos, contentDescription = stringResource(R.string.web_005)) }
                    IconButton(onClick = { webView?.reload() }) { Icon(Icons.Default.Refresh, contentDescription = stringResource(R.string.web_006)) }
                    IconButton(onClick = {
                        // 分享当前页；不再外跳浏览器
                        runCatching {
                            val intent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/plain"
                                putExtra(Intent.EXTRA_TEXT, pageUrl)
                            }
                            context.startActivity(Intent.createChooser(intent, context.getString(R.string.web_007)))
                        }
                    }) { Icon(Icons.Default.Share, contentDescription = stringResource(R.string.web_008)) }
                }
            )
        }
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                OutlinedTextField(
                    value = pageUrl,
                    onValueChange = { pageUrl = it },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.web_009)) }
                )
                IconButton(onClick = {
                    val u = if (pageUrl.startsWith("http")) pageUrl else "https://$pageUrl"
                    webView?.loadUrl(u)
                }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.web_010)) }
            }

            // 嗅探结果面板
            if (showSniffer) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .weight(0.35f)
                ) {
                    Text(
                        stringResource(R.string.web_res_001, snifferLinks.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                    androidx.compose.foundation.lazy.LazyColumn(Modifier.fillMaxSize()) {
                        items(snifferLinks) { link ->
                            val isMedia = remember(link) { isMediaLink(link) }
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        // 页面链接 → 站内跳转；媒体资源 → 下载
                                        if (isMedia) download(context, link) else webView?.loadUrl(link)
                                        navTick++
                                    }
                                    .padding(horizontal = 16.dp, vertical = 6.dp)
                            ) {
                                Icon(
                                    if (isMedia) Icons.Default.Download else Icons.Default.Link,
                                    null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(Modifier.width(8.dp))
                                Text(link, maxLines = 1, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                }
                HorizontalDivider()
            }

            AndroidView(
                factory = { ctx ->
                    WebView(ctx).apply {
                        webViewClient = object : WebViewClient() {
                            override fun onPageFinished(view: WebView?, url: String?) {
                                super.onPageFinished(view, url)
                                currentTitle = view?.title ?: ""
                                pageUrl = url ?: pageUrl
                                navTick++
                                // 嗅探：执行 JS 收集页面里的链接/媒体资源
                                view?.evaluateJavascript(
                                    "javascript:(function(){var l=[];" +
                                            "document.querySelectorAll('a[href]').forEach(function(a){var h=a.href;if(h&&(h.startsWith('http')))l.push(h);});" +
                                            "document.querySelectorAll('video,source,img').forEach(function(e){var s=e.src||e.currentSrc;if(s&&s.startsWith('http'))l.push(s);});" +
                                            "return JSON.stringify(l.slice(0,50));})()"
                                ) { result ->
                                    runCatching {
                                        val arr = org.json.JSONArray(result)
                                        val list = (0 until arr.length()).map { arr.getString(it) }.distinct()
                                        snifferLinks = list
                                    }
                                }
                            }
                        }
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.loadsImagesAutomatically = true
                        setDownloadListener { downloadUrl, _, _, _, _ ->
                            download(ctx, downloadUrl)
                        }
                        loadUrl(url)
                        webView = this
                    }
                },
                modifier = Modifier.fillMaxWidth().weight(if (showSniffer) 0.65f else 1f)
            )
        }
    }
}

private val MEDIA_EXT = setOf(
    "jpg", "jpeg", "png", "gif", "webp", "bmp", "svg",
    "mp4", "mkv", "webm", "mov", "avi",
    "mp3", "m4a", "aac", "wav", "flac", "ogg",
    "pdf", "zip", "rar", "7z", "apk", "txt", "doc", "docx", "xls", "xlsx", "ppt", "pptx"
)

/** 判断嗅探链接是页面（站内跳转）还是媒体资源（下载）。 */
private fun isMediaLink(url: String): Boolean {
    val path = url.substringBefore('?').substringBefore('#')
    val ext = path.substringAfterLast('.', "").lowercase()
    return ext in MEDIA_EXT
}

/** 交给系统 DownloadManager 下载，不跳外部浏览器。 */
private fun download(context: android.content.Context, url: String) {
    runCatching {
        val request = android.app.DownloadManager.Request(Uri.parse(url)).apply {
            setNotificationVisibility(android.app.DownloadManager.Request.VISIBILITY_VISIBLE_NOTIFY_COMPLETED)
            setDestinationInExternalPublicDir(Environment.DIRECTORY_DOWNLOADS, url.substringAfterLast('/'))
        }
        val dm = context.getSystemService(android.app.DownloadManager::class.java)
        dm?.enqueue(request)
        android.widget.Toast.makeText(context, context.getString(R.string.web_011), android.widget.Toast.LENGTH_SHORT).show()
    }.onFailure {
        android.widget.Toast.makeText(context, context.getString(R.string.web_012), android.widget.Toast.LENGTH_SHORT).show()
    }
}
