package org.rsmod.content.other.barrows

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.api.random.DefaultGameRandom

class BarrowsRulesTest {
    @Test
    fun everyLayoutOpensOneChestDoorAndKeepsTheOuterRoomsJoined() {
        val random = DefaultGameRandom(seed = 7)
        repeat(2_000) {
            val locked = TunnelLayout.generate(random)
            val open = TunnelCorridor.entries.toSet() - locked
            assertEquals(1, open.count { it.entersCentre }, "open chest doors in $open")
            assertTrue(TunnelLayout.outerRoomsConnected(open), "outer rooms split by $locked")
        }
    }

    @Test
    fun corridorsIntoTheChestRoomAreTheFourAroundIt() {
        val centre = TunnelCorridor.entries.filter { it.entersCentre }
        assertEquals(listOf(TunnelCorridor.E, TunnelCorridor.I, TunnelCorridor.J, TunnelCorridor.L), centre)
    }

    @Test
    fun puzzleAnswerIsTheFirstSequenceModelMinusThree() {
        for (type in 0 until BarrowsPuzzle.TYPES) {
            for (slot in 0 until BarrowsPuzzle.ANSWER_SLOTS) {
                val answers = BarrowsPuzzle.answers(type, slot)
                assertEquals(BarrowsPuzzle.sequence(type).first() - 3, answers[slot])
                assertEquals(3, answers.toSet().size)
            }
        }
    }

    @Test
    fun uniqueChanceMatchesTheWikiTable() {
        assertEquals(listOf(392, 334, 276, 218, 160, 102), (1..6).map(BarrowsLoot::uniqueChanceDenominator))
        assertEquals(200, BarrowsLoot.clueChanceDenominator(0))
        assertEquals(29, BarrowsLoot.clueChanceDenominator(6))
    }

    @Test
    fun noKillsMeansNoLoot() {
        assertTrue(BarrowsLoot.roll(emptyList(), 0, DefaultGameRandom(seed = 1)).isEmpty())
    }

    @Test
    fun onlySlainBrothersDropTheirPiecesAndNeverTwice() {
        val random = DefaultGameRandom(seed = 3)
        val killed = listOf(Brother.Dharok, Brother.Verac)
        val allowed = killed.flatMap { it.items }.toSet()
        val everyPiece = Brother.entries.flatMap { it.items }.toSet()
        repeat(20_000) {
            val rewards = BarrowsLoot.roll(killed, 1012, random)
            val pieces = rewards.filter { it.obj in everyPiece }
            assertTrue(pieces.all { it.obj in allowed && it.count == 1 }, "$pieces")
            assertTrue(rewards.count { it.obj == BarrowsLoot.ELITE_CLUE } <= 1)
        }
    }

    @Test
    fun lowPotentialOnlyReachesCoins() {
        val random = DefaultGameRandom(seed = 5)
        repeat(5_000) {
            val rewards = BarrowsLoot.roll(listOf(Brother.Ahrim), 100, random)
            val table = rewards.filterNot { it.obj in Brother.Ahrim.items || it.obj == BarrowsLoot.ELITE_CLUE }
            assertTrue(table.all { it.obj == "obj.coins" && it.count in 1..TWO_ROLLS_OF_SCALED_COINS }, "$table")
        }
    }

    @Test
    fun anUnslainRunRollsOnceForCoins() {
        val random = DefaultGameRandom(seed = 11)
        repeat(1_000) {
            val rewards = BarrowsLoot.roll(emptyList(), 380, random).filterNot { it.obj == BarrowsLoot.ELITE_CLUE }
            assertEquals(1, rewards.size)
            assertEquals("obj.coins", rewards.single().obj)
            assertTrue(rewards.single().count in 2..774)
        }
    }

    private companion object {
        /** Coins scale to 100/380 of 774 at 100 potential; Ahrim slain means two rolls. */
        const val TWO_ROLLS_OF_SCALED_COINS = 2 * (774 * 100 / 380)
    }
}
