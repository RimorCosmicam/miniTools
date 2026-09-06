package com.rimor.minitools

import android.content.Context
import android.graphics.PixelFormat
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner

/**
 * Compose, in a window that is not an activity's.
 *
 * The launcher has to appear *over* whatever you were doing rather than instead of it, and that
 * rules out starting an activity — an activity is a change of place, and this is a panel that
 * opens on top of the place you are already in. An accessibility overlay is the only window an
 * ordinary app gets that behaves that way, and Compose will happily live in one provided somebody
 * hands it the three owners it would normally inherit from an Activity.
 */
class OverlayHost(
    private val service: Context,
    private val display: Display,
) : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {

    private val registry = LifecycleRegistry(this)
    private val savedState = SavedStateRegistryController.create(this)
    private var root: ComposeView? = null

    override val lifecycle: Lifecycle get() = registry
    override val viewModelStore = ViewModelStore()
    override val savedStateRegistry: SavedStateRegistry get() = savedState.savedStateRegistry

    val isShowing: Boolean get() = root != null

    fun show(content: @Composable () -> Unit): Boolean {
        if (root != null) return true
        return try {
            savedState.performAttach()
            savedState.performRestore(null)
            registry.currentState = Lifecycle.State.CREATED

            val context = service.createDisplayContext(display)
                .createWindowContext(display, TYPE, null)
            val view = ComposeView(context).apply {
                setViewTreeLifecycleOwner(this@OverlayHost)
                setViewTreeViewModelStoreOwner(this@OverlayHost)
                setViewTreeSavedStateRegistryOwner(this@OverlayHost)
                setContent(content)
            }
            context.getSystemService(WindowManager::class.java).addView(view, params())
            root = view
            registry.currentState = Lifecycle.State.RESUMED
            true
        } catch (e: Exception) {
            registry.currentState = Lifecycle.State.DESTROYED
            root = null
            false
        }
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        registry.currentState = Lifecycle.State.DESTROYED
        runCatching {
            service.createDisplayContext(display)
                .createWindowContext(display, TYPE, null)
                .getSystemService(WindowManager::class.java)
                .removeView(view)
        }
        viewModelStore.clear()
    }

    private fun params() = WindowManager.LayoutParams(
        WindowManager.LayoutParams.MATCH_PARENT,
        WindowManager.LayoutParams.MATCH_PARENT,
        TYPE,
        // Focusable, so the back key closes it the way any panel should close.
        WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        layoutInDisplayCutoutMode =
            WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
        title = "miniTools/OVERLAY"
    }

    private companion object {
        const val TYPE = WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
    }
}
