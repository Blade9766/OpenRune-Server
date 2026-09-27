package org.rsmod.content.other.barrows

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.lefthand
import org.rsmod.api.player.output.CamShakeAxis
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What being at the Barrows does to a player over time: the brothers overlay on the surface and
 * below, the brothers' faces draining prayer every 30 ticks underground, the collapsing tunnels
 * after the chest is looted, and ending a looted run once the player leaves the tunnels.
 */
class BarrowsAreaScript
@Inject
constructor(
    private val eventBus: EventBus,
    private val spawns: BarrowsSpawns,
    private val random: GameRandom,
) : PluginScript() {
    private val drainImmunityHilts: Set<Int> by lazy {
        IMMUNITY_HILTS.mapTo(mutableSetOf()) { it.asRSCM(RSCMType.OBJ) }
    }

    override fun ScriptContext.startup() {
        onPlayerCoordsChanged { moved(player, lastKnownCoords) }
        onPlayerSoftTimer(PRAYER_DRAIN_TIMER) { drainPrayer(player) }
        onPlayerSoftTimer(COLLAPSE_TIMER) { collapse(player) }
        onPlayerLogout { spawns.dismissAll(player) }
    }

    private fun moved(player: Player, from: CoordGrid) {
        val to = player.coords
        if (from == to) {
            return
        }
        val wasAtBarrows = BarrowsCoords.inBarrows(from)
        val atBarrows = BarrowsCoords.inBarrows(to)
        if (atBarrows && !wasAtBarrows) {
            player.ifOpenOverlay(OVERLAY, eventBus)
        } else if (!atBarrows && wasAtBarrows) {
            player.ifCloseOverlay(OVERLAY, eventBus)
        }

        val wasUnderground = BarrowsCoords.inUnderground(from)
        val underground = BarrowsCoords.inUnderground(to)
        if (underground && !wasUnderground) {
            player.softTimer(PRAYER_DRAIN_TIMER, PRAYER_DRAIN_INTERVAL)
        }
        if (wasUnderground && (!underground || from.level != to.level)) {
            spawns.dismissAll(player)
        }

        val wasInTunnels = BarrowsCoords.inTunnels(from)
        val inTunnels = BarrowsCoords.inTunnels(to)
        if (inTunnels && !wasInTunnels) {
            enterTunnels(player)
        } else if (wasInTunnels && !inTunnels) {
            leaveTunnels(player)
        }
    }

    private fun enterTunnels(player: Player) {
        BarrowsRun.ensureStarted(player, random)
        BarrowsRun.showLadders(player)
        if (BarrowsRun.isLooted(player)) {
            player.softTimer(COLLAPSE_TIMER, COLLAPSE_INTERVAL)
        }
    }

    private fun leaveTunnels(player: Player) {
        player.clearSoftTimer(COLLAPSE_TIMER)
        Camera.camShakeResetAll(player)
        if (BarrowsRun.isLooted(player)) {
            BarrowsRun.reset(player)
        }
    }

    private fun drainPrayer(player: Player) {
        if (!BarrowsCoords.inUnderground(player.coords)) {
            player.clearSoftTimer(PRAYER_DRAIN_TIMER)
            return
        }
        val face = Brother.entries[random.of(Brother.entries.size)]
        player.runClientScript(
            NODNOD_SCRIPT.asRSCM(RSCMType.CLIENTSCRIPT),
            face.overlayHead.asRSCM(RSCMType.COMPONENT),
            face.faceModel,
        )
        if (player.lefthand?.id in drainImmunityHilts) {
            return
        }
        val drain = BASE_PRAYER_DRAIN + BarrowsRun.killed(player).size
        player.statSub(PRAYER, drain, 0)
        player.mes("The brothers drain your prayer.")
    }

    private fun collapse(player: Player) {
        if (!BarrowsCoords.inTunnels(player.coords) || !BarrowsRun.isLooted(player)) {
            player.clearSoftTimer(COLLAPSE_TIMER)
            Camera.camShakeResetAll(player)
            return
        }
        if (!random.randomBoolean(ROCKFALL_CHANCE)) {
            Camera.camShakeResetAll(player)
            return
        }
        if (player.vars[CAMERA_EFFECTS_DISABLED] == 0) {
            Camera.camShake(player, CamShakeAxis.LEFT_RIGHT, SHAKE_RANDOM, 0, 0)
            Camera.camShake(player, CamShakeAxis.UP_DOWN, SHAKE_RANDOM, 0, 0)
        }
        player.queueHit(
            delay = 1,
            type = HitType.Typeless,
            damage = random.of(ROCKFALL_DAMAGE),
            modifier = NoopPlayerHitModifier,
        )
        player.mes("Some rocks fall from the ceiling and hit you.")
    }

    companion object {
        const val OVERLAY = "interface.barrows_overlay"
        const val PRAYER_DRAIN_TIMER = "timer.barrows_prayer_drain"
        const val COLLAPSE_TIMER = "timer.barrows_collapse"
        const val PRAYER_DRAIN_INTERVAL = 30
        const val COLLAPSE_INTERVAL = 5

        private const val NODNOD_SCRIPT = "clientscript.[clientscript,barrows_nodnod]"
        private const val PRAYER = "stat.prayer"
        private const val BASE_PRAYER_DRAIN = 8
        private const val CAMERA_EFFECTS_DISABLED = "varbit.option_camera_effect_barrows_disabled"
        private const val ROCKFALL_CHANCE = 3
        private const val SHAKE_RANDOM = 4
        private val ROCKFALL_DAMAGE = 1..8

        val IMMUNITY_HILTS =
            listOf(
                "obj.ca_offhand_medium",
                "obj.ca_offhand_hard",
                "obj.ca_offhand_elite",
                "obj.ca_offhand_master",
                "obj.ca_offhand_grandmaster",
            )
    }
}
