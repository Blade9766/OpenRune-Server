package org.rsmod.content.travel.wayfinder

import dev.openrune.ServerCacheManager
import io.mockk.mockk
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.PlayerTeleportValidator
import org.rsmod.content.quest.manager.QuestRequirementMode
import org.rsmod.content.quest.manager.QuestRequirementPolicy
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.travel.wayfinder.WayfinderRegistryTest.Companion.dest
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class WayfinderTravelRulesTest {
    private var teleblocked = false
    private val travel =
        WayfinderTravel(
            registry = WayfinderRegistry.of(emptyList()),
            teleportValidator =
                PlayerTeleportValidator(setOf(PlayerTeleportValidateHook { _, _, _ -> if (teleblocked) TELEBLOCK else null })),
            areaChecker = mockk<AreaChecker>(relaxed = true),
            npcList = NpcList(),
        )

    @AfterEach
    fun restorePolicy() {
        QuestRequirements.install(QuestRequirementPolicy())
    }

    @Test
    fun `a healthy player beside a mage may travel`() {
        assertNull(denial(player(), mage(), dest("lumbridge", 1)))
    }

    @Test
    fun `unknown and disabled destinations are refused`() {
        assertEquals(NOT_ON_NETWORK, denial(player(), mage(), null))
        assertEquals(NOT_ON_NETWORK, denial(player(), mage(), dest("old", 2, enabled = false)))
    }

    @Test
    fun `travel needs a wayfinder close by on the same level`() {
        assertEquals(NO_MAGE, denial(player(), null, dest("lumbridge", 1)))
        assertEquals(NO_MAGE, denial(player(), mage(at = HERE.translate(WayfinderTravel.MAGE_RANGE + 1, 0)), dest("lumbridge", 1)))
        assertEquals(NO_MAGE, denial(player(), mage(at = HERE.translate(1, 0, 1)), dest("lumbridge", 1)))
        assertEquals(NO_MAGE, denial(player(), mage(type = "npc.man"), dest("lumbridge", 1)))
        assertEquals(NO_MAGE, denial(player(), mage(slot = false), dest("lumbridge", 1)))
    }

    @Test
    fun `a second request while one is casting is refused`() {
        val player = player().apply { actionDelay = NOW + 3 }
        assertEquals("You're already being teleported.", denial(player, mage(), dest("lumbridge", 1)))
        assertNull(travel.denial(player, mage(), dest("lumbridge", 1), WayfinderTraveller.Player, NOW, starting = false))
    }

    @Test
    fun `dying players cannot travel`() {
        val player = player().apply { statMap.setCurrentLevel("stat.hitpoints", 0.toByte()) }
        assertEquals("You can't travel while you're dying.", denial(player, mage(), dest("lumbridge", 1)))
    }

    @Test
    fun `bots only reach destinations the registry allows them`() {
        val forbidden = dest("ditch", 1, category = WayfinderCategory.Wilderness, botAllowed = false)
        assertEquals("Bots may not travel to ditch.", denial(player(), mage(), forbidden, WayfinderTraveller.Bot))
        assertNull(denial(player(), mage(), dest("varrock", 2), WayfinderTraveller.Bot))
    }

    @Test
    fun `skill levels and membership are not checked`() {
        val guild = dest("mining_guild", 1, name = "Mining Guild")
        assertNull(denial(player().apply { members = false }, mage(), guild))
    }

    @Test
    fun `quest requirements follow the server's quest policy`() {
        val canifis = dest("canifis", 1, name = "Canifis", quest = "quest_priestinperil")
        assertNull(denial(player(), mage(), canifis))
        QuestRequirements.install(QuestRequirementPolicy(QuestRequirementMode.RespectProgress))
        val reason = denial(player(), mage(), canifis)
        assertEquals(true, reason?.endsWith("to travel to Canifis."), reason)
    }

    @Test
    fun `teleport blocks and wilderness limits from the teleport rules apply`() {
        teleblocked = true
        assertEquals(TELEBLOCK, denial(player(), mage(), dest("lumbridge", 1)))
    }

    private fun denial(
        player: Player,
        mage: Npc?,
        destination: WayfinderDestination?,
        traveller: WayfinderTraveller = WayfinderTraveller.Player,
    ): String? = travel.denial(player, mage, destination, traveller, NOW)

    private fun player(): Player =
        Player().apply {
            coords = HERE
            slotId = 1
            members = true
            statMap.setBaseLevel("stat.hitpoints", 10.toByte())
            statMap.setCurrentLevel("stat.hitpoints", 10.toByte())
        }

    private fun mage(
        at: CoordGrid = HERE.translate(1, 0),
        type: String = "npc.wayfinder_vaelor",
        slot: Boolean = true,
    ): Npc = Npc(type, at).apply { if (slot) slotId = 1 }

    companion object {
        private val HERE = CoordGrid(3088, 3494, 0)
        private const val NOW = 100
        private const val TELEBLOCK = "A teleport block has been cast on you."
        private const val NOT_ON_NETWORK = "That destination is not on the wayfinder network."
        private const val NO_MAGE = "You need to stand beside a wayfinder to travel."

        private lateinit var cache: dev.openrune.filesystem.Cache

        @JvmStatic
        @BeforeAll
        fun load() {
            cache = ServerCacheManager.init(241)
        }

        @JvmStatic
        @AfterAll
        fun close() {
            cache.close()
        }
    }
}
