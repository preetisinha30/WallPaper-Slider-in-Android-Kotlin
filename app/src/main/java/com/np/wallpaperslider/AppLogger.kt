package com.np.wallpaperslider
import com.np.wallpaperslider.BuildConfig

object AppLogger {
    private val LOGS_ENABLED = BuildConfig.DEBUG

    fun d(tag: String, message: String) {
        if (LOGS_ENABLED) {
            // Note: Check the log level here. 'd' is for Debug/Verbose info.
            android.util.Log.d(tag, message)
        }
    }
    fun i(tag: String, message: String) {
        if (LOGS_ENABLED) {
            // Note: Check the log level here. 'd' is for Debug/Verbose info.
            android.util.Log.d(tag, message)
        }
    }
    fun v(tag: String, message: String) {
        if (LOGS_ENABLED) {
            // Note: Check the log level here. 'd' is for Debug/Verbose info.
            android.util.Log.d(tag, message)
        }
    }
    fun w(tag: String, message: String) {
        if (LOGS_ENABLED) {
            // Note: Check the log level here. 'd' is for Debug/Verbose info.
            android.util.Log.d(tag, message)
        }
    }

    fun e(tag: String, message: String, tr: Throwable? = null) {
        if (LOGS_ENABLED) {
            // 'e' is for Errors/Critical info. Include the Throwable if provided.
            android.util.Log.e(tag, message, tr)
        }
    }
}