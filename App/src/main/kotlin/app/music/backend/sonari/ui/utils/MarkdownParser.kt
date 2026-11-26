package app.music.backend.sonari.ui.utils

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.withLink
import androidx.compose.ui.text.withStyle
import app.music.backend.sonari.sonari.updater.ChangelogSection

/**
 * Parses markdown formatted text into an [AnnotatedString] supporting:
 * - **bold** text
 * - *italic* text
 * - `inline code`
 * - @mentions (styled with bold weight and optional accent color)
 * - [links](url) (interactive clickable URLs via [LinkAnnotation.Url])
 *
 * @param text The markdown string to parse.
 * @param primaryColor The accent color used for mentions and hyperlinks.
 * @return An [AnnotatedString] ready for rendering in Compose [androidx.compose.material3.Text].
 */
fun parseSimpleMarkdown(text: String, primaryColor: Color = Color.Unspecified): AnnotatedString {
  // Strip leading bullet marker if the item itself starts with one and normalize nested markdown
  // links (e.g. GitHub release [[user](url)](url))
  val cleanText =
    text
      .replace(Regex("^(?:[-*+•]|\\d+\\.)\\s+"), "")
      .replace(Regex("\\[\\[([^\\]]+)\\]\\(([^)]+)\\)\\](?:\\([^)]+\\))?"), "[$1]($2)")
  val pattern =
    Regex(
      "(\\*\\*(.*?)\\*\\*)|" + // 1, 2: **bold**
        "(\\*([^*]+)\\*)|" + // 3, 4: *italic*
        "(`([^`]+)`)|" + // 5, 6: `code`
        "(@[a-zA-Z0-9_-]+)|" + // 7: @username
        "(\\[([^\\]]+)\\]\\(([^)]+)\\))" // 8, 9, 10: [text](url)
    )

  return buildAnnotatedString {
    var currentIndex = 0
    val matches = pattern.findAll(cleanText)

    for (match in matches) {
      if (match.range.first > currentIndex) {
        append(cleanText.substring(currentIndex, match.range.first))
      }

      when {
        match.groups[1] != null -> { // **bold**
          withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(match.groups[2]!!.value) }
        }
        match.groups[3] != null -> { // *italic*
          withStyle(SpanStyle(fontStyle = FontStyle.Italic)) { append(match.groups[4]!!.value) }
        }
        match.groups[5] != null -> { // `code`
          withStyle(SpanStyle(fontFamily = FontFamily.Monospace, fontWeight = FontWeight.Medium)) {
            append(match.groups[6]!!.value)
          }
        }
        match.groups[7] != null -> { // @username
          withStyle(
            SpanStyle(
              fontWeight = FontWeight.SemiBold,
              color = if (primaryColor != Color.Unspecified) primaryColor else Color.Unspecified
            )
          ) {
            append(match.groups[7]!!.value)
          }
        }
        match.groups[8] != null -> { // [text](url)
          val linkText = match.groups[9]!!.value
          val linkUrl = match.groups[10]!!.value
          val linkStyle =
            TextLinkStyles(
              style =
                SpanStyle(
                  color =
                    if (primaryColor != Color.Unspecified) primaryColor else Color.Unspecified,
                  textDecoration = TextDecoration.Underline
                )
            )
          withLink(LinkAnnotation.Url(linkUrl, styles = linkStyle)) { append(linkText) }
        }
      }
      currentIndex = match.range.last + 1
    }

    if (currentIndex < cleanText.length) {
      append(cleanText.substring(currentIndex))
    }
  }
}

/**
 * Whether this line is a row of a markdown table: the header row, the `|---|---|` rule, or a body
 * row. Release notes are read one line at a time and drawn as a plain list of items, which cannot
 * draw a table — so GitHub's "which file do I want?" table came out as raw pipes in the update
 * sheet.
 */
private fun String.isTableRow(): Boolean {
  val text = trim()
  if (text.isEmpty() || !text.contains('|')) return false
  if (text.startsWith("|") || text.endsWith("|")) return true
  return Regex("\\|\\s*:?-{2,}:?\\s*\\|").containsMatchIn(text)
}

