package com.miohue.notepad.ui

import android.annotation.SuppressLint
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.webkit.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.res.stringResource
import com.miohue.notepad.R
import java.net.URLEncoder

/** 起始页快捷方式（适合笔记/技术阅读场景）。 */
private data class QuickSite(val name: String, val host: String, val label: String)

private val QUICK_SITES = listOf(
    QuickSite("GitHub", "github.com", "G"),
    QuickSite("MDN", "developer.mozilla.org", "M"),
    QuickSite("StackOverflow", "stackoverflow.com", "S"),
    QuickSite("掘金", "juejin.cn", "掘"),
    QuickSite("知乎", "zhihu.com", "知"),
    QuickSite("哔哩哔哩", "bilibili.com", "B"),
    QuickSite("维基百科", "zh.wikipedia.org", "维"),
    QuickSite("CSDN", "csdn.net", "C")
)

private const val SEARCH_URL = "https://www.baidu.com/s?wd="
private const val BH_PREFS = "browser_history"

/** 判定输入是搜索词还是网址：含空格或无点 → 搜索；否则按网址处理。 */
private fun resolveQuery(q: String): String {
    val t = q.trim()
    return when {
        t.isEmpty() -> t
        t.startsWith("http://") || t.startsWith("https://") -> t
        t.contains(' ') || !t.contains('.') -> SEARCH_URL + URLEncoder.encode(t, "UTF-8")
        else -> "https://$t"
    }
}

