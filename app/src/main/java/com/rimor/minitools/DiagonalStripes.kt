package com.rimor.minitools

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp

/**
 * Interleaved diagonal bands, scrolling. Carried over from MiniMate unchanged, because the
 * stripes are the family's onboarding ground and they should be the same stripes.
 *
 * Everything is drawn in a frame turned to the bands' own slope, so within it they are plain
 * horizontal rows and the whole thing is easy to reason about. [split] cuts the sheet down the
 * middle and pulls the two halves apart along the bands' own axis; each half is exactly as wide
 * as it travels, so pulling it clears the side it was covering. Drawn on nothing rather than over
 * a fill, so what opens up between the halves is whatever is behind it.
 */
@Composable
fun DiagonalStripes(
    travel: Float,
    first: Color,
    second: Color,
    split: Float = 0f,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier) {
        val spacing = 34.dp.toPx()
        val band = spacing * 0.5f
        val drift = travel * spacing
        val middle = size.width / 2f
        val span = size.width + size.height
        val pull = split * span

        fun half(from: Float, shift: Float) {
            translate(left = shift) {
                var y = -span
                while (y < size.height + span) {
                    drawRect(first, Offset(from, y + drift), Size(span, band))
                    drawRect(second, Offset(from, y + drift + band), Size(span, band))
                    y += spacing
                }
            }
        }

        rotate(degrees = 26.565f) {
            clipRect(-span, -span, middle, size.height + span) { half(middle - span, -pull) }
            clipRect(middle, -span, size.width + span, size.height + span) { half(middle, pull) }
        }
    }
}
