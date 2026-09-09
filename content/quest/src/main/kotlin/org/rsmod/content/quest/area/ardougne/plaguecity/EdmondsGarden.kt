package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.BUCKETS_NEEDED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.MUD_FILLED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.MUD_HOLE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SOIL_SOFTENED
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mud patch behind Edmond's house: four buckets of water soften it, a spade opens it into a
 * hole down to the sewers, and the hole stays until the mourners fill it in at the start of
 * Biohazard. The patch is a varbit multiloc (`loc.plaguemudpatch2`: mud, hole, filled in) with two
 * plain "Mud patch" tiles either side of it that share the same handling.
 */
class EdmondsGarden @Inject constructor(private val plagueCity: PlagueCityQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (patch in MUD_PATCHES) {
            for (water in WATER_CONTAINERS.keys) {
                onOpLocU(patch, water) { pourWater(water) }
            }
            onOpLocU(patch, SPADE) { dig() }
        }
        onOpLoc1(DUG_HOLE) { climbDown() }
        onOpHeld1(SPADE) { digWithSpade() }
    }

    private suspend fun ProtectedAccess.pourWater(container: String) {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        val stage = plagueCity.stage(player)
        if (stage < STAGE_HAS_GAS_MASK || !plagueCity.toldToDig.get(player) || stage > STAGE_HAS_GAS_MASK) {
            mesbox("You see no reason to do that at the moment.")
            return
        }
        val poured = plagueCity.waterPoured.get(player) + 1
        anim(POUR_SEQ)
        soundSynth(POUR_SOUND)
        invReplace(inv, container, 1, WATER_CONTAINERS.getValue(container))
        plagueCity.waterPoured.set(player, poured)
        delay(1)
        if (poured >= BUCKETS_NEEDED) {
            plagueCity.advanceTo(this, STAGE_SOIL_SOFTENED)
            mesbox("You pour water onto the soil. The soil is now soft enough to dig into.")
        } else {
            mesbox("You pour water onto the soil. The soil softens slightly.")
        }
    }

    /** The spade's own Dig option, when it is used standing on the patch. */
    private suspend fun ProtectedAccess.digWithSpade() {
        if (player.coords.chebyshevDistance(MUD_PATCH_TILE) > 1) {
            anim(DIG_SEQ)
            soundSynth(DIG_SOUND)
            delay(1)
            mes("You dig, but find nothing of interest.")
            return
        }
        dig()
    }

    private suspend fun ProtectedAccess.dig() {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        val stage = plagueCity.stage(player)
        when {
            plagueCity.mudState.get(player) == MUD_FILLED -> {
                anim(DIG_SEQ)
                soundSynth(DIG_SOUND)
                delay(1)
                objbox(SPADE, "The ground's been filled in and packed hard.")
            }
            plagueCity.mudState.get(player) == MUD_HOLE -> climbDown()
            stage < STAGE_SOIL_SOFTENED -> {
                anim(DIG_SEQ)
                soundSynth(DIG_SOUND)
                delay(1)
                objbox(SPADE, "You dig the soil... The ground is rather hard.")
            }
            else -> tunnelThrough()
        }
    }

    /** The soft soil gives way and Edmond follows the player down into the sewer. */
    private suspend fun ProtectedAccess.tunnelThrough() {
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(2)
        anim(DIG_SEQ)
        delay(2)
        soundSynth(CRUMBLE_SOUND)
        plagueCity.mudState.set(player, MUD_HOLE)
        plagueCity.edmondBelow.set(player, true)
        plagueCity.syncVars(player)
        if (plagueCity.stage(player) == STAGE_SOIL_SOFTENED) {
            plagueCity.advanceTo(this, STAGE_DUG_TUNNEL)
        }
        anim(FALL_SEQ)
        delay(1)
        telejump(SEWER_ARRIVAL)
        soundSynth(LAND_SOUND)
        mesbox("You dig deep into the soft soil... Suddenly it crumbles away! You fall through into the sewer. Edmond follows you down the hole.")
    }

    private suspend fun ProtectedAccess.climbDown() {
        arriveDelay()
        faceSquare(MUD_PATCH_TILE)
        anim(CLIMB_DOWN_SEQ)
        soundSynth(CLIMB_SOUND)
        delay(2)
        telejump(SEWER_ARRIVAL)
        mes("You climb down into the hole.")
    }

    companion object {
        /** The plain "Mud patch" tiles north and south of the multiloc, and its two forms. */
        val MUD_PATCHES = listOf("loc.plaguemudpatch1", "loc.plaguemudpatch2", "loc.plague_mud", "loc.plague_hole")
        const val DUG_HOLE = "loc.plague_hole"
        const val SPADE = "obj.spade"

        /** Full container to the empty one it leaves behind. */
        val WATER_CONTAINERS = mapOf("obj.bucket_water" to "obj.bucket_empty")

        val MUD_PATCH_TILE = CoordGrid(2566, 3332, 0)

        /** Beside the mud pile at the north end of the sewer corridor. */
        val SEWER_ARRIVAL = CoordGrid(2518, 9759, 0)

        const val POUR_SEQ = "seq.human_pickuptable"
        const val DIG_SEQ = "seq.human_dig"
        const val FALL_SEQ = "seq.human_falling"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val POUR_SOUND = "synth.liquid"
        const val DIG_SOUND = "synth.digspade"
        const val CRUMBLE_SOUND = "synth.crumble_all"
        const val LAND_SOUND = "synth.fall_land"
        const val CLIMB_SOUND = "synth.climb_under"
    }
}
