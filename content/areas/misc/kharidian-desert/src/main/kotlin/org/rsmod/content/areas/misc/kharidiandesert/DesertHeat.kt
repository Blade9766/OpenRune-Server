package org.rsmod.content.areas.misc.kharidiandesert

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.player.front
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Desert heat in the open Kharidian Desert south of the Shantay Pass.
 *
 * Every so often - 90 seconds by default, less in armour, more in desert clothing (see
 * [DesertHeatGear]) - the heat evaporates any open water the player carries and they take a drink
 * from a waterskin, or take 1-10 damage if they have none left. Towns have their own music areas
 * and so fall outside the heat; the Bedabin camp and the Desert Mining Camp are shaded too. The
 * timer holds off while the player is busy in a dialogue, and resets on leaving the desert.
 */
class DesertHeat
@Inject
constructor(private val areas: AreaChecker, private val launcher: ProtectedAccessLauncher) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged { updateHeat(player) }
        onPlayerLogin { updateHeat(player) }
        onPlayerSoftTimer(HEAT_TIMER) {
            if (!launcher.launch(player) { heatStrikes() }) {
                player.softTimer(HEAT_TIMER, BUSY_RETRY_CYCLES)
            }
        }
    }

    private fun updateHeat(player: Player) {
        val heated = inHeat(player.coords)
        val running = HEAT_TIMER in player.softTimerMap
        if (heated && !running) {
            player.softTimer(HEAT_TIMER, DesertHeatGear.interval(player))
        } else if (!heated && running) {
            player.clearSoftTimer(HEAT_TIMER)
        }
    }

    private suspend fun ProtectedAccess.heatStrikes() {
        if (!inHeat(coords)) {
            clearSoftTimer(HEAT_TIMER)
            return
        }
        softTimer(HEAT_TIMER, DesertHeatGear.interval(player))
        if (player.front.isType(DESERT_AMULET_ELITE)) {
            return
        }
        for ((full, empty, name) in OPEN_WATER) {
            if (invReplace(inv, full, 1, empty).success) {
                mes("The water in your $name evaporates in the desert heat.")
            }
        }
        for ((skin, drunk) in WATERSKIN_SIPS) {
            if (skin in inv) {
                invReplace(inv, skin, 1, drunk)
                mes("You take a drink of water.")
                anim(DRINK_SEQ)
                soundSynth(DRINK_SOUND)
                return
            }
        }
        if (Waterskins.EMPTY in inv) {
            mes("Perhaps you should fill up one of your empty waterskins.")
        } else {
            mes("You should get a waterskin for any travelling in the desert.")
        }
        mes("You start dying of thirst while you're in the desert.")
        val damage = random.of(MIN_DAMAGE, MAX_DAMAGE).coerceAtMost(player.hitpoints)
        queueHit(player, delay = 0, type = HitType.Typeless, damage = damage)
    }

    private fun inHeat(coords: CoordGrid): Boolean {
        if (coords.level != 0 || coords.x !in MIN_X..MAX_X || coords.z !in MIN_Z..MAX_Z) {
            return false
        }
        if (SHADED.any { it.contains(coords) }) {
            return false
        }
        return HEAT_AREAS.any { areas.inArea(it, coords) }
    }

    private data class Box(val minX: Int, val minZ: Int, val maxX: Int, val maxZ: Int) {
        fun contains(coords: CoordGrid): Boolean =
            coords.x in minX..maxX && coords.z in minZ..maxZ
    }

    private data class OpenWater(val full: String, val empty: String, val name: String)

    private companion object {
        const val HEAT_TIMER = "timer.desert_heat"
        const val BUSY_RETRY_CYCLES = 1

        const val MIN_DAMAGE = 1
        const val MAX_DAMAGE = 10

        const val DESERT_AMULET_ELITE = "obj.desert_amulet_elite"
        const val DRINK_SEQ = "seq.human_eat"
        const val DRINK_SOUND = "synth.liquid"

        /* Everything south of the Shantay Pass gate; the pass itself is at z 3116. */
        const val MIN_X = 3135
        const val MAX_X = 3536
        const val MIN_Z = 2560
        const val MAX_Z = 3115

        /** The open-desert music areas; each town has its own track, and so no heat. */
        val HEAT_AREAS =
            listOf(
                "area.kharidian_desert",
                "area.dunes_of_eternity",
                "area.pharaohs_tomb",
                "area.ruins_of_isolation",
                "area.the_golem",
            )

        /** The Bedabin tents and the Desert Mining Camp, which the desert music plays over. */
        val SHADED =
            listOf(
                Box(3154, 3019, 3186, 3055),
                Box(3274, 3011, 3306, 3043),
            )

        val OPEN_WATER =
            listOf(
                OpenWater("obj.jug_water", "obj.jug_empty", "jug"),
                OpenWater("obj.bowl_water", "obj.bowl_empty", "bowl"),
                OpenWater("obj.bucket_water", "obj.bucket_empty", "bucket"),
                OpenWater("obj.vial_water", "obj.vial_empty", "vial"),
            )

        /** The emptiest skin is drunk from first, so full ones last as long as possible. */
        val WATERSKIN_SIPS =
            listOf(
                Waterskins.DOSES[1] to Waterskins.DOSES[0],
                Waterskins.DOSES[2] to Waterskins.DOSES[1],
                Waterskins.DOSES[3] to Waterskins.DOSES[2],
                Waterskins.DOSES[4] to Waterskins.DOSES[3],
            )
    }
}
