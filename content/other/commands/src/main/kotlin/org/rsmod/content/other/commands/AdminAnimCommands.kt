package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.seq.EntitySeq
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Animation browsing for admins. `::anim` needs a gameval name; these work on raw sequence ids so
 * every animation in the cache can be reached, and they step or cycle through them:
 * - `::animid <id>` plays one animation.
 * - `::animnext [step]` / `::animprev [step]` play the next or previous id after the last one
 *   played (defaults to a step of 1).
 * - `::animcycle <start> [end] [ticks]` plays each id from start to end in turn, waiting `ticks`
 *   between them (default 4). Doing anything else, such as walking, stops the cycle.
 *
 * Every play reports the id, its gameval name when it has one, and its length.
 */
class AdminAnimCommands @Inject constructor(private val launcher: ProtectedAccessLauncher) : PluginScript() {

    private val lastPlayed = HashMap<PlayerUid, Int>()

    override fun ScriptContext.startup() {
        onCommand("animid", "Play animation by id", ::animId) {
            invalidArgs = "Use as ::animid <id> (ex: ::animid 2710)"
        }
        onCommand("animnext", "Play the next animation id", { step(1) }) {
            invalidArgs = "Use as ::animnext [step] (ex: ::animnext 5)"
        }
        onCommand("animprev", "Play the previous animation id", { step(-1) }) {
            invalidArgs = "Use as ::animprev [step] (ex: ::animprev 5)"
        }
        onCommand("animcycle", "Play a range of animation ids in turn", ::animCycle) {
            invalidArgs = "Use as ::animcycle <start> [end] [ticks] (ex: ::animcycle 2700 2720 4)"
        }
    }

    private fun animId(cheat: Cheat) {
        val id = cheat.args.getOrNull(0)?.toIntOrNull()
        if (id == null) {
            cheat.player.mes("Use as ::animid <id> (ex: ::animid 2710)")
            return
        }
        play(cheat.player, id)
    }

    private fun Cheat.step(direction: Int) {
        val step = (args.getOrNull(0)?.toIntOrNull() ?: 1).coerceAtLeast(1) * direction
        var id = (lastPlayed[player.uid] ?: 0) + step
        // Skip ids the cache has no sequence for, up to a reasonable distance.
        var tries = 0
        while (ServerCacheManager.getAnim(id) == null && tries < MAX_SKIP) {
            id += direction
            tries++
        }
        play(player, id)
    }

    private fun animCycle(cheat: Cheat) =
        with(cheat) {
            val start = args.getOrNull(0)?.toIntOrNull()
            if (start == null) {
                player.mes("Use as ::animcycle <start> [end] [ticks] (ex: ::animcycle 2700 2720 4)")
                return
            }
            val end = (args.getOrNull(1)?.toIntOrNull() ?: (start + DEFAULT_CYCLE_LENGTH - 1))
                .coerceIn(start, start + MAX_CYCLE_LENGTH - 1)
            val ticks = (args.getOrNull(2)?.toIntOrNull() ?: DEFAULT_CYCLE_TICKS).coerceAtLeast(1)
            val launched =
                launcher.launch(player, busyText = "Finish what you are doing first.") {
                    player.mes("Cycling animations $start to $end, $ticks ticks each. Move to stop.")
                    for (id in start..end) {
                        if (!play(player, id)) {
                            continue
                        }
                        delay(ticks)
                    }
                    player.resetAnim()
                    player.mes("Animation cycle finished.")
                }
            if (!launched) {
                player.mes("You are busy; try again in a moment.")
            }
        }

    /** Plays [id] regardless of priority and reports it; false if the cache has no such sequence. */
    private fun play(player: Player, id: Int): Boolean {
        val type = ServerCacheManager.getAnim(id)
        if (type == null) {
            player.mes("There is no animation with id $id.")
            return false
        }
        val name = runCatching { RSCM.getReverseMapping(RSCMType.SEQ, id) }.getOrDefault("(no gameval name)")
        player.pendingSequence = EntitySeq(id, 0, type.priority)
        lastPlayed[player.uid] = id
        player.mes(
            "Anim $id: $name (priority ${type.priority}, ${type.totalDelay} client cycles, " +
                "${type.tickDuration} ticks)"
        )
        return true
    }

    private companion object {
        const val MAX_SKIP = 500
        const val DEFAULT_CYCLE_LENGTH = 20
        const val MAX_CYCLE_LENGTH = 200
        const val DEFAULT_CYCLE_TICKS = 4
    }
}
