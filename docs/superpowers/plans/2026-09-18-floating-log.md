# LogDoy Floating Log Library Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Ship an Android library (`:logdoy`) that, after `LogDoy.init(app)`, shows an in-app draggable floating log panel (~1/4 screen) with minimize-to-bubble, and exposes only `init` + `log` APIs with pre-init buffering.

**Architecture:** Thread-safe `LogBuffer` (ring, 500) feeds a `FloatingLogController` registered via `ActivityLifecycleCallbacks`, which attaches `FloatingLogView` to the resumed Activity `DecorView`. `LogDoy` is a thin facade; UI uses View/XML only.

**Tech Stack:** Kotlin, Android Library (AGP 9.3.3), AppCompat/RecyclerView, JUnit4 JVM unit tests for buffer/facade logic.

## Global Constraints

- In-app overlay only — no `SYSTEM_ALERT_WINDOW`
- View + XML only — no Compose
- Public API: `LogDoy.init(Application)`, `LogDoy.log(String)`, `LogDoy.log(String, String)` only
- Init → show; no init → no UI; `log` before init still buffers
- `minSdk = 24`, package/namespace for library: `com.example.logdoy.lib` (app keeps `com.example.logdoy`)
- Ring buffer capacity: 500
- Spec: `docs/superpowers/specs/2026-09-18-floating-log-design.md`

---

## File Structure

| Path | Responsibility |
|------|----------------|
| `logdoy/build.gradle.kts` | Library module build |
| `logdoy/src/main/AndroidManifest.xml` | Empty manifest (no components) |
| `logdoy/src/main/java/com/example/logdoy/lib/LogEntry.kt` | Log data class |
| `logdoy/src/main/java/com/example/logdoy/lib/LogBuffer.kt` | Thread-safe ring buffer + observers |
| `logdoy/src/main/java/com/example/logdoy/lib/LogDoy.kt` | Public facade |
| `logdoy/src/main/java/com/example/logdoy/lib/FloatingLogController.kt` | Lifecycle attach/detach + state |
| `logdoy/src/main/java/com/example/logdoy/lib/FloatingLogView.kt` | Panel/bubble UI, drag, list |
| `logdoy/src/main/java/com/example/logdoy/lib/LogListAdapter.kt` | RecyclerView adapter |
| `logdoy/src/main/res/layout/logdoy_floating_root.xml` | Root overlay container |
| `logdoy/src/main/res/layout/logdoy_panel.xml` | Expanded panel |
| `logdoy/src/main/res/layout/logdoy_log_item.xml` | One log row |
| `logdoy/src/main/res/drawable/logdoy_panel_bg.xml` | Semi-transparent panel bg |
| `logdoy/src/main/res/drawable/logdoy_bubble_bg.xml` | Bubble bg |
| `logdoy/src/main/res/values/strings.xml` | Library strings |
| `logdoy/src/test/java/com/example/logdoy/lib/LogBufferTest.kt` | Buffer unit tests |
| `logdoy/src/test/java/com/example/logdoy/lib/LogDoyBufferingTest.kt` | Pre-init buffering tests |
| `settings.gradle.kts` | Include `:logdoy` |
| `gradle/libs.versions.toml` | Add `android-library` + recyclerview |
| `build.gradle.kts` | Apply library plugin false |
| `app/build.gradle.kts` | Depend on `:logdoy` |
| `app/src/main/java/.../DemoApp.kt` | Call `LogDoy.init` |
| `app/src/main/AndroidManifest.xml` | Register Application + SecondActivity |
| `app/src/main/.../MainActivity.kt` + layout | Demo log button |
| `app/src/main/.../SecondActivity.kt` + layout | Cross-activity demo |

---

### Task 1: Scaffold `:logdoy` module

**Files:**
- Create: `logdoy/build.gradle.kts`
- Create: `logdoy/src/main/AndroidManifest.xml`
- Modify: `settings.gradle.kts`
- Modify: `gradle/libs.versions.toml`
- Modify: `build.gradle.kts`
- Modify: `app/build.gradle.kts`

