package com.rimor.minitools

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/**
 * The language, as the four things this app actually needs from it.
 *
 * A row, a detail line under one, a toggle and a wordmark. No cards, no dividers, no icons and
 * no radius anywhere — a surface is a black rectangle and selected is simply the bright one.
 */

/** A word, full width, tappable, with its current setting hanging off the right. */
@Composable
fun MontRow(
    label: String,
    value: String? = null,
    enabled: Boolean = true,
    dim: Boolean = false,
    onClick: (() -> Unit)? = null,
) {
    val colour = when {
        !enabled -> Mont.Disabled
        dim -> Mont.Dim
        else -> Mont.Selected
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = Mont.RowPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = label.uppercase(), style = Mont.row, color = colour)
        if (value != null) {
            Text(
                text = value.uppercase(),
                style = Mont.caption,
                color = if (enabled) Mont.Dim else Mont.Disabled,
                modifier = Modifier
                    .weight(1f)
                    .padding(start = 10.dp),
                textAlign = androidx.compose.ui.text.style.TextAlign.End,
            )
        }
    }
}

/** An explanatory line under a row. Mont has no tooltips, so this is where the tooltip went. */
@Composable
fun MontDetail(text: String) {
    Text(
        text = text,
        style = Mont.explanatory,
        color = Mont.Explanatory,
        modifier = Modifier.padding(bottom = 5.dp),
    )
}

/**
 * A row with the toggle at its right — 56 x 18, the slider stopped at two positions, with the
 * state written in the half the white block has left. The word names what the control *is*,
 * never what pressing it would do.
 */
@Composable
fun MontToggleRow(
    label: String,
    on: Boolean,
    enabled: Boolean = true,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onToggle(!on) }
            .padding(vertical = Mont.RowPadding.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label.uppercase(),
            style = Mont.row,
            color = if (!enabled) Mont.Disabled else if (on) Mont.Selected else Mont.Dim,
            modifier = Modifier.weight(1f),
        )
        MontToggle(on = on, enabled = enabled)
    }
}

@Composable
fun MontToggle(on: Boolean, enabled: Boolean = true) {
    Box(
        modifier = Modifier
            .width(56.dp)
            .height(18.dp)
            .background(Mont.Track),
    ) {
        // The white block fills one half...
        Box(
            modifier = Modifier
                .width(28.dp)
                .height(18.dp)
                .align(if (on) Alignment.CenterStart else Alignment.CenterEnd)
                .background(if (enabled) Mont.Selected else Mont.Disabled),
        )
        // ...and the state is written in the half it has left, never on top of it. The word
        // names what the control currently is, not what pressing it would do.
        Text(
            text = if (on) "ON" else "OFF",
            style = Mont.toggle,
            color = if (enabled) Mont.Primary else Mont.Disabled,
            modifier = Modifier
                .align(if (on) Alignment.CenterEnd else Alignment.CenterStart)
                .width(28.dp),
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
        )
    }
}

/** Thin over Black at one size. The contrast is the logo. */
@Composable
fun MontWordmark(light: String, heavy: String) {
    Row {
        Text(text = light, style = Mont.wordmark, color = Mont.Selected, fontWeight = FontWeight.Thin)
        Text(text = heavy, style = Mont.wordmarkBold, color = Mont.Selected, fontWeight = FontWeight.Black)
    }
}

/** Vertical air between rows. 9 on a cover screen. */
@Composable
fun MontGap(height: Int = 9) {
    Column(modifier = Modifier.height(height.dp)) {}
}
