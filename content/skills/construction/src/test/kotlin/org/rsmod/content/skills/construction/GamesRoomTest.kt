package org.rsmod.content.skills.construction

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Games
import org.rsmod.content.skills.construction.data.RoomType

@ResourceLock("ServerCacheManager")
class GamesRoomTest {
    private val room = RoomType.GAMES_ROOM

    @Test
    fun `the games room is a unique room with doors east, south and west`() {
        assertEquals(6, room.roomTypeId)
        assertTrue(room.unique)
        assertEquals(setOf(Floor.GROUND, Floor.UPPER), room.floors)
        assertEquals(listOf(0, 2, 3), (0..3).filter { room.hasDoor(it, 0) })
    }

    @Test
    fun `every game is built with the op that plays it`() {
        val ops =
            mapOf(
                "attack_stone" to "Set-up",
                "elemental_balance" to "Activate",
                "game" to "Activate",
                "prize_chest" to "Open",
            )
        for (group in room.hotspots) {
            group.locs.forEach { it.asRSCM(RSCMType.LOC) }
            for (option in group.options) {
                val type = ServerCacheManager.getObject(option.built.single().asRSCM(RSCMType.LOC))!!
                val expected = ops[group.key] ?: continue
                assertEquals(expected, type.actions.getOpOrNull(0), option.label)
            }
        }
        val ranging = Games.RangingGame.entries.map { ServerCacheManager.getObject(it.loc.asRSCM(RSCMType.LOC))!!.actions.getOpOrNull(0) }
        assertEquals(listOf("Hoop", "Throw-at", "Shoot-at"), ranging)
        assertEquals(room.hotspot("ranging_game")!!.options.map { it.built.single() }, Games.RangingGame.entries.map { it.loc })
    }

    @Test
    fun `a stone cracks through its npc stages and breaks at its hitpoints`() {
        for (stone in Games.AttackStone.entries) {
            assertEquals(stone.stages.first(), stone.npcAt(0))
            assertEquals(stone.stages.last(), stone.npcAt(stone.hitpoints - 1))
            assertEquals(stone.destroyed, stone.npcAt(stone.hitpoints))
            (stone.stages + stone.destroyed).forEach { it.asRSCM(RSCMType.NPC) }
            assertEquals("Hit", ServerCacheManager.getNpc(stone.stages.first().asRSCM(RSCMType.NPC))!!.actions.getOpOrNull(0))
        }
    }

    @Test
    fun `a balance shows the heavier pair's colour, and white when both are even`() {
        val balance = Games.Balance.LESSER
        assertEquals("npc.poh_elemental_balance_1", balance.npcFor(0, 0))
        assertEquals("npc.poh_elemental_balance_1_cyan2", balance.npcFor(2, 1))
        assertEquals("npc.poh_elemental_balance_1_green3", balance.npcFor(-3, 0))
        assertEquals("npc.poh_elemental_balance_1_red6", balance.npcFor(1, -9))
        for (entry in Games.Balance.entries) {
            entry.forms.forEach { it.asRSCM(RSCMType.NPC) }
            assertEquals("Banish", ServerCacheManager.getNpc(entry.npcFor(0, 0).asRSCM(RSCMType.NPC))!!.actions.getOpOrNull(1))
        }
        assertEquals(20, Games.BALANCE_SPELLS.size)
        Games.BALANCE_SPELLS.forEach { it.component.asRSCM(RSCMType.COMPONENT) }
    }

    @Test
    fun `hangman's stages, the jester's emotes and the hunt's pieces all exist`() {
        Games.HANGMAN_STAGES.forEach { it.asRSCM(RSCMType.NPC) }
        assertEquals("Guess-letter", ServerCacheManager.getNpc(Games.HANGMAN_STAGES.first().asRSCM(RSCMType.NPC))!!.actions.getOpOrNull(0))
        assertEquals("Reset", ServerCacheManager.getNpc(Games.HANGMAN_STAGES.last().asRSCM(RSCMType.NPC))!!.actions.getOpOrNull(0))
        assertTrue(Games.HANGMAN_WORDS.all { word -> word.all { it in 'A'..'Z' } })
        Games.JESTER_EMOTES.flatMap { it.second }.forEach { it.asRSCM(RSCMType.SEQ) }
        listOf(Games.JESTER, Games.JESTER_PLAYING, Games.TREASURE_HUNT, Games.TREASURE_HUNT_OPEN, Games.HANGMAN)
            .forEach { it.asRSCM(RSCMType.LOC) }
        Games.TREASURE_FAIRY.asRSCM(RSCMType.NPC)
        assertEquals("Feel", ServerCacheManager.getItem(Games.TREASURE_STONE.asRSCM(RSCMType.OBJ))!!.interfaceOptions?.get(0))
    }

    @Test
    fun `the prize chest and scoreboard are wired to real ids`() {
        Games.PRIZE_KEY.asRSCM(RSCMType.OBJ)
        Games.PRIZE_VARP.asRSCM(RSCMType.VARP)
        for (chest in Games.PrizeChest.entries) {
            chest.closed.asRSCM(RSCMType.LOC)
            chest.open.asRSCM(RSCMType.LOC)
        }
        assertEquals(room.hotspot("prize_chest")!!.options.map { it.built.single() }, Games.PrizeChest.entries.map { it.closed })
        "interface.poh_ranging".asRSCM(RSCMType.INTERFACE)
        for (column in listOf("player", "shots", "score", "winner")) {
            for (row in 1..Games.SCOREBOARD_ROWS) {
                "component.poh_ranging:poh_ranging_$column$row".asRSCM(RSCMType.COMPONENT)
            }
        }
        listOf("human_throw_hoop1", "ii_human_dart_throw", "human_bow", "human_crossbow", "human_caststrike", "human_castwave")
            .forEach { "seq.$it".asRSCM(RSCMType.SEQ) }
    }

    companion object {
        @JvmStatic
        @BeforeAll
        fun loadCache() {
            ServerCacheManager.init(240).close()
        }
    }
}
