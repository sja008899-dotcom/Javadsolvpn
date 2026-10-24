package com.example

import android.app.Application
import libv2ray.Libv2ray

class JavadApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        try {
            Libv2ray.initCoreEnv(filesDir.absolutePath, "")
        } catch (_: Throwable) {}
    }
}
