package com.rimor.minitools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect

/**
 * The toolbox.
 *
 * One screen, one list, no header naming the panel — it opened from the widget you tapped, so
 * the first line is already an option. Recents is the only tool that does anything in V1; the
 * ones below it are named and sitting at 35% because a toolbox that hides what it will be is
 * harder to read than one that admits it.
 */
class CoverActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MiniToolsTheme {
                val prefs = remember { Prefs(this) }
                // Re-read on every resume: this screen is most often come back to from the
                // Accessibility settings the grant row just opened.
                var granted by remember { mutableStateOf(isServiceEnabled(this)) }
                LifecycleResumeEffect(Unit) {
                    granted = isServiceEnabled(this@CoverActivity)
                    onPauseOrDispose { }
                }
                var onboarded by remember { mutableStateOf(prefs.onboarded) }

                if (onboarded) {
                    Toolbox(granted, ::openAccessibilitySettings, ::finish)
                } else {
                    Welcome(
                        granted = granted,
                        onGrant = ::openAccessibilitySettings,
                        onFinished = { prefs.onboarded = true; onboarded = true },
                    )
                }
            }
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}

@Composable
private fun Toolbox(granted: Boolean, onGrant: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var corner by remember { mutableStateOf(prefs.cornerSwipe) }
    var flash by remember { mutableStateOf(prefs.flashPress) }
    var haptics by remember { mutableStateOf(prefs.haptics) }

    val live = granted && (corner || flash)

    // The same ground the welcome stands on. The toolbox is where you come back to, so it should
    // look like the place you arrived at rather than a different app that inherited the name.
    val transition = rememberInfiniteTransition(label = "toolbox")
    val travel by transition.animateFloat(
        0f, 1f, infiniteRepeatable(tween(5200, easing = LinearEasing)), label = "stripes",
    )

    Box(Modifier.fillMaxSize()) {
        DiagonalStripes(
            travel = travel,
            first = Mont.Mustard,
            second = Color.Black,
            modifier = Modifier.fillMaxSize(),
        )

        Column(
            Modifier
                .fillMaxWidth()
                .align(Alignment.Center)
                // The camera cutout and the navigation bar are real; the status inset is not
                // wanted, because the card is centred rather than hung from the top.
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 18.dp)
                .background(Mont.Surface)
                // 22 left, 18 right: text hangs off a generous left margin and nothing needs the
                // right one.
                .padding(start = 22.dp, top = 22.dp, end = 18.dp, bottom = 16.dp),
        ) {
            MontWordmark(light = "mini", heavy = "Tools")
            MontGap(14)

            // Capped, and it scrolls inside the cap. Without a ceiling the list decides whether
            // the card still fits on the panel, and the panel is 399dp tall.
            Column(
                Modifier
                    .heightIn(max = 186.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                MontRow(label = "Recents", value = if (live) "on" else "off", enabled = granted)
                MontGap()

                MontToggleRow(label = "Corner swipe", on = corner, enabled = granted) {
                    corner = it; prefs.cornerSwipe = it
                }
                MontToggleRow(label = "Flash press", on = flash, enabled = granted) {
                    flash = it; prefs.flashPress = it
                }
                MontToggleRow(label = "Haptics", on = haptics, enabled = granted) {
                    haptics = it; prefs.haptics = it
                }
                MontGap()

                MontRow(label = "Notifications", value = "—", enabled = false)
                MontRow(label = "Quick settings", value = "—", enabled = false)
                MontGap()

                if (granted) {
                    MontRow(label = "Accessibility", value = "granted", dim = true)
                } else {
                    MontRow(label = "Accessibility — grant", onClick = onGrant)
                    MontDetail("miniTools needs it to hold a window over the cover screen. It reads nothing.")
                }
                MontRow(label = "Close", dim = true, onClick = onClose)
            }
        }
    }
}

/**
 * Whether the service is switched on, read from the setting rather than from the service.
 *
 * [ToolsService.isRunning] only knows about a process that is already alive, and this screen is
 * most often opened by somebody who has just come back from turning the service on.
 */
private fun isServiceEnabled(context: Context): Boolean {
    val expected = ComponentName(context, ToolsService::class.java).flattenToString()
    val enabled = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
    ) ?: return false
    val splitter = TextUtils.SimpleStringSplitter(':')
    splitter.setString(enabled)
    return splitter.any { it.equals(expected, ignoreCase = true) }
}
