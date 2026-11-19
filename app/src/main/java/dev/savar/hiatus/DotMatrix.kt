package dev.savar.hiatus

import android.graphics.Canvas
import android.graphics.Paint

/**
 * Nothing-style 5×7 dot-matrix type. Draws into a plain android Canvas so the widget bitmap and the
 * Compose UI share one implementation. Digits are drawn in [lit]; letters (h, m, d units) in [unit];
 * every unlit cell of a glyph gets a faint [unlit] dot, which gives the matrix look.
 */
object DotMatrix {
    private const val ROWS = 7
    private const val GAP = 1
    private const val SPACE = 2

    private val glyphs: Map<Char, Array<String>> = mapOf(
        '0' to arrayOf(".###.", "#...#", "#..##", "#.#.#", "##..#", "#...#", ".###."),
        '1' to arrayOf("..#..", ".##..", "..#..", "..#..", "..#..", "..#..", ".###."),
        '2' to arrayOf(".###.", "#...#", "....#", "...#.", "..#..", ".#...", "#####"),
        '3' to arrayOf("#####", "...#.", "..#..", "...#.", "....#", "#...#", ".###."),
        '4' to arrayOf("...#.", "..##.", ".#.#.", "#..#.", "#####", "...#.", "...#."),
        '5' to arrayOf("#####", "#....", "####.", "....#", "....#", "#...#", ".###."),
        '6' to arrayOf("..##.", ".#...", "#....", "####.", "#...#", "#...#", ".###."),
        '7' to arrayOf("#####", "....#", "...#.", "..#..", ".#...", ".#...", ".#..."),
        '8' to arrayOf(".###.", "#...#", "#...#", ".###.", "#...#", "#...#", ".###."),
        '9' to arrayOf(".###.", "#...#", "#...#", ".####", "....#", "...#.", ".##.."),
        'h' to arrayOf("#....", "#....", "#.##.", "##..#", "#...#", "#...#", "#...#"),
        'm' to arrayOf(".....", ".....", "##.#.", "#.#.#", "#.#.#", "#.#.#", "#.#.#"),
        'd' to arrayOf("....#", "....#", ".##.#", "#..##", "#...#", "#...#", ".####"),
        'A' to arrayOf(".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"),
        'W' to arrayOf("#...#", "#...#", "#...#", "#.#.#", "#.#.#", "##.##", "#...#"),
        'Y' to arrayOf("#...#", "#...#", ".#.#.", "..#..", "..#..", "..#..", "..#.."),
        '-' to arrayOf(".....", ".....", ".....", ".###.", ".....", ".....", "....."),
    )

    /** Width of one line in dot columns. */
    fun cols(text: String): Int {
        var c = 0
        text.forEachIndexed { i, ch ->
            if (i > 0) c += GAP
            c += if (ch == ' ') SPACE else 5
        }
        return c
    }

    class Fit(val lines: List<String>, val pitch: Float, val width: Float, val height: Float)

    /**
     * Largest layout of [text] inside w×h: one line, or (when [wrap] and it's bigger) split at the space into
     * two lines. [maxPitch] caps the dot size.
     */
    fun fit(text: String, w: Float, h: Float, wrap: Boolean, maxPitch: Float = Float.MAX_VALUE): Fit {
        fun make(lines: List<String>): Fit {
            val c = lines.maxOf { cols(it) }.coerceAtLeast(1)
            val r = lines.size * ROWS + (lines.size - 1) * 2
            val p = minOf(w / c, h / r, maxPitch)
            return Fit(lines, p, c * p, r * p)
        }
        val one = make(listOf(text))
        if (!wrap || ' ' !in text) return one
        val two = make(listOf(text.substringBefore(' '), text.substringAfter(' ')))
        return if (two.pitch > one.pitch * 1.15f) two else one
    }

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)

    fun draw(c: Canvas, f: Fit, x: Float, y: Float, lit: Int, unit: Int, unlit: Int) {
        val p = f.pitch
        val r = p * 0.4f
        var ly = y
        for (line in f.lines) {
            var gx = x
            line.forEachIndexed { i, ch ->
                if (i > 0) gx += GAP * p
                if (ch == ' ') { gx += SPACE * p; return@forEachIndexed }
                val g = glyphs[ch]
                if (g != null) {
                    val on = if (ch.isDigit()) lit else unit
                    for (row in 0 until ROWS) for (col in 0 until 5) {
                        paint.color = if (g[row][col] == '#') on else unlit
                        if (paint.alpha == 0) continue
                        c.drawCircle(gx + col * p + p / 2, ly + row * p + p / 2, r, paint)
                    }
                }
                gx += 5 * p
            }
            ly += (ROWS + 2) * p
        }
    }
}
