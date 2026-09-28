package org.rsmod.content.quest.area.mortton.shades.catacombs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Player.unlockedDoors: Int by intVarp("varp.shades_catacomb_doors")

/**
 * The Shade Catacombs beneath Mort'ton.
 *
 * The wooden doors north of Razmire's store let anyone who has finished the quest and carries a
 * shade key down into the catacombs, just inside the entrance's bronze door; going back north
 * through that door leads out again. Every other door opens for a key of its metal or better,
 * without using the key up, and a door the player has unlocked stays passable for them until they
 * leave, so a key spent on a chest can never shut anyone in. The Altar of the Damned in the centre
 * restores prayer for bleached bones.
 */
class ShadeCatacombs
@Inject
constructor(
    private val shades: ShadesOfMorttonQuest,
    private val passages: GenericPassageScript,
    private val locRepo: LocRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ENTRANCE_LEFT) { enter(it.loc) }
        onOpLoc1(ENTRANCE_RIGHT) { enter(it.loc) }
        for (metal in ShadeMetal.entries) {
            onOpLoc1(metal.door) { openDoor(it.loc, metal) }
        }
        onOpLocU(ALTAR, BLEACHED_BONES) { offerBones() }
        onOpLocU(ALTAR) { mes("Nothing interesting happens.") }
    }

    private suspend fun ProtectedAccess.enter(door: BoundLocInfo) {
        if (!shades.isComplete(player)) {
            mes("You need to have completed Shades of Mort'ton to enter the catacombs.")
            return
        }
        if (ShadeKeys.bestMetal(inv) == null) {
            mes("The doors are locked. You need a shade key to open them.")
            return
        }
        faceSquare(door.adjustedCentre)
        soundSynth(OPEN_SOUND)
        mes("You unlock the doors and make your way down into the tombs.")
        delay(1)
        player.unlockedDoors = 0
        telejump(CATACOMB_ENTRY)
        openEntrance()
    }

    /** Swapping the doors ends the script, so it is the last thing done. */
    private fun openEntrance() {
        val leaves =
            listOf(
                Triple(ENTRANCE_LEFT_COORDS, ENTRANCE_LEFT, ENTRANCE_LEFT_OPEN),
                Triple(ENTRANCE_RIGHT_COORDS, ENTRANCE_RIGHT, ENTRANCE_RIGHT_OPEN),
            )
        for ((coords, closed, open) in leaves) {
            val loc = locRepo.findExact(coords, locType(closed)) ?: continue
            locRepo.change(loc, locType(open), ENTRANCE_OPEN_TICKS)
        }
    }

    private suspend fun ProtectedAccess.openDoor(door: BoundLocInfo, metal: ShadeMetal) {
        if (door.coords == EXIT_DOOR) {
            leave()
            return
        }
        val bit = DOORS.indexOf(door.coords).takeIf { it >= 0 }?.let { 1 shl it } ?: 0
        val unlocked = player.unlockedDoors and bit != 0
        val best = ShadeKeys.bestMetal(inv)
        if (!unlocked && (best == null || best < metal)) {
            mes("The door is locked. You need a ${metal.key} key or better to open it.")
            return
        }
        player.unlockedDoors = player.unlockedDoors or bit
        with(passages) { walkThrough(door, locType(metal.door)) }
    }

    private suspend fun ProtectedAccess.leave() {
        soundSynth(OPEN_SOUND)
        mes("You make your way back out of the catacombs.")
        delay(1)
        player.unlockedDoors = 0
        telejump(SURFACE_EXIT)
    }

    private suspend fun ProtectedAccess.offerBones() {
        anim(PRAY_SEQ)
        soundSynth(PRAYER_SOUND)
        delay(1)
        invDel(inv, BLEACHED_BONES)
        statRestore("stat.prayer")
        mes("You offer the bleached bones to the altar and feel your prayer restored.")
    }

    private fun locType(name: String) =
        requireNotNull(ServerCacheManager.getObject(name.asRSCM(RSCMType.LOC))) { "Missing loc: $name" }

    internal companion object {
        const val ENTRANCE_LEFT = "loc.shadelairentrancel"
        const val ENTRANCE_RIGHT = "loc.shadelairentrancer"
        const val ENTRANCE_LEFT_OPEN = "loc.shadelairdooropenl"
        const val ENTRANCE_RIGHT_OPEN = "loc.shadelairdooropenr"
        const val ENTRANCE_OPEN_TICKS = 3
        val ENTRANCE_LEFT_COORDS = CoordGrid(3485, 3320, 0)
        val ENTRANCE_RIGHT_COORDS = CoordGrid(3484, 3320, 0)

        const val ALTAR = "loc.shade_lair_temple_altar"
        const val BLEACHED_BONES = "obj.shade_bleached_bones"

        const val OPEN_SOUND = "synth.iron_door_open"
        const val PRAYER_SOUND = "synth.prayer_recharge"
        const val PRAY_SEQ = "seq.human_pray"

        /** Just inside the entrance's bronze door, and the tile outside the surface doors. */
        val CATACOMB_ENTRY = CoordGrid(3493, 9725, 0)
        val SURFACE_EXIT = CoordGrid(3485, 3318, 0)

        /** The entrance's bronze door, on the north edge of the corridor leading out. */
        val EXIT_DOOR = CoordGrid(3493, 9726, 0)

        /** Every door in the catacombs; a door's index is its bit in `varp.shades_catacomb_doors`. */
        val DOORS =
            listOf(
                CoordGrid(3482, 9723, 0),
                CoordGrid(3493, 9726, 0),
                CoordGrid(3504, 9723, 0),
                CoordGrid(3471, 9710, 0),
                CoordGrid(3479, 9710, 0),
                CoordGrid(3487, 9710, 0),
                CoordGrid(3493, 9715, 0),
                CoordGrid(3499, 9710, 0),
                CoordGrid(3507, 9710, 0),
                CoordGrid(3515, 9710, 0),
                CoordGrid(3465, 9668, 0),
                CoordGrid(3465, 9677, 0),
                CoordGrid(3465, 9686, 0),
                CoordGrid(3469, 9668, 0),
                CoordGrid(3478, 9681, 0),
                CoordGrid(3479, 9692, 0),
                CoordGrid(3482, 9668, 0),
                CoordGrid(3482, 9677, 0),
                CoordGrid(3482, 9686, 0),
                CoordGrid(3462, 9708, 0),
                CoordGrid(3464, 9700, 0),
                CoordGrid(3464, 9716, 0),
                CoordGrid(3467, 9708, 0),
                CoordGrid(3501, 9677, 0),
                CoordGrid(3507, 9671, 0),
                CoordGrid(3507, 9683, 0),
                CoordGrid(3507, 9692, 0),
                CoordGrid(3513, 9677, 0),
            )
    }
}
