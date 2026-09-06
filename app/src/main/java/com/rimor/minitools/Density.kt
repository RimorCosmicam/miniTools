package com.rimor.minitools

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass

/**
 * The density of one display, set and put back.
 *
 * Two things stand between an ordinary app and this. The first is a permission, and
 * WRITE_SECURE_SETTINGS grants once and stays granted. The second is the hidden-API blocklist,
 * which is the real obstacle: without lifting it, IWindowManager offers us twenty of its
 * two hundred and thirty-one methods and the density setter is not among them.
 *
 * Everything here is best-effort and reports whether it worked. A phone that cannot do this
 * should get a switcher at its native density, not a crash.
 */
object Density {
    private const val TAG = "miniTools"
    private const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    fun apply(context: Context, displayId: Int, density: Int): Boolean {
        if (!permitted(context) || density <= 0) return false
        return call("setForcedDisplayDensityForUser") { service, method ->
            method.invoke(service, displayId, density, 0)
        }
    }

    fun restore(context: Context, displayId: Int): Boolean {
        if (!permitted(context)) return false
        return call("clearForcedDisplayDensityForUser") { service, method ->
            method.invoke(service, displayId, 0)
        }
    }

    private inline fun call(
        name: String,
        invoke: (Any, java.lang.reflect.Method) -> Unit,
    ): Boolean = try {
        HiddenApiBypass.addHiddenApiExemptions("")
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "window") as IBinder
        val service = Class.forName("android.view.IWindowManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
        val method = service.javaClass.methods.first { it.name == name }
        invoke(service, method)
        true
    } catch (e: Throwable) {
        Log.w(TAG, "$name failed", e)
        false
    }
}
