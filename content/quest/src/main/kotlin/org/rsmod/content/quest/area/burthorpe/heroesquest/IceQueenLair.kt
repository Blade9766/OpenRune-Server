package org.rsmod.content.quest.area.burthorpe.heroesquest

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.config.constants
import org.rsmod.api.config.refs.params
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.player.stat.miningLvl
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The rock slide on the north side of White Wolf Mountain that blocks the way down to the Ice
 * Queen's lair. A miner with a pickaxe and 50 Mining can clear a way through it, in either
 * direction; the slide itself stays put, so the player is carried across it.
 */
class IceQueenLair : PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc1(ROCK_SLIDE) {
            arriveDelay()
            mes("These rocks contain nothing interesting. They are just in the way.")
        }
        onOpLoc2(ROCK_SLIDE) { clear(it.vis) }
        onOpLocU(ROCK_SLIDE) {
            if (it.objType.isContentType(PICKAXE_CONTENT)) {
                clear(it.vis)
            } else {
                mes(constants.dm_default)
            }
        }
    }

    private suspend fun ProtectedAccess.clear(slide: BoundLocInfo) {
        arriveDelay()
        val pickaxe = bestPickaxe()
        if (pickaxe == null) {
            mesbox(
                "You need a pickaxe to clear the rockslide. You do not have one that you have " +
                    "the Mining level to use.",
            )
            return
        }
        if (player.miningLvl < REQUIRED_MINING) {
            mes("You need a Mining level of $REQUIRED_MINING to clear the rockslide.")
            return
        }
        faceLoc(slide)
        anim(pickaxeAnim(pickaxe))
        soundSynth(MINE_SOUND)
        delay(MINE_TICKS)
        val east = coords.x > slide.coords.x
        val z = coords.z.coerceIn(slide.coords.z, slide.coords.z + slide.adjustedLength - 1)
        val dest =
            if (east) {
                CoordGrid(slide.coords.x - 1, z, slide.coords.level)
            } else {
                CoordGrid(slide.coords.x + slide.adjustedWidth, z, slide.coords.level)
            }
        anim(CLIMB_SEQ)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = CROSS_TICKS * CLIENT_CYCLES_PER_TICK,
            dir = if (east) constants.em_face_west else constants.em_face_east,
            teleportType = TeleportType.Exempt,
        )
        delay(CROSS_TICKS)
    }

    private fun ProtectedAccess.bestPickaxe(): ItemServerType? {
        val carried = inv.filterNotNull { true } + listOfNotNull(player.righthand)
        return carried
            .map { getInvObj(it) }
            .filter {
                it.isContentType(PICKAXE_CONTENT) &&
                    player.miningLvl >= (it.paramOrNull(params.levelrequire) ?: 1)
            }
            .maxByOrNull { it.paramOrNull(params.levelrequire) ?: 1 }
    }

    private fun pickaxeAnim(pickaxe: ItemServerType): String {
        val seq = pickaxe.paramOrNull(params.skill_anim) ?: return DEFAULT_MINE_SEQ
        return RSCM.getReverseMapping(RSCMType.SEQ, seq.id)
    }

    private companion object {
        const val ROCK_SLIDE = "loc.herorockslide"
        const val PICKAXE_CONTENT = "content.mining_pickaxe"
        const val REQUIRED_MINING = 50

        const val MINE_TICKS = 3
        const val CROSS_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30

        const val DEFAULT_MINE_SEQ = "seq.human_mining_bronze_pickaxe"
        const val CLIMB_SEQ = "seq.human_walk_f"
        const val MINE_SOUND = "synth.mine_quick"
    }
}
