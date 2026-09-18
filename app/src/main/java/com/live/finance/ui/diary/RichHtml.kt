package com.live.finance.ui.diary

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.live.finance.data.model.htmlToPlain
import com.live.finance.theme.LocalAppColors
import com.live.finance.ui.flow.FText

/**
 * 动态正文的**轻量 HTML 渲染器**（不引三方库）。
 *
 * 背景：web 详情页用 `v-html` 渲染后端 `content`（精准模式由 WangEditor 产出 HTML）；原生原先用
 * [htmlToPlain] 去标签 → 加粗/引用/列表/代码块等**样式全丢**。这里把 WangEditor 常见标签解析成
 * 「块 + 富文本行」，再用 Compose 画出来，覆盖：
 *
 * | 类别 | 支持 |
 * |---|---|
 * | 块 | `<p>` `<div>` `<h1>`~`<h6>` `<blockquote>` `<ul>/<ol>/<li>` `<pre>` `<hr>` |
 * | 行内 | `<strong>/<b>` `<em>/<i>` `<u>` `<s>/<strike>/<del>` `<code>` `<a>` `<span style="font-size:…px">` |
 * | 其他 | `&nbsp;/&lt;/&gt;/&quot;/&#39;/&amp;/&#NNN;/&#xNN;` 实体解码；`<p style="line-height:1.6">` 生效 |
 *
 * ⚠ 有意不做的两件事（记档，别再当 bug 修）：
 *  1. **`<img>` 一律丢弃** —— 图片由调用方的九宫格（`Moment.images`/`img_url`）渲染；web 在精准模式下
 *     其实是「正文内联 + 九宫格」双份显示，原生只保留九宫格。
 *  2. **`todo`（`<input type="checkbox">`）与 `emotion` 表情图**不渲染（前者是原生表单控件，后者本质是 img）。
 *  3. 未识别的标签**静默忽略**（容错优先，绝不因脏 HTML 崩页面）。
 */
internal enum class RichBlockKind { Para, Heading, Quote, Bullet, Ordered, Code, Divider }

/** 一个渲染块（`text` 已含行内样式）。 */
internal data class RichBlock(
    val kind: RichBlockKind,
    val text: AnnotatedString = AnnotatedString(""),
    /** 标题级别（1~6），仅 [RichBlockKind.Heading] 用。 */
    val level: Int = 0,
    /** 列表前缀（`• ` 或 `1. `）。 */
    val marker: String = "",
    /** `<p style="line-height:1.6">` 解析出的倍数；0 = 用默认 1.6。 */
    val lineHeight: Float = 0f,
)

// ───────────────────────────── 解析 ─────────────────────────────

private val RE_FONT_SIZE = Regex("""font-size\s*:\s*([0-9.]+)\s*px""", RegexOption.IGNORE_CASE)
private val RE_LINE_HEIGHT = Regex("""line-height\s*:\s*([0-9.]+)""", RegexOption.IGNORE_CASE)
private val RE_NUM_ENTITY = Regex("""&#(x?[0-9a-fA-F]+);""")

