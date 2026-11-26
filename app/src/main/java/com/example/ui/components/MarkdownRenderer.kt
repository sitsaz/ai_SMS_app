package com.example.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.ClickableText
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.links.LinkExtractor
import com.example.ui.theme.JetBrainsMonoFontFamily

enum class MarkdownTableAlign {
    LEFT, CENTER, RIGHT
}

sealed interface MarkdownBlock {
    data class Heading(val level: Int, val text: String) : MarkdownBlock
    data class Paragraph(val text: String) : MarkdownBlock
    data class BulletList(val items: List<String>) : MarkdownBlock
    data class NumberedList(val items: List<Pair<Int, String>>) : MarkdownBlock
    data class Blockquote(val text: String) : MarkdownBlock
    data class CodeBlock(val language: String, val code: String) : MarkdownBlock
    data class Table(
        val headers: List<String>,
        val alignments: List<MarkdownTableAlign>,
        val rows: List<List<String>>
    ) : MarkdownBlock
    data object HorizontalRule : MarkdownBlock
}

object MarkdownParser {

    private val headingRegex = Regex("""^(#{1,6})\s+(.+)$""")
    private val bulletRegex = Regex("""^\s*[-*+]\s+(.+)$""")
    private val numberedRegex = Regex("""^\s*(\d+)[.)]\s+(.+)$""")
    private val ruleRegex = Regex("""^\s*([-*_])\1{2,}\s*$""")

    fun parse(raw: String): List<MarkdownBlock> {
        if (raw.isBlank()) return emptyList()
        val lines = raw.replace("\r\n", "\n").split('\n')
        val blocks = mutableListOf<MarkdownBlock>()
        var i = 0

        while (i < lines.size) {
            val line = lines[i]
            val trimmed = line.trim()

            if (trimmed.isEmpty()) {
                i++
                continue
            }

            // Fenced code block
            if (trimmed.startsWith("```")) {
                val lang = trimmed.removePrefix("```").trim()
                val codeLines = mutableListOf<String>()
                i++
                while (i < lines.size && !lines[i].trim().startsWith("```")) {
                    codeLines.add(lines[i])
                    i++
                }
                if (i < lines.size) i++ // consume closing ```
                blocks.add(MarkdownBlock.CodeBlock(lang, codeLines.joinToString("\n")))
                continue
            }

            // Horizontal rule
            if (ruleRegex.matches(trimmed)) {
                blocks.add(MarkdownBlock.HorizontalRule)
                i++
                continue
            }

            // Heading
            headingRegex.matchEntire(trimmed)?.let { m ->
                blocks.add(MarkdownBlock.Heading(m.groupValues[1].length, m.groupValues[2].trim()))
                i++
                continue
            }

            // GFM Table (header row + separator row)
            if (trimmed.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1])) {
                val headers = parseTableRow(trimmed)
                val sepCells = parseTableRow(lines[i + 1].trim())
                val alignments = headers.indices.map { idx ->
                    val cell = sepCells.getOrElse(idx) { "---" }.trim()
                    when {
                        cell.startsWith(":") && cell.endsWith(":") -> MarkdownTableAlign.CENTER
                        cell.endsWith(":") -> MarkdownTableAlign.RIGHT
                        else -> MarkdownTableAlign.LEFT
                    }
                }
                i += 2
                val rows = mutableListOf<List<String>>()
                while (i < lines.size) {
                    val rowLine = lines[i].trim()
                    if (rowLine.isEmpty() || !rowLine.contains('|')) break
                    rows.add(parseTableRow(rowLine))
                    i++
                }
                blocks.add(MarkdownBlock.Table(headers, alignments, rows))
                continue
            }

            // Blockquote
            if (trimmed.startsWith(">")) {
                val quoteLines = mutableListOf<String>()
                while (i < lines.size && lines[i].trim().startsWith(">")) {
                    quoteLines.add(lines[i].trim().removePrefix(">").trimStart())
                    i++
                }
                blocks.add(MarkdownBlock.Blockquote(quoteLines.joinToString("\n")))
                continue
            }

            // Bullet list
            if (bulletRegex.matches(line)) {
                val items = mutableListOf<String>()
                while (i < lines.size) {
                    val m = bulletRegex.matchEntire(lines[i]) ?: break
                    items.add(m.groupValues[1].trim())
                    i++
                }
                blocks.add(MarkdownBlock.BulletList(items))
                continue
            }

