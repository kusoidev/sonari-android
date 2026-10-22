package com.kusoi.sonari.music

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The reveal curve is the one piece of the port where "looks close" is not
 * good enough. These pin the exact behaviour of the Swift original.
 */
class LyricMathTest {

    private val lines = listOf(
        LyricLine(time = 0.0, text = "short line"),
        LyricLine(time = 4.0, text = "a much longer line of lyrics here"),
        LyricLine(time = 8.0, text = "end")
    )

    private fun fraction(index: Int, now: Double): Double {
        // Mirrors PlayerEngine.revealedFraction without needing an ExoPlayer.
        val line = lines[index]
        val totalChars = line.text.length
        if (totalChars == 0) return 0.0
        val lineStart = line.time
        val lineEnd = if (index + 1 < lines.size) lines[index + 1].time else lineStart + 4
        val lineDur = maxOf(0.15, lineEnd - lineStart)
        val rawT = ((now - lineStart + 0.30) / lineDur).coerceIn(0.0, 1.0)
        val eased = if (rawT < 0.5) 2.4 * rawT * rawT else 1 - (-2 * rawT + 2) * (-2 * rawT + 2) / 2.4
        return eased.coerceIn(0.0, 1.0)
    }

    @Test
    fun startsAtZeroBeforeTheLine() {
        assertEquals(0.0, fraction(1, 3.0), 0.001)
    }

    @Test
    fun increasesMonotonicallyThroughTheLine() {
        var previous = -1.0
        var t = 4.0
        while (t <= 8.0) {
            val f = fraction(1, t)
            assertTrue("fraction went backwards at $t", f >= previous - 0.0001)
            previous = f
            t += 0.05
        }
    }

    @Test
    fun reachesFullAtLineEnd() {
        assertEquals(1.0, fraction(1, 8.0), 0.001)
    }

    @Test
    fun parsesLrcWithCentiseconds() {
        val parsed = LyricsService.parseLRC("[00:01.50]first\n[00:03.25]second")
        assertEquals(2, parsed?.size)
        assertEquals(1.5, parsed!![0].time, 0.001)
        assertEquals(3.25, parsed[1].time, 0.001)
    }

    @Test
    fun parsesLrcWithMilliseconds() {
        val parsed = LyricsService.parseLRC("[00:01.250]first")
        assertEquals(1.25, parsed!![0].time, 0.001)
    }

    @Test
    fun ignoresLinesWithoutTimestamps() {
        val parsed = LyricsService.parseLRC("[ar:Artist]\n[00:02.00]only line")
        assertEquals(1, parsed?.size)
        assertEquals("only line", parsed!![0].text)
    }
}