/** HTML → 渲染块列表（纯函数，便于单测/预览）。 */
internal fun parseRichHtml(html: String): List<RichBlock> {
    if (html.isBlank()) return emptyList()

    val out = mutableListOf<RichBlock>()
    var cur: AnnotatedString.Builder? = null
    var curKind = RichBlockKind.Para
    var curLevel = 0
    var curMarker = ""
    var curLh = 0f

    var quoteDepth = 0
    var preDepth = 0
    var listItemOpen = false
    var orderedSeq = 0
    val listKinds = ArrayDeque<Boolean>() // true = ol
    val inlineStack = ArrayDeque<String>()

    fun begin(kind: RichBlockKind, level: Int = 0, marker: String = "", lh: Float = 0f) {
        if (cur == null) {
            cur = AnnotatedString.Builder()
            curKind = kind
            curLevel = level
            curMarker = marker
            curLh = lh
        }
    }

    fun flush() {
        val b = cur ?: return
        val s = b.toAnnotatedString()
        if (s.text.isNotEmpty()) out.add(RichBlock(curKind, s, curLevel, curMarker, curLh))
        cur = null
        inlineStack.clear() // 跨块的行内样式一律丢弃（web 也不会跨块生效）
    }

    /**
     * 按当前上下文决定块的类型：`<pre>` 内=代码块、`<blockquote>` 内=引用、
     * `<li>` 内=列表项、其余=普通段落。
     */
    fun contextKind(): RichBlockKind = when {
        preDepth > 0 -> RichBlockKind.Code
        quoteDepth > 0 -> RichBlockKind.Quote
        listItemOpen -> if (listKinds.lastOrNull() == true) RichBlockKind.Ordered else RichBlockKind.Bullet
        else -> RichBlockKind.Para
    }

    /** 追加文本到当前块（按上下文决定块类型与前缀）。 */
    fun append(text: String) {
        if (text.isEmpty()) return
        val marker = when {
            !listItemOpen -> ""
            listKinds.lastOrNull() == true -> "${orderedSeq}. "
            else -> "• "
        }
        begin(contextKind(), marker = marker)
        cur?.append(text)
    }

    fun ensureInline(): AnnotatedString.Builder {
        begin(RichBlockKind.Para)
        return cur!!
    }

    fun pushInline(tag: String, style: SpanStyle) {
        ensureInline().pushStyle(style)
        inlineStack.addLast(tag)
    }

    fun popInline(tag: String) {
        val idx = inlineStack.indexOfLast { it == tag }
        if (idx < 0) return
        val b = cur ?: return
        repeat(inlineStack.size - idx) {
            runCatching { b.pop() }
            if (inlineStack.isNotEmpty()) inlineStack.removeLast()
        }
    }

    var i = 0
    while (i < html.length) {
        val lt = html.indexOf('<', i)
        if (lt < 0) {
            append(decodeEntities(html.substring(i)))
            break
        }
        if (lt > i) append(decodeEntities(html.substring(i, lt)))
        val gt = html.indexOf('>', lt)
        if (gt < 0) {
            append(decodeEntities(html.substring(lt)))
            break
        }
        val raw = html.substring(lt + 1, gt).trim()
        i = gt + 1
        if (raw.isEmpty() || raw.startsWith("!--") || raw.startsWith("!")) continue

        val closing = raw.startsWith("/")
        val body = if (closing) raw.substring(1) else raw
        val name = body.substringBefore(' ').substringBefore('/').trim().lowercase()
        val attrs = body.substringAfter(' ', "")

        when {
            // ---- 行内 ----
            !closing && (name == "strong" || name == "b") -> pushInline(name, SpanStyle(fontWeight = FontWeight.Bold))
            closing && (name == "strong" || name == "b") -> popInline(name)
            !closing && (name == "em" || name == "i") -> pushInline(name, SpanStyle(fontStyle = FontStyle.Italic))
            closing && (name == "em" || name == "i") -> popInline(name)
            !closing && name == "u" -> pushInline(name, SpanStyle(textDecoration = TextDecoration.Underline))
            closing && name == "u" -> popInline(name)
            !closing && (name == "s" || name == "strike" || name == "del") ->
                pushInline(name, SpanStyle(textDecoration = TextDecoration.LineThrough))
            closing && (name == "s" || name == "strike" || name == "del") -> popInline(name)
            !closing && name == "code" && preDepth == 0 ->
                pushInline(name, SpanStyle(fontFamily = FontFamily.Monospace, background = LocalAppColorsHolder.inlineCodeBg))
            closing && name == "code" && preDepth == 0 -> popInline(name)
            !closing && name == "a" -> pushInline(name, SpanStyle(textDecoration = TextDecoration.Underline))
            closing && name == "a" -> popInline(name)
            !closing && name == "span" -> {
                val px = RE_FONT_SIZE.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()
                val lh = RE_LINE_HEIGHT.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it in 0.8f..3f }
                if (px != null) pushInline(name, SpanStyle(fontSize = px.sp)) else if (cur == null && lh != null) begin(RichBlockKind.Para, lh = lh)
            }
            closing && name == "span" -> popInline(name)

            // ---- 代码块 ----
            !closing && name == "pre" -> { flush(); preDepth++ }
            closing && name == "pre" -> { flush(); preDepth = (preDepth - 1).coerceAtLeast(0) }

            // ---- 引用 ----
            !closing && name == "blockquote" -> { flush(); quoteDepth++ }
            closing && name == "blockquote" -> { flush(); quoteDepth = (quoteDepth - 1).coerceAtLeast(0) }

            // ---- 列表 ----
            !closing && (name == "ul" || name == "ol") -> {
                flush()
                listKinds.addLast(name == "ol")
                if (name == "ol") orderedSeq = 1
            }
            closing && (name == "ul" || name == "ol") -> {
                flush()
                if (listKinds.isNotEmpty()) listKinds.removeLast()
            }
            !closing && name == "li" -> {
                flush()
                listItemOpen = true
                val ordered = listKinds.lastOrNull() == true
                begin(if (ordered) RichBlockKind.Ordered else RichBlockKind.Bullet, marker = if (ordered) "$orderedSeq. " else "• ")
                if (ordered) orderedSeq++
            }
            closing && name == "li" -> { flush(); listItemOpen = false }

            // ---- 标题 ----
            !closing && name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> {
                flush()
                begin(RichBlockKind.Heading, level = name[1] - '0', lh = lineHeightOf(attrs))
            }
            closing && name.length == 2 && name[0] == 'h' && name[1] in '1'..'6' -> flush()

            // ---- 分隔线 ----
            !closing && name == "hr" -> { flush(); out.add(RichBlock(RichBlockKind.Divider)) }

            // ---- 段落/容器 ----
            !closing && (name == "p" || name == "div") -> {
                // `<li><p>` 里的 p 当成行内换行处理（见下方闭合分支）；引用内的 p 保持 Quote 块
                if (!listItemOpen) { flush(); begin(contextKind(), lh = lineHeightOf(attrs)) }
            }
            closing && (name == "p" || name == "div") -> {
                if (listItemOpen) append("\n") else flush()
            }
            !closing && name == "br" -> append("\n")

            // ---- 其余（含 img / input / 未知标签）静默忽略 ----
            else -> Unit
        }
    }
    flush()
    return out
}

