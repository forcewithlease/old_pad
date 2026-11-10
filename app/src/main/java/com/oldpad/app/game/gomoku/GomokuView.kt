package com.oldpad.app.game.gomoku

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
 * 五子棋：老人执黑先手，电脑执白。
 * 规则简单、按钮只有一个“重开一局”，适合触摸屏。
 */
class GomokuView(context: Context) : View(context) {

    companion object {
        const val SIZE = 15
        private const val EMPTY = 0
        private const val PLAYER = 1
        private const val AI = 2
    }

    private val board = IntArray(SIZE * SIZE) { EMPTY }
    private val handler = Handler(Looper.getMainLooper())

    private val boardPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#E8C48A") }
    private val boardEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#B98A4B")
        style = Paint.Style.STROKE
    }
    private val linePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#8A5A2B")
        style = Paint.Style.STROKE
    }
    private val blackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#1B1B1B") }
    private val blackHiPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#4A4A4A") }
    private val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.parseColor("#FAFAFA") }
    private val whiteEdgePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#9A9A9A")
        style = Paint.Style.STROKE
    }
    private val lastPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.parseColor("#FFC53D")
        style = Paint.Style.STROKE
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.WHITE
        textAlign = Paint.Align.CENTER
    }

    private var cell = 0f
    private var originX = 0f
    private var originY = 0f

    private var gameOver = false
    private var aiThinking = false
    private var lastMove = -1
    private var gameToken = 0
    private var status = "你先下，点棋盘放黑子"

    init {
        setBackgroundColor(Color.parseColor("#0E1116"))
        isClickable = true
    }

    fun restart() {
        gameToken++
        handler.removeCallbacksAndMessages(null)
        board.fill(EMPTY)
        gameOver = false
        aiThinking = false
        lastMove = -1
        status = "你先下，点棋盘放黑子"
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
        val header = min(h * 0.13f, dp(100f))
        val available = min(w * 0.96f, (h - header) * 0.96f)
        cell = available / SIZE
        originX = (w - cell * (SIZE - 1)) / 2f
        originY = header + (h - header - cell * (SIZE - 1)) / 2f

        // 棋盘底色
        val pad = cell * 0.55f
        val boardRect = RectF(
            originX - pad,
            originY - pad,
            originX + cell * (SIZE - 1) + pad,
            originY + cell * (SIZE - 1) + pad
        )
        canvas.drawRoundRect(boardRect, cell * 0.5f, cell * 0.5f, boardPaint)
        boardEdgePaint.strokeWidth = cell * 0.06f
        canvas.drawRoundRect(boardRect, cell * 0.5f, cell * 0.5f, boardEdgePaint)

        // 网格
        linePaint.strokeWidth = cell * 0.035f
        for (i in 0 until SIZE) {
            val x = originX + i * cell
            val y = originY + i * cell
            canvas.drawLine(originX, y, originX + cell * (SIZE - 1), y, linePaint)
            canvas.drawLine(x, originY, x, originY + cell * (SIZE - 1), linePaint)
        }

        // 星位
        val stars = intArrayOf(3, 3, 11, 3, 3, 11, 11, 11, 7, 7)
        var s = 0
        while (s < stars.size) {
            val cx = originX + stars[s] * cell
            val cy = originY + stars[s + 1] * cell
            canvas.drawCircle(cx, cy, cell * 0.08f, linePaint)
            s += 2
        }

        // 棋子
        for (index in board.indices) {
            val p = board[index]
            if (p == EMPTY) continue
            val x = originX + (index % SIZE) * cell
            val y = originY + (index / SIZE) * cell
            drawStone(canvas, x, y, p)
        }

        // 最后一手
        if (lastMove >= 0) {
            val x = originX + (lastMove % SIZE) * cell
            val y = originY + (lastMove / SIZE) * cell
            lastPaint.strokeWidth = cell * 0.1f
            canvas.drawCircle(x, y, cell * 0.3f, lastPaint)
        }

        // 状态文字
        textPaint.textSize = min(header * 0.62f, w / 15f).coerceAtLeast(dp(18f))
        val baseline = header * 0.72f
        canvas.drawText(status, w / 2f, baseline, textPaint)
    }

    private fun drawStone(canvas: Canvas, x: Float, y: Float, player: Int) {
        val r = cell * 0.43f
        canvas.drawCircle(x + r * 0.08f, y + r * 0.12f, r, blackHiPaint)
        if (player == PLAYER) {
            canvas.drawCircle(x, y, r, blackPaint)
            canvas.drawCircle(x - r * 0.28f, y - r * 0.3f, r * 0.3f, blackHiPaint)
        } else {
            canvas.drawCircle(x, y, r, whitePaint)
            whiteEdgePaint.strokeWidth = cell * 0.05f
            canvas.drawCircle(x, y, r, whiteEdgePaint)
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.action != MotionEvent.ACTION_DOWN) return true
        if (gameOver || aiThinking || cell <= 0f) return true

        val col = ((event.x - originX) / cell).roundToInt()
        val row = ((event.y - originY) / cell).roundToInt()
        if (col !in 0 until SIZE || row !in 0 until SIZE) return true
        val dx = event.x - (originX + col * cell)
        val dy = event.y - (originY + row * cell)
        if (abs(dx) > cell * 0.55f || abs(dy) > cell * 0.55f) return true

        val index = row * SIZE + col
        if (board[index] != EMPTY) return true

        board[index] = PLAYER
        lastMove = index
        if (checkWin(index, PLAYER)) {
            finishGame("你赢了！点“重开一局”再来")
            return true
        }
        if (board.full()) {
            finishGame("平局，点“重开一局”再来")
            return true
        }

        status = "电脑思考中…"
        aiThinking = true
        invalidate()

        val token = gameToken
        handler.postDelayed({
            if (token == gameToken) aiTurn()
        }, 260)
        return true
    }

    private fun aiTurn() {
        if (gameOver) {
            aiThinking = false
            return
        }
        val move = chooseAiMove()
        if (move < 0) {
            finishGame("平局，点“重开一局”再来")
            return
        }
        board[move] = AI
        lastMove = move
        aiThinking = false

        if (checkWin(move, AI)) {
            finishGame("电脑赢了，点“重开一局”再来")
            return
        }
        if (board.full()) {
            finishGame("平局，点“重开一局”再来")
            return
        }
        status = "轮到你了，点棋盘放黑子"
        invalidate()
    }

    private fun finishGame(text: String) {
        gameOver = true
        aiThinking = false
        status = text
        invalidate()
    }

    private fun chooseAiMove(): Int {
        var best = -1
        var bestScore = -1.0
        for (index in board.indices) {
            if (board[index] != EMPTY) continue
            val x = index % SIZE
            val y = index / SIZE
            if (!hasNeighbor(x, y)) continue

            val aiScore = scoreFor(x, y, AI)
            val playerScore = scoreFor(x, y, PLAYER)
            val score = aiScore * 2.0 + playerScore * 1.15
            if (score > bestScore) {
                bestScore = score
                best = index
            }
        }
        if (best < 0) {
            val center = (SIZE / 2) * SIZE + SIZE / 2
            if (board[center] == EMPTY) return center
            return board.indexOfFirst { it == EMPTY }
        }
        return best
    }

    private fun hasNeighbor(x: Int, y: Int, range: Int = 2): Boolean {
        for (dy in -range..range) {
            for (dx in -range..range) {
                if (dx == 0 && dy == 0) continue
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until SIZE && ny in 0 until SIZE &&
                    board[ny * SIZE + nx] != EMPTY
                ) {
                    return true
                }
            }
        }
        return false
    }

    private fun scoreFor(x: Int, y: Int, player: Int): Double {
        var total = 0.0
        total += lineScore(x, y, 1, 0, player)
        total += lineScore(x, y, 0, 1, player)
        total += lineScore(x, y, 1, 1, player)
        total += lineScore(x, y, 1, -1, player)
        return total
    }

    private fun lineScore(x: Int, y: Int, dx: Int, dy: Int, player: Int): Int {
        var count = 1
        var open = 0

        var cx = x + dx
        var cy = y + dy
        while (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == player) {
            count++
            cx += dx
            cy += dy
        }
        if (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == EMPTY) open++

        cx = x - dx
        cy = y - dy
        while (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == player) {
            count++
            cx -= dx
            cy -= dy
        }
        if (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == EMPTY) open++

        return when {
            count >= 5 -> 1_000_000
            count == 4 && open == 2 -> 100_000
            count == 4 && open == 1 -> 10_000
            count == 3 && open == 2 -> 8_000
            count == 3 && open == 1 -> 800
            count == 2 && open == 2 -> 500
            count == 2 && open == 1 -> 50
            count == 1 && open == 2 -> 10
            else -> 1
        }
    }

    private fun checkWin(index: Int, player: Int): Boolean {
        val x = index % SIZE
        val y = index / SIZE
        val directions = intArrayOf(1, 0, 0, 1, 1, 1, 1, -1)
        var d = 0
        while (d < directions.size) {
            val dx = directions[d]
            val dy = directions[d + 1]
            var count = 1

            var cx = x + dx
            var cy = y + dy
            while (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == player) {
                count++
                cx += dx
                cy += dy
            }

            cx = x - dx
            cy = y - dy
            while (cx in 0 until SIZE && cy in 0 until SIZE && board[cy * SIZE + cx] == player) {
                count++
                cx -= dx
                cy -= dy
            }

            if (count >= 5) return true
            d += 2
        }
        return false
    }

    private fun IntArray.full(): Boolean = none { it == EMPTY }

    private fun dp(value: Float): Float = value * resources.displayMetrics.density
}
