package com.rimor.minitools

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap

/**
 * The launcher.
 *
 * A card, a word, a grid, and the name of the order it is in. Everything else — what is starred,
 * what is hidden, whether the card or the word are there at all — is reached by holding something
 * down, because a launcher that spends a row of its own grid on a settings button is a launcher
 * with fewer apps in it.
 */
class LauncherActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MiniToolsTheme { LauncherScreen(::openUsageAccess, ::finish) } }
    }

    private fun openUsageAccess() {
        runCatching {
            startActivity(
                Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}

private sealed interface Menu {
    data object None : Menu
    data object Settings : Menu
    data object Hidden : Menu
    data class App(val app: LaunchableApp) : Menu
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LauncherScreen(onUsageAccess: () -> Unit, onClose: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }

    var catalog by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    var order by remember { mutableStateOf(prefs.sortOrder) }
    var favourites by remember { mutableStateOf(prefs.favourites) }
    var hidden by remember { mutableStateOf(prefs.hidden) }
    var background by remember { mutableStateOf(prefs.launcherBackground) }
    var title by remember { mutableStateOf(prefs.launcherTitle) }
    var lastUsed by remember { mutableStateOf(emptyMap<String, Long>()) }
    var menu by remember { mutableStateOf<Menu>(Menu.None) }

    LaunchedEffect(Unit) {
        catalog = AppCatalog.load(context)
        lastUsed = UsageAccess.lastUsed(context, prefs.ownLaunchTimes())
    }

    val apps = remember(catalog, order, favourites, hidden, lastUsed) {
        AppCatalog.arrange(catalog, order, favourites, hidden, lastUsed)
    }
    val displayId = remember { CoverDisplay.idOrDefault(context) }

    Box(
        Modifier
            .fillMaxSize()
            .then(if (background) Modifier.background(Color.Black) else Modifier),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(start = 22.dp, end = 14.dp, top = 44.dp, bottom = 12.dp),
        ) {
            // The header is a row: the word on the left, the order on the right. The order is
            // written out rather than drawn as a glyph — in this language the type is the icon,
            // and "A–Z" says more in the same space than a picture of a sorted list.
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (title) {
                    Text(
                        "LAUNCHER",
                        style = Mont.row,
                        color = Mont.Selected,
                        modifier = Modifier.combinedClickable(
                            onClick = { menu = Menu.Settings },
                            onLongClick = { menu = Menu.Settings },
                        ),
                    )
                }
                Text(
                    order.label.uppercase(),
                    style = Mont.caption,
                    color = Mont.Dim,
                    textAlign = TextAlign.End,
                    modifier = Modifier
                        .weight(1f)
                        .combinedClickable(
                            onClick = { order = order.next().also { prefs.sortOrder = it } },
                            onLongClick = { menu = Menu.Settings },
                        )
                        .padding(vertical = 6.dp),
                )
            }

            MontGap(12)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 74.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { it.packageName }) { app ->
                    AppCell(
                        app = app,
                        starred = app.packageName in favourites,
                        onOpen = {
                            prefs.recordLaunch(app.packageName)
                            AppLaunch.open(context, app, displayId)
                        },
                        onHold = { menu = Menu.App(app) },
                    )
                }
            }
        }

        when (val open = menu) {
            Menu.None -> Unit

            // Mont's command bar: full width, anchored to the top, one word per line, no title.
            // You opened it from the thing it belongs to, so the first line is already an option.
            Menu.Settings -> CommandBar(onDismiss = { menu = Menu.None }) {
                MontRow(
                    label = "Background",
                    value = if (background) "on" else "off",
                ) { background = !background; prefs.launcherBackground = background }
                MontRow(
                    label = "Title",
                    value = if (title) "on" else "off",
                ) { title = !title; prefs.launcherTitle = title }
                MontRow(label = "Hidden apps", value = "${hidden.size}") { menu = Menu.Hidden }
                if (order == SortOrder.USED && !UsageAccess.granted(context)) {
                    MontRow(label = "Usage access — grant", dim = true, onClick = onUsageAccess)
                    MontDetail("Without it, USED orders by the launches miniTools made itself.")
                }
                MontRow(label = "Close launcher", dim = true, onClick = onClose)
                MontRow(label = "Close", dim = true) { menu = Menu.None }
            }

            Menu.Hidden -> CommandBar(onDismiss = { menu = Menu.None }) {
                val hiddenApps = catalog.filter { it.packageName in hidden }
                    .sortedBy { it.label.lowercase() }
                if (hiddenApps.isEmpty()) {
                    MontRow(label = "Nothing hidden", enabled = false)
                } else {
                    hiddenApps.forEach { app ->
                        MontRow(label = app.label, value = "show") {
                            hidden = (hidden - app.packageName).also { prefs.hidden = it }
                        }
                    }
                }
                MontRow(label = "Close", dim = true) { menu = Menu.None }
            }

            is Menu.App -> CommandBar(onDismiss = { menu = Menu.None }) {
                val app = open.app
                val starred = app.packageName in favourites
                MontRow(label = if (starred) "Unfavourite" else "Favourite") {
                    favourites = (if (starred) favourites - app.packageName else favourites + app.packageName)
                        .also { prefs.favourites = it }
                    menu = Menu.None
                }
                MontRow(label = "Hide") {
                    hidden = (hidden + app.packageName).also { prefs.hidden = it }
                    menu = Menu.None
                }
                MontRow(label = "Cancel", dim = true) { menu = Menu.None }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppCell(
    app: LaunchableApp,
    starred: Boolean,
    onOpen: () -> Unit,
    onHold: () -> Unit,
) {
    val context = LocalContext.current
    val icon = remember(app.packageName) {
        runCatching {
            context.packageManager.getApplicationIcon(app.packageName)
                .toBitmap(width = 144, height = 144)
                .asImageBitmap()
        }.getOrNull()
    }
    Column(
        modifier = Modifier
            .combinedClickable(onClick = onOpen, onLongClick = onHold)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        if (icon != null) {
            Image(bitmap = icon, contentDescription = app.label, modifier = Modifier.size(48.dp))
        } else {
            Box(Modifier.size(48.dp).background(Mont.Track))
        }
        Text(
            text = app.label.uppercase(),
            style = Mont.mark,
            // A favourite is simply the bright one. No star, no pill, no badge.
            color = if (starred) Mont.Selected else Mont.Dim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

private fun SortOrder.next(): SortOrder =
    SortOrder.entries[(ordinal + 1) % SortOrder.entries.size]