private fun lineHeightOf(attrs: String): Float =
    RE_LINE_HEIGHT.find(attrs)?.groupValues?.get(1)?.toFloatOrNull()?.takeIf { it in 0.8f..3f } ?: 0f

/** 实体解码（`&amp;` 必须最后处理）。 */
private fun decodeEntities(s: String): String {
    if (!s.contains('&')) return s
    var t = s
        .replace("&nbsp;", "\u00A0")
        .replace("&lt;", "<")
        .replace("&gt;", ">")
        .replace("&quot;", "\"")
        .replace("&#39;", "'")
        .replace("&apos;", "'")
    t = RE_NUM_ENTITY.replace(t) { m ->
        val v = m.groupValues[1]
        val code = runCatching {
            if (v.startsWith("x", true)) v.substring(1).toInt(16) else v.toInt()
        }.getOrDefault(-1)
        if (code > 0) String(Character.toChars(code)) else m.value
    }
    return t.replace("&amp;", "&")
}

/** 行内 `<code>` 底色（无主题依赖的常量，避免解析器读取 Composable 作用域）。 */
private object LocalAppColorsHolder {
    val inlineCodeBg: Color = Color(0x14808080)
}

// ───────────────────────────── 渲染 ─────────────────────────────

/**
 * 渲染 [html]（web `v-html` 的轻量等价物）。
 *
 * @param baseSizeSp 正文基准字号（详情主贴 17 / 追文 15）。
 * @param paraGap 段落间距（web 详情 `.content-text p{margin:0 0 8px}` = 8，追文 `6`）。
 */
