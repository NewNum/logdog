package com.uxnhe.logdog.demo

import android.app.Application
import com.uxnhe.logdog.LogDog

class DemoApp : Application() {
    override fun onCreate() {
        super.onCreate()
        LogDog.log("before-init-should-buffer")
        LogDog.init(this)
        LogDog.log("DemoApp", "initialized")
    }
}