**Interfaces:**
- Consumes: existing AGP / version catalog
- Produces: compilable empty `:logdoy` Android library; `:app` depends on it

- [ ] **Step 1: Add library plugin and RecyclerView to version catalog**

In `gradle/libs.versions.toml`, under `[versions]` keep existing; under `[libraries]` add:

```toml
androidx-recyclerview = { group = "androidx.recyclerview", name = "recyclerview", version = "1.4.0" }
```

Under `[plugins]` add:

```toml
android-library = { id = "com.android.library", version.ref = "agp" }
```

- [ ] **Step 2: Register plugin in root `build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.android.library) apply false
}
```

- [ ] **Step 3: Include module in `settings.gradle.kts`**

Replace the include line with:

```kotlin
rootProject.name = "LogDoy"
include(":app")
include(":logdoy")
```

- [ ] **Step 4: Create `logdoy/build.gradle.kts`**

```kotlin
plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.example.logdoy.lib"
    compileSdk {
        version = release(36) {
            minorApiLevel = 1
        }
    }

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.recyclerview)
    testImplementation(libs.junit)
}
```

- [ ] **Step 5: Create empty manifest and consumer rules**

`logdoy/src/main/AndroidManifest.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<manifest />
```

`logdoy/consumer-rules.pro`: empty file (or a single comment line).

- [ ] **Step 6: Wire app dependency**

In `app/build.gradle.kts` `dependencies` block add:

```kotlin
implementation(project(":logdoy"))
```

- [ ] **Step 7: Verify modules resolve**

Run: `./gradlew :logdoy:assembleDebug :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 8: Commit**

```bash
git add settings.gradle.kts build.gradle.kts gradle/libs.versions.toml \
  logdoy/ app/build.gradle.kts
git commit -m "chore: scaffold :logdoy Android library module"
```

---

### Task 2: `LogEntry` + `LogBuffer` (TDD)

**Files:**
- Create: `logdoy/src/main/java/com/example/logdoy/lib/LogEntry.kt`
- Create: `logdoy/src/main/java/com/example/logdoy/lib/LogBuffer.kt`
- Test: `logdoy/src/test/java/com/example/logdoy/lib/LogBufferTest.kt`

**Interfaces:**
- Consumes: none
- Produces:
  - `data class LogEntry(val timestampMs: Long, val tag: String?, val message: String)`
  - `class LogBuffer(capacity: Int = 500)` with:
    - `fun add(entry: LogEntry)`
    - `fun snapshot(): List<LogEntry>`
    - `fun size(): Int`
    - `fun addObserver(observer: (LogEntry) -> Unit)`
    - `fun removeObserver(observer: (LogEntry) -> Unit)`

- [ ] **Step 1: Write failing tests**

Create `LogBufferTest.kt`:

```kotlin
package com.example.logdoy.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class LogBufferTest {
    @Test
    fun add_keepsOrderAndSize() {
        val buffer = LogBuffer(capacity = 3)
        buffer.add(LogEntry(1, null, "a"))
        buffer.add(LogEntry(2, "t", "b"))
        assertEquals(2, buffer.size())
        assertEquals(listOf("a", "b"), buffer.snapshot().map { it.message })
    }

    @Test
    fun add_dropsOldestWhenOverCapacity() {
        val buffer = LogBuffer(capacity = 2)
        buffer.add(LogEntry(1, null, "a"))
        buffer.add(LogEntry(2, null, "b"))
        buffer.add(LogEntry(3, null, "c"))
        assertEquals(2, buffer.size())
        assertEquals(listOf("b", "c"), buffer.snapshot().map { it.message })
    }

    @Test
    fun observer_receivesNewEntries() {
        val buffer = LogBuffer(capacity = 10)
        val received = mutableListOf<String>()
        val observer: (LogEntry) -> Unit = { received += it.message }
        buffer.addObserver(observer)
        buffer.add(LogEntry(1, null, "x"))
        assertEquals(listOf("x"), received)
    }

    @Test
    fun removeObserver_stopsNotifications() {
        val buffer = LogBuffer(capacity = 10)
        val received = mutableListOf<String>()
        val observer: (LogEntry) -> Unit = { received += it.message }
        buffer.addObserver(observer)
        buffer.removeObserver(observer)
        buffer.add(LogEntry(1, null, "x"))
        assertTrue(received.isEmpty())
    }
}
```

- [ ] **Step 2: Run tests — expect compile/fail**

Run: `./gradlew :logdoy:testDebugUnitTest --tests com.example.logdoy.lib.LogBufferTest`

Expected: FAIL (classes missing)

- [ ] **Step 3: Implement `LogEntry` and `LogBuffer`**

`LogEntry.kt`:

```kotlin
package com.example.logdoy.lib

