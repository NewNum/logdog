package com.uxnhe.logdog

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.FrameLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

internal class FloatingLogView(context: Context) : FrameLayout(context) {

    var onStateChanged: ((expanded: Boolean, x: Float, y: Float) -> Unit)? = null
    var onPanelSizeChanged: ((width: Int, height: Int) -> Unit)? = null

    private val panelContainer: FrameLayout
    private val clearButton: View
    private val minimizeButton: View
    private val bubble: TextView
    private val logList: RecyclerView
    private val adapter = LogListAdapter()
    private var clearConfirmDialog: AlertDialog? = null

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val density = resources.displayMetrics.density
    private val marginPx = (16 * density).toInt()
    private val bubbleSizePx = (48 * density).toInt()
    private val keepTouchablePx = bubbleSizePx
    private val borderDragPx = (28 * density).toInt()
    private val handleLenPx = (40 * density).toInt()
    private val handleHitPx = (28 * density).toInt()
    private val minPanelPx = (120 * density).toInt()
    private val locationScratch = IntArray(2)

    private var expanded = false
    private var defaultPositionApplied = false
    private var pendingApplyState: Pair<Boolean, Pair<Float, Float>>? = null
    private var storedPanelWidth = 0
    private var storedPanelHeight = 0

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartTranslationX = 0f
    private var dragStartTranslationY = 0f
    private var dragStartPanelWidth = 0
    private var dragStartPanelHeight = 0
    private var bubbleDragMoved = false
    private var bubbleTracking = false
    private var panelDragging = false
    private var panelResizing = false
    private var ignorePanelDrag = false
    private var activeResizeHandle: ResizeHandle? = null

    private enum class ResizeHandle {
        BOTTOM_LEFT_UP,
        BOTTOM_LEFT_RIGHT,
        BOTTOM_RIGHT_UP,
        BOTTOM_RIGHT_LEFT,
    }

