package org.rsmod.content.areas.wilderness.locs

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isWalkBlocked
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The Wilderness teleport levers. The ones in Ardougne and Edgeville drop the player into the
 * Deserted Keep in level 56 Wilderness, the keep's own lever brings them back out. Anyone
 * heading in is warned first. A lever ignores the usual Wilderness level limits.
 */
class WildernessLevers
@Inject
constructor(
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(ARDOUGNE_LEVER) { pullIn(it.loc) }
        onOpLoc1(EDGEVILLE_LEVER) { pullIn(it.loc) }
        for (lever in KEEP_LEVERS) {
            onOpLoc1(lever) { pullOut(it.loc, choice = lever != KEEP_LEVER_DEFAULT) }
        }
    }

    private suspend fun ProtectedAccess.pullIn(lever: BoundLocInfo) {
        arriveDelay()
        faceLoc(lever)
        var proceed = false
        startDialogue {
            proceed =
                choice2(
                    "Yes, I'm brave.", 1,
                    "Eeep! The Wilderness... No thank you.", 2,
                    title = "Warning! Pulling the lever will teleport you deep into the Wilderness.",
                ) == 1
        }
        if (!proceed) {
            return
        }
        pull(lever, DESERTED_KEEP)
    }

    private suspend fun ProtectedAccess.pullOut(lever: BoundLocInfo, choice: Boolean) {
        arriveDelay()
        faceLoc(lever)
        var dest = ARDOUGNE
        if (choice) {
            var picked = 0
            startDialogue {
                picked = choice2("Ardougne", 1, "Edgeville", 2, title = "Where would you like to go?")
            }
            dest =
                when (picked) {
                    1 -> ARDOUGNE
                    2 -> EDGEVILLE
                    else -> return
                }
        }
        pull(lever, dest)
    }

    private suspend fun ProtectedAccess.pull(lever: BoundLocInfo, dest: CoordGrid) {
        anim(PULL_ANIM)
        worldRepo.locAnim(lever, LEVER_ANIM)
        soundSynth(LEVER_SOUND)
        mes("You pull the lever...")
        delay(PULL_TICKS)
        anim(TELEPORT_ANIM)
        spotanim(TELEPORT_SPOTANIM, height = TELEPORT_GFX_HEIGHT)
        soundSynth(TELEPORT_SOUND)
        delay(TELEPORT_TICKS)
        telejump(collision.nearestFree(dest) ?: dest, TeleportType.Exempt)
        resetAnim()
        mes("...and are teleported away.")
    }

    private fun CollisionFlagMap.nearestFree(center: CoordGrid, radius: Int = 2): CoordGrid? {
        val tiles = mutableListOf<CoordGrid>()
        for (dz in -radius..radius) {
            for (dx in -radius..radius) {
                tiles += center.translate(dx, dz)
            }
        }
        return tiles
            .sortedWith(compareBy({ it.chebyshevDistance(center) }, { it.z }, { it.x }))
            .firstOrNull { isZoneValid(it) && !isWalkBlocked(it) }
    }

    private companion object {
        const val ARDOUGNE_LEVER = "loc.wildinlever"
        const val EDGEVILLE_LEVER = "loc.edgeville_wildy_lever"
        const val KEEP_LEVER_DEFAULT = "loc.wildoutlever_default"

        /** The keep lever is a multiloc; the diary forms offer a choice of destination. */
        val KEEP_LEVERS =
            listOf(
                "loc.wildoutlever",
                KEEP_LEVER_DEFAULT,
                "loc.wildoutlever_diary",
                "loc.wildoutlever_diary_swapped",
            )

        /** Beside the keep lever at 3153,3923. */
        val DESERTED_KEEP = CoordGrid(3154, 3923, 0)

        /** Beside the Ardougne lever at 2561,3311 and the Edgeville lever at 3090,3475. */
        val ARDOUGNE = CoordGrid(2562, 3311, 0)
        val EDGEVILLE = CoordGrid(3090, 3475, 0)

        const val PULL_ANIM = "seq.human_pull_lever"
        const val LEVER_ANIM = "seq.lever_switch_up_down"
        const val LEVER_SOUND = "synth.lever"
        const val PULL_TICKS = 2
        const val TELEPORT_ANIM = "seq.human_castteleport"
        const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        const val TELEPORT_GFX_HEIGHT = 92
        const val TELEPORT_SOUND = "synth.teleport_all"
        const val TELEPORT_TICKS = 2
    }
}
