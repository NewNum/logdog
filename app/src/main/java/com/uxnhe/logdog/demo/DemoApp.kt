package com.uxnhe.logdog.demo

import android.app.Application
import com.uxnhe.logdog.LogDoy

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogDoy.log("before-init-should-buffer")
        LogDoy.init(this)
        LogDoy.log("DemoApp", "initialized")
    }
}
