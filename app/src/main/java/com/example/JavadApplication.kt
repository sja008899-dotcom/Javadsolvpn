package com.example

import android.app.Application
import com.example.data.local.AppDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import libv2ray.Libv2ray

class JavadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        } catch (_: Throwable) {}

        // Ensure database has default sources and initial 12 bundled configs
        CoroutineScope(Dispatchers.IO).launch {
            try {
                AppDatabase.getInstance(this@JavadApplication).seedInitialDataIfNeeded()
            } catch (_: Exception) {}
        }
    }
}
