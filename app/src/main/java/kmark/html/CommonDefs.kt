package kmark.html

// 对齐 Kori：单 sourceSet（Android）平台实现，替换原 expect 声明，
// 内容取自 kori-ref androidMain/kmark/html/CommonDefs.android.kt（去 actual 化）。
import java.net.URLEncoder

class BitSet(size: Int) : java.util.BitSet(size) {
    val size = size()
}

private const val PUNCTUATION_MASK: Int = (1 shl Character.DASH_PUNCTUATION.toInt()) or
        (1 shl Character.START_PUNCTUATION.toInt()) or
        (1 shl Character.END_PUNCTUATION.toInt()) or
        (1 shl Character.CONNECTOR_PUNCTUATION.toInt()) or
        (1 shl Character.OTHER_PUNCTUATION.toInt()) or
        (1 shl Character.INITIAL_QUOTE_PUNCTUATION.toInt()) or
        (1 shl Character.FINAL_QUOTE_PUNCTUATION.toInt()) or
        (1 shl Character.MATH_SYMBOL.toInt())

typealias URI = java.net.URI

// 对齐 Kori commonMain CommonDefs.kt：URI 相对解析扩展（expect 声明里的非-abc 扩展）
fun URI.resolveToStringSafe(str: String): String {
    return try {
        resolve(str).toString()
    } catch (e: Throwable) {
        str
    }
}

// 对齐 Kori commonMain CommonDefs.kt：BitSet.clear 扩展
fun BitSet.clear(index: Int) = set(index, false)

fun isWhitespace(char: Char): Boolean {
    return char == 0.toChar() || Character.isSpaceChar(char) || char.isWhitespace()
}

fun isPunctuation(char: Char): Boolean {
    return isAsciiPunctuationFix(char) || (PUNCTUATION_MASK shr Character.getType(char)) and 1 != 0
}

private fun isAsciiPunctuationFix(char: Char): Boolean {
    // the ones which are not covered by a more general check
    return "$^`".contains(char)
}

fun urlEncode(str: String): String {
    return URLEncoder.encode(str, "UTF-8")
}
