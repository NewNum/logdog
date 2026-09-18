package com.example.logdoy.lib

import android.content.Context
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.ViewTreeObserver
import android.widget.FrameLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs

class FloatingLogView(context: Context) : FrameLayout(context) {

    var onStateChanged: ((expanded: Boolean, x: Float, y: Float) -> Unit)? = null

    private val panelContainer: FrameLayout
    private val header: View
    private val bubble: TextView
    private val logList: RecyclerView
    private val adapter = LogListAdapter()

    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop
    private val marginPx = (16 * resources.displayMetrics.density).toInt()
    private val bubbleSizePx = (48 * resources.displayMetrics.density).toInt()

    private var expanded = true
    private var defaultPositionApplied = false
    private var pendingApplyState: Pair<Boolean, Pair<Float, Float>>? = null

    private var dragStartRawX = 0f
    private var dragStartRawY = 0f
    private var dragStartTranslationX = 0f
    private var dragStartTranslationY = 0f
    private var bubbleDragMoved = false

    init {
        isClickable = false
        isFocusable = false
        LayoutInflater.from(context).inflate(R.layout.logdoy_floating_root, this, true)
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)

        panelContainer = findViewById(R.id.logdoy_panel_container)
        header = findViewById(R.id.logdoy_header)
        bubble = findViewById(R.id.logdoy_bubble)
        logList = findViewById(R.id.logdoy_list)

        logList.layoutManager = LinearLayoutManager(context)
        logList.adapter = adapter

        findViewById<View>(R.id.logdoy_minimize).setOnClickListener { minimize() }

        viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
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
                        }
                    }
                }
                viewTreeObserver.removeOnGlobalLayoutListener(this)
            }
        })
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
        if (width <= 0 || height <= 0 || panelContainer.width <= 0) {
            pendingApplyState = expanded to (x to y)
            return
        }
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
                placeBubbleDefaultFromPanel()
            } else {
                bubble.translationX = x
                bubble.translationY = y
            }
            clampTranslation(bubble)
        }
    }

    override fun onInterceptTouchEvent(ev: MotionEvent): Boolean = false

    override fun onTouchEvent(event: MotionEvent): Boolean = false

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
        if (w > 0 && h > 0) {
            sizePanelToHalfParent()
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

    private fun placeBubbleDefaultFromPanel() {
        placePanelDefault()
        alignBubbleToPanelBottomEnd()
    }

    private fun alignBubbleToPanelBottomEnd() {
        bubble.translationX = panelContainer.translationX + panelWidthPx() - bubbleSizePx
        bubble.translationY = panelContainer.translationY + panelHeightPx() - bubbleSizePx
    }

    private fun expandFromBubble() {
        panelContainer.translationX = bubble.translationX + bubbleSizePx - panelWidthPx()
        panelContainer.translationY = bubble.translationY + bubbleSizePx - panelHeightPx()
        clampTranslation(panelContainer)
        panelContainer.visibility = VISIBLE
        bubble.visibility = GONE
        expanded = true
        onStateChanged?.invoke(true, panelContainer.translationX, panelContainer.translationY)
    }

    private fun minimize() {
        alignBubbleToPanelBottomEnd()
        clampTranslation(bubble)
        panelContainer.visibility = GONE
        bubble.visibility = VISIBLE
        expanded = false
        onStateChanged?.invoke(false, bubble.translationX, bubble.translationY)
    }

    private fun clampTranslation(view: View) {
        if (width <= 0 || height <= 0) return
        val maxX = (width - view.width).coerceAtLeast(0)
        val maxY = (height - view.height).coerceAtLeast(0)
        view.translationX = view.translationX.coerceIn(0f, maxX.toFloat())
        view.translationY = view.translationY.coerceIn(0f, maxY.toFloat())
    }

    private fun isTouchInside(view: View, event: MotionEvent): Boolean {
        if (view.visibility != VISIBLE) return false
        val x = event.x - (view.left + view.translationX)
        val y = event.y - (view.top + view.translationY)
        return x >= 0f && x <= view.width && y >= 0f && y <= view.height
    }

    private val panelDragListener = View.OnTouchListener { _, event ->
        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragStartRawX = event.rawX
                dragStartRawY = event.rawY
                dragStartTranslationX = panelContainer.translationX
                dragStartTranslationY = panelContainer.translationY
                true
            }
            MotionEvent.ACTION_MOVE -> {
                panelContainer.translationX =
                    dragStartTranslationX + (event.rawX - dragStartRawX)
                panelContainer.translationY =
                    dragStartTranslationY + (event.rawY - dragStartRawY)
                true
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                clampTranslation(panelContainer)
                onStateChanged?.invoke(true, panelContainer.translationX, panelContainer.translationY)
                true
            }
            else -> false
        }
    }

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
        header.setOnTouchListener(panelDragListener)
        bubble.setOnTouchListener(bubbleTouchListener)
    }
}
