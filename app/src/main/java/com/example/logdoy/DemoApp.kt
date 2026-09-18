package com.example.logdoy

import android.app.Application
import com.example.logdoy.lib.LogDoy

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogDoy.log("before-init-should-buffer")
        LogDoy.init(this)
        LogDoy.log("DemoApp", "initialized")
    }
}
