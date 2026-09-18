package com.example.logdoy.lib

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import java.lang.ref.WeakReference
import java.util.ArrayDeque

internal object FloatingLogController {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var registered = false
    private var expanded: Boolean = false
    private var posX: Float = Float.NaN
    private var posY: Float = Float.NaN
    private var attachedActivity: WeakReference<Activity>? = null
    private var floatingView: FloatingLogView? = null
    private var observer: ((LogEntry) -> Unit)? = null

    fun start(app: Application) {
        if (registered) return
        registered = true
        app.registerActivityLifecycleCallbacks(callbacks)
    }

    private val callbacks = object : Application.ActivityLifecycleCallbacks {
        override fun onActivityResumed(activity: Activity) {
            attach(activity)
        }

        override fun onActivityPaused(activity: Activity) {
            if (attachedActivity?.get() === activity) {
                detach()
            }
        }

        override fun onActivityCreated(a: Activity, b: Bundle?) {}
        override fun onActivityStarted(a: Activity) {}
        override fun onActivityStopped(a: Activity) {}
        override fun onActivitySaveInstanceState(a: Activity, b: Bundle) {}
        override fun onActivityDestroyed(a: Activity) {}
    }

    private fun attach(activity: Activity) {
        if (attachedActivity?.get() === activity && floatingView?.parent != null) {
            return
        }
        detach()
        val decor = activity.window?.decorView as? ViewGroup ?: return
        val view = FloatingLogView(activity).also { floatingView = it }
        view.onStateChanged = { exp, x, y ->
            expanded = exp
            posX = x
            posY = y
        }
        decor.addView(
            view,
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT,
            ),
        )
        var ready = false
        val pending = ArrayDeque<LogEntry>()
        val obs: (LogEntry) -> Unit = { entry ->
            val deliver = {
                if (floatingView === view) {
                    if (!ready) {
                        pending.addLast(entry)
                    } else {
                        view.append(entry)
                    }
                }
            }
            if (Looper.myLooper() == Looper.getMainLooper()) {
                deliver()
            } else {
                mainHandler.post(deliver)
            }
        }
        val snap = LogDoy.buffer.subscribe(obs)
        observer = obs
        view.bind(snap)
        ready = true
        while (pending.isNotEmpty()) {
            view.append(pending.removeFirst())
        }
        view.applyState(expanded, posX, posY)
        attachedActivity = WeakReference(activity)
    }

    private fun detach() {
        observer?.let { LogDoy.buffer.removeObserver(it) }
        observer = null
        floatingView?.let { v ->
            (v.parent as? ViewGroup)?.removeView(v)
        }
        floatingView = null
        attachedActivity = null
    }
}
