package org.rsmod.content.quest.area.wilderness.magearena

import com.github.michaelbull.logging.InlineLogger
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.wilderness.magearena.MageArenaQuest.Companion.STAGE_DUEL
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.map.collision.isZoneValid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The four levers of the Mage Arena: the one in the ruined house that drops into the bank cave,
 * its twin in the cave that comes back up, and the pair by the arena wall that let duellists in
 * and out of the arena. The arena levers are Kolodion's, and he keeps them shut until a player
 * has agreed to his duel.
 *
 * The jump itself runs from a world queue a few ticks after the pull, so it does not depend on
 * the player's script surviving the wait; the destination zone is checked up front.
 */
class MageArenaLevers
@Inject
constructor(
    private val mageArena: MageArenaQuest,
    private val mageArena2: MageArena2Quest,
    private val fight: KolodionFight,
    private val worldRepo: WorldRepository,
    private val collision: CollisionFlagMap,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(HOUSE_LEVER) { pull(it.loc, MageArenaCoords.CAVE_LEVER_ARRIVAL) }
        onOpLoc1(CAVE_LEVER) { pull(it.loc, MageArenaCoords.HOUSE_LEVER_ARRIVAL) }
        onOpLoc1(ARENA_IN_LEVER) { enterArena(it.loc) }
        onOpLoc1(ARENA_OUT_LEVER) { pull(it.loc, MageArenaCoords.ARENA_OUTSIDE) }
    }

    private suspend fun ProtectedAccess.enterArena(lever: BoundLocInfo) {
        if (mageArena.stage(player) == 0) {
            arriveDelay()
            faceLoc(lever)
            startDialogue {
                chatNpcSpecific(
                    "Kolodion",
                    MageArenaCoords.KOLODION,
                    neutral,
                    "You're not allowed in there. Come downstairs if you want to enter my arena.",
                )
            }
            return
        }
        pull(lever, MageArenaCoords.ARENA_INSIDE) { arrived ->
            if (mageArena.stage(arrived) == STAGE_DUEL) {
                launcher.launch(arrived) { with(fight) { summonKolodion() } }
            }
        }
    }

    /**
     * Pulls [lever] and jumps the player to [dest] (or the nearest free tile to it) once the
     * pull and the teleport animations have played. [onArrive] runs right after the jump.
     * Everything after the click is scheduled on the world queue, so the sequence does not rely
     * on the player's script surviving a suspension. Returns false if nothing happened.
     */
    private fun ProtectedAccess.pull(
        lever: BoundLocInfo,
        dest: CoordGrid,
        onArrive: (Player) -> Unit = {},
    ): Boolean {
        if (mageArena2.isTeleBlocked(player)) {
            mes("A magical force stops you from teleporting.")
            return false
        }
        val target = collision.nearestFree(dest) ?: dest
        if (!collision.isZoneValid(target)) {
            logger.warn { "Mage Arena lever at ${lever.coords} cannot send ${player.displayName} to $target: zone not loaded." }
            mes("The lever creaks, but nothing happens.")
            return false
        }
        // Let a player who is still stepping up to the lever finish the step first.
        val start = if (player.hasMovedPreviousCycle) 1 else 0
        val uid = player.uid
        player.delay(start + PULL_TICKS + TELEPORT_TICKS)

        worldQueues.add(start) {
            val puller = uid.resolve(playerList) ?: return@add
            puller.faceLoc(lever)
            puller.anim(PULL_ANIM)
            runCatching { worldRepo.locAnim(lever, LEVER_ANIM) }
                .onFailure { logger.warn(it) { "Mage Arena lever animation failed at ${lever.coords}." } }
            puller.soundSynth(LEVER_SOUND)
            puller.mes("You pull the lever.")
        }
        worldQueues.add(start + PULL_TICKS) {
            val puller = uid.resolve(playerList) ?: return@add
            puller.anim(TELEPORT_ANIM)
            puller.spotanim(TELEPORT_SPOTANIM, height = TELEPORT_GFX_HEIGHT)
            puller.soundSynth(TELEPORT_SOUND)
        }
        worldQueues.add(start + PULL_TICKS + TELEPORT_TICKS) {
            val puller = uid.resolve(playerList) ?: return@add
            PathingEntityCommon.telejump(puller, collision, target)
            puller.resetAnim()
            logger.debug { "Mage Arena lever sent ${puller.displayName} to $target." }
            onArrive(puller)
        }
        return true
    }

    private companion object {
        private val logger = InlineLogger()

        const val HOUSE_LEVER = "loc.magearena_lever_to_cellar"
        const val CAVE_LEVER = "loc.magearena_lever_from_cellar"
        const val ARENA_IN_LEVER = "loc.magearena_lever_in"
        const val ARENA_OUT_LEVER = "loc.magearena_lever_out"

        const val PULL_ANIM = "seq.macro_lever_switch_down"
        const val LEVER_ANIM = "seq.pull_magearena_lever"
        const val LEVER_SOUND = "synth.lever"
        const val TELEPORT_ANIM = "seq.human_castteleport"
        const val TELEPORT_SPOTANIM = "spotanim.teleport_casting"
        const val TELEPORT_GFX_HEIGHT = 92
        const val TELEPORT_SOUND = "synth.teleport_all"

        /** The pull animation is three ticks long; the teleport cast follows it. */
        const val PULL_TICKS = 3
        const val TELEPORT_TICKS = 2
    }
}