            // Numbered list
            if (numberedRegex.matches(line)) {
                val items = mutableListOf<Pair<Int, String>>()
                while (i < lines.size) {
                    val m = numberedRegex.matchEntire(lines[i]) ?: break
                    val num = m.groupValues[1].toIntOrNull() ?: (items.size + 1)
                    items.add(num to m.groupValues[2].trim())
                    i++
                }
                blocks.add(MarkdownBlock.NumberedList(items))
                continue
            }

            // Paragraph (collect contiguous normal lines)
            val paraLines = mutableListOf<String>()
            while (i < lines.size) {
                val cur = lines[i]
                val curTrim = cur.trim()
                if (curTrim.isEmpty() ||
                    curTrim.startsWith("```") ||
                    curTrim.startsWith(">") ||
                    ruleRegex.matches(curTrim) ||
                    headingRegex.matches(curTrim) ||
                    bulletRegex.matches(cur) ||
                    numberedRegex.matches(cur) ||
                    (curTrim.contains('|') && i + 1 < lines.size && isTableSeparator(lines[i + 1]))
                ) {
                    break
                }
                paraLines.add(curTrim)
                i++
            }
            if (paraLines.isNotEmpty()) {
                blocks.add(MarkdownBlock.Paragraph(paraLines.joinToString(" ")))
            }
        }
        return blocks
    }

    private fun isTableSeparator(line: String): Boolean {
        val t = line.trim()
        if (!t.contains('-') || !t.contains('|')) return false
        val cells = parseTableRow(t)
        return cells.isNotEmpty() && cells.all { cell ->
            val c = cell.trim()
            c.matches(Regex("""^:?-{2,}:?$"""))
        }
    }

    private fun parseTableRow(row: String): List<String> {
        var trimmed = row.trim()
        if (trimmed.startsWith("|")) trimmed = trimmed.drop(1)
        if (trimmed.endsWith("|")) trimmed = trimmed.dropLast(1)
        return trimmed.split('|').map { it.trim() }
    }
}

@Composable
fun MarkdownText(
    markdown: String,
    modifier: Modifier = Modifier,
    textColor: Color = MaterialTheme.colorScheme.onSurface,
    baseStyle: TextStyle = MaterialTheme.typography.bodyMedium
) {
    val blocks = remember(markdown) { MarkdownParser.parse(markdown) }
    val linkColor = MaterialTheme.colorScheme.primary
    val codeBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current

    SelectionContainer(modifier = modifier) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            for (block in blocks) {
                when (block) {
                    is MarkdownBlock.Heading -> {
                        val style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                            2 -> MaterialTheme.typography.titleMedium.copy(fontWeight = FontWeight.Bold)
                            else -> MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold)
                        }
                        InlineMarkdownText(
                            text = block.text,
                            style = style.copy(color = textColor),
                            linkColor = linkColor,
                            codeBg = codeBg,
                            onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                        )
                    }
                    is MarkdownBlock.Paragraph -> {
                        InlineMarkdownText(
                            text = block.text,
                            style = baseStyle.copy(color = textColor),
                            linkColor = linkColor,
                            codeBg = codeBg,
                            onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                        )
                    }
                    is MarkdownBlock.BulletList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for (item in block.items) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "•",
                                        style = baseStyle.copy(fontWeight = FontWeight.Bold, color = linkColor)
                                    )
                                    InlineMarkdownText(
                                        text = item,
                                        style = baseStyle.copy(color = textColor),
                                        linkColor = linkColor,
                                        codeBg = codeBg,
                                        modifier = Modifier.weight(1f),
                                        onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                                    )
                                }
                            }
                        }
                    }
                    is MarkdownBlock.NumberedList -> {
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            for ((num, item) in block.items) {
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.Top
                                ) {
                                    Text(
                                        text = "$num.",
                                        style = baseStyle.copy(fontWeight = FontWeight.SemiBold, color = linkColor)
                                    )
                                    InlineMarkdownText(
                                        text = item,
                                        style = baseStyle.copy(color = textColor),
                                        linkColor = linkColor,
                                        codeBg = codeBg,
                                        modifier = Modifier.weight(1f),
                                        onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                                    )
                                }
                            }
                        }
                    }
                    is MarkdownBlock.Blockquote -> {
                        Surface(
                            shape = RoundedCornerShape(10.dp),
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                                Box(
                                    modifier = Modifier
                                        .width(4.dp)
                                        .background(MaterialTheme.colorScheme.primary)
                                        .padding(vertical = 4.dp)
                                )
                                InlineMarkdownText(
                                    text = block.text,
                                    style = baseStyle.copy(
                                        color = textColor.copy(alpha = 0.9f),
                                        fontStyle = FontStyle.Italic
                                    ),
                                    linkColor = linkColor,
                                    codeBg = codeBg,
                                    modifier = Modifier.padding(10.dp),
                                    onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                                )
                            }
                        }
                    }
                    is MarkdownBlock.CodeBlock -> {
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = codeBg,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = block.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelSmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    IconButton(
                                        onClick = { clipboard.setText(AnnotatedString(block.code)) },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            Icons.Default.ContentCopy,
                                            contentDescription = "Copy code",
                                            modifier = Modifier.size(14.dp)
                                        )
                                    }
                                }
                                Spacer(Modifier.height(4.dp))
                                Box(modifier = Modifier.horizontalScroll(rememberScrollState())) {
                                    Text(
                                        text = block.code,
                                        style = MaterialTheme.typography.bodySmall.copy(
                                            fontFamily = JetBrainsMonoFontFamily,
                                            lineHeight = 18.sp
                                        ),
                                        color = MaterialTheme.colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                    is MarkdownBlock.Table -> {
                        MarkdownTableView(
                            table = block,
                            textColor = textColor,
                            linkColor = linkColor,
                            codeBg = codeBg,
                            onOpenUrl = { LinkExtractor.openUrlSafely(context, it) }
                        )
                    }
                    MarkdownBlock.HorizontalRule -> {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                    }
                }
            }
        }
    }
}