data class LogEntry(
    val timestampMs: Long,
    val tag: String?,
    val message: String,
)
```

`LogBuffer.kt`:

```kotlin
package com.example.logdoy.lib

import java.util.ArrayDeque
import java.util.concurrent.CopyOnWriteArrayList

class LogBuffer(private val capacity: Int = 500) {
    private val lock = Any()
    private val entries = ArrayDeque<LogEntry>(capacity.coerceAtLeast(1))
    private val observers = CopyOnWriteArrayList<(LogEntry) -> Unit>()

    fun add(entry: LogEntry) {
        synchronized(lock) {
            while (entries.size >= capacity) {
                entries.removeFirst()
            }
            entries.addLast(entry)
        }
        observers.forEach { it(entry) }
    }

    fun snapshot(): List<LogEntry> = synchronized(lock) {
        entries.toList()
    }

    fun size(): Int = synchronized(lock) {
        entries.size
    }

    fun addObserver(observer: (LogEntry) -> Unit) {
        observers.add(observer)
    }

    fun removeObserver(observer: (LogEntry) -> Unit) {
        observers.remove(observer)
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :logdoy:testDebugUnitTest --tests com.example.logdoy.lib.LogBufferTest`

Expected: BUILD SUCCESSFUL, all tests PASS

- [ ] **Step 5: Commit**

```bash
git add logdoy/src/main/java/com/example/logdoy/lib/LogEntry.kt \
  logdoy/src/main/java/com/example/logdoy/lib/LogBuffer.kt \
  logdoy/src/test/java/com/example/logdoy/lib/LogBufferTest.kt
git commit -m "feat: add thread-safe LogBuffer with ring capacity"
```

---

### Task 3: `LogDoy` facade with pre-init buffering (TDD)

**Files:**
- Create: `logdoy/src/main/java/com/example/logdoy/lib/LogDoy.kt`
- Test: `logdoy/src/test/java/com/example/logdoy/lib/LogDoyBufferingTest.kt`

**Interfaces:**
- Consumes: `LogBuffer`, `LogEntry`
- Produces:
  - `object LogDoy` with `init(app: Application)`, `log(message: String)`, `log(tag: String, message: String)`
  - Internal (same module, `internal`): `internal val buffer: LogBuffer`, `internal var initialized: Boolean`, hook for controller start used in Task 6
  - For unit tests without Android Framework: extract pure buffering into testable path — `LogDoy` uses `System.currentTimeMillis()` and always writes to shared `buffer` before any UI; `init` sets flag and calls `FloatingLogController.start(app)` (controller stubbed/no-op until Task 5–6)

**Note:** JVM unit tests cannot construct `Application`. Structure as:

```kotlin
object LogDoy {
    internal val buffer = LogBuffer()
    @Volatile internal var initialized: Boolean = false
    @Volatile private var started: Boolean = false
    internal var starter: ((android.app.Application) -> Unit)? = null

    fun init(app: android.app.Application) {
        if (started) return
        started = true
        initialized = true
        starter?.invoke(app)
    }

    fun log(message: String) = log(tag = null, message = message)

    fun log(tag: String, message: String) = log(tag = tag as String?, message = message)

    private fun log(tag: String?, message: String) {
        buffer.add(LogEntry(System.currentTimeMillis(), tag, message))
    }
}
```

Use a single private `log(tag: String?, message: String)` to avoid overload ambiguity; public API remains as in the spec (`log(message)` and `log(tag, message)`).

- [ ] **Step 1: Write failing buffering tests**

```kotlin
package com.example.logdoy.lib

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class LogDoyBufferingTest {
    @Before
    fun reset() {
        // Reset singleton state between tests via internal API
        LogDoy.initialized = false
        LogDoy.resetForTest()
    }

    @Test
    fun log_beforeInit_stillBuffers() {
        LogDoy.log("early")
        LogDoy.log("Net", "ok")
        assertFalse(LogDoy.initialized)
        val snap = LogDoy.buffer.snapshot()
        assertEquals(2, snap.size)
        assertEquals("early", snap[0].message)
        assertEquals("Net", snap[1].tag)
        assertEquals("ok", snap[1].message)
    }

    @Test
    fun log_afterFlagInit_stillAppends() {
        LogDoy.initialized = true
        LogDoy.log("later")
        assertEquals(1, LogDoy.buffer.size())
        assertTrue(LogDoy.buffer.snapshot().last().message == "later")
    }
}
```

Add `internal fun resetForTest()` on `LogDoy` that clears buffer observers, replaces buffer contents by recreating, and resets `started`/`initialized`/`starter` — only for tests.

Implementation detail for reset (in `LogDoy.kt`):

```kotlin
@VisibleForTesting
internal fun resetForTest() {
    started = false
    initialized = false
    starter = null
    // Recreate buffer: keep same reference by draining via capacity trick —
    // simplest: hold `var bufferRef` and reassign. Prefer:
}
```

Prefer this structure so tests work:

```kotlin
object LogDoy {
    @Volatile
    private var bufferRef = LogBuffer()
    internal val buffer: LogBuffer get() = bufferRef
    ...
    internal fun resetForTest() {
        started = false
        initialized = false
        starter = null
        bufferRef = LogBuffer()
    }
}
```

Use `androidx.annotation.VisibleForTesting` or omit annotation and keep `internal`.

- [ ] **Step 2: Run test — expect FAIL**

Run: `./gradlew :logdoy:testDebugUnitTest --tests com.example.logdoy.lib.LogDoyBufferingTest`

Expected: FAIL (LogDoy missing)

- [ ] **Step 3: Implement `LogDoy.kt`**

```kotlin
package com.example.logdoy.lib

import android.app.Application

object LogDoy {
    @Volatile
    private var bufferRef = LogBuffer()
    internal val buffer: LogBuffer get() = bufferRef

    @Volatile
    internal var initialized: Boolean = false

    @Volatile
    private var started: Boolean = false

    /** Set by library internals before/during first real init wiring (Task 6). */
    @Volatile
    internal var starter: ((Application) -> Unit)? = null

    fun init(app: Application) {
        if (started) return
        started = true
        initialized = true
        starter?.invoke(app)
    }

    fun log(message: String) {
        append(tag = null, message = message)
    }

    fun log(tag: String, message: String) {
        append(tag = tag, message = message)
    }

    private fun append(tag: String?, message: String) {
        bufferRef.add(LogEntry(System.currentTimeMillis(), tag, message))
    }

    internal fun resetForTest() {
        started = false
        initialized = false
        starter = null
        bufferRef = LogBuffer()
    }
}
```

- [ ] **Step 4: Run tests — expect PASS**

Run: `./gradlew :logdoy:testDebugUnitTest --tests "com.example.logdoy.lib.*"`

Expected: all PASS

- [ ] **Step 5: Commit**

```bash
git add logdoy/src/main/java/com/example/logdoy/lib/LogDoy.kt \
  logdoy/src/test/java/com/example/logdoy/lib/LogDoyBufferingTest.kt
git commit -m "feat: add LogDoy facade with pre-init log buffering"
```

---

### Task 4: Floating UI layouts + `FloatingLogView`

**Files:**
- Create: `logdoy/src/main/res/layout/logdoy_floating_root.xml`
- Create: `logdoy/src/main/res/layout/logdoy_panel.xml`
- Create: `logdoy/src/main/res/layout/logdoy_log_item.xml`
- Create: `logdoy/src/main/res/drawable/logdoy_panel_bg.xml`
- Create: `logdoy/src/main/res/drawable/logdoy_bubble_bg.xml`
- Create: `logdoy/src/main/res/values/strings.xml`
- Create: `logdoy/src/main/java/com/example/logdoy/lib/LogListAdapter.kt`
- Create: `logdoy/src/main/java/com/example/logdoy/lib/FloatingLogView.kt`

**Interfaces:**
- Consumes: `LogEntry`, `LogBuffer.snapshot` / incremental entries
- Produces: `class FloatingLogView(context: Context) : FrameLayout` with:
  - `fun bind(entries: List<LogEntry>)`
  - `fun append(entry: LogEntry)`
  - `fun applyState(expanded: Boolean, x: Float, y: Float)`
  - `var onStateChanged: ((expanded: Boolean, x: Float, y: Float) -> Unit)?`
  - Default size: width = parentWidth/2, height = parentHeight/2; default position bottom-end with margin

- [ ] **Step 1: Add drawables and strings**

`logdoy_panel_bg.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="rectangle">
    <solid android:color="#CC1A1A1A" />
    <corners android:radius="8dp" />
</shape>
```

`logdoy_bubble_bg.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<shape xmlns:android="http://schemas.android.com/apk/res/android"
    android:shape="oval">
    <solid android:color="#E61A1A1A" />
</shape>
```

`strings.xml`:

```xml
<?xml version="1.0" encoding="utf-8"?>
<resources>
    <string name="logdoy_title">LogDoy</string>
    <string name="logdoy_minimize">—</string>
    <string name="logdoy_bubble_label">L</string>
</resources>
```

- [ ] **Step 2: Add layouts**

`logdoy_log_item.xml`: single `TextView` (`@+id/logdoy_item_text`), `12sp`, white, padding `4dp`, `fontFamily=monospace`.

`logdoy_panel.xml`: vertical `LinearLayout` with:
- header `LinearLayout` horizontal: title `TextView` (`logdoy_title_view`, weight 1) + minimize `TextView`/`ImageButton` (`logdoy_minimize`)
- `RecyclerView` (`logdoy_list`) weight 1

`logdoy_floating_root.xml`: `FrameLayout` match_parent containing:
- include/merge panel container `FrameLayout` (`logdoy_panel_container`) that inflates panel
- bubble `TextView` (`logdoy_bubble`) 48dp, gravity center, bg bubble, initially `gone`

- [ ] **Step 3: Implement `LogListAdapter`**

`RecyclerView.Adapter` holding `MutableList<LogEntry>`; `submit(List)`, `append(LogEntry)`; bind formats:

```kotlin
val time = SimpleDateFormat("HH:mm:ss.SSS", Locale.getDefault()).format(Date(entry.timestampMs))
val text = if (entry.tag.isNullOrEmpty()) "$time  ${entry.message}" else "$time  [${entry.tag}] ${entry.message}"
```

- [ ] **Step 4: Implement `FloatingLogView`**

Behavior requirements in code:
1. Inflate `logdoy_floating_root` into self (`LayoutParams.MATCH_PARENT` for overlay root).
2. Panel container sized to `parentWidth/2` × `parentHeight/2` after layout (use `ViewTreeObserver` or `onSizeChanged` of parent); default `translationX/Y` so panel sits bottom-end with 16dp margin.
3. Header touch: track `ACTION_DOWN`/`MOVE` to drag panel; on `UP` clamp translation so panel stays fully inside parent bounds; invoke `onStateChanged(true, x, y)`.
4. Minimize click: hide panel, show bubble at same approximate corner; `onStateChanged(false, x, y)`.
5. Bubble: drag same as panel; click (if not dragged beyond touch slop) expands panel; `onStateChanged(true, x, y)`.
6. `bind`/`append` update adapter; after append `scrollToPosition(last)`.

Use touch slop from `ViewConfiguration.get(context).scaledTouchSlop` to distinguish click vs drag on bubble.

- [ ] **Step 5: Compile library**

Run: `./gradlew :logdoy:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add logdoy/src/main/res logdoy/src/main/java/com/example/logdoy/lib/FloatingLogView.kt \
  logdoy/src/main/java/com/example/logdoy/lib/LogListAdapter.kt
git commit -m "feat: add FloatingLogView panel and bubble UI"
```

---

### Task 5: `FloatingLogController` lifecycle attach

**Files:**
- Create: `logdoy/src/main/java/com/example/logdoy/lib/FloatingLogController.kt`

**Interfaces:**
- Consumes: `LogDoy.buffer`, `FloatingLogView`
- Produces:
  - `object FloatingLogController` with `fun start(app: Application)`
  - Registers `Application.ActivityLifecycleCallbacks`
  - State: `expanded: Boolean = true`, `posX: Float`, `posY: Float` (NaN = use view default)
  - `onActivityResumed`: if not already attached to this activity, create/reuse view, `addView` to `(activity.window.decorView as ViewGroup)`, `bind(buffer.snapshot())`, `applyState`, subscribe observer that posts to main `Handler(Looper.getMainLooper())` and `append`
  - `onActivityPaused`: remove observer from buffer for that view, `removeView`, keep state from `onStateChanged`
  - Idempotent `start`: only register callbacks once

- [ ] **Step 1: Implement controller**

```kotlin
package com.example.logdoy.lib

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.ViewGroup
import java.lang.ref.WeakReference

object FloatingLogController {
    private val mainHandler = Handler(Looper.getMainLooper())
    @Volatile private var registered = false
    private var expanded: Boolean = true
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
        view.bind(LogDoy.buffer.snapshot())
        view.applyState(expanded, posX, posY)
        val obs: (LogEntry) -> Unit = { entry ->
            mainHandler.post {
                floatingView?.append(entry)
            }
        }
        observer = obs
        LogDoy.buffer.addObserver(obs)
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
```

Ensure `FloatingLogView` does not block touches outside panel/bubble: override `onInterceptTouchEvent`/`dispatchTouchEvent` so only panel/bubble consume events; empty areas return false / call `super` only when hitting children. Pattern: root `FrameLayout` with `clickable=false`; in `onTouchEvent` return false; children handle their own touches. Optionally override:

```kotlin
override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false
```

And set root `importantForAccessibility` as needed. Critical: host UI under the overlay must remain interactive.

- [ ] **Step 2: Compile**

Run: `./gradlew :logdoy:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 3: Commit**

```bash
git add logdoy/src/main/java/com/example/logdoy/lib/FloatingLogController.kt \
  logdoy/src/main/java/com/example/logdoy/lib/FloatingLogView.kt
git commit -m "feat: attach floating log view across activity lifecycle"
```

---

### Task 6: Wire `LogDoy.init` → controller

**Files:**
- Modify: `logdoy/src/main/java/com/example/logdoy/lib/LogDoy.kt`

**Interfaces:**
- Consumes: `FloatingLogController.start`
- Produces: real `init` starts controller; default `starter` assigned in `init` block of object

- [ ] **Step 1: Wire starter**

In `LogDoy` object init / property default:

```kotlin
init {
    starter = { app -> FloatingLogController.start(app) }
}
```

Keep `resetForTest()` setting `starter = null` then re-assign in tests if needed; after `resetForTest`, tests that don't call real `init` stay fine. Update `resetForTest` to restore default starter:

```kotlin
internal fun resetForTest() {
    started = false
    initialized = false
    bufferRef = LogBuffer()
    starter = { app -> FloatingLogController.start(app) }
}
```

- [ ] **Step 2: Re-run unit tests**

Run: `./gradlew :logdoy:testDebugUnitTest`

Expected: PASS (buffering tests never invoke Application)

- [ ] **Step 3: Commit**

```bash
git add logdoy/src/main/java/com/example/logdoy/lib/LogDoy.kt
git commit -m "feat: LogDoy.init starts floating log controller"
```

---

### Task 7: Demo app integration

**Files:**
- Create: `app/src/main/java/com/example/logdoy/DemoApp.kt`
- Create: `app/src/main/java/com/example/logdoy/SecondActivity.kt`
- Create: `app/src/main/res/layout/activity_second.xml`
- Modify: `app/src/main/AndroidManifest.xml`
- Modify: `app/src/main/java/com/example/logdoy/MainActivity.kt`
- Modify: `app/src/main/res/layout/activity_main.xml`
- Modify: `app/src/main/res/values/strings.xml` (add button labels if needed)

**Interfaces:**
- Consumes: `com.example.logdoy.lib.LogDoy`
- Produces: runnable demo that shows floating panel after launch

- [ ] **Step 1: Create `DemoApp`**

```kotlin
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
```

- [ ] **Step 2: Register Application + SecondActivity in manifest**

On `<application>` add `android:name=".DemoApp"`.

Add:

```xml
<activity
    android:name=".SecondActivity"
    android:exported="false" />
```

- [ ] **Step 3: Update `activity_main.xml`**

Replace Hello World with:
- `Button` `@+id/btn_log` text “写一条日志”
- `Button` `@+id/btn_second` text “打开第二页”

Both centered vertically stacked.

- [ ] **Step 4: Update `MainActivity`**

```kotlin
findViewById<Button>(R.id.btn_log).setOnClickListener {
    LogDoy.log("UI", "clicked at ${System.currentTimeMillis()}")
}
findViewById<Button>(R.id.btn_second).setOnClickListener {
    startActivity(Intent(this, SecondActivity::class.java))
}
```

Keep existing edge-to-edge padding logic if present.

- [ ] **Step 5: Add `SecondActivity` + layout**

Simple layout with `TextView` “第二页 — 悬浮窗应仍在” and `Button` finish.

In `onCreate`, `LogDoy.log("Second", "opened")`.

- [ ] **Step 6: Assemble app**

Run: `./gradlew :app:assembleDebug`

Expected: BUILD SUCCESSFUL

- [ ] **Step 7: Commit**

```bash
git add app/
git commit -m "feat: demo app initializes LogDoy and exercises logging"
```

---

### Task 8: Manual verification checklist

**Files:** none (verification only)

- [ ] **Step 1: Install and launch**

Run: `./gradlew :app:installDebug` (device/emulator available)

- [ ] **Step 2: Verify checklist**

- [ ] Launch shows floating panel (~1/4 screen) with buffered `before-init-should-buffer` and `initialized` lines
- [ ] Tap “写一条日志” appends lines in real time; auto-scroll to bottom
- [ ] Drag panel by title; stays on screen
- [ ] Minimize → bubble; drag bubble; tap bubble → expand
- [ ] Open second page: panel/bubble reappears with same logs and state
- [ ] Host buttons remain clickable outside panel (touch passthrough)

- [ ] **Step 3: Final commit if any polish fixes were needed**

```bash
git add -u
git commit -m "fix: polish floating log touch and layout behavior"
```

(Skip empty commit if nothing changed.)

---

## Self-Review

1. **Spec coverage:** init+log API, pre-init buffer, in-app DecorView overlay, 1/4 size, drag, minimize bubble, realtime updates, ring 500, demo — all mapped to Tasks 1–8. No SYSTEM_ALERT_WINDOW / Compose / show-hide-clear.
2. **Placeholders:** none intentionally left; layout XML described with ids and structure for implementer to write fully in Task 4.
3. **Type consistency:** `LogEntry`, `LogBuffer`, `LogDoy.buffer`, `FloatingLogController.start(Application)`, `FloatingLogView.bind/append/applyState/onStateChanged` used consistently across tasks.
