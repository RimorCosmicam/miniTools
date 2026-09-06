package com.rimor.minitools

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The launcher as a panel over whatever you were doing.
 *
 * The same list, the same order, the same favourites as the full screen — only the surface is
 * different. It is a Mont card: a black rectangle at 92%, full width, square, with the thing it
 * opened over still faintly present behind it. Anywhere off the card closes it, because a panel
 * that opens on top of something does not need a button to say so.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun LauncherOverlay(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val prefs = remember { Prefs(context) }
    var apps by remember { mutableStateOf(emptyList<LaunchableApp>()) }
    val displayId = remember { CoverDisplay.idOrDefault(context) }

    LaunchedEffect(Unit) {
        apps = withContext(Dispatchers.IO) {
            val loaded = AppCatalog.load(context)
            loaded.forEach { IconCache.load(context, it.packageName) }
            AppCatalog.arrange(
                apps = loaded,
                order = prefs.sortOrder,
                favourites = prefs.favourites,
                hidden = prefs.hidden,
                lastUsed = UsageAccess.lastUsed(context, prefs.ownLaunchTimes()),
            )
        }
    }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onDismiss,
            ),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom),
                )
                .padding(horizontal = 14.dp, vertical = 18.dp)
                .background(Mont.Surface)
                .clickable(
                    interactionSource = remember { MutableInteractionSource() },
                    indication = null,
                    onClick = {},
                )
                .padding(start = 22.dp, end = 14.dp, top = 20.dp, bottom = 12.dp),
        ) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("LAUNCHER", style = Mont.row, color = Mont.Selected, modifier = Modifier.weight(1f))
                Text(
                    "CLOSE",
                    style = Mont.caption,
                    color = Mont.Dim,
                    modifier = Modifier
                        .clickable(onClick = onDismiss)
                        .padding(start = 12.dp, top = 4.dp, bottom = 4.dp),
                )
            }
            MontGap(12)

            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 72.dp),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.fillMaxSize(),
            ) {
                items(apps, key = { "${it.packageName}/${it.activityName}" }) { app ->
                    OverlayCell(app) {
                        prefs.recordLaunch(app.packageName)
                        AppLaunch.open(context, app, displayId)
                        onDismiss()
                    }
                }
            }
        }
    }
}

@Composable
private fun OverlayCell(app: LaunchableApp, onOpen: () -> Unit) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(IconCache.cached(app.packageName), app.packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { IconCache.load(context, app.packageName) }
    }
    Column(
        modifier = Modifier
            .clickable(onClick = onOpen)
            .padding(vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val bitmap = icon
        if (bitmap != null) {
            Image(bitmap = bitmap, contentDescription = app.label, modifier = Modifier.size(44.dp))
        } else {
            Box(Modifier.size(44.dp).background(Mont.Track))
        }
        Text(
            text = app.label.uppercase(),
            style = Mont.mark,
            color = Mont.Dim,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}