@Composable
private fun MarkdownTableView(
    table: MarkdownBlock.Table,
    textColor: Color,
    linkColor: Color,
    codeBg: Color,
    onOpenUrl: (String) -> Unit
) {
    val borderColor = MaterialTheme.colorScheme.outline.copy(alpha = 0.35f)
    val headerBg = MaterialTheme.colorScheme.surfaceContainerHighest
    val altRowBg = MaterialTheme.colorScheme.surfaceContainerLow

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .border(1.dp, borderColor, RoundedCornerShape(12.dp))
            .horizontalScroll(rememberScrollState())
    ) {
        Column {
            // Header
            Row(modifier = Modifier.background(headerBg)) {
                table.headers.forEachIndexed { idx, header ->
                    val align = when (table.alignments.getOrElse(idx) { MarkdownTableAlign.LEFT }) {
                        MarkdownTableAlign.CENTER -> TextAlign.Center
                        MarkdownTableAlign.RIGHT -> TextAlign.End
                        MarkdownTableAlign.LEFT -> TextAlign.Start
                    }
                    Box(
                        modifier = Modifier
                            .widthIn(min = 96.dp, max = 220.dp)
                            .padding(horizontal = 10.dp, vertical = 8.dp)
                    ) {
                        InlineMarkdownText(
                            text = header,
                            style = MaterialTheme.typography.labelLarge.copy(
                                fontWeight = FontWeight.Bold,
                                color = textColor,
                                textAlign = align
                            ),
                            linkColor = linkColor,
                            codeBg = codeBg,
                            onOpenUrl = onOpenUrl
                        )
                    }
                }
            }
            HorizontalDivider(color = borderColor)

            // Body rows
            table.rows.forEachIndexed { rowIdx, rowCells ->
                Row(
                    modifier = Modifier.background(
                        if (rowIdx % 2 == 1) altRowBg.copy(alpha = 0.6f) else Color.Transparent
                    )
                ) {
                    table.headers.indices.forEach { colIdx ->
                        val cellText = rowCells.getOrElse(colIdx) { "" }
                        val align = when (table.alignments.getOrElse(colIdx) { MarkdownTableAlign.LEFT }) {
                            MarkdownTableAlign.CENTER -> TextAlign.Center
                            MarkdownTableAlign.RIGHT -> TextAlign.End
                            MarkdownTableAlign.LEFT -> TextAlign.Start
                        }
                        Box(
                            modifier = Modifier
                                .widthIn(min = 96.dp, max = 220.dp)
                                .padding(horizontal = 10.dp, vertical = 7.dp)
                        ) {
                            InlineMarkdownText(
                                text = cellText,
                                style = MaterialTheme.typography.bodySmall.copy(
                                    color = textColor,
                                    textAlign = align
                                ),
                                linkColor = linkColor,
                                codeBg = codeBg,
                                onOpenUrl = onOpenUrl
                            )
                        }
                    }
                }
                if (rowIdx < table.rows.lastIndex) {
                    HorizontalDivider(color = borderColor.copy(alpha = 0.45f))
                }
            }
        }
    }
}

