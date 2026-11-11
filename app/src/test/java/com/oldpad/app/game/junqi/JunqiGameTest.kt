package com.oldpad.app.game.junqi

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class JunqiGameTest {

    @Test
    fun boardStartsWith32HiddenPieces() {
        val game = JunqiGame(Random(1))
        assertEquals(32, game.cells.count { it != null })
        assertTrue(game.cells.all { it?.hidden == true })
        assertEquals(JunqiGame.Side.NONE, game.playerSide)
        assertEquals(JunqiGame.Side.NONE, game.winner)
    }

    @Test
    fun firstFlipAssignsSideAndPassesTurn() {
        val game = JunqiGame(Random(2))
        val piece = game.cells[0]!!
        assertTrue(game.flip(0))
        assertFalse(piece.hidden)
        assertEquals(piece.side, game.playerSide)
        assertEquals(JunqiGame.other(piece.side), game.turn)
    }

    @Test
    fun cannotMoveBeforeSideIsAssigned() {
        val game = JunqiGame(Random(3))
        game.cells.fill(null)
        game.cells[0] = JunqiGame.Piece(JunqiGame.Side.RED, JunqiGame.Rank.COMMANDER, false)
        assertFalse(game.move(0, 1))
    }

    @Test
    fun legalMovesSkipHiddenAndOwnPieces() {
        val game = JunqiGame(Random(4))
        game.cells.fill(null)
        game.cells[0] = JunqiGame.Piece(JunqiGame.Side.RED, JunqiGame.Rank.COMMANDER, false)
        game.cells[1] = JunqiGame.Piece(JunqiGame.Side.BLUE, JunqiGame.Rank.PLATOON, true)
        val moves = game.legalMoves(JunqiGame.Side.RED)
        assertTrue(moves.contains(0 to 8))
        assertFalse(moves.any { it.second == 1 })

        game.cells[8] = JunqiGame.Piece(JunqiGame.Side.RED, JunqiGame.Rank.PLATOON, false)
        val blocked = game.legalMoves(JunqiGame.Side.RED)
        assertFalse(blocked.contains(0 to 8))
    }

    @Test
    fun restartRestoresHiddenBoard() {
        val game = JunqiGame(Random(5))
        game.flip(0)
        assertTrue(game.cells.any { it?.hidden == false })
        game.reset()
        assertTrue(game.cells.all { it?.hidden == true })
        assertEquals(JunqiGame.Side.NONE, game.playerSide)
    }
}
