package com.rimor.minitools

import android.content.Context
import android.content.pm.PackageManager
import android.os.IBinder
import android.util.Log
import java.lang.reflect.Method

/**
 * A probe, not a feature.
 *
 * The question it answers: can an ordinary app holding WRITE_SECURE_SETTINGS actually set the
 * density of one display? The permission is necessary but the setter is hidden API, and this
 * Android build has already refused us one hidden method by reflection today, so the answer is
 * measured rather than assumed. Delete this file once it has told us.
 */
object DensityProbe {
    private const val TAG = "miniToolsProbe"

    fun run(context: Context, displayId: Int, density: Int): String {
        val lines = mutableListOf<String>()

        val granted = context.checkSelfPermission(
            "android.permission.WRITE_SECURE_SETTINGS",
        ) == PackageManager.PERMISSION_GRANTED
        lines += "WRITE_SECURE_SETTINGS granted=$granted"

        lines += "exemptions=" + runCatching { liftHiddenApiRestrictions() }
            .fold({ "ok" }, { "failed: ${it.javaClass.simpleName}: ${it.message}" })

        val wm = runCatching { windowManagerService() }
        if (wm.isFailure) {
            lines += "IWindowManager=unreachable: ${wm.exceptionOrNull()}"
            return report(lines)
        }
        val service = wm.getOrNull()!!
        lines += "IWindowManager=${service.javaClass.name}"

        lines += runCatching {
            val set: Method = service.javaClass.getMethod(
                "setForcedDisplayDensityForUser",
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
                Int::class.javaPrimitiveType,
            )
            set.invoke(service, displayId, density, 0)
            "setForcedDisplayDensityForUser($displayId, $density, 0) = OK"
        }.getOrElse { "setForcedDisplayDensityForUser FAILED: ${it.cause ?: it}" }

        return report(lines)
    }

    fun clear(context: Context, displayId: Int): String = runCatching {
        liftHiddenApiRestrictions()
        val service = windowManagerService()
        service.javaClass.getMethod(
            "clearForcedDisplayDensityForUser",
            Int::class.javaPrimitiveType,
            Int::class.javaPrimitiveType,
        ).invoke(service, displayId, 0)
        report(listOf("clearForcedDisplayDensityForUser($displayId, 0) = OK"))
    }.getOrElse { report(listOf("clear FAILED: ${it.cause ?: it}")) }

    private fun report(lines: List<String>): String {
        lines.forEach { Log.i(TAG, it) }
        return lines.joinToString("\n")
    }

    private fun windowManagerService(): Any {
        val serviceManager = Class.forName("android.os.ServiceManager")
        val binder = serviceManager.getMethod("getService", String::class.java)
            .invoke(null, "window") as IBinder
        val stub = Class.forName("android.view.IWindowManager\$Stub")
        return stub.getMethod("asInterface", IBinder::class.java).invoke(null, binder)!!
    }

    /**
     * The double-reflection trick. Reflecting on Class's own getDeclaredMethod makes the
     * *platform* the caller of the inner lookup, and the blocklist only applies to callers
     * outside it — so the exemption setter can be reached and told to exempt everything.
     */
    private fun liftHiddenApiRestrictions() {
        val getDeclaredMethod = Class::class.java.getDeclaredMethod(
            "getDeclaredMethod",
            String::class.java,
            Array<Class<*>>::class.java,
        )
        val vmRuntime = Class::class.java
            .getDeclaredMethod("forName", String::class.java)
            .invoke(null, "dalvik.system.VMRuntime") as Class<*>
        val getRuntime = getDeclaredMethod
            .invoke(vmRuntime, "getRuntime", emptyArray<Class<*>>()) as Method
        val setExemptions = getDeclaredMethod
            .invoke(vmRuntime, "setHiddenApiExemptions", arrayOf(Array<String>::class.java)) as Method
        setExemptions.invoke(getRuntime.invoke(null), arrayOf("L"))
    }
}
