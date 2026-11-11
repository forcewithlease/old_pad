package com.oldpad.app.game.junqi

import kotlin.random.Random

/**
 * 军棋翻棋（两人单机）：
 * - 4×8 棋盘，32 枚棋子，全部背面朝下
 * - 先翻到哪一方的棋子，玩家就是哪一方
 * - 轮流翻棋或走棋，目标是吃掉对方军旗
 * - 地雷不能走，工兵可挖雷；炸弹同归于尽；同级同归于尽
 *
 * 这是一个纯逻辑引擎，便于单元测试；界面在 JunqiView。
 */
class JunqiGame(private val random: Random = Random.Default) {

    enum class Side { NONE, RED, BLUE }

    enum class Rank(val power: Int, val label: String) {
        COMMANDER(9, "司令"),
        ARMY(8, "军长"),
        DIVISION(7, "师长"),
        BRIGADE(6, "旅长"),
        REGIMENT(5, "团长"),
        BATTALION(4, "营长"),
        COMPANY(3, "连长"),
        PLATOON(2, "排长"),
        ENGINEER(1, "工兵"),
        MINE(0, "地雷"),
        BOMB(-1, "炸弹"),
        FLAG(-2, "军旗")
    }

    data class Piece(val side: Side, val rank: Rank, var hidden: Boolean = true)

    companion object {
        const val ROWS = 4
        const val COLS = 8
        const val SIZE = ROWS * COLS
        private val COUNTS = listOf(
            Rank.COMMANDER to 1,
            Rank.ARMY to 1,
            Rank.DIVISION to 1,
            Rank.BRIGADE to 1,
            Rank.REGIMENT to 2,
            Rank.BATTALION to 2,
            Rank.COMPANY to 1,
            Rank.PLATOON to 2,
            Rank.ENGINEER to 2,
            Rank.MINE to 1,
            Rank.BOMB to 1,
            Rank.FLAG to 1
        )

        fun other(side: Side): Side = when (side) {
            Side.RED -> Side.BLUE
            Side.BLUE -> Side.RED
            Side.NONE -> Side.NONE
        }
    }

    val cells = arrayOfNulls<Piece>(SIZE)
    var playerSide: Side = Side.NONE
        private set
    var turn: Side = Side.RED
        private set
    var winner: Side = Side.NONE
        private set
    var message: String = "点一个暗棋翻开，先翻到什么颜色，你就是哪一方"
        private set

    var selected: Int = -1
        private set

    init {
        reset()
    }

    fun reset() {
        cells.fill(null)
        val pieces = ArrayList<Piece>(SIZE)
        for (side in listOf(Side.RED, Side.BLUE)) {
            for ((rank, count) in COUNTS) {
                repeat(count) { pieces += Piece(side, rank) }
            }
        }
        pieces.shuffle(random)
        pieces.forEachIndexed { index, piece -> cells[index] = piece }
        playerSide = Side.NONE
        turn = Side.RED
        winner = Side.NONE
        selected = -1
        message = "点一个暗棋翻开，先翻到什么颜色，你就是哪一方"
    }

    fun isPlayerTurn(): Boolean = winner == Side.NONE && playerSide != Side.NONE && turn == playerSide

    fun canAct(side: Side): Boolean {
        if (side == Side.NONE || winner != Side.NONE) return false
        if (cells.any { it?.hidden == true }) return true
        return legalMoves(side).isNotEmpty()
    }

    /** 玩家翻棋；成功后轮到电脑。 */
    fun flip(index: Int): Boolean {
        if (winner != Side.NONE || playerSide != Side.NONE && turn != playerSide) return false
        val piece = cells.getOrNull(index) ?: return false
        if (!piece.hidden) return false

        piece.hidden = false
        selected = -1
        lastMessageForFlip(piece)

        if (playerSide == Side.NONE) {
            playerSide = piece.side
            turn = other(piece.side)
            message = "你翻到了${sideLabel(piece.side)}棋子，你是${sideLabel(piece.side)}方；电脑先走"
        } else {
            switchTurn()
        }
        afterTurnChange()
        return true
    }

    /** 玩家走棋；from 为已选中的己方棋子，to 必须相邻。 */
    fun move(from: Int, to: Int): Boolean {
        if (winner != Side.NONE || !isPlayerTurn()) return false
        if (!isAdjacent(from, to)) return false
        val attacker = cells.getOrNull(from) ?: return false
        val defender = cells.getOrNull(to) ?: return false
        if (attacker.hidden || attacker.side != playerSide) return false
        if (attacker.rank == Rank.MINE || attacker.rank == Rank.FLAG) return false
        if (defender != null && defender.hidden) return false

        resolveMove(from, to, attacker, defender)
        selected = -1
        if (winner == Side.NONE) switchTurn()
        afterTurnChange()
        return true
    }

    fun select(index: Int): Boolean {
        val piece = cells.getOrNull(index) ?: return false
        if (piece.hidden || piece.side != playerSide) return false
        if (piece.rank == Rank.MINE || piece.rank == Rank.FLAG) return false
        selected = index
        message = "已选中${piece.rank.label}，点相邻空格或对方棋子移动"
        return true
    }

    fun clearSelection() {
        selected = -1
    }

