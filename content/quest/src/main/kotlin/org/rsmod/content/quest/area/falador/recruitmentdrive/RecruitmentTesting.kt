package org.rsmod.content.quest.area.falador.recruitmentdrive

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentDriveQuest.Companion.SIR_TIFFY
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/** The room the player was in when they last left the grounds without a proper exit (0: none). */
internal var Player.rdRoomLogout by intVarBit("varbit.rd_roomlogout")

/** One testing room's own behaviour: what happens on arrival, and what to undo when leaving it. */
interface TestRoomScript {
    val room: TestRoom

    suspend fun ProtectedAccess.arrive(attempt: Int)

    fun leave(player: Player) {}
}

/**
 * Runs an attempt at the tests: taking the player into their copy of the grounds, loading each room
 * in the attempt's order, passing, failing, quitting and finishing.
 *
 * Every room action is checked against [attempt], a number that changes whenever the player enters
 * a room, leaves the grounds, dies or logs out, and against the room the player is standing in. A
 * dialogue or interface click that belongs to an earlier room or attempt therefore does nothing.
 */
@Singleton
class RecruitmentTesting
@Inject
constructor(
    val quest: RecruitmentDriveQuest,
    val grounds: TestingGrounds,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) {
    /** Replaces the world-queue launch in tests, which run scripts without a game loop. */
    internal var launchHook: ((Player, suspend ProtectedAccess.() -> Unit) -> Unit)? = null

    private val scripts = mutableMapOf<TestRoom, TestRoomScript>()
    private val attempts = HashMap<PlayerUid, Int>()

    fun register(script: TestRoomScript) {
        scripts[script.room] = script
    }

    fun attempt(player: Player): Int = attempts[player.uid] ?: 0

    private fun nextAttempt(player: Player): Int {
        val next = attempt(player) + 1
        attempts[player.uid] = next
        return next
    }

    /** The player is in [room], it is the room they are being tested in, and [attempt] is current. */
    fun testing(player: Player, room: TestRoom, attempt: Int = attempt(player)): Boolean =
        attempt == attempt(player) &&
            grounds.inside(player) &&
            RecruitmentState.currentRoom(player) == room &&
            grounds.roomOf(player) == room

    /** The player is in [room] and has already passed it. */
    fun passedHere(player: Player, room: TestRoom): Boolean =
        grounds.inside(player) && RecruitmentState.passed(player, room) && grounds.roomOf(player) == room &&
            RecruitmentState.order(player)?.contains(room) == true

    /** [local] (a tile of the player's copy) belongs to [room]. */
    fun inRoom(player: Player, local: CoordGrid, room: TestRoom): Boolean {
        val world = grounds.world(player, local) ?: return false
        return world in room
    }

    fun worldOf(player: Player, local: CoordGrid): CoordGrid? = grounds.world(player, local)

    /** Why the player may not be taken to the grounds, or null when they may. */
    fun entryRefusal(player: Player): String? =
        when {
            !player.inv.isEmpty() || !player.worn.isEmpty() -> REFUSAL_ITEMS
            else -> null
        }

    /**
     * Takes the player to their copy of the grounds and the first room they have not passed, rolling
     * a new order when no attempt is under way.
     */
    suspend fun ProtectedAccess.beginTesting(): Boolean {
        if (entryRefusal(player) != null || grounds.inside(player)) {
            return false
        }
        if (RecruitmentState.order(player) == null || RecruitmentState.allPassed(player)) {
            RecruitmentState.clearAttempt(player)
            RecruitmentState.setOrder(player, RoomOrder.roll(random))
        }
        val room = RecruitmentState.currentRoom(player) ?: return false
        RecruitmentState.clearRoom(player, keep = room)
        with(grounds) {
            if (!open(room.arrival)) return false
        }
        enterRoom(room)
        return true
    }

    /** Loads [room]: clears what the last room left behind and moves the player to its entrance. */
    suspend fun ProtectedAccess.enterRoom(room: TestRoom) {
        val attempt = nextAttempt(player)
        leaveRooms(player)
        SessionItems.strip(player)
        RecruitmentState.clearRoom(player, keep = room)
        player.rdRoomLogout = room.id
        with(grounds) { moveTo(room.arrival) }
        val script = scripts[room] ?: return
        with(script) { arrive(attempt) }
    }

    /**
     * Marks [room] passed, once, plays its jingle and has its observer say so. After the fifth room
     * the player is taken straight back to Falador Park.
     */
    suspend fun ProtectedAccess.passRoom(
        room: TestRoom,
        attempt: Int,
        success: (suspend Dialogue.() -> Unit)? = null,
    ): Boolean {
        if (!testing(player, room, attempt)) {
            return false
        }
        RecruitmentState.markPassed(player, room)
        player.midiJingle(room.jingle)
        val passed = RecruitmentState.passedCount(player)
        val observer = grounds.observer(player, room)
        val name = player.displayName
        val congratulate: suspend Dialogue.() -> Unit = {
            when (passed) {
                RoomOrder.SIZE -> chatNpc(happy, "Excellent work! You have passed all five of the required tests! Please accept my congratulations!")
                RoomOrder.SIZE - 1 -> {
                    chatNpc(happy, "Well done, that was your fourth test, you only have one more to go.")
                    chatNpc(happy, "Please step through the portal to your final test... ...and good luck, $name.")
                }
                else ->
                    if (success != null) {
                        success()
                    } else {
                        chatNpc(happy, "Excellent work, $name. Please step through the portal to meet your next challenge.")
                    }
            }
        }
        if (observer != null) startDialogue(observer, conversation = congratulate) else startDialogue(congratulate)
        if (passed == RoomOrder.SIZE && attempt(player) == attempt) {
            finishGrounds()
        }
        return true
    }

    /** The observer's failure line, then back to Falador Park with the attempt cleared. */
    suspend fun ProtectedAccess.failRoom(room: TestRoom, attempt: Int, line: String? = FAIL_LINE): Boolean {
        if (!testing(player, room, attempt)) {
            return false
        }
        nextAttempt(player)
        val observer = grounds.observer(player, room)
        if (line != null) {
            if (observer != null) startDialogue(observer) { chatNpc(sad, line) }
        }
        RecruitmentState.clearAttempt(player)
        exitGrounds()
        startDialogue {
            chatNpcSpecific("Sir Tiffy Cashien", SIR_TIFFY, laugh, "Oh, jolly bad luck, what? Not quite the brainbox you thought you were, eh?")
            chatNpcSpecific(
                "Sir Tiffy Cashien",
                SIR_TIFFY,
                happy,
                "Well, never mind! You have an open invitation to join our organisation, so when you're feeling a little smarter, come back and talk to me again!",
            )
        }
        return true
    }

    /** Leaving through a room's entrance portal: the attempt is given up and starts again from scratch. */
    suspend fun ProtectedAccess.quit(room: TestRoom) {
        if (!grounds.inside(player) || grounds.roomOf(player) != room) {
            return
        }
        nextAttempt(player)
        RecruitmentState.clearAttempt(player)
        exitGrounds()
    }

    /** The exit door or portal of a passed room: on to the next room of the attempt. */
    suspend fun ProtectedAccess.proceed(room: TestRoom) {
        if (!passedHere(player, room)) {
            return
        }
        val next = RecruitmentState.currentRoom(player)
        if (next == null) {
            finishGrounds()
            return
        }
        enterRoom(next)
    }

    private fun ProtectedAccess.finishGrounds() {
        nextAttempt(player)
        exitGrounds()
    }

    private fun ProtectedAccess.exitGrounds() {
        leaveRooms(player)
        SessionItems.strip(player)
        RecruitmentState.clearRoom(player)
        player.rdRoomLogout = 0
        with(grounds) { close(TestingGrounds.FALADOR_PARK) }
    }

    /**
     * The player left the grounds without an exit (logout or death): the copy is gone, the room's
     * state and items with it; the passed rooms stay for the next visit.
     */
    fun abandonVisit(player: Player) {
        if (!grounds.inside(player) && player.rdRoomLogout == 0) {
            return
        }
        nextAttempt(player)
        leaveRooms(player)
        grounds.forget(player)
        SessionItems.strip(player)
        RecruitmentState.clearRoom(player, keep = RecruitmentState.currentRoom(player))
        player.rdRoomLogout = 0
    }

    /**
     * Runs [block] for the player as soon as they are free (a timer or an npc's death cannot run a
     * dialogue itself), provided [attempt] is still the current one by then.
     */
    fun launchWhenFree(player: Player, attempt: Int, block: suspend ProtectedAccess.() -> Unit) {
        val hook = launchHook
        if (hook != null) {
            hook(player, block)
            return
        }
        schedule(player.uid, attempt, LAUNCH_ATTEMPTS, block)
    }

    private fun schedule(uid: PlayerUid, attempt: Int, remaining: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (attempt(player) != attempt) return@add
            if (!launcher.launch(player, block = block) && remaining > 0) {
                schedule(uid, attempt, remaining - 1, block)
            }
        }
    }

    fun forgetPlayer(player: Player) {
        attempts.remove(player.uid)
    }

    private fun leaveRooms(player: Player) {
        for (script in scripts.values) {
            script.leave(player)
        }
    }

    companion object {
        const val FAIL_LINE =
            "No... I am very sorry. Apparently you are not up to the challenge. I will return you " +
                "where you came from, better luck in the future."

        const val REFUSAL_ITEMS = "items"
        const val LAUNCH_ATTEMPTS = 50
    }
}
