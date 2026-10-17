package com.miohue.notepad.data

import java.util.regex.Pattern

/** 自动扫描笔记内容，识别其中的链接、图片等，返回分类统计。 */
object ContentScanner {

    // 匹配 URL：http/https
    private val URL_REGEX = Pattern.compile(
        "https?://[\\w\\-._~:/?#\\[\\]@!$&'()*+,;=%]+",
        Pattern.CASE_INSENSITIVE
    )

    /** 图片扩展名（判定与 `BrowserScreen.isMediaLink` 同思路：剥掉 query/fragment 再看后缀）。 */
    private val IMAGE_EXTS = setOf("png", "jpg", "jpeg", "gif", "webp", "bmp", "svg")

    /** 剥离 URL 尾部被正则顺带吃进来的标点（如 Markdown 的 `(...url...)`）。 */
    private fun trimUrlTail(url: String): String =
        url.trimEnd(')', ']', '}', ',', '.', ';', '!', '"', '\'')

    /**
     * URL 是否为图片直链。
     *
     * 两个坑都要处理：
     * 1. 不能只看扩展名结尾——URL 常带 `?query` / `#fragment`，要先剥掉；
     * 2. URL 正文正则允许 `)` 等标点，所以 `.../p.png)` 这种（Markdown 里 `(...url...)`）
     *    会连右括号一起匹配进来，必须先去掉尾部非路径字符再判扩展名。
     */
    private fun isImageUrl(url: String): Boolean {
        val path = trimUrlTail(url).substringBefore('?').substringBefore('#')
        return path.substringAfterLast('.', "").lowercase() in IMAGE_EXTS
    }

    data class ScanResult(
        val urls: List<String>,
        val imageUrls: List<String>,
        val fileUrls: List<String>,
        val text: String
    )

    /**
     * 扫描笔记内容，识别链接 / 图片。
     *
     * 性能说明：原实现把 `URL_REGEX` 在 matcher 上找出全部匹配，然后为了算「去链接后的纯文字」
     * 又用 `replaceAll` 对整个字符串重跑一遍正则，等于同一段文本扫两遍；
     * 分类页一次要处理全部笔记，笔记多时开销可观。
     * 这里改成单遍收集匹配区间，再用 StringBuilder 一次性拼接纯文字。
     */
    fun scan(content: String): ScanResult {
        val urls = mutableListOf<String>()
        val imageUrls = mutableListOf<String>()
        val m = URL_REGEX.matcher(content)
        val sb = StringBuilder(content.length)
        var lastEnd = 0
        while (m.find()) {
            val url = m.group()
            urls.add(url)
            if (isImageUrl(url)) imageUrls.add(trimUrlTail(url))
            sb.append(content, lastEnd, m.start())
            lastEnd = m.end()
        }
        sb.append(content, lastEnd, content.length)
        val imageSet = imageUrls.toHashSet()
        val fileUrls = urls.filter { it !in imageSet }
        return ScanResult(urls, imageUrls, fileUrls, sb.toString().trim())
    }
}