/** 读取最近访问历史（url to title，最多 12 条）。 */
private fun loadHistory(context: android.content.Context): List<Pair<String, String>> = runCatching {
    val sp = context.getSharedPreferences(BH_PREFS, 0)
    val arr = org.json.JSONArray(sp.getString("recent", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        o.getString("u") to o.getString("t")
    }
}.getOrDefault(emptyList())

/** 记录一条访问历史：去重置顶，最多保留 50 条。 */
private fun addHistory(context: android.content.Context, url: String, title: String) {
    runCatching {
        val sp = context.getSharedPreferences(BH_PREFS, 0)
        val list = loadHistory(context).toMutableList()
        list.removeAll { it.first == url }
        list.add(0, url to title)
        val arr = org.json.JSONArray()
        list.take(50).forEach { (u, t) ->
            arr.put(org.json.JSONObject().put("u", u).put("t", t))
        }
        sp.edit().putString("recent", arr.toString()).apply()
    }
}

/** 读取书签（url to title）。 */
private fun loadBookmarks(context: android.content.Context): List<Pair<String, String>> = runCatching {
    val arr = org.json.JSONArray(context.getSharedPreferences(BH_PREFS, 0).getString("bookmarks", "[]"))
    (0 until arr.length()).map { i ->
        val o = arr.getJSONObject(i)
        o.getString("u") to o.getString("t")
    }
}.getOrDefault(emptyList())

/** 切换书签状态（已收藏则移除，否则添加置顶），返回切换后是否已收藏。 */
private fun toggleBookmark(context: android.content.Context, url: String, title: String): Boolean {
    val sp = context.getSharedPreferences(BH_PREFS, 0)
    val list = loadBookmarks(context).toMutableList()
    val existed = list.removeAll { it.first == url }
    if (!existed) list.add(0, url to title)
    val arr = org.json.JSONArray()
    list.take(200).forEach { (u, t) ->
        arr.put(org.json.JSONObject().put("u", u).put("t", t))
    }
    sp.edit().putString("bookmarks", arr.toString()).apply()
    return !existed
}

/** 从书签中移除指定 URL。 */
private fun removeBookmark(context: android.content.Context, url: String) {
    val sp = context.getSharedPreferences(BH_PREFS, 0)
    val list = loadBookmarks(context).toMutableList()
    list.removeAll { it.first == url }
    val arr = org.json.JSONArray()
    list.forEach { (u, t) ->
        arr.put(org.json.JSONObject().put("u", u).put("t", t))
    }
    sp.edit().putString("bookmarks", arr.toString()).apply()
}

/** 清空全部浏览历史。 */
private fun clearHistory(context: android.content.Context) {
    context.getSharedPreferences(BH_PREFS, 0).edit().putString("recent", "[]").apply()
}

/**
 * 内置浏览器：起始页（搜索 + 快捷方式 + 最近访问）+ WebView（地址栏/前进后退/刷新/嗅探/下载）。
 * browser: 路由不带 URL 时进入起始页；顶栏 Home 按钮随时返回起始页。
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
    var progress by remember { mutableStateOf(0) }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var bookmarks by remember { mutableStateOf(loadBookmarks(context)) }
    var menuOpen by remember { mutableStateOf(false) }
    val isBookmarked = bookmarks.any { it.first == pageUrl }
    // 驱动「后退/前进」按钮的可用状态刷新
    var navTick by remember { mutableStateOf(0) }
    val canBack = remember(navTick, webView) { webView?.canGoBack() == true }
    val canForward = remember(navTick, webView) { webView?.canGoForward() == true }
    val scheme = MaterialTheme.colorScheme
    val isHome = pageUrl.isBlank()

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        containerColor = if (isHome) scheme.surfaceContainer else Color.Transparent,
        topBar = {
            // 浏览页：顶部仅加载进度条；起始页无顶栏
            if (!isHome && progress in 1..99) {
                LinearProgressIndicator(
                    progress = { progress / 100f },
                    modifier = Modifier.fillMaxWidth().height(2.dp)
                )
            }
        },
        bottomBar = {
            // 浏览页：工具栏沉底（返回 + 地址栏 + 书签星标 + 更多菜单），起始页无底栏
            if (!isHome) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .height(56.dp)
                        .padding(horizontal = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.web_002)) }
                    var urlInput by remember(pageUrl) { mutableStateOf(pageUrl) }
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        modifier = Modifier.weight(1f).padding(horizontal = 4.dp).heightIn(min = 44.dp),
                        shape = RoundedCornerShape(22.dp),
                        singleLine = true,
                        textStyle = MaterialTheme.typography.bodySmall,
                        placeholder = { Text(stringResource(R.string.web_009), style = MaterialTheme.typography.bodySmall, maxLines = 1) },
                        trailingIcon = {
                            IconButton(onClick = {
                                val u = if (urlInput.startsWith("http")) urlInput else "https://$urlInput"
                                webView?.loadUrl(u); navTick++
                            }) { Icon(Icons.Default.Search, contentDescription = stringResource(R.string.web_010), modifier = Modifier.size(20.dp)) }
                        }
                    )
                    // 书签星标：点亮 = 已收藏
                    IconButton(onClick = {
                        toggleBookmark(context, pageUrl, currentTitle)
                        bookmarks = loadBookmarks(context)
                    }) {
                        Icon(
                            if (isBookmarked) Icons.Default.Bookmark else Icons.Default.BookmarkBorder,
                            contentDescription = stringResource(if (isBookmarked) R.string.web_023 else R.string.web_022),
                            tint = if (isBookmarked) scheme.primary else scheme.onSurfaceVariant
                        )
                    }
                    // 更多菜单：回首页/嗅探/后退/前进/刷新/分享/外部浏览器
                    Box {
                        IconButton(onClick = { menuOpen = true }) { Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.web_024)) }
                        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_019)) },
                                leadingIcon = { Icon(Icons.Default.Home, null) },
                                onClick = { menuOpen = false; pageUrl = "" }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_003)) },
                                leadingIcon = { Icon(Icons.Default.TravelExplore, null) },
                                onClick = { menuOpen = false; showSniffer = !showSniffer }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_004)) },
                                enabled = canBack,
                                leadingIcon = { Icon(Icons.Default.ArrowBackIosNew, null) },
                                onClick = { menuOpen = false; webView?.goBack(); navTick++ }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_005)) },
                                enabled = canForward,
                                leadingIcon = { Icon(Icons.Default.ArrowForwardIos, null) },
                                onClick = { menuOpen = false; webView?.goForward(); navTick++ }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_006)) },
                                leadingIcon = { Icon(Icons.Default.Refresh, null) },
                                onClick = { menuOpen = false; webView?.reload() }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_007)) },
                                leadingIcon = { Icon(Icons.Default.Share, null) },
                                onClick = {
                                    menuOpen = false
                                    runCatching {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_TEXT, pageUrl)
                                        }
                                        context.startActivity(Intent.createChooser(intent, context.getString(R.string.web_007)))
                                    }
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.web_013)) },
                                leadingIcon = { Icon(Icons.Default.OpenInNew, null) },
                                onClick = {
                                    menuOpen = false
                                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(pageUrl))) }
                                }
                            )
                        }
                    }
                }
            }
        }
    ) { padding ->

        Column(Modifier.padding(padding).fillMaxSize()) {
            if (isHome) {
                BrowserHomePage(
                    history = history,
                    bookmarks = bookmarks,
                    onOpen = { pageUrl = it },
                    onSearch = { pageUrl = resolveQuery(it) },
                    onRemoveBookmark = { u -> removeBookmark(context, u); bookmarks = loadBookmarks(context) },
                    onClearHistory = { clearHistory(context); history = emptyList() }
                )
            } else {
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
                                    // 记录访问历史（起始页刷新首页列表）
                                    if (!url.isNullOrBlank()) {
                                        addHistory(context, url, view?.title ?: "")
                                        history = loadHistory(context)
                                    }
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
                            webChromeClient = object : WebChromeClient() {
                                override fun onProgressChanged(view: WebView?, newProgress: Int) {
                                    progress = newProgress
                                }
                            }
                            settings.javaScriptEnabled = true
                            settings.domStorageEnabled = true
                            settings.loadsImagesAutomatically = true
                            setDownloadListener { downloadUrl, _, _, _, _ ->
                                download(ctx, downloadUrl)
                            }
                            loadUrl(pageUrl)
                            webView = this
                        }
                    },
                    modifier = Modifier.fillMaxWidth().weight(if (showSniffer) 0.65f else 1f)
                )
            }
        }
    }
}

/** 起始页：猫咪标识 + 搜索框 + 快捷方式网格 + 最近访问。 */
@Composable
private fun BrowserHomePage(
    history: List<Pair<String, String>>,
    bookmarks: List<Pair<String, String>>,
    onOpen: (String) -> Unit,
    onSearch: (String) -> Unit,
    onRemoveBookmark: (String) -> Unit,
    onClearHistory: () -> Unit
) {
    val scheme = MaterialTheme.colorScheme
    var query by remember { mutableStateOf("") }

    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(30.dp))
        Image(
            painter = painterResource(R.drawable.ic_meow_cat),
            contentDescription = null,
            modifier = Modifier.size(64.dp)
        )
        Spacer(Modifier.height(10.dp))
        Text("MeowPad", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = scheme.onSurface)
        Text(
            stringResource(R.string.web_014),
            style = MaterialTheme.typography.bodySmall,
            color = scheme.onSurfaceVariant
        )
        Spacer(Modifier.height(24.dp))

        // 搜索 / 输入网址
        OutlinedTextField(
            value = query,
            onValueChange = { query = it },
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(24.dp),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.web_015)) },
            leadingIcon = { Icon(Icons.Default.Search, null, tint = scheme.onSurfaceVariant) },
            trailingIcon = {
                if (query.isNotBlank()) {
                    IconButton(onClick = { onSearch(query); query = "" }) {
                        Icon(Icons.Default.ArrowForward, contentDescription = stringResource(R.string.web_010), tint = scheme.primary)
                    }
                }
            }
        )
        Spacer(Modifier.height(28.dp))

        // 快捷方式
        Text(
            stringResource(R.string.web_016),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(14.dp))
        QUICK_SITES.chunked(4).forEach { rowSites ->
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                rowSites.forEach { site -> QuickSiteItem(site, onOpen) }
            }
            Spacer(Modifier.height(16.dp))
        }

        Spacer(Modifier.height(12.dp))
        // 书签
        Text(
            stringResource(R.string.web_020),
            style = MaterialTheme.typography.labelLarge,
            color = scheme.onSurfaceVariant,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(6.dp))
        if (bookmarks.isEmpty()) {
            Text(
                stringResource(R.string.web_027),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 16.dp)
            )
        } else {
            bookmarks.forEach { (u, t) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(u) }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Bookmark,
                        null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.ifBlank { u },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            u,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    IconButton(onClick = { onRemoveBookmark(u) }, modifier = Modifier.size(28.dp)) {
                        Icon(Icons.Default.Close, contentDescription = stringResource(R.string.web_023), tint = scheme.onSurfaceVariant, modifier = Modifier.size(16.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        // 历史记录（标题行 + 清空按钮）
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Text(
                stringResource(R.string.web_021),
                style = MaterialTheme.typography.labelLarge,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.weight(1f)
            )
            if (history.isNotEmpty()) {
                TextButton(onClick = onClearHistory, contentPadding = PaddingValues(horizontal = 8.dp)) {
                    Text(stringResource(R.string.web_025), style = MaterialTheme.typography.labelMedium)
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        if (history.isEmpty()) {
            Text(
                stringResource(R.string.web_018),
                style = MaterialTheme.typography.bodySmall,
                color = scheme.onSurfaceVariant,
                modifier = Modifier.padding(vertical = 20.dp)
            )
        } else {
            history.forEach { (u, t) ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .clickable { onOpen(u) }
                        .padding(horizontal = 4.dp, vertical = 8.dp)
                ) {
                    Icon(
                        Icons.Default.Public,
                        null,
                        tint = scheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(10.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            t.ifBlank { u },
                            style = MaterialTheme.typography.bodyMedium,
                            color = scheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            u,
                            style = MaterialTheme.typography.labelSmall,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
        Spacer(Modifier.height(32.dp))
    }
}

/** 快捷方式圆标（首字头像 + 名称）。 */
@Composable
private fun QuickSiteItem(site: QuickSite, onOpen: (String) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.clickable { onOpen("https://" + site.host) }
    ) {
        Box(
            Modifier
                .size(52.dp)
                .clip(CircleShape)
                .background(scheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Text(
                site.label,
                style = MaterialTheme.typography.titleMedium,
                color = scheme.onPrimaryContainer
            )
        }
        Spacer(Modifier.height(6.dp))
        Text(
            site.name,
            style = MaterialTheme.typography.labelSmall,
            color = scheme.onSurface,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
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
    }
}
