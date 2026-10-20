package org.rsmod.content.areas.misc.waterbirth

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.util.UncheckedType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.rsmod.api.testing.GameTestState
import org.rsmod.api.testing.scope.GameTestScope
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.inv.InvObj
import org.rsmod.map.CoordGrid

/** A pet rock put on a pressure plate must come back exactly once, and never be lost. */
@OptIn(UncheckedType::class)
@Execution(ExecutionMode.SAME_THREAD)
class PetRockPadsTest {
    @Test
    fun GameTestState.`a rock put on a plate leaves the pack and comes back when taken`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            var placed = false
            player.withProtectedAccess { placed = pads.place(this, 0, coords) }
            assertTrue(placed)
            assertEquals(0, player.inv.count(PET_ROCK))
            val npc = rockOn(PAD)
            assertNotNull(npc)
            assertEquals(setOf(PAD), pads.pressed())

            var taken = false
            player.withProtectedAccess { taken = pads.take(this, npc!!) }
            assertTrue(taken)
            assertEquals(1, player.inv.count(PET_ROCK))
            assertTrue(pads.pressed().isEmpty())
        }

    @Test
    fun GameTestState.`a rock dropped off the plates is left to the normal drop`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            player.clearInv()
            player.inv[0] = obj(PET_ROCK)
            val floor = PAD.translate(-3, 0)
            allocZoneCollision(floor)
            player.placeAt(floor)
            var placed = true
            player.withProtectedAccess { placed = pads.place(this, 0, coords) }
            assertFalse(placed)
            assertEquals(1, player.inv.count(PET_ROCK))
            assertNull(rockOn(floor))
        }

    @Test
    fun GameTestState.`a second rock on a weighed plate stays in the pack`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.inv[1] = obj(PET_ROCK)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            player.withProtectedAccess { pads.place(this, 1, coords) }
            assertEquals(1, player.inv.count(PET_ROCK))
            assertEquals(1, npcRepo.findAll(PAD).count { it.isRock() })
        }

    @Test
    fun GameTestState.`taking with a full pack leaves the rock on the plate`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            val npc = rockOn(PAD)!!
            player.fillInv()
            var taken = true
            player.withProtectedAccess { taken = pads.take(this, npc) }
            assertFalse(taken)
            assertEquals(0, player.inv.count(PET_ROCK))
            assertEquals(setOf(PAD), pads.pressed())

            player.inv[0] = null
            player.withProtectedAccess { taken = pads.take(this, npc) }
            assertTrue(taken)
            assertEquals(1, player.inv.count(PET_ROCK))
        }

    @Test
    fun GameTestState.`taking twice in one tick gives back one rock`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            val npc = rockOn(PAD)!!
            var second = true
            player.withProtectedAccess {
                pads.take(this, npc)
                second = pads.take(this, npc)
            }
            assertFalse(second)
            assertEquals(1, player.inv.count(PET_ROCK))
        }

    @Test
    fun GameTestState.`only the owner can take a rock`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            val npc = rockOn(PAD)!!
            val other = Player()
            registerPlayer(coords = PAD.translate(0, 1), player = other)
            other.clearInv()
            var taken = true
            other.withProtectedAccess { taken = pads.take(this, npc) }
            assertFalse(taken)
            assertEquals(0, other.inv.count(PET_ROCK))
            assertEquals(setOf(PAD), pads.pressed())
        }

    @Test
    fun GameTestState.`walking through the door collects the rock once`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            player.withProtectedAccess {
                pads.collect(this, WaterbirthPath.Middle)
                pads.collect(this, WaterbirthPath.Middle)
            }
            assertEquals(1, player.inv.count(PET_ROCK))
            assertTrue(pads.pressed().isEmpty())
        }

    @Test
    fun GameTestState.`a forgotten rock returns to its owner once, and not after being taken`() =
        runInjectedGameTest(PetRockPads::class) { pads ->
            standOnPad(player)
            player.withProtectedAccess { pads.place(this, 0, coords) }
            val npc = rockOn(PAD)!!
            pads.expire(PAD, npc)
            pads.expire(PAD, npc)
            assertEquals(1, player.inv.count(PET_ROCK))
            assertTrue(pads.pressed().isEmpty())
            player.withProtectedAccess { pads.take(this, npc) }
            assertEquals(1, player.inv.count(PET_ROCK))
        }

    private fun GameTestScope.standOnPad(player: Player) {
        player.clearInv()
        player.inv[0] = obj(PET_ROCK)
        allocZoneCollision(PAD)
        player.placeAt(PAD)
    }

    private fun GameTestScope.rockOn(tile: CoordGrid): Npc? = npcRepo.findAll(tile).firstOrNull { it.isRock() }

    private fun Npc.isRock(): Boolean = id == PetRockPads.PET_ROCK_NPC.asRSCM(RSCMType.NPC)

    private fun obj(name: String) = InvObj(name.asRSCM(RSCMType.OBJ), 1)

    private companion object {
        const val PET_ROCK = PetRockPads.PET_ROCK
        val PAD = WaterbirthPath.Middle.pads[0]
    }
}
