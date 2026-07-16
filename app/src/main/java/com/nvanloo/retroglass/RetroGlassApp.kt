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

    companion object {
        const val CRASH_DIR = "crashes"

        fun crashDir(app: Application): File = File(app.filesDir, CRASH_DIR)
    }
}