@Composable
fun RichContent(
    html: String,
    baseSizeSp: Float,
    modifier: Modifier = Modifier,
    paraGap: Dp = 8.dp,
) {
    val colors = LocalAppColors.current
    val blocks = remember(html) { parseRichHtml(html) }
    if (blocks.isEmpty()) {
        val plain = remember(html) { htmlToPlain(html) }
        if (plain.isNotEmpty()) FText(plain, baseSizeSp, FontWeight.Normal, colors.textPrimary, modifier)
        return
    }

    Column(modifier) {
        blocks.forEach { b ->
            when (b.kind) {
                RichBlockKind.Divider -> Box(
                    Modifier.fillMaxWidth().padding(vertical = 8.dp).height(1.dp).background(colors.border),
                )

                RichBlockKind.Quote -> Row(
                    Modifier.fillMaxWidth().height(IntrinsicSize.Min).padding(vertical = 4.dp),
                ) {
                    // web `blockquote{border-left:3px solid primary; padding:4px 12px; color:text-secondary; bg:bg-tertiary}`
                    Box(Modifier.width(3.dp).fillMaxHeight().background(colors.primary))
                    Box(
                        Modifier
                            .weight(1f)
                            .clip(RoundedCornerShape(topEnd = 4.dp, bottomEnd = 4.dp))
                            .background(colors.bgThird)
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        BasicText(
                            b.text,
                            style = TextStyle(
                                color = colors.textSecondary,
                                fontSize = baseSizeSp.sp,
                                lineHeight = (baseSizeSp * 1.6f).sp,
                            ),
                        )
                    }
                }

                RichBlockKind.Code -> Column(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(6.dp))
                            .background(colors.bgThird)
                            .padding(horizontal = 10.dp, vertical = 8.dp),
                    ) {
                        BasicText(
                            b.text.text,
                            style = TextStyle(
                                color = colors.textPrimary,
                                fontSize = (baseSizeSp * 0.92f).sp,
                                lineHeight = (baseSizeSp * 0.92f * 1.5f).sp,
                                fontFamily = FontFamily.Monospace,
                            ),
                        )
                    }
                }

                RichBlockKind.Heading -> {
                    val factor = when (b.level) {
                        1 -> 1.5f
                        2 -> 1.32f
                        3 -> 1.16f
                        else -> 1.06f
                    }
                    val size = baseSizeSp * factor
                    BasicText(
                        b.text,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = size.sp,
                            lineHeight = (size * 1.4f).sp,
                            fontWeight = FontWeight.Bold,
                        ),
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = paraGap),
                    )
                }

                RichBlockKind.Bullet, RichBlockKind.Ordered -> Row(Modifier.fillMaxWidth().padding(bottom = paraGap)) {
                    BasicText(
                        b.marker.ifEmpty { "• " },
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = baseSizeSp.sp,
                            lineHeight = (baseSizeSp * 1.6f).sp,
                        ),
                        modifier = Modifier.width(22.dp),
                    )
                    BasicText(
                        b.text,
                        style = TextStyle(
                            color = colors.textPrimary,
                            fontSize = baseSizeSp.sp,
                            lineHeight = (baseSizeSp * (b.lineHeight.takeIf { it > 0f } ?: 1.6f)).sp,
                        ),
                        modifier = Modifier.weight(1f),
                    )
                }

                RichBlockKind.Para -> BasicText(
                    b.text,
                    style = TextStyle(
                        color = colors.textPrimary,
                        fontSize = baseSizeSp.sp,
                        lineHeight = (baseSizeSp * (b.lineHeight.takeIf { it > 0f } ?: 1.6f)).sp,
                    ),
                    modifier = Modifier.fillMaxWidth().padding(bottom = paraGap),
                )
            }
        }
    }
}
