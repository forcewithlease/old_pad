package com.oldpad.app.game.junqi

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.View
import kotlin.math.abs
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 军棋翻棋老人版：
 * - 大棋盘、大字号，只有“翻棋/走棋”两个动作
 * - 电脑自动走，玩家不用设置任何规则
 * - 先抢到对方军旗就赢
 */
class JunqiView(context: Context) : View(context) {

    private val game = JunqiGame()
    private val handler = Handler(Looper.getMainLooper())
    private var token = 0
    private var aiThinking = false

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#143A2E")
    }
    private val boardEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E6B52")
        style = Paint.Style.STROKE
    }
    private val emptyPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#0F2A20")
    }
    private val emptyEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#255442")
        style = Paint.Style.STROKE
    }
    private val hiddenPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2C3440")
    }
    private val hiddenEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#4A5A6E")
        style = Paint.Style.STROKE
    }
    private val redPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B03A2E")
    }
    private val redEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#E06B5A")
        style = Paint.Style.STROKE
    }
    private val bluePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#2E5F8A")
    }
    private val blueEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#6AA6D8")
        style = Paint.Style.STROKE
    }
    private val selectedPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC53D")
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }
    private val statusPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }
    private val overlayPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#CC000000")
    }
    private val overlayTextPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC53D")
        textAlign = Paint.Align.CENTER
        isFakeBoldText = true
    }

    private var cell = 0f
    private var boardLeft = 0f
    private var boardTop = 0f
    private var statusHeight = 0f
    private val cellRect = RectF()

    init {
        setBackgroundColor(Color.parseColor("#0E1116"))
        isClickable = true
    }

    fun restart() {
        token++
        handler.removeCallbacksAndMessages(null)
        aiThinking = false
        game.reset()
        invalidate()
    }

    override fun onDetachedFromWindow() {
        handler.removeCallbacksAndMessages(null)
        super.onDetachedFromWindow()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        statusHeight = min(h * 0.15f, dp(120f))
        val boardArea = h - statusHeight
        cell = min(w * 0.98f / JunqiGame.COLS, boardArea * 0.94f / JunqiGame.ROWS)
        val boardWidth = cell * JunqiGame.COLS
        val boardHeight = cell * JunqiGame.ROWS
        boardLeft = (w - boardWidth) / 2f
        boardTop = statusHeight + (boardArea - boardHeight) / 2f

        // 状态文字
        statusPaint.textSize = min(statusHeight * 0.34f, w / 26f).coerceAtLeast(dp(18f))
        val status = if (aiThinking) "电脑思考中…" else game.message
        canvas.drawText(status, w / 2f, statusHeight * 0.56f, statusPaint)

        // 棋盘
        val boardRect = RectF(
            boardLeft - cell * 0.12f,
            boardTop - cell * 0.12f,
            boardLeft + boardWidth + cell * 0.12f,
            boardTop + boardHeight + cell * 0.12f
        )
        canvas.drawRoundRect(boardRect, cell * 0.22f, cell * 0.22f, boardPaint)
        boardEdgePaint.strokeWidth = cell * 0.04f
        canvas.drawRoundRect(boardRect, cell * 0.22f, cell * 0.22f, boardEdgePaint)

        for (index in 0 until JunqiGame.SIZE) {
            val row = index / JunqiGame.COLS
            val col = index % JunqiGame.COLS
            val left = boardLeft + col * cell
            val top = boardTop + row * cell
            val gap = cell * 0.055f
            cellRect.set(left + gap, top + gap, left + cell - gap, top + cell - gap)
            val piece = game.cells[index]

            when {
                piece == null -> {
                    canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, emptyPaint)
                    emptyEdgePaint.strokeWidth = cell * 0.015f
                    canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, emptyEdgePaint)
                }

                piece.hidden -> {
                    canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, hiddenPaint)
                    hiddenEdgePaint.strokeWidth = cell * 0.03f
                    canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, hiddenEdgePaint)
                    textPaint.color = Color.parseColor("#C3CEDC")
                    textPaint.textSize = cell * 0.44f
                    canvas.drawText(
                        "?", cellRect.centerX(),
                        cellRect.centerY() + textPaint.textSize * 0.35f, textPaint
                    )
                    textPaint.color = Color.WHITE
                }

                else -> {
                    val isRed = piece.side == JunqiGame.Side.RED
                    canvas.drawRoundRect(
                        cellRect,
                        cell * 0.12f,
                        cell * 0.12f,
                        if (isRed) redPaint else bluePaint
                    )
                    val edge = if (isRed) redEdgePaint else blueEdgePaint
                    edge.strokeWidth = cell * 0.03f
                    canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, edge)

                    textPaint.color = Color.WHITE
                    textPaint.textSize = cell * 0.27f
                    canvas.drawText(
                        piece.rank.label,
                        cellRect.centerX(),
                        cellRect.centerY() + textPaint.textSize * 0.35f,
                        textPaint
                    )
                }
            }

            if (index == game.selected) {
                selectedPaint.strokeWidth = cell * 0.06f
                canvas.drawRoundRect(cellRect, cell * 0.12f, cell * 0.12f, selectedPaint)
            }
        }

        if (game.winner != JunqiGame.Side.NONE) {
            val centerX = w / 2f
            val centerY = h / 2f
            val boxWidth = min(w * 0.8f, cell * 5f)
            val boxHeight = cell * 1.6f
            val box = RectF(
                centerX - boxWidth / 2f,
                centerY - boxHeight / 2f,
                centerX + boxWidth / 2f,
                centerY + boxHeight / 2f
            )
            canvas.drawRoundRect(box, cell * 0.2f, cell * 0.2f, overlayPaint)
            overlayTextPaint.textSize = min(cell * 0.5f, w / 10f)
            val text = if (game.winner == game.playerSide) "你赢了！" else "电脑赢了"
            canvas.drawText(
                text, centerX,
                centerY + overlayTextPaint.textSize * 0.2f,
                overlayTextPaint
            )
            overlayTextPaint.textSize = min(cell * 0.22f, w / 24f)
            canvas.drawText(
                "点右上角“重开”再来一局", centerX,
                centerY + cell * 0.55f,
                overlayTextPaint
            )
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (game.winner != JunqiGame.Side.NONE || aiThinking) return true
        if (!game.isPlayerTurn()) return true
        if (cell <= 0f) return true

        val col = ((event.x - boardLeft) / cell).toInt()
        val row = ((event.y - boardTop) / cell).toInt()
        if (col !in 0 until JunqiGame.COLS || row !in 0 until JunqiGame.ROWS) return true
        val index = row * JunqiGame.COLS + col

        val piece = game.cells[index]
        if (piece != null && piece.hidden) {
            if (game.flip(index)) {
                invalidate()
                scheduleAi()
            }
            return true
        }

        if (piece != null && piece.side == game.playerSide &&
            piece.rank != JunqiGame.Rank.MINE && piece.rank != JunqiGame.Rank.FLAG
        ) {
            if (game.selected == index) {
                game.clearSelection()
                invalidate()
            } else {
                game.select(index)
                invalidate()
            }
            return true
        }

        val selected = game.selected
        if (selected >= 0 && game.isAdjacent(selected, index)) {
            if (game.move(selected, index)) {
                invalidate()
                scheduleAi()
            } else {
                game.clearSelection()
                invalidate()
            }
            return true
        }

        game.clearSelection()
        invalidate()
        return true
    }

    private fun scheduleAi() {
        if (game.winner != JunqiGame.Side.NONE) return
        if (game.turn == game.playerSide) return
        aiThinking = true
        invalidate()
        val current = token
        handler.postDelayed({
            if (current != token) return@postDelayed
            if (game.winner == JunqiGame.Side.NONE && game.turn != game.playerSide) {
                game.aiAct()
            }
            aiThinking = false
            invalidate()
        }, 520)
    }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