    private val bubbleTouchListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = bubble.translationX
                dragStartTranslationY = bubble.translationY
                bubbleDragMoved = false
                bubbleTracking = true
                true
            }
            MotionEvent.ACTION_MOVE -> {
                if (!bubbleTracking) return@OnTouchListener false
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
                if (!bubbleTracking) return@OnTouchListener false
                bubbleTracking = false
                if (!bubbleDragMoved) {
                    expandFromBubble()
                } else {
                    clampTranslation(bubble)
                    onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (!bubbleTracking) return@OnTouchListener false
                bubbleTracking = false
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
        clearButton = findViewById(R.id.logdoy_clear)
        minimizeButton = findViewById(R.id.logdoy_minimize)
        bubble = findViewById(R.id.logdoy_bubble)
        logList = findViewById(R.id.logdoy_list)

        logList.layoutManager = LinearLayoutManager(context)
        logList.adapter = adapter

        clearButton.setOnClickListener { confirmClearLogs() }
        minimizeButton.setOnClickListener { minimize() }
        bubble.setOnTouchListener(bubbleTouchListener)

        panelContainer.layoutParams = LayoutParams(0, 0)
        panelContainer.visibility = GONE
        bubble.visibility = VISIBLE
        post { ensurePanelSizeAndDefaultPlacement() }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        post { ensurePanelSizeAndDefaultPlacement() }
    }

    fun setPanelSize(widthPx: Int, heightPx: Int) {
        if (widthPx > 0 && heightPx > 0) {
            storedPanelWidth = widthPx
            storedPanelHeight = heightPx
            if (this.width > 0 && this.height > 0) {
                applyStoredPanelSize()
            }
        }
    }

    private fun ensurePanelSizeAndDefaultPlacement() {
        if (width <= 0 || height <= 0) return
        ensureDefaultPanelSize()
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
        ensureDefaultPanelSize()
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

    override fun requestDisallowInterceptTouchEvent(disallowIntercept: Boolean) {
        if (panelDragging || panelResizing || !ignorePanelDrag) return
        super.requestDisallowInterceptTouchEvent(disallowIntercept)
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
        if (panelContainer.visibility != VISIBLE) return false
        return when (ev.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                if (isTouchInside(minimizeButton, ev) || isTouchInside(clearButton, ev)) {
                    ignorePanelDrag = true
                    panelDragging = false
                    panelResizing = false
                    activeResizeHandle = null
                    false
                } else {
                    val resizeHandle = hitResizeHandle(ev)
                    if (resizeHandle != null) {
                        ignorePanelDrag = false
                        panelDragging = false
                        panelResizing = true
                        activeResizeHandle = resizeHandle
                        rememberPanelDragStart(ev)
                        true
                    } else {
                        val onBorder = isTouchOnPanelBorder(ev)
                        ignorePanelDrag = !onBorder
                        panelDragging = false
                        panelResizing = false
                        activeResizeHandle = null
                        if (ignorePanelDrag) {
                            false
                        } else {
                            rememberPanelDragStart(ev)
                            panelDragging = true
                            true
                        }
                    }
                }
            }
            MotionEvent.ACTION_MOVE -> panelDragging || panelResizing
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                val wasCapturing = panelDragging || panelResizing
                panelDragging = false
                panelResizing = false
                activeResizeHandle = null
                ignorePanelDrag = false
                wasCapturing
            }
            else -> false
        }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (panelContainer.visibility != VISIBLE) return false
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> return panelDragging || panelResizing
            MotionEvent.ACTION_MOVE -> {
                when {
                    panelResizing -> {
                        applyResize(event)
                        return true
                    }
                    panelDragging -> {
                        panelContainer.translationX =
                            dragStartTranslationX + (event.rawX - dragStartRawX)
                        panelContainer.translationY =
                            dragStartTranslationY + (event.rawY - dragStartRawY)
                        return true
                    }
                    else -> return false
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!panelDragging && !panelResizing) return false
                clampTranslation(panelContainer)
                onStateChanged?.invoke(
                    true,
                    panelContainer.translationX,
                    panelContainer.translationY,
                )
                if (panelResizing) {
                    onPanelSizeChanged?.invoke(storedPanelWidth, storedPanelHeight)
                }
                panelDragging = false
                panelResizing = false
                activeResizeHandle = null
                ignorePanelDrag = false
                return true
            }
        }
        return panelDragging || panelResizing
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        if (panelDragging || panelResizing || bubbleTracking) {
            return super.dispatchTouchEvent(ev)
        }
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
            ensureDefaultPanelSize()
            clampStoredPanelSize()
            applyStoredPanelSize()
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

    private fun rememberPanelDragStart(ev: MotionEvent) {
        dragStartRawX = ev.rawX
        dragStartRawY = ev.rawY
        dragStartTranslationX = panelContainer.translationX
        dragStartTranslationY = panelContainer.translationY
        dragStartPanelWidth = panelWidthPx()
        dragStartPanelHeight = panelHeightPx()
    }

    private fun applyResize(event: MotionEvent) {
        val handle = activeResizeHandle ?: return
        val dx = event.rawX - dragStartRawX
        val dy = event.rawY - dragStartRawY
        var newW = dragStartPanelWidth
        var newH = dragStartPanelHeight
        var newX = dragStartTranslationX
        val newY = dragStartTranslationY

        when (handle) {
            ResizeHandle.BOTTOM_LEFT_UP -> {
                newW = (dragStartPanelWidth - dx).toInt()
                newX = dragStartTranslationX + dx
            }
            ResizeHandle.BOTTOM_RIGHT_UP -> {
                newW = (dragStartPanelWidth + dx).toInt()
            }
            ResizeHandle.BOTTOM_LEFT_RIGHT,
            ResizeHandle.BOTTOM_RIGHT_LEFT,
            -> {
                newH = (dragStartPanelHeight + dy).toInt()
            }
        }

        val maxW = width.coerceAtLeast(minPanelPx)
        val maxH = height.coerceAtLeast(minPanelPx)
        newW = newW.coerceIn(minPanelPx, maxW)
        newH = newH.coerceIn(minPanelPx, maxH)

        // Keep the opposite edge stable when clamping width from the left.
        if (handle == ResizeHandle.BOTTOM_LEFT_UP) {
            val right = dragStartTranslationX + dragStartPanelWidth
            newX = right - newW
        }

        storedPanelWidth = newW
        storedPanelHeight = newH
        applyStoredPanelSize()
        panelContainer.translationX = newX
        panelContainer.translationY = newY
        clampTranslation(panelContainer)
    }

    private fun ensureDefaultPanelSize() {
        if (width <= 0 || height <= 0) return
        if (storedPanelWidth <= 0 || storedPanelHeight <= 0) {
            storedPanelWidth = width / 2
            storedPanelHeight = height / 2
        }
        clampStoredPanelSize()
        applyStoredPanelSize()
    }

    private fun clampStoredPanelSize() {
        if (width <= 0 || height <= 0) return
        storedPanelWidth = storedPanelWidth.coerceIn(minPanelPx, width.coerceAtLeast(minPanelPx))
        storedPanelHeight = storedPanelHeight.coerceIn(minPanelPx, height.coerceAtLeast(minPanelPx))
    }

    private fun applyStoredPanelSize() {
        val lp = panelContainer.layoutParams as LayoutParams
        if (lp.width != storedPanelWidth || lp.height != storedPanelHeight) {
            lp.width = storedPanelWidth
            lp.height = storedPanelHeight
            panelContainer.layoutParams = lp
        }
    }

    private fun panelWidthPx(): Int =
        if (panelContainer.width > 0) panelContainer.width
        else if (storedPanelWidth > 0) storedPanelWidth
        else width / 2

    private fun panelHeightPx(): Int =
        if (panelContainer.height > 0) panelContainer.height
        else if (storedPanelHeight > 0) storedPanelHeight
        else height / 2

    private fun placePanelDefault() {
        ensureDefaultPanelSize()
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
        ensureDefaultPanelSize()
        panelContainer.translationX = bubble.translationX + bubbleSizePx - panelWidthPx()
        panelContainer.translationY = bubble.translationY + bubbleSizePx - panelHeightPx()
        clampTranslation(panelContainer)
        panelContainer.visibility = VISIBLE
        bubble.visibility = GONE
        expanded = true
        onStateChanged?.invoke(true, panelContainer.translationX, panelContainer.translationY)
    }

    override fun onDetachedFromWindow() {
        clearConfirmDialog?.dismiss()
        clearConfirmDialog = null
        super.onDetachedFromWindow()
    }

    private fun confirmClearLogs() {
        if (clearConfirmDialog?.isShowing == true) return
        clearConfirmDialog = AlertDialog.Builder(context)
            .setTitle(R.string.logdoy_clear_confirm_title)
            .setMessage(R.string.logdoy_clear_confirm_message)
            .setPositiveButton(R.string.logdoy_clear_confirm_positive) { _, _ -> clearLogs() }
            .setNegativeButton(R.string.logdoy_clear_confirm_negative, null)
            .setOnDismissListener { clearConfirmDialog = null }
            .show()
    }

    private fun clearLogs() {
        LogDoy.buffer.clear()
        adapter.submit(emptyList())
    }

    private fun minimize() {
        clearConfirmDialog?.dismiss()
        clearConfirmDialog = null
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
        val minX = (keepX - viewW).toFloat()
        val maxX = (width - keepX).toFloat()
        val minY = (keepY - viewH).toFloat()
        val maxY = (height - keepY).toFloat()
        view.translationX = view.translationX.coerceIn(minX, maxX)
        view.translationY = view.translationY.coerceIn(minY, maxY)
    }

    private fun hitResizeHandle(event: MotionEvent): ResizeHandle? {
        if (!isTouchInside(panelContainer, event)) return null
        panelContainer.getLocationOnScreen(locationScratch)
        val x = event.rawX - locationScratch[0]
        val y = event.rawY - locationScratch[1]
        val w = panelContainer.width.toFloat()
        val h = panelContainer.height.toFloat()
        if (w <= 0f || h <= 0f) return null

        val hit = handleHitPx.toFloat()
        val len = handleLenPx.toFloat().coerceAtMost(minOf(w, h) / 2f)
        val nearLeft = x <= hit
        val nearRight = x >= w - hit
        val nearBottom = y >= h - hit
        val inBottomLen = y >= h - len - hit
        val inLeftLen = x <= len + hit
        val inRightLen = x >= w - len - hit

        // Prefer corner segments; vertical vs horizontal by closer axis.
        if (nearLeft && inBottomLen && nearBottom && inLeftLen) {
            val distV = x
            val distH = h - y
            return if (distV <= distH) ResizeHandle.BOTTOM_LEFT_UP else ResizeHandle.BOTTOM_LEFT_RIGHT
        }
        if (nearLeft && inBottomLen) return ResizeHandle.BOTTOM_LEFT_UP
        if (nearBottom && inLeftLen) return ResizeHandle.BOTTOM_LEFT_RIGHT

        if (nearRight && inBottomLen && nearBottom && inRightLen) {
            val distV = w - x
            val distH = h - y
            return if (distV <= distH) ResizeHandle.BOTTOM_RIGHT_UP else ResizeHandle.BOTTOM_RIGHT_LEFT
        }
        if (nearRight && inBottomLen) return ResizeHandle.BOTTOM_RIGHT_UP
        if (nearBottom && inRightLen) return ResizeHandle.BOTTOM_RIGHT_LEFT

        return null
    }

    private fun isTouchOnPanelBorder(event: MotionEvent): Boolean {
        if (!isTouchInside(panelContainer, event)) return false
        // Resize grips own the bottom-corner border segments.
        if (hitResizeHandle(event) != null) return false
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