@Suppress("DEPRECATION")
@Composable
private fun InlineMarkdownText(
    text: String,
    style: TextStyle,
    linkColor: Color,
    codeBg: Color,
    modifier: Modifier = Modifier,
    onOpenUrl: (String) -> Unit
) {
    val annotated = remember(text, linkColor, codeBg) {
        buildInlineMarkdownAnnotatedString(text, linkColor, codeBg)
    }
    val hasLinks = remember(annotated) {
        annotated.getStringAnnotations("URL", 0, annotated.length).isNotEmpty()
    }

    if (hasLinks) {
        ClickableText(
            text = annotated,
            style = style,
            modifier = modifier,
            onClick = { offset ->
                annotated.getStringAnnotations("URL", offset, offset)
                    .firstOrNull()?.let { ann -> onOpenUrl(ann.item) }
            }
        )
    } else {
        Text(
            text = annotated,
            style = style,
            modifier = modifier
        )
    }
}

fun buildInlineMarkdownAnnotatedString(
    input: String,
    linkColor: Color = Color(0xFF1A73E8),
    codeBg: Color = Color(0x22888888)
): AnnotatedString {
    return buildAnnotatedString {
        var idx = 0
        val len = input.length

        while (idx < len) {
            // Markdown link: [label](url)
            if (input[idx] == '[') {
                val closeBracket = input.indexOf(']', idx + 1)
                if (closeBracket > idx && closeBracket + 1 < len && input[closeBracket + 1] == '(') {
                    val closeParen = input.indexOf(')', closeBracket + 2)
                    if (closeParen > closeBracket + 2) {
                        val label = input.substring(idx + 1, closeBracket)
                        val url = input.substring(closeBracket + 2, closeParen).trim()
                        val start = length
                        append(label)
                        addStyle(
                            SpanStyle(
                                color = linkColor,
                                textDecoration = TextDecoration.Underline,
                                fontWeight = FontWeight.Medium
                            ),
                            start,
                            length
                        )
                        addStringAnnotation("URL", url, start, length)
                        idx = closeParen + 1
                        continue
                    }
                }
            }

            // Bold **...** or __...__
            if (idx + 1 < len && ((input[idx] == '*' && input[idx + 1] == '*') || (input[idx] == '_' && input[idx + 1] == '_'))) {
                val marker = input.substring(idx, idx + 2)
                val end = input.indexOf(marker, idx + 2)
                if (end > idx + 2) {
                    val inner = input.substring(idx + 2, end)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontWeight = FontWeight.Bold), start, length)
                    idx = end + 2
                    continue
                }
            }

            // Strikethrough ~~...~~
            if (idx + 1 < len && input[idx] == '~' && input[idx + 1] == '~') {
                val end = input.indexOf("~~", idx + 2)
                if (end > idx + 2) {
                    val inner = input.substring(idx + 2, end)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(textDecoration = TextDecoration.LineThrough), start, length)
                    idx = end + 2
                    continue
                }
            }

            // Inline code `...`
            if (input[idx] == '`') {
                val end = input.indexOf('`', idx + 1)
                if (end > idx + 1) {
                    val inner = input.substring(idx + 1, end)
                    val start = length
                    append(inner)
                    addStyle(
                        SpanStyle(
                            fontFamily = FontFamily.Monospace,
                            background = codeBg
                        ),
                        start,
                        length
                    )
                    idx = end + 1
                    continue
                }
            }

            // Italic *...* or _..._
            if (input[idx] == '*' || input[idx] == '_') {
                val ch = input[idx]
                val end = input.indexOf(ch, idx + 1)
                if (end > idx + 1 && input.substring(idx + 1, end).none { it == '\n' }) {
                    val inner = input.substring(idx + 1, end)
                    val start = length
                    append(inner)
                    addStyle(SpanStyle(fontStyle = FontStyle.Italic), start, length)
                    idx = end + 1
                    continue
                }
            }

            // Autolink bare http(s)://...
            if (input.startsWith("http://", idx, ignoreCase = true) ||
                input.startsWith("https://", idx, ignoreCase = true)
            ) {
                var end = idx
                while (end < len && !input[end].isWhitespace() && input[end] !in listOf(')', ']', '>', '"', '\'')) {
                    end++
                }
                val url = input.substring(idx, end)
                val start = length
                append(url)
                addStyle(
                    SpanStyle(
                        color = linkColor,
                        textDecoration = TextDecoration.Underline
                    ),
                    start,
                    length
                )
                addStringAnnotation("URL", url, start, length)
                idx = end
                continue
            }

            append(input[idx])
            idx++
        }
    }
}
