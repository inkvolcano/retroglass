package com.nvanloo.retroglass

import android.app.Application
import android.os.Build
import android.util.Log
import java.io.File

/**
 * Installs a process-wide crash handler that writes each uncaught exception to a log file
 * under filesDir/crashes/. MainActivity offers to share the newest one on the next launch.
 * Fully local — nothing is sent anywhere.
 */
class RetroGlassApp : Application() {

    override fun onCreate() {
        super.onCreate()
        giveNativeCoresAWritableTmpdir()
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching {
                val dir = File(filesDir, CRASH_DIR).apply { mkdirs() }
                val version = runCatching {
                    packageManager.getPackageInfo(packageName, 0).versionName
                }.getOrNull() ?: "?"
                File(dir, "crash-${System.currentTimeMillis()}.log").writeText(
                    buildString {
                        append("RetroGlass crash report\n")
                        append("app: $packageName v$version\n")
                        append("device: ${Build.MANUFACTURER} ${Build.MODEL}\n")
                        append("android: ${Build.VERSION.RELEASE} (SDK ${Build.VERSION.SDK_INT})\n")
                        append("thread: ${thread.name}\n\n")
                        append(Log.getStackTraceString(error))
                    },
                )
            }
            previous?.uncaughtException(thread, error)
        }
    }

    /**
     * Points `TMPDIR` at our own cache directory, which Android never sets for an app process.
     *
     * **This does not fix the Dreamcast crash** — it was tried for that and the core still
     * failed identically. Kept because an unset TMPDIR is a real gap for any native core that
     * wants scratch space, and it costs nothing. The Flycast story is recorded below because it
     * is the reason this was investigated at all.
     *
     * Flycast reserves its fast guest-memory mapping through a shared-memory file
     * (`core/linux/posix_vmem.cpp`). It tries `/dev/ashmem`, then `$TMPDIR`, then
     * `/data/local/tmp`. On Android 11+ `/dev/ashmem` no longer exists, `/data/local/tmp` belongs
     * to the shell rather than to apps, and Android never sets `TMPDIR` for an app process — so
     * every candidate fails with EACCES. The core logs
     * "Virtual memory file allocation failed: errno 13", disables nvmem and carries on with
     * `BASE 0x0`, but its dynarec still emits writes through that base, so the first guest write
     * segfaults in `addrspace::write32` and takes the whole process down.
     *
     * Setting TMPDIR was the obvious lever, since it is the one candidate of the three an app
     * can control. It did not help: the core fails the same way with it set, so Flycast is not
     * consulting TMPDIR on this path — it goes to /dev/ashmem and gives up. That leaves the
     * crash a core-side incompatibility with Android 11+, not something fixable from here.
     */
    private fun giveNativeCoresAWritableTmpdir() {
        runCatching {
            val tmp = File(cacheDir, "native-tmp").apply { mkdirs() }
            android.system.Os.setenv("TMPDIR", tmp.absolutePath, true)
        }.onFailure { Log.w("RetroGlass", "could not set TMPDIR for native cores", it) }
    }

    companion object {
        const val CRASH_DIR = "crashes"

        fun crashDir(app: Application): File = File(app.filesDir, CRASH_DIR)
    }
}
