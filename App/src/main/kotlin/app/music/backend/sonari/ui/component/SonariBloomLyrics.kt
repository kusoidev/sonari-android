package app.music.backend.sonari.ui.component

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.music.backend.sonari.lyrics.LyricsEntry
import app.music.backend.sonari.ui.screens.settings.LyricsPosition
import kotlin.math.PI
import kotlin.math.sin

/** How far an unsung letter sits below its place, in dp. */
private const val LetterWaitDropDp = 3f

/** How far the letter under the sweep lifts as it is sung, in dp. */
private const val LetterLiftDp = 2f

/** How much the letter under the sweep grows. */
private const val LetterSwell = 0.09f

/** How bright the soft light behind a word is while its letters are sweeping. */
private const val WordBloomAlpha = 0.22f

private class TimedWord(val text: String, val start: Long, val end: Long)

private class TimedLetter(val char: String, val start: Long, val end: Long)

/**
 * The word-by-word style, taken one letter further: the whole line is drawn as single letters,
 * each one waiting a little below its place and rising on its own spring as the sweep reaches it,
 * with the letter under the sweep swelling and lighting up. The word being sung gets a soft light
 * behind it.
 *
 * This is the Compose-only form of the model's per-letter renderer: the same motion, without the
 * GPU shader that paints the trailing bloom on top of it.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun sonariBloomLine(
  entry: LyricsEntry,
  nextEntryTime: Long?,
  effectivePlaybackPosition: Long,
  isSynced: Boolean,
  isActive: Boolean,
  distanceFromCurrent: Int,
  lyricsTextPosition: LyricsPosition,
  textColor: Color,
  showRomanized: Boolean,
  showTranslated: Boolean,
  textSize: Float,
  lineSpacing: Float,
  onClick: () -> Unit,
  onLongClick: () -> Unit,
  isSelected: Boolean,
  isSelectionModeActive: Boolean,
  isAutoScrollActive: Boolean,
  expressiveAccent: Color,
  modifier: Modifier = Modifier
) {
  val duration =
    remember(entry.time, nextEntryTime) {
      if (nextEntryTime != null) nextEntryTime - entry.time else 4000L
    }

  val activeDuration = remember(duration) { (duration * 0.95).toLong().coerceAtLeast(300L) }

  val words =
    remember(entry.text, entry.words, activeDuration) {
      val isHindiText = app.music.backend.sonari.lyrics.LyricsUtils.isHindi(entry.text)
      val spans =
        if (!isHindiText && entry.words?.isNotEmpty() == true) {
          entry.words!!.map { word ->
            val wordStart = ((word.startTime * 1000).toLong() - entry.time).coerceAtLeast(0L)
            val wordEnd =
              ((word.endTime * 1000).toLong() - entry.time).coerceAtLeast(wordStart + 50L)
            TimedWord(word.text, wordStart, wordEnd)
          }
        } else {
          val parts = entry.text.split(" ").filter { it.isNotEmpty() }
          if (parts.isEmpty()) {
            listOf(TimedWord(entry.text, 0L, activeDuration))
          } else {
            val totalChars = entry.text.length
            var accumulatedTime = 0L
            parts.mapIndexed { index, word ->
              val includeSpace = index < parts.size - 1
              val charCount = word.length + if (includeSpace) 1 else 0
              val wordStart = accumulatedTime
              val wordDur =
                if (totalChars > 0) (activeDuration * charCount.toFloat() / totalChars).toLong()
                else activeDuration
              accumulatedTime += wordDur
              TimedWord(word, wordStart, wordStart + wordDur)
            }
          }
        }
      spans
    }

  val targetAlpha =
    when {
      !isSynced || (isSelectionModeActive && isSelected) -> 1f
      isActive -> 1f
      distanceFromCurrent == 1 -> 0.65f
      distanceFromCurrent == 2 -> 0.45f
      else -> 0.35f
    }

  val animatedAlpha by
    animateFloatAsState(
      targetValue = targetAlpha,
      animationSpec = tween(durationMillis = 300),
      label = "bloomLineAlpha"
    )

  val targetBlur =
    when {
      !isSynced || isActive || isSelectionModeActive -> 0f
      distanceFromCurrent <= 2 -> 0f
      distanceFromCurrent == 3 -> 2f
      distanceFromCurrent == 4 -> 4f
      else -> 6f
    }

  val animatedBlur by
    animateFloatAsState(
      targetValue = targetBlur,
      animationSpec = tween(durationMillis = 1000),
      label = "bloomLineBlur"
    )

  val lineRise by
    animateFloatAsState(
      targetValue = if (isActive) 1f else 0f,
      animationSpec = spring(dampingRatio = 0.78f, stiffness = Spring.StiffnessLow),
      label = "bloomLineRise"
    )

  val scale by
    animateFloatAsState(
      targetValue = if (isActive) 1.05f else 1f,
      animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
      label = "bloomLineScale"
    )

  val waitDropPx = with(LocalDensity.current) { LetterWaitDropDp.dp.toPx() }
  val liftPx = with(LocalDensity.current) { LetterLiftDp.dp.toPx() }

  val itemModifier =
    modifier
      .fillMaxWidth()
      .graphicsLayer {
        this.alpha = animatedAlpha
        this.scaleX = scale
        this.scaleY = scale
        this.translationY = (1f - lineRise) * waitDropPx
      }
      .blur(animatedBlur.dp)
      .clip(RoundedCornerShape(16.dp))
      .combinedClickable(enabled = true, onClick = onClick, onLongClick = onLongClick)
      .background(
        if (isSelected && isSelectionModeActive)
          MaterialTheme.colorScheme.primary.copy(alpha = 0.2f)
        else Color.Transparent
      )
      .padding(horizontal = 24.dp, vertical = (8 * lineSpacing).dp)

  val agentAlignment =
    when {
      entry.isBackground -> Alignment.CenterHorizontally
      entry.agent == "v1" -> Alignment.Start
      entry.agent == "v2" -> Alignment.End
      entry.agent == "v1000" -> Alignment.CenterHorizontally
      else ->
        when (lyricsTextPosition) {
          LyricsPosition.LEFT -> Alignment.Start
          LyricsPosition.CENTER -> Alignment.CenterHorizontally
          LyricsPosition.RIGHT -> Alignment.End
        }
    }

  val agentTextAlign =
    when {
      entry.isBackground -> TextAlign.Center
      entry.agent == "v1" -> TextAlign.Left
      entry.agent == "v2" -> TextAlign.Right
      entry.agent == "v1000" -> TextAlign.Center
      else ->
        when (lyricsTextPosition) {
          LyricsPosition.LEFT -> TextAlign.Left
          LyricsPosition.CENTER -> TextAlign.Center
          LyricsPosition.RIGHT -> TextAlign.Right
        }
    }

  val letterHeight = (textSize * lineSpacing.coerceAtMost(1.3f)).sp

  Column(modifier = itemModifier, horizontalAlignment = agentAlignment) {
    FlowRow(
      modifier = Modifier.fillMaxWidth(),
      horizontalArrangement =
        when (agentTextAlign) {
          TextAlign.Center -> Arrangement.Center
          TextAlign.Right -> Arrangement.End
          else -> Arrangement.Start
        },
      verticalArrangement =
        Arrangement.spacedBy(
          with(LocalDensity.current) {
            (textSize * (lineSpacing.coerceAtMost(1.3f) - 1f)).sp.toDp()
          }
        )
    ) {
      words.forEachIndexed { wordIndex, word ->
        val letters =
          remember(word) {
            val span = (word.end - word.start).coerceAtLeast(1L)
            word.text.mapIndexed { index, char ->
              val from = (span * index.toFloat() / word.text.length).toLong()
              val to = (span * (index + 1).toFloat() / word.text.length).toLong()
              TimedLetter(char.toString(), word.start + from, word.start + to)
            }
          }

        val lineRelTime = (effectivePlaybackPosition - entry.time).coerceAtLeast(0L)

        // the word's own head, so its light can follow whatever letter is being sung inside it
        val wordSwell =
          if (lineRelTime in word.start..word.end && word.end > word.start) {
            sin(((lineRelTime - word.start).toFloat() / (word.end - word.start)) * PI.toFloat())
          } else 0f

        Row(
          modifier =
            Modifier.drawBehind {
              if (wordSwell > 0.01f) {
                drawRect(
                  brush =
                    Brush.radialGradient(
                      colors =
                        listOf(
                          textColor.copy(alpha = WordBloomAlpha * wordSwell),
                          Color.Transparent
                        ),
                      center = center,
                      radius = size.maxDimension * 0.75f
                    )
                )
              }
            }
        ) {
          letters.forEach { letter ->
            val letterSpan = (letter.end - letter.start).coerceAtLeast(1L)
            val progress =
              ((lineRelTime - letter.start).toFloat() / letterSpan).coerceIn(0f, 1f)

            // Each letter waits below its place and rises on its own spring the moment the sweep
            // reaches it, so the rise runs through the word instead of the word arriving at once.
            val rise by
              animateFloatAsState(
                targetValue = if (lineRelTime >= letter.start) 1f else 0f,
                animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                label = "letterRise"
              )

            val swell = if (progress > 0f && progress < 1f) sin(progress * PI.toFloat()) else 0f

            Text(
              text = letter.char,
              fontSize = textSize.sp,
              modifier =
                Modifier.graphicsLayer {
                  translationY = (1f - rise) * waitDropPx - swell * liftPx
                  scaleX = 1f + swell * LetterSwell
                  scaleY = 1f + swell * LetterSwell
                },
              style =
                TextStyle(
                  color = textColor.copy(alpha = 0.42f + 0.58f * rise),
                  fontWeight = FontWeight.Bold,
                  lineHeight = letterHeight,
                  textAlign = agentTextAlign,
                  shadow =
                    Shadow(
                      color = textColor.copy(alpha = 0.55f * swell),
                      offset = Offset.Zero,
                      blurRadius = 14f * swell
                    )
                )
            )
          }
        }

        if (wordIndex != words.lastIndex) {
          Text(text = " ", fontSize = textSize.sp, lineHeight = letterHeight)
        }
      }
    }

    if (showRomanized) {
      val romanizedText by entry.romanizedTextFlow.collectAsState()
      romanizedText?.let { romanized ->
        Text(
          text = romanized,
          fontSize = (textSize * 0.65f).sp,
          color = textColor.copy(alpha = 0.6f),
          textAlign = agentTextAlign,
          fontWeight = FontWeight.SemiBold,
          modifier = Modifier.padding(top = 2.dp).fillMaxWidth(),
          lineHeight = (textSize * 0.65f * lineSpacing.coerceAtMost(1.3f)).sp
        )
      }
    }

    if (showTranslated) {
      val translatedText by entry.translatedTextFlow.collectAsState()
      translatedText?.let { translated ->
        Text(
          text = translated,
          fontSize = (textSize * 0.7f).sp,
          color = textColor.copy(alpha = 0.8f),
          textAlign = agentTextAlign,
          fontWeight = FontWeight.Medium,
          modifier = Modifier.padding(top = 4.dp).fillMaxWidth(),
          lineHeight = (textSize * 0.7f * lineSpacing.coerceAtMost(1.3f)).sp
        )
      }
    }
  }
}
