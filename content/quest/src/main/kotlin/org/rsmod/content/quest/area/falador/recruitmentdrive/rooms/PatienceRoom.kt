package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState.PATIENCE_PASSED
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState.PATIENCE_WAITING
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdPatiencePhase
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdPatienceTicks
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Tinley's test of patience: after his one-word clue the player must do nothing until he speaks
 * again, [WAIT_TICKS] game cycles (nine seconds) after the chat closes.
 *
 * A soft timer looks at the player every cycle. Anything that is a game action fails the test: a
 * step or a route (walking or clicking the ground), an interaction with a loc, npc, ground item or
 * player (taking the hourglass included), and anything that closes the player's interfaces the way
 * an action does (item and equipment ops, emotes), which shows as the [WATCH_QUEUE] weak queue
 * disappearing. Keepalives, chat, the camera, interface redraws and other client traffic touch none
 * of these, so they never fail it.
 */
@Singleton
class PatienceRoom @Inject constructor(private val testing: RecruitmentTesting) : PluginScript(), TestRoomScript {
    override val room = TestRoom.PATIENCE

    private val starts = HashMap<PlayerUid, CoordGrid>()

    override fun ScriptContext.startup() {
        testing.register(this@PatienceRoom)
        onOpNpc1(TINLEY) { startDialogue(it.npc) { talk() } }
        onPlayerSoftTimer(TIMER) { tick(player) }
        onPlayerQueue(WATCH_QUEUE) {}
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        testing.grounds.spawnObj(player, HOURGLASS, HOURGLASS_TILE)
        val tinley = testing.grounds.observer(player, room) ?: return
        startDialogue(tinley) { chatNpc(neutral, "Ah, ${player.displayName}, you have arrived. Speak to me to begin your task.") }
    }

    override fun leave(player: Player) {
        stopWatching(player)
        if (player.rdPatiencePhase == PATIENCE_WAITING) {
            player.rdPatiencePhase = 0
        }
    }

    private suspend fun Dialogue.talk() {
        val name = player.displayName
        if (RecruitmentState.passed(player, room) || player.rdPatiencePhase == PATIENCE_PASSED) {
            chatNpc(happy, "Patience is a virtue that few possess in this world. Excellent work, $name. Please step through the portal to meet your next challenge.")
            return
        }
        if (!testing.testing(player, room) || player.rdPatiencePhase == PATIENCE_WAITING) {
            return
        }
        chatNpc(neutral, "Ah, welcome $name. I have but one clue for you to pass this room's puzzle: 'Patience'.")
        if (testing.testing(player, room)) {
            player.rdPatiencePhase = PATIENCE_WAITING
            player.rdPatienceTicks = 0
            player.softTimer(TIMER, 1)
        }
    }

    private fun stopWatching(player: Player) {
        player.clearSoftTimer(TIMER)
        player.clearWeakQueue(WATCH_QUEUE)
        starts.remove(player.uid)
        player.rdPatienceTicks = 0
    }

    /** Whether the player did something since the wait began; see the class description. */
    fun acted(player: Player): Boolean {
        val start = starts[player.uid] ?: return true
        return player.coords != start ||
            player.routeDestination.isNotEmpty() ||
            player.interaction != null ||
            WATCH_QUEUE !in player.weakQueueList
    }

    fun tick(player: Player) {
        if (player.rdPatiencePhase != PATIENCE_WAITING || !testing.testing(player, room)) {
            stopWatching(player)
            return
        }
        val attempt = testing.attempt(player)
        if (player.rdPatienceTicks == 0) {
            if (player.isAccessProtected) {
                player.softTimer(TIMER, 1)
                return
            }
            starts[player.uid] = player.coords
            player.weakQueue(WATCH_QUEUE, WATCH_QUEUE_CYCLES)
            player.rdPatienceTicks = 1
            player.softTimer(TIMER, 1)
            return
        }
        if (acted(player)) {
            stopWatching(player)
            player.rdPatiencePhase = 0
            testing.launchWhenFree(player, attempt) { with(testing) { failRoom(room, attempt) } }
            return
        }
        if (player.rdPatienceTicks < WAIT_TICKS) {
            player.rdPatienceTicks++
            player.softTimer(TIMER, 1)
            return
        }
        stopWatching(player)
        player.rdPatiencePhase = PATIENCE_PASSED
        testing.launchWhenFree(player, attempt) { with(testing) { passRoom(room, attempt) } }
    }

    companion object {
        const val TINLEY = "npc.rd_observer_room_4"
        const val HOURGLASS = "obj.rd_hourglass"

        /** On the room's table (`loc.rd_wooden_table`), where the cache map spawns it. */
        val HOURGLASS_TILE = CoordGrid(2475, 4956, 0)

        const val TIMER = "timer.rd_patience"
        const val WATCH_QUEUE = "queue.rd_patience_watch"

        /** Nine seconds, as the RS3 transcript times it (the OSRS guide says "about eight"). */
        const val WAIT_TICKS = 15

        /** Longer than the test, so the marker never runs out on its own. */
        const val WATCH_QUEUE_CYCLES = 100
    }
}
