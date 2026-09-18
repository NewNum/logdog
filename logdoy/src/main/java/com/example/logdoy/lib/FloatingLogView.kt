package com.example.logdoy.lib

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

internal class FloatingLogView(context: Context) : FrameLayout(context) {

    var onStateChanged: ((expanded: Boolean, x: Float, y: Float) -> Unit)? = null

    private val panelContainer: FrameLayout
    private val minimizeButton: View
    private val bubble: TextView
    private val logList: RecyclerView
    private val adapter = LogListAdapter()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val marginPx = (16 * resources.displayMetrics.density).toInt()
    private val bubbleSizePx = (48 * resources.displayMetrics.density).toInt()
    /** Minimum on-screen touchable strip so the panel/bubble can still be grabbed. */
    private val keepTouchablePx = bubbleSizePx
    /** Drag handle thickness — matches the visible border and stays easy to grab. */
    private val borderDragPx = (10 * resources.displayMetrics.density).toInt()
    private val locationScratch = IntArray(2)

    private var expanded = false
    private var defaultPositionApplied = false
    private var pendingApplyState: Pair<Boolean, Pair<Float, Float>>? = null

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartTranslationX = 0f
    private var dragStartTranslationY = 0f
    private var bubbleDragMoved = false
    private var panelDragging = false
    private var ignorePanelDrag = false

