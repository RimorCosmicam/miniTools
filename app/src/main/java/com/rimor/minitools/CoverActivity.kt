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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.key
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
                    Toolbox(granted, ::openAccessibilitySettings, ::openLauncher, ::finish)
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

    private fun openLauncher() {
        val options = android.app.ActivityOptions.makeBasic()
            .apply { launchDisplayId = CoverDisplay.idOrDefault(this@CoverActivity) }
        runCatching {
            startActivity(Intent(this, LauncherActivity::class.java), options.toBundle())
        }
    }

    private fun openAccessibilitySettings() {
        val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { startActivity(intent) }
    }
}

@Composable
private fun Toolbox(granted: Boolean, onGrant: () -> Unit, onLauncher: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var screen by remember { mutableStateOf(Screen.HOME) }
    // Bumped whenever a setting changes, so the pages and the warnings below them agree.
    var revision by remember { mutableStateOf(0) }

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
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 18.dp, vertical = 20.dp)
                .background(Mont.Surface)
                .padding(start = 22.dp, top = 22.dp, end = 18.dp, bottom = 16.dp),
        ) {
            MontWordmark(light = "mini", heavy = "Tools")
            MontGap(14)

            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState()),
            ) {
                key(revision) {
                    when (screen) {
                        Screen.HOME -> Home(prefs, granted) { screen = it }
                        Screen.LAUNCHER -> LauncherPage(prefs, onLauncher) { revision++ }
                        Screen.RECENTS -> RecentsPage(prefs, granted) { revision++ }
                        Screen.ROTATE -> RotatePage(prefs) { revision++ }
                        Screen.SHORTCUTS -> ShortcutsPage(prefs) { revision++ }
                    }
                }

                MontGap()
                if (screen == Screen.HOME) {
                    if (granted) {
                        MontRow(label = "Accessibility", value = "granted", dim = true)
                    } else {
                        MontRow(label = "Accessibility — grant", onClick = onGrant)
                        MontDetail("miniTools needs it to hold its windows over the cover screen.")
                    }
                    MontRow(label = "Close", dim = true, onClick = onClose)
                } else {
                    MontRow(label = "Back", dim = true) { screen = Screen.HOME }
                }
            }
        }
    }
}

/** The list. Every tool, and the page that configures it. */
@Composable
private fun Home(prefs: Prefs, granted: Boolean, onOpen: (Screen) -> Unit) {
    MontRow(label = "Launcher", value = summary(prefs, Action.LAUNCHER), enabled = granted) {
        onOpen(Screen.LAUNCHER)
    }
    MontRow(label = "Recents", value = summary(prefs, Action.RECENTS), enabled = granted) {
        onOpen(Screen.RECENTS)
    }
    MontRow(label = "Rotate", value = summary(prefs, Action.ROTATE), enabled = granted) {
        onOpen(Screen.ROTATE)
    }
    MontRow(label = "Flash shortcuts", value = "set", enabled = granted) {
        onOpen(Screen.SHORTCUTS)
    }

    // Said plainly, and only when it is true: a feature with no gesture pointed at it cannot be
    // reached at all, and nothing else on this screen would tell you.
    val unreachable = Action.features.filter { prefs.gesturesFor(it).isEmpty() }
    if (unreachable.isNotEmpty()) {
        MontGap()
        unreachable.forEach {
            MontDetail("You have no shortcut set for the ${it.label} feature.")
        }
    }
}

@Composable
private fun ShortcutsPage(prefs: Prefs, onChange: () -> Unit) {
    val actions = Action.entries.toList()
    // The corner swipe sits with them because it is the same question — what does this do — and
    // splitting it onto its own page would only hide one answer from the other three.
    listOf(
        Gesture.TAP to "1 tap",
        Gesture.DOUBLE_TAP to "2 taps",
        Gesture.HOLD to "Hold",
        Gesture.SWIPE_UP to "Corner swipe",
    ).forEach { (gesture, label) ->
        MontRow(label = label, dim = true)
        MontChips(
            options = actions.map { it.label },
            selected = actions.indexOf(prefs.actionFor(gesture)),
        ) { picked ->
            prefs.setAction(gesture, actions[picked])
            onChange()
        }
    }
    MontDetail("The first three are the flash. A tap waits a moment to find out whether a second one is coming.")
}

@Composable
private fun LauncherPage(prefs: Prefs, onOpen: () -> Unit, onChange: () -> Unit) {
    MontRow(label = "Open it", value = "now", onClick = onOpen)
    MontGap()
    MontToggleRow(label = "Card background", on = prefs.launcherBackground) {
        prefs.launcherBackground = it; onChange()
    }
    MontToggleRow(label = "Title", on = prefs.launcherTitle) {
        prefs.launcherTitle = it; onChange()
    }
    MontRow(label = "Sort", value = prefs.sortOrder.label) {
        val all = SortOrder.entries
        prefs.sortOrder = all[(prefs.sortOrder.ordinal + 1) % all.size]
        onChange()
    }
    MontRow(label = "Hidden apps", value = "${prefs.hidden.size}", enabled = prefs.hidden.isNotEmpty()) {
        prefs.hidden = emptySet(); onChange()
    }
    if (prefs.hidden.isNotEmpty()) MontDetail("Tap to unhide all of them.")
    MontRow(label = "Favourites", value = "${prefs.favourites.size}", enabled = false)
    MontDetail("Hold an app in the launcher to favourite or hide it.")
}

@Composable
private fun RecentsPage(prefs: Prefs, granted: Boolean, onChange: () -> Unit) {
    MontRow(label = "Opened by", value = summary(prefs, Action.RECENTS), enabled = false)
    MontDetail("One UI's own task switcher, put on the cover screen at the panel's native density. The list is rebuilt on every open, so what you used last is where it should be.")
}

@Composable
private fun RotatePage(prefs: Prefs, onChange: () -> Unit) {
    MontRow(label = "Rotation", value = if (Rotation.isOn) "on" else "off", dim = true)
    MontDetail("Samsung pins the cover panel to portrait and will not be talked out of it. miniTools holds an invisible window that asks for sensor orientation instead, and everything behind it turns with the phone.")
    MontGap()
    MontRow(label = "Toggled by", value = shortcutFor(prefs, Action.ROTATE), enabled = false)
}

/** Which gestures reach a feature, said as a value rather than a sentence. */
private fun summary(prefs: Prefs, action: Action): String {
    val gestures = prefs.gesturesFor(action)
    return if (gestures.isEmpty()) "no shortcut" else gestures.joinToString(" · ") { shortLabel(it) }
}

private fun shortcutFor(prefs: Prefs, action: Action): String = summary(prefs, action)

private fun shortLabel(gesture: Gesture): String = when (gesture) {
    Gesture.TAP -> "1 tap"
    Gesture.DOUBLE_TAP -> "2 taps"
    Gesture.HOLD -> "hold"
    Gesture.SWIPE_UP -> "corner"
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
