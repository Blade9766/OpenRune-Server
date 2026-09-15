package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.script.onApLocT
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.HAMMER
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.NAILS
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.PLANK
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_STARTED
import org.rsmod.game.hit.HitType
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The storm-wrecked bridge between the Lighthouse causeway and the Fremennik mainland: a plank on
 * each side of the gap makes a walkway Larrissa can balance along. Until both are laid the gap
 * has to be jumped, which can go badly for the unfit; afterwards it is a safe walk.
 */
class BrokenBridge @Inject constructor(private val horror: HorrorFromTheDeepQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        val inventory = ServerCacheManager.fromComponent(INVENTORY.asRSCM(RSCMType.COMPONENT))
        for (side in BridgeSide.entries) {
            onOpLoc1(side.loc) { cross() }
            onApLocT(side.loc, inventory) {
                if (!isWithinApRange(it.loc, 1)) {
                    return@onApLocT
                }
                if (it.objType?.id == PLANK.asRSCM(RSCMType.OBJ)) layPlank(it.loc, side) else mes("That won't help fix the bridge.")
            }
        }
    }

    private suspend fun ProtectedAccess.layPlank(spot: BoundLocInfo, side: BridgeSide) {
        if (horror[player, side.flag]) {
            mes("There is already a plank across this side of the bridge.")
            return
        }
        if (sideOf(coords) != side) {
            mes("You can't reach that side of the bridge from here.")
            return
        }
        if (horror.stage(player) != STAGE_STARTED) {
            mes("That won't help fix the bridge.")
            return
        }
        if (HAMMER !in player.inv) {
            mes("You need a hammer to nail the plank in place.")
            return
        }
        if (player.inv.count(NAILS) < NAILS_PER_PLANK) {
            mes("You need $NAILS_PER_PLANK steel nails to secure the plank.")
            return
        }
        faceSquare(spot.coords)
        anim(BUILD_SEQ)
        soundSynth(BUILD_SOUND)
        delay(BUILD_TICKS)
        if (invDel(inv, PLANK, 1, NAILS, NAILS_PER_PLANK).failure) {
            return
        }
        horror.set(player, side.flag)
        if (horror.bridgeRepaired(player)) {
            objbox(PLANK, "You have now made a makeshift walkway across the bridge.")
        } else {
            objbox(PLANK, "Using the plank, you build half of a makeshift walkway.")
        }
    }

    private suspend fun ProtectedAccess.cross() {
        val from = sideOf(coords)
        val to = from.opposite()
        if (coords != from.tile) {
            teleport(from.tile, TeleportType.Exempt)
            delay(1)
        }
        faceSquare(to.tile)
        if (horror.bridgeRepaired(player)) {
            glide(to.tile, WALK_SEQ, WALK_TICKS)
            mes("You walk carefully across the makeshift walkway.")
            return
        }
        soundSynth(JUMP_SOUND)
        glide(to.tile, JUMP_SEQ, JUMP_TICKS)
        soundSynth(LAND_SOUND)
        if (statRandom("stat.agility", SUCCESS_LOW, SUCCESS_HIGH, 0)) {
            statAdvance("stat.agility", JUMP_XP)
            return
        }
        statAdvance("stat.agility", FAIL_XP)
        val damage = random.of(1, MAX_DAMAGE).coerceAtMost(player.hitpoints)
        queueHit(delay = 1, type = HitType.Typeless, damage = damage)
        say("Ouch!")
        mes("You land awkwardly and hurt yourself.")
    }

    private suspend fun ProtectedAccess.glide(dest: CoordGrid, seq: String, ticks: Int) {
        anim(seq)
        exactMove(
            start = coords,
            end = dest,
            delay1 = 0,
            delay2 = ticks * CLIENT_CYCLES_PER_TICK,
            dir = if (dest.x > coords.x) constants.em_face_east else constants.em_face_west,
            teleportType = TeleportType.Exempt,
        )
        delay(ticks)
        resetAnim()
    }

    private fun sideOf(coords: CoordGrid): BridgeSide = if (coords.x <= BridgeSide.West.tile.x) BridgeSide.West else BridgeSide.East

    private enum class BridgeSide(val loc: String, val flag: HorrorFlag, val tile: CoordGrid) {
        West("loc.horror_broken_bridge_left_spot", HorrorFlag.BridgeLeft, CoordGrid(2596, 3608, 0)),
        East("loc.horror_broken_bridge_right_spot", HorrorFlag.BridgeRight, CoordGrid(2598, 3608, 0)),
        ;

        fun opposite(): BridgeSide = if (this == West) East else West
    }

    private companion object {
        const val INVENTORY = "component.inventory:items"
        const val NAILS_PER_PLANK = 30
        const val JUMP_XP = 1.0
        const val FAIL_XP = 0.7
        const val MAX_DAMAGE = 5
        const val SUCCESS_LOW = 128
        const val SUCCESS_HIGH = 256

        const val BUILD_SEQ = "seq.human_poh_build"
        const val JUMP_SEQ = "seq.human_longjump"
        const val WALK_SEQ = "seq.human_walk_logbalance"
        const val BUILD_TICKS = 3
        const val JUMP_TICKS = 2
        const val WALK_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30

        const val BUILD_SOUND = "synth.hammer_and_build"
        const val JUMP_SOUND = "synth.jump"
        const val LAND_SOUND = "synth.jump_land_bridge"
    }
}
