package com.rimor.minitools

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.SizeTransform
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

/**
 * The first run, as one card on the family's mustard.
 *
 * Mustard is the onboarding colour and it is used at full-screen scale here and nowhere else in
 * the app. The card is a single surface throughout: it changes what it holds and resizes to fit
 * rather than one screen vanishing and another taking its place, because the box is the thing
 * being followed and it should never be the thing that blinks.
 *
 * Two steps, because miniTools asks for exactly two things — one permission, and that you know
 * where the gestures are. The second is not a formality: both zones are invisible, and a gesture
 * nobody has been told about is a gesture nobody uses.
 */
@Composable
fun Welcome(granted: Boolean, onGrant: () -> Unit, onFinished: () -> Unit) {
    val transition = rememberInfiniteTransition(label = "welcome")
    val travel by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "stripes",
    )

    var showingTour by remember { mutableStateOf(false) }
    var leaving by remember { mutableStateOf(false) }

    val journey by animateFloatAsState(
        targetValue = if (leaving) 1f else 0f,
        animationSpec = tween(durationMillis = 620, easing = FastOutSlowInEasing),
        label = "journey",
        finishedListener = { if (it == 1f) onFinished() },
    )

    Box(Modifier.fillMaxSize()) {
        DiagonalStripes(
            travel = travel,
            first = Mont.Mustard,
            second = Color.Black,
            split = journey,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                .padding(horizontal = 18.dp)
                // The card goes first, and faster than the ground it is standing on.
                .alpha(1f - (journey / 0.22f).coerceAtMost(1f))
                .background(Mont.Surface)
                .padding(start = 22.dp, top = 22.dp, end = 18.dp, bottom = 16.dp),
        ) {
            Text("mini", style = Mont.wordmark, color = Color.White, fontSize = 36.sp)
            Text("Tools", style = Mont.wordmarkBold, color = Color.White, fontSize = 36.sp)

            Spacer(Modifier.height(14.dp))

            AnimatedContent(
                targetState = showingTour,
                transitionSpec = {
                    (fadeIn(tween(240, delayMillis = 120)) togetherWith fadeOut(tween(140)))
                        .using(SizeTransform(clip = false) { _, _ -> tween(360, easing = FastOutSlowInEasing) })
                },
                label = "welcomeStep",
            ) { tour ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (tour) {
                        TourStep(onOkay = { leaving = true })
                    } else {
                        PermissionStep(
                            granted = granted,
                            onGrant = onGrant,
                            onDone = { showingTour = true },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ColumnScope.PermissionStep(granted: Boolean, onGrant: () -> Unit, onDone: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(enabled = !granted) { onGrant() },
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Label("ACCESSIBILITY", if (granted) 1f else .55f, 12)
            Detail("Holds two invisible zones over the cover screen. Reads nothing.")
        }
        Label(if (granted) "GRANTED" else "ALLOW", if (granted) .55f else 1f, 11)
    }

    // Text alone, like every other commitment in the family. Dim until there is nothing left to
    // grant, so it reads as the end of the list rather than a way past it.
    Text(
        "ALL DONE",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = granted, onClick = onDone)
            .padding(vertical = 6.dp),
        color = Color.White.copy(alpha = if (granted) 1f else .30f),
        style = Mont.row,
    )
}

@Composable
private fun ColumnScope.TourStep(onOkay: () -> Unit) {
    Label("THE GESTURES", .55f, 11)
    TourLine("CORNER SWIPE", "Up, from the bottom-left")
    TourLine("THE FLASH", "Press and hold the dot beside the lenses")
    Spacer(Modifier.height(4.dp))
    Detail("Each opens a tool, and which does what is yours to change later.")
    if (BuildConfig.HAS_ROTATION) {
        Spacer(Modifier.height(6.dp))
        Label("ONE THING TO KNOW", .55f, 11)
        Detail("Auto-rotate breaks the Recents interface — Samsung saves the panel's insets while it is sideways and keeps using them. Opening Recents on the inner screen fixes it, and miniTools has a Repair row that does exactly that. A reboot also works.")
    }
    Spacer(Modifier.height(4.dp))
    Text(
        "OKAY",
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onOkay)
            .padding(vertical = 6.dp),
        color = Color.White,
        style = Mont.row,
    )
}

@Composable
private fun TourLine(gesture: String, meaning: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(
            gesture,
            modifier = Modifier.weight(.44f),
            color = Color.White,
            style = Mont.term,
        )
        Text(
            meaning,
            modifier = Modifier.weight(.56f),
            color = Color.White.copy(alpha = .62f),
            style = Mont.meaning,
        )
    }
}

@Composable
private fun Label(text: String, alpha: Float, size: Int) {
    Text(
        text,
        color = Color.White.copy(alpha = alpha),
        style = Mont.row,
        fontSize = size.sp,
    )
}

@Composable
private fun Detail(text: String) {
    Text(
        text,
        color = Color.White.copy(alpha = .42f),
        style = Mont.meaning,
        fontSize = 9.sp,
    )
}