    /** 电脑行动：翻棋或走一步。 */
    fun aiAct(): Boolean {
        if (winner != Side.NONE || turn == Side.NONE || turn == playerSide) return false
        val moves = legalMoves(turn)
        val hidden = cells.indices.filter { cells[it]?.hidden == true }

        if (hidden.isNotEmpty() && (moves.isEmpty() || random.nextFloat() < 0.22f)) {
            val index = hidden[random.nextInt(hidden.size)]
            val piece = cells[index]!!
            piece.hidden = false
            message = "电脑翻开了一个${sideLabel(piece.side)}${piece.rank.label}"
            switchTurn()
            afterTurnChange()
            return true
        }

        if (moves.isEmpty()) {
            if (hidden.isEmpty()) {
                winner = other(turn)
                message = "${sideLabel(winner)}方获胜：对方无棋可走"
            }
            return false
        }

        val best = moves.maxByOrNull { moveScore(turn, it.first, it.second) } ?: return false
        val attacker = cells[best.first]!!
        val defender = cells[best.second]
        resolveMove(best.first, best.second, attacker, defender)
        switchTurn()
        afterTurnChange()
        return true
    }

    fun isAdjacent(from: Int, to: Int): Boolean {
        if (from !in 0 until SIZE || to !in 0 until SIZE) return false
        val fr = from / COLS
        val fc = from % COLS
        val tr = to / COLS
        val tc = to % COLS
        return kotlin.math.abs(fr - tr) + kotlin.math.abs(fc - tc) == 1
    }

    fun legalMoves(side: Side): List<Pair<Int, Int>> {
        val result = ArrayList<Pair<Int, Int>>()
        for (index in 0 until SIZE) {
            val piece = cells[index] ?: continue
            if (piece.hidden || piece.side != side) continue
            if (piece.rank == Rank.MINE || piece.rank == Rank.FLAG) continue
            val row = index / COLS
            val col = index % COLS
            val neighbours = intArrayOf(
                index - 1, index + 1,
                index - COLS, index + COLS
            )
            for (target in neighbours) {
                if (target !in 0 until SIZE) continue
                val tr = target / COLS
                val tc = target % COLS
                if (kotlin.math.abs(tr - row) + kotlin.math.abs(tc - col) != 1) continue
                val occupant = cells[target]
                if (occupant != null && (occupant.hidden || occupant.side == side)) continue
                result += index to target
            }
        }
        return result
    }

    fun rankLabel(rank: Rank): String = rank.label

    private fun resolveMove(from: Int, to: Int, attacker: Piece, defender: Piece?) {
        if (defender == null) {
            cells[to] = attacker
            cells[from] = null
            message = "${sideLabel(attacker.side)}${attacker.rank.label}移动了一步"
            return
        }

        if (defender.rank == Rank.FLAG) {
            cells[to] = attacker
            cells[from] = null
            winner = attacker.side
            message = "${sideLabel(attacker.side)}吃掉军旗，获得胜利！"
            return
        }

        if (attacker.rank == Rank.BOMB || defender.rank == Rank.BOMB) {
            cells[from] = null
            cells[to] = null
            message = "炸弹同归于尽"
            return
        }

        if (defender.rank == Rank.MINE) {
            if (attacker.rank == Rank.ENGINEER) {
                cells[to] = attacker
                cells[from] = null
                message = "工兵挖掉了地雷"
            } else {
                cells[from] = null
                message = "${sideLabel(attacker.side)}${attacker.rank.label}踩到地雷"
            }
            return
        }

        when {
            attacker.rank.power > defender.rank.power -> {
                cells[to] = attacker
                cells[from] = null
                message = "${sideLabel(attacker.side)}${attacker.rank.label}吃掉${defender.rank.label}"
            }
            attacker.rank.power < defender.rank.power -> {
                cells[from] = null
                message = "${sideLabel(attacker.side)}${attacker.rank.label}被${defender.rank.label}吃掉"
            }
            else -> {
                cells[from] = null
                cells[to] = null
                message = "同级棋子同归于尽"
            }
        }
    }

    private fun afterTurnChange() {
        if (winner != Side.NONE) return
        if (!canAct(turn)) {
            winner = other(turn)
            message = "${sideLabel(winner)}方获胜：对方无棋可走"
            return
        }
        if (turn == playerSide) {
            message = if (cells.any { it?.hidden == true }) {
                "轮到你了：翻暗棋，或点自己的棋子移动"
            } else {
                "轮到你了：点自己的棋子移动"
            }
        }
    }

    private fun switchTurn() {
        turn = other(turn)
    }

    private fun lastMessageForFlip(piece: Piece) {
        // 翻开后若立刻能判断胜负，交给 afterTurnChange；这里只做提示
        if (piece.rank == Rank.FLAG) {
            message = "翻出了${sideLabel(piece.side)}方军旗"
        } else {
            message = "翻出了${sideLabel(piece.side)}${piece.rank.label}"
        }
    }

    private fun moveScore(side: Side, from: Int, to: Int): Double {
        val attacker = cells[from] ?: return -1000.0
        val defender = cells[to]
        var score = random.nextDouble() * 0.5

        if (defender == null) {
            score += 5.0
        } else {
            if (defender.rank == Rank.FLAG) return 10000.0
            if (defender.rank == Rank.MINE) {
                score += if (attacker.rank == Rank.ENGINEER) 500.0 else -300.0
            } else if (attacker.rank == Rank.BOMB || defender.rank == Rank.BOMB) {
                score += 60.0 - defender.rank.power
            } else {
                val diff = attacker.rank.power - defender.rank.power
                score += when {
                    diff > 0 -> 100.0 + defender.rank.power * 8
                    diff == 0 -> -20.0
                    else -> -120.0 + attacker.rank.power * 5
                }
            }
        }

        // 朝对方半场推进一点点
        val fromRow = from / COLS
        val toRow = to / COLS
        val forward = if (side == Side.RED) toRow - fromRow else fromRow - toRow
        score += forward * 1.5
        return score
    }

    private fun sideLabel(side: Side): String = when (side) {
        Side.RED -> "红"
        Side.BLUE -> "蓝"
        Side.NONE -> ""
    }
}
