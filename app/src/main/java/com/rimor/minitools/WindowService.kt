package com.rimor.minitools

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.lang.reflect.Method

/**
 * The window manager, reached the only way an ordinary app can reach it.
 *
 * Two things stand in the way and only one of them is a permission. WRITE_SECURE_SETTINGS grants
 * once and stays granted; the hidden-API blocklist is the real obstacle, and without lifting it
 * IWindowManager offers twenty of its two hundred and thirty-one methods — none of the ones worth
 * having. Everything here is best-effort and says whether it worked, because a phone that cannot
 * do this should behave as though the feature is simply absent.
 */
object WindowService {
    private const val TAG = "miniTools"
    const val PERMISSION = "android.permission.WRITE_SECURE_SETTINGS"

    fun permitted(context: Context): Boolean =
        context.checkSelfPermission(PERMISSION) == PackageManager.PERMISSION_GRANTED

    /**
     * Call a method on IWindowManager by name.
     *
     * Signatures drift between releases — several of these methods grew a trailing `String caller`
     * somewhere along the way — so the method is found by name and arity is adapted rather than
     * hard-coded against one version of the platform.
     */
    fun call(name: String, vararg args: Any?): Boolean = try {
        HiddenApiBypass.addHiddenApiExemptions("")
        val service = service()
        val method = service.javaClass.methods
            .filter { it.name == name }
            .minByOrNull { it.parameterTypes.size }
            ?: error("no method named $name")
        method.invoke(service, *adapt(method, args))
        true
    } catch (e: Throwable) {
        Log.w(TAG, "$name failed", e)
        false
    }

    /** Read a value back, for the callers that need to know the current state. */
    fun <T> read(name: String, vararg args: Any?): T? = try {
        HiddenApiBypass.addHiddenApiExemptions("")
        val service = service()
        val method = service.javaClass.methods
            .filter { it.name == name }
            .minByOrNull { it.parameterTypes.size }
            ?: error("no method named $name")
        @Suppress("UNCHECKED_CAST")
        method.invoke(service, *adapt(method, args)) as T?
    } catch (e: Throwable) {
        Log.w(TAG, "$name failed", e)
        null
    }

    /** Pad with the caller name a newer platform wants, or trim what an older one does not. */
    private fun adapt(method: Method, args: Array<out Any?>): Array<Any?> {
        val wanted = method.parameterTypes
        if (wanted.size == args.size) return args.toList().toTypedArray()
        val out = args.toMutableList()
        while (out.size < wanted.size) {
            out += if (wanted[out.size] == String::class.java) "miniTools" else 0
        }
        while (out.size > wanted.size) out.removeAt(out.lastIndex)
        return out.toTypedArray()
    }

    private fun service(): Any {
        val binder = Class.forName("android.os.ServiceManager")
            .getMethod("getService", String::class.java)
            .invoke(null, "window") as IBinder
        return Class.forName("android.view.IWindowManager\$Stub")
            .getMethod("asInterface", IBinder::class.java)
            .invoke(null, binder)!!
    }
}