    private val bubbleTouchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = bubble.translationX
                dragStartTranslationY = bubble.translationY
                bubbleDragMoved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = event.rawX - dragStartRawX
                val dy = event.rawY - dragStartRawY
                if (abs(dx) > touchSlop || abs(dy) > touchSlop) {
                    bubbleDragMoved = true
                }
                bubble.translationX = dragStartTranslationX + dx
                bubble.translationY = dragStartTranslationY + dy
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!bubbleDragMoved) {
                    expandFromBubble()
                } else {
                    clampTranslation(bubble)
                    onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                clampTranslation(bubble)
                onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
                true
            }
            else -> false
        }
    }

    init {
        isClickable = false
        isFocusable = false
        LayoutInflater.from(context).inflate(R.layout.logdoy_floating_root, this, true)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        panelContainer = findViewById(R.id.logdoy_panel_container)
        minimizeButton = findViewById(R.id.logdoy_minimize)
        bubble = findViewById(R.id.logdoy_bubble)
        logList = findViewById(R.id.logdoy_list)

        logList.layoutManager = LinearLayoutManager(context)
        logList.adapter = adapter

        minimizeButton.setOnClickListener { minimize() }
        bubble.setOnTouchListener(bubbleTouchListener)

        // Lock panel size before first content measure so log appends cannot grow it.
        panelContainer.layoutParams = LayoutParams(0, 0)
        panelContainer.visibility = GONE
        bubble.visibility = VISIBLE
        post { ensurePanelSizeAndDefaultPlacement() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { ensurePanelSizeAndDefaultPlacement() }
    }

    private fun ensurePanelSizeAndDefaultPlacement() {
        if (width <= 0 || height <= 0) return
        sizePanelToHalfParent()
        pendingApplyState?.let { (exp, pos) ->
            pendingApplyState = null
            applyStateInternal(exp, pos.first, pos.second)
        } ?: run {
            if (!defaultPositionApplied) {
                defaultPositionApplied = true
                if (expanded) {
                    placePanelDefault()
                    clampTranslation(panelContainer)
                } else {
                    panelContainer.visibility = GONE
                    bubble.visibility = VISIBLE
                    placeBubbleDefault()
                    clampTranslation(bubble)
                }
            }
        }
    }

    fun bind(entries: List<LogEntry>) {
        adapter.submit(entries)
        if (entries.isNotEmpty()) {
            logList.scrollToPosition(entries.lastIndex)
        }
    }

    fun append(entry: LogEntry) {
        adapter.append(entry)
        logList.scrollToPosition(adapter.itemCount - 1)
    }

    fun applyState(expanded: Boolean, x: Float, y: Float) {
        this.expanded = expanded
        if (width <= 0 || height <= 0) {
            pendingApplyState = expanded to (x to y)
            return
        }
        sizePanelToHalfParent()
        applyStateInternal(expanded, x, y)
    }

    private fun applyStateInternal(expanded: Boolean, x: Float, y: Float) {
        this.expanded = expanded
        if (expanded) {
            bubble.visibility = GONE
            panelContainer.visibility = VISIBLE
            if (x.isNaN() || y.isNaN()) {
                placePanelDefault()
            } else {
                panelContainer.translationX = x
                panelContainer.translationY = y
            }
            clampTranslation(panelContainer)
        } else {
            panelContainer.visibility = GONE
            bubble.visibility = VISIBLE
            if (x.isNaN() || y.isNaN()) {
                placeBubbleDefault()
            } else {
                bubble.translationX = x
                bubble.translationY = y
            }
            clampTranslation(bubble)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (panelContainer.visibility != VISIBLE) return false
        when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val onBorder = isTouchOnPanelBorder(ev)
                ignorePanelDrag = !onBorder || isTouchInside(minimizeButton, ev)
                panelDragging = false
                if (ignorePanelDrag) return false
                dragStartRawX = ev.rawX
                dragStartRawY = ev.rawY
                dragStartTranslationX = panelContainer.translationX
                dragStartTranslationY = panelContainer.translationY
                return false
            }
            MotionEvent.ACTION_MOVE -> {
                if (ignorePanelDrag || panelDragging) return panelDragging
                val dx = abs(ev.rawX - dragStartRawX)
                val dy = abs(ev.rawY - dragStartRawY)
                if (dx > touchSlop || dy > touchSlop) {
                    panelDragging = true
                    parent?.requestDisallowInterceptTouchEvent(true)
                    return true
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                panelDragging = false
                ignorePanelDrag = false
            }
        }
        return false
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (panelContainer.visibility != VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                // Intercept may have already recorded; keep receiving follow-up events.
                return !ignorePanelDrag
            }
            MotionEvent.ACTION_MOVE -> {
                if (!panelDragging) return false
                panelContainer.translationX =
                    dragStartTranslationX + (event.rawX - dragStartRawX)
                panelContainer.translationY =
                    dragStartTranslationY + (event.rawY - dragStartRawY)
                return true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!panelDragging) return false
                clampTranslation(panelContainer)
                onStateChanged?.invoke(
                    true,
                    panelContainer.translationX,
                    panelContainer.translationY,
                )
                panelDragging = false
                ignorePanelDrag = false
                return true
            }
        }
        return panelDragging
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        val targetVisible = when {
            panelContainer.visibility == VISIBLE && isTouchInside(panelContainer, ev) -> true
            bubble.visibility == VISIBLE && isTouchInside(bubble, ev) -> true
            else -> false
        }
        if (!targetVisible) return false
        return super.dispatchTouchEvent(ev)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w <= 0 || h <= 0) return
        val parentSizeChanged = w != oldw || h != oldh
        if (parentSizeChanged) {
            sizePanelToHalfParent()
        }
        if (expanded) {
            clampTranslation(panelContainer)
            if (parentSizeChanged) {
                onStateChanged?.invoke(true, panelContainer.translationX, panelContainer.translationY)
            }
        } else {
            clampTranslation(bubble)
            if (parentSizeChanged) {
                onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
            }
        }
    }

    private fun sizePanelToHalfParent() {
        if (width <= 0 || height <= 0) return
        val lp = panelContainer.layoutParams as LayoutParams
        val targetW = width / 2
        val targetH = height / 2
        if (lp.width != targetW || lp.height != targetH) {
            lp.width = targetW
            lp.height = targetH
            panelContainer.layoutParams = lp
        }
    }

    private fun panelWidthPx(): Int =
        if (panelContainer.width > 0) panelContainer.width else width / 2

    private fun panelHeightPx(): Int =
        if (panelContainer.height > 0) panelContainer.height else height / 2

    private fun placePanelDefault() {
        sizePanelToHalfParent()
        val panelW = panelWidthPx()
        val panelH = panelHeightPx()
        panelContainer.translationX = (width - panelW - marginPx).toFloat().coerceAtLeast(0f)
        panelContainer.translationY = (height - panelH - marginPx).toFloat().coerceAtLeast(0f)
    }

    private fun placeBubbleDefault() {
        bubble.translationX = (width - bubbleSizePx - marginPx).toFloat().coerceAtLeast(0f)
        bubble.translationY = (height - bubbleSizePx - marginPx).toFloat().coerceAtLeast(0f)
    }

    private fun alignBubbleToPanelBottomEnd() {
        bubble.translationX = panelContainer.translationX + panelWidthPx() - bubbleSizePx
        bubble.translationY = panelContainer.translationY + panelHeightPx() - bubbleSizePx
    }

    private fun expandFromBubble() {
        pendingApplyState = null
        panelContainer.translationX = bubble.translationX + bubbleSizePx - panelWidthPx()
        panelContainer.translationY = bubble.translationY + bubbleSizePx - panelHeightPx()
        clampTranslation(panelContainer)
        panelContainer.visibility = VISIBLE
        bubble.visibility = GONE
        expanded = true
        onStateChanged?.invoke(true, panelContainer.translationX, panelContainer.translationY)
    }

    private fun minimize() {
        pendingApplyState = null
        alignBubbleToPanelBottomEnd()
        clampTranslation(bubble)
        panelContainer.visibility = GONE
        bubble.visibility = VISIBLE
        expanded = false
        onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
    }

    private fun clampTranslation(view: View) {
        if (width <= 0 || height <= 0) return
        val viewW = view.width.coerceAtLeast(1)
        val viewH = view.height.coerceAtLeast(1)
        val keepX = keepTouchablePx.coerceAtMost(viewW).coerceAtLeast(1)
        val keepY = keepTouchablePx.coerceAtMost(viewH).coerceAtLeast(1)
        // Allow hanging off-screen; only keep a touchable strip inside the parent.
        val minX = (keepX - viewW).toFloat()
        val maxX = (width - keepX).toFloat()
        val minY = (keepY - viewH).toFloat()
        val maxY = (height - keepY).toFloat()
        view.translationX = view.translationX.coerceIn(minX, maxX)
        view.translationY = view.translationY.coerceIn(minY, maxY)
    }

    private fun isTouchOnPanelBorder(event: MotionEvent): Boolean {
        if (!isTouchInside(panelContainer, event)) return false
        panelContainer.getLocationOnScreen(locationScratch)
        val localX = event.rawX - locationScratch[0]
        val localY = event.rawY - locationScratch[1]
        val w = panelContainer.width.toFloat()
        val h = panelContainer.height.toFloat()
        if (w <= 0f || h <= 0f) return false
        val border = borderDragPx.toFloat().coerceAtMost(minOf(w, h) / 3f)
        return localX < border ||
            localX >= w - border ||
            localY < border ||
            localY >= h - border
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        if (view.visibility != VISIBLE || view.width <= 0 || view.height <= 0) return false
        view.getLocationOnScreen(locationScratch)
        val x = event.rawX
        val y = event.rawY
        return x >= locationScratch[0] &&
            x < locationScratch[0] + view.width &&
            y >= locationScratch[1] &&
            y < locationScratch[1] + view.height
    }
}
