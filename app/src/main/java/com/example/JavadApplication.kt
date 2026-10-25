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
        extractCoreAssets()
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

    private fun extractCoreAssets() {
        val assetNames = listOf("geoip.dat", "geosite.dat", "geoip-only-cn-private.dat")
        for (name in assetNames) {
            val destFile = java.io.File(filesDir, name)
            if (!destFile.exists() || destFile.length() == 0L) {
                try {
                    assets.open(name).use { input ->
                        java.io.FileOutputStream(destFile).use { output ->
                            input.copyTo(output)
                        }
                    }
                } catch (_: Exception) {}
            }
        }
    }
}
