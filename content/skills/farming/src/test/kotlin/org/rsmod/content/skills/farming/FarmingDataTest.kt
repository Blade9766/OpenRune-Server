package org.rsmod.content.skills.farming

import kotlin.math.abs
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.farming.data.Crop
import org.rsmod.content.skills.farming.data.Crops
import org.rsmod.content.skills.farming.data.FarmingPatches
import org.rsmod.content.skills.farming.data.PatchKind
import org.rsmod.content.skills.farming.state.Compost
import org.rsmod.content.skills.farming.state.PatchState

class FarmingDataTest {
    /**
     * Two patches sharing a transmit varbit must never be able to share a scene, or setting one
     * would redraw the other. The scene is 104 tiles across, so any pair within that box collides.
     */
    @Test
    fun `patches sharing a transmit varbit are never in the same scene`() {
        val patches = FarmingPatches.ALL
        for (i in patches.indices) {
            for (j in i + 1 until patches.size) {
                val left = patches[i]
                val right = patches[j]
                if (left.varbit != right.varbit || left.coords.level != right.coords.level) {
                    continue
                }
                val overlaps =
                    abs(left.coords.x - right.coords.x) <= FarmingPatches.SCENE_RADIUS * 2 &&
                        abs(left.coords.z - right.coords.z) <= FarmingPatches.SCENE_RADIUS * 2
                assertTrue(!overlaps, "${left.loc} and ${right.loc} share ${left.varbit}")
            }
        }
    }

    @Test
    fun `patch loc keys are unique`() {
        val locs = FarmingPatches.ALL.map { it.loc }
        assertEquals(locs.size, locs.toSet().size)
    }

    @Test
    fun `every patch kind has crops to grow in it`() {
        for (kind in PatchKind.entries) {
            assertTrue(Crops.of(kind).isNotEmpty(), "$kind has no crops")
        }
    }

    /**
     * The disease and dead tables are indexed by growth stage, and only stages between the first
     * and the last can catch anything, so both must be exactly two shorter than the growth table.
     */
    @Test
    fun `crop state tables line up with their growth stages`() {
        for (crop in Crops.ALL) {
            assertEquals(crop.growth.size - 2, crop.diseased.size, "${crop.key} diseased")
            assertEquals(crop.growth.size - 2, crop.dead.size, "${crop.key} dead")
            if (crop.watered.isNotEmpty()) {
                assertEquals(crop.growth.size - 1, crop.watered.size, "${crop.key} watered")
            }
            assertTrue(crop.growth.all { it in 0..255 }, "${crop.key} growth out of varbit range")
        }
    }

    @Test
    fun `growth times divide evenly into cycles`() {
        for (crop in Crops.ALL) {
            assertEquals(
                crop.growthMinutes,
                crop.minutesPerCycle * crop.cycles,
                "${crop.key} does not divide evenly",
            )
        }
    }

    @Test
    fun `seeds map to exactly one crop`() {
        val seeds = Crops.ALL.map { it.seed }
        assertEquals(seeds.size, seeds.toSet().size)
        for (crop in Crops.ALL) {
            assertEquals(crop, Crops.forSeed(crop.seed))
        }
    }

    @Test
    fun `state round trips through the save encoding`() {
        val crop = requireNotNull(Crops.forKey("ranarr_weed"))
        val state =
            PatchState(
                cropKey = crop.key,
                stage = 2,
                weeds = PatchKind.WEEDED,
                lastTick = 29_123_456,
                diseased = true,
                watered = false,
                compost = Compost.ULTRA,
                protectedByFarmer = true,
                lives = 6,
            )
        val decoded = requireNotNull(PatchState.decode(state.encode()))
        assertEquals(state.cropKey, decoded.cropKey)
        assertEquals(state.stage, decoded.stage)
        assertEquals(state.lastTick, decoded.lastTick)
        assertEquals(state.compost, decoded.compost)
        assertEquals(state.lives, decoded.lives)
        assertTrue(decoded.diseased)
        assertTrue(decoded.protectedByFarmer)
    }

    @Test
    fun `varbit value follows the crop state`() {
        val potato = requireNotNull(Crops.forKey("potato"))
        val state = PatchState(cropKey = potato.key, stage = 0, lives = 3)
        assertEquals(potato.growth[0], state.varbitValue())

        state.watered = true
        assertEquals(potato.watered[0], state.varbitValue())

        state.watered = false
        state.stage = 2
        state.diseased = true
        assertEquals(potato.diseasedState(2), state.varbitValue())

        state.diseased = false
        state.dead = true
        assertEquals(potato.deadState(2), state.varbitValue())
    }

    @Test
    fun `compost lowers disease chance but never below zero`() {
        for (crop in Crops.ALL) {
            var previous = Int.MAX_VALUE
            for (compost in Compost.entries) {
                val chance = crop.diseaseChance / compost.diseaseDivisor
                assertTrue(chance <= previous, "${crop.key} got riskier with ${compost.name}")
                assertTrue(chance >= 0)
                previous = chance
            }
        }
    }

    @Test
    fun `only flowers are single harvest`() {
        for (crop in Crops.ALL) {
            val expected = crop.kind == PatchKind.FLOWER
            assertEquals(expected, crop.singleHarvest, crop.key)
        }
    }

    @Test
    fun `crops can never catch disease on the first or last cycle`() {
        for (crop in Crops.ALL) {
            assertTrue(!crop.canCatchDisease(0), "${crop.key} stage 0")
            assertTrue(!crop.canCatchDisease(crop.cycles), "${crop.key} final stage")
            assertTrue(crop.canCatchDisease(1) || crop.cycles <= 1, "${crop.key} stage 1")
        }
    }

    @Test
    fun `default chance to save constants are used where no override is given`() {
        val guam = requireNotNull(Crops.forKey("guam"))
        assertEquals(Crop.DEFAULT_SAVE_LOW, guam.saveLifeLow)
        assertEquals(Crop.DEFAULT_SAVE_HIGH, guam.saveLifeHigh)
    }
}
