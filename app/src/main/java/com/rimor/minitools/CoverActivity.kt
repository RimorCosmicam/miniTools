package com.rimor.minitools

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            // Only the bottom and the sides. The cutout is the camera island and the
            // navigation bar is real, but the status inset at the top is not wanted here — Mont
            // supplies its own 44, and adding one to the other pushes the first row into the
            // middle of the panel.
            .windowInsetsPadding(
                WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
            )
            .verticalScroll(rememberScrollState())
            // 22 left, 14 right: text hangs off a generous left margin, and nothing needs the
            // right one. 44 at the top keeps the first row clear of the lip of a case.
            .padding(start = 22.dp, end = 14.dp, top = 44.dp, bottom = 28.dp),
    ) {
        MontWordmark(light = "mini", heavy = "Tools")
        MontGap(22)

        MontRow(label = "Recents", value = if (live) "on" else "off", enabled = granted)
        MontDetail("One UI's own task switcher, put on the cover screen at its native density.")
        MontGap()

        MontToggleRow(label = "Corner swipe", on = corner, enabled = granted) {
            corner = it; prefs.cornerSwipe = it
        }
        MontDetail("Swipe up from the bottom-left. The centre stays Samsung Pay's.")

        MontToggleRow(label = "Flash press", on = flash, enabled = granted) {
            flash = it; prefs.flashPress = it
        }
        MontDetail("Press and hold the flash. The camera island is a cutout in the display, not in the digitiser.")
        MontGap()

        MontRow(label = "Notifications", value = "—", enabled = false)
        MontRow(label = "Quick settings", value = "—", enabled = false)
        MontGap()

        MontToggleRow(label = "Haptics", on = haptics, enabled = granted) {
            haptics = it; prefs.haptics = it
        }
        MontGap()

        if (granted) {
            MontRow(label = "Accessibility", value = "granted", dim = true)
        } else {
            MontRow(label = "Accessibility — grant", dim = false, onClick = onGrant)
            MontDetail("miniTools needs it to hold a window over the cover screen. It reads nothing.")
        }
        MontGap()
        MontRow(label = "Close", dim = true, onClick = onClose)
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
