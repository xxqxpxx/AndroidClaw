package com.androidclaw.app.ui.chat

import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * Lightweight Markdown renderer for chat messages.
 * Handles: bold, italic, code, code blocks, headers, lists, links, strikethrough.
 * No external dependencies.
 */
@Composable
fun MarkdownText(
    text: String,
    modifier: Modifier = Modifier
) {
    val blocks = remember(text) { parseMarkdownBlocks(text) }

    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        for (block in blocks) {
            when (block) {
                is MdBlock.CodeBlock -> {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surfaceContainerHighest)
                            .padding(8.dp)
                            .horizontalScroll(rememberScrollState())
                    ) {
                        Text(
                            text = block.code,
                            style = MaterialTheme.typography.bodySmall.copy(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            ),
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
                is MdBlock.Heading -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = when (block.level) {
                            1 -> MaterialTheme.typography.titleLarge
                            2 -> MaterialTheme.typography.titleMedium
                            else -> MaterialTheme.typography.titleSmall
                        },
                        fontWeight = FontWeight.Bold
                    )
                }
                is MdBlock.ListItem -> {
                    Row {
                        Text(
                            text = if (block.ordered) "${block.index}. " else "  \u2022  ",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = renderInlineMarkdown(block.text),
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is MdBlock.Paragraph -> {
                    Text(
                        text = renderInlineMarkdown(block.text),
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }
}

private sealed class MdBlock {
    data class CodeBlock(val code: String, val language: String = "") : MdBlock()
    data class Heading(val text: String, val level: Int) : MdBlock()
    data class ListItem(val text: String, val ordered: Boolean, val index: Int) : MdBlock()
    data class Paragraph(val text: String) : MdBlock()
}

private fun parseMarkdownBlocks(text: String): List<MdBlock> {
    val blocks = mutableListOf<MdBlock>()
    val lines = text.lines()
    var i = 0

    while (i < lines.size) {
        val line = lines[i]

        // Code block
        if (line.trimStart().startsWith("```")) {
            val lang = line.trimStart().removePrefix("```").trim()
            val codeLines = mutableListOf<String>()
            i++
            while (i < lines.size && !lines[i].trimStart().startsWith("```")) {
                codeLines.add(lines[i])
                i++
            }
            blocks.add(MdBlock.CodeBlock(codeLines.joinToString("\n"), lang))
            i++ // skip closing ```
            continue
        }

        // Heading
        val headingMatch = Regex("""^(#{1,3})\s+(.+)$""").find(line)
        if (headingMatch != null) {
            blocks.add(MdBlock.Heading(headingMatch.groupValues[2], headingMatch.groupValues[1].length))
            i++
            continue
        }

        // Unordered list
        if (line.trimStart().matches(Regex("""^[-*+]\s+.+"""))) {
            val text = line.trimStart().replaceFirst(Regex("""^[-*+]\s+"""), "")
            blocks.add(MdBlock.ListItem(text, ordered = false, index = 0))
            i++
            continue
        }

        // Ordered list
        val olMatch = Regex("""^\s*(\d+)[.)]\s+(.+)""").find(line)
        if (olMatch != null) {
            blocks.add(MdBlock.ListItem(olMatch.groupValues[2], ordered = true, index = olMatch.groupValues[1].toInt()))
            i++
            continue
        }

        // Empty line - skip
        if (line.isBlank()) {
            i++
            continue
        }

        // Paragraph: accumulate consecutive non-empty lines
        val paraLines = mutableListOf(line)
        i++
        while (i < lines.size && lines[i].isNotBlank() &&
            !lines[i].trimStart().startsWith("```") &&
            !lines[i].trimStart().startsWith("#") &&
            !lines[i].trimStart().matches(Regex("""^[-*+]\s+.+""")) &&
            !lines[i].trimStart().matches(Regex("""^\d+[.)]\s+.+"""))
        ) {
            paraLines.add(lines[i])
            i++
        }
        blocks.add(MdBlock.Paragraph(paraLines.joinToString(" ")))
    }

    return blocks
}

private fun renderInlineMarkdown(text: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < text.length) {
            when {
                // Bold+Italic ***text***
                text.startsWith("***", i) -> {
                    val end = text.indexOf("***", i + 3)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold, fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 3, end))
                        }
                        i = end + 3
                    } else { append(text[i]); i++ }
                }
                // Bold **text**
                text.startsWith("**", i) -> {
                    val end = text.indexOf("**", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // Italic *text* or _text_
                (text[i] == '*' || text[i] == '_') && (i == 0 || text[i - 1] != text[i]) -> {
                    val marker = text[i]
                    val end = text.indexOf(marker, i + 1)
                    if (end > 0 && end > i + 1) {
                        withStyle(SpanStyle(fontStyle = FontStyle.Italic)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // Inline code `text`
                text[i] == '`' -> {
                    val end = text.indexOf('`', i + 1)
                    if (end > 0) {
                        withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp)) {
                            append(text.substring(i + 1, end))
                        }
                        i = end + 1
                    } else { append(text[i]); i++ }
                }
                // Strikethrough ~~text~~
                text.startsWith("~~", i) -> {
                    val end = text.indexOf("~~", i + 2)
                    if (end > 0) {
                        withStyle(SpanStyle(textDecoration = TextDecoration.LineThrough)) {
                            append(text.substring(i + 2, end))
                        }
                        i = end + 2
                    } else { append(text[i]); i++ }
                }
                // Link [text](url) - render just the text
                text[i] == '[' -> {
                    val closeBracket = text.indexOf(']', i + 1)
                    if (closeBracket > 0 && closeBracket + 1 < text.length && text[closeBracket + 1] == '(') {
                        val closeParen = text.indexOf(')', closeBracket + 2)
                        if (closeParen > 0) {
                            val linkText = text.substring(i + 1, closeBracket)
                            withStyle(SpanStyle(
                                color = androidx.compose.ui.graphics.Color(0xFF2196F3),
                                textDecoration = TextDecoration.Underline
                            )) {
                                append(linkText)
                            }
                            i = closeParen + 1
                        } else { append(text[i]); i++ }
                    } else { append(text[i]); i++ }
                }
                else -> { append(text[i]); i++ }
            }
        }
    }
}