/**
 * Drops markdown tables from release notes that already arrive broken into sections (a release's
 * `changelog.json` asset), together with any section left with no items, so the table's heading
 * leaves with its rows. [parseMarkdownToSections] does the same for raw markdown.
 */
fun List<ChangelogSection>.withoutTables(): List<ChangelogSection> =
  mapNotNull { section ->
    val items = section.items.filterNot { it.isTableRow() }
    if (items.isEmpty()) null else section.copy(items = items)
  }

/**
 * Parses markdown release notes (such as those from GitHub Releases) into an optional introductory
 * description and a list of structured [ChangelogSection]s.
 *
 * Headings (e.g. `## New Features`, `### Fixes`) become section titles, while list items (`- `, `*
 * `, `+ `, `• `, `1. `) are grouped under their respective sections.
 *
 * @param markdown The raw release notes markdown text.
 * @return A pair containing the optional description (preceding headings) and the list of sections.
 */
fun parseMarkdownToSections(markdown: String): Pair<String?, List<ChangelogSection>> {
  if (markdown.isBlank()) return Pair(null, emptyList())

  // Table rows are dropped as they are met, so a section that was nothing but a table (its heading,
  // say) ends up with no items and is dropped by flushSection() below.
  val lines = markdown.lines().filterNot { it.isTableRow() }
  val sections = mutableListOf<ChangelogSection>()
  val descriptionLines = mutableListOf<String>()

  var currentSectionTitle: String? = null
  val currentItems = mutableListOf<String>()
  var hadBlankLine = false

  fun flushSection() {
    val title = currentSectionTitle
    if (title != null && currentItems.isNotEmpty()) {
      sections.add(ChangelogSection(title, currentItems.toList()))
      currentItems.clear()
    }
  }

  for (rawLine in lines) {
    val line = rawLine.trim()
    if (line.isEmpty()) {
      hadBlankLine = true
      if (
        currentSectionTitle == null &&
          descriptionLines.isNotEmpty() &&
          descriptionLines.last().isNotEmpty()
      ) {
        descriptionLines.add("")
      }
      continue
    }

    // Ignore horizontal rules: --- or ***
    if (line.matches(Regex("^-{3,}$|^\\*{3,}$|^_{3,}$"))) {
      hadBlankLine = true
      continue
    }

    // Ignore generic release footer links like "**Full Changelog**: https://..."
    if (line.startsWith("**Full Changelog**", ignoreCase = true)) {
      hadBlankLine = true
      continue
    }

    // Headings: 1 to 6 '#' characters followed by whitespace (avoids matching issue references like
    // #123)
    val headingMatch = Regex("^#{1,6}\\s+(.+?)(?:\\s+#+)?\\s*$").matchEntire(line)
    if (headingMatch != null) {
      flushSection()
      val cleanTitle = headingMatch.groupValues[1].trim()
      currentSectionTitle = cleanTitle
      hadBlankLine = false
      continue
    }

    // Bullet list items: - , * , + , • or numbered list (e.g., "1. ")
    val bulletMatch = Regex("^(?:[-*+•]|\\d+\\.)\\s+(.*)$").find(line)
    if (bulletMatch != null) {
      val itemContent = bulletMatch.groupValues[1].trim()
      if (currentSectionTitle == null) {
        currentSectionTitle = ""
      }
      currentItems.add(itemContent)
      hadBlankLine = false
      continue
    }

    // Ordinary non-bullet text
    val isIndented = rawLine.startsWith("  ") || rawLine.startsWith("\t")
    if (currentSectionTitle == null) {
      descriptionLines.add(line)
    } else if (currentItems.isNotEmpty() && (!hadBlankLine || isIndented)) {
      // Continuation of the previous bullet item across multiple lines
      val lastIdx = currentItems.size - 1
      currentItems[lastIdx] = "${currentItems[lastIdx]} $line"
    } else {
      currentItems.add(line)
    }
    hadBlankLine = false
  }

  flushSection()

  val description = descriptionLines.joinToString("\n").trim().takeIf { it.isNotEmpty() }
  return Pair(description, sections)
}
