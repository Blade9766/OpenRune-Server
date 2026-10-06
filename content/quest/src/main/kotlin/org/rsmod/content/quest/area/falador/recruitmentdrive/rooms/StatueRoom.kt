package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState.STATUE_MEMORISING
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState.STATUE_READY
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdStatueAnswer
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdStatueLayout
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdStatuePhase
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lady Table's test of memory. The twelve statues (bronze, silver and gold; sword, halberd, axe and
 * mace) are twelve multilocs on `varbit.rd_room_order`: value 0 shows all twelve, and each of values
 * 1-12 shows a shuffled layout with exactly one statue missing. The room picks one of those layouts,
 * once per visit, and keeps it on `varbit.rd_statue_answer`; after [MEMORISE_TICKS] (ten seconds)
 * the full set comes back and the player must touch the statue that was missing.
 *
 * As on the OSRS wiki, the statues are not changed while the player is in a dialogue: the swap waits
 * until the chat is closed.
 */
@Singleton
class StatueRoom @Inject constructor(private val testing: RecruitmentTesting) : PluginScript(), TestRoomScript {
    override val room = TestRoom.STATUES

    override fun ScriptContext.startup() {
        testing.register(this@StatueRoom)
        onOpNpc1(LADY_TABLE) { startDialogue(it.npc) { talk() } }
        for (statue in STATUES) {
            onOpLoc1(statue) { touch(it.loc, it.type.id) }
        }
        onPlayerSoftTimer(TIMER) { memoriseOver(player) }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        if (player.rdStatueAnswer == 0) {
            player.rdStatueAnswer = random.of(1, LAYOUTS)
        }
        player.rdStatuePhase = STATUE_MEMORISING
        player.rdStatueLayout = player.rdStatueAnswer
        val lady = testing.grounds.observer(player, room) ?: return
        val name = player.displayName
        var armed = false
        try {
            startDialogue(lady) {
                chatNpc(neutral, "Welcome, $name. This room will test your observation skills.")
                chatNpc(neutral, "Study the statues closely. There is one missing statue in this room.")
                chatNpc(neutral, "We will also mix the order up a little to make things interesting for you!")
                if (testing.testing(player, room, attempt)) {
                    access.softTimer(TIMER, MEMORISE_TICKS)
                }
                armed = true
                chatNpc(neutral, "You have 10 seconds to memorise the statues... starting NOW!")
            }
        } finally {
            // A player who walks off mid-greeting still gets the swap, or the room could never be solved.
            if (!armed && testing.testing(player, room, attempt)) {
                softTimer(TIMER, MEMORISE_TICKS)
            }
        }
    }

    override fun leave(player: Player) {
        player.clearSoftTimer(TIMER)
    }

    internal fun memoriseOver(player: Player) {
        if (player.rdStatuePhase != STATUE_MEMORISING || !testing.testing(player, room)) {
            player.clearSoftTimer(TIMER)
            return
        }
        if (player.isAccessProtected) {
            player.softTimer(TIMER, 1)
            return
        }
        player.clearSoftTimer(TIMER)
        player.rdStatuePhase = STATUE_READY
        player.rdStatueLayout = FULL_SET
        testing.launchWhenFree(player, testing.attempt(player)) {
            val lady = testing.grounds.observer(player, room) ?: return@launchWhenFree
            startDialogue(lady) {
                chatNpc(neutral, "We will now dim the lights and bring the missing statue back in.")
                chatNpc(neutral, "Please touch the statue you think has been added.")
            }
        }
    }

    private suspend fun Dialogue.talk() {
        if (RecruitmentState.passed(player, room) || !testing.testing(player, room)) {
            return
        }
        if (player.rdStatuePhase == STATUE_READY) {
            chatPlayer(quiz, "What am I supposed to be doing again?")
            chatNpc(neutral, "Touch the statue that was brought in after you entered.")
            chatNpc(neutral, "If you touch the wrong one, you will fail this task, and will be escorted back to Falador.")
            return
        }
        chatNpc(neutral, "Study the statues closely. There is one missing statue in this room.")
    }

    private suspend fun ProtectedAccess.touch(loc: BoundLocInfo, statue: Int) {
        if (!testing.testing(player, room) || !testing.inRoom(player, loc.coords, room)) {
            return
        }
        if (player.rdStatuePhase != STATUE_READY) {
            mes("Wait until all statues have been placed before selecting one.")
            return
        }
        val attempt = testing.attempt(player)
        if (statue == missingStatue(player.rdStatueAnswer)) {
            with(testing) { passRoom(room, attempt) }
        } else {
            with(testing) { failRoom(room, attempt) }
        }
    }

    companion object {
        const val LADY_TABLE = "npc.rd_observer_room_2"
        const val TIMER = "timer.rd_statues"
        const val FULL_SET = 0
        const val LAYOUTS = 12

        /** Ten seconds: 17 game cycles of 0.6 seconds. */
        const val MEMORISE_TICKS = 17

        val STATUES =
            listOf(
                "loc.rd_1g", "loc.rd_1s", "loc.rd_1b",
                "loc.rd_2g", "loc.rd_2s", "loc.rd_2b",
                "loc.rd_3g", "loc.rd_3s", "loc.rd_3b",
                "loc.rd_4g", "loc.rd_4s", "loc.rd_4b",
            )

        val MULTILOCS = (1..LAYOUTS).map { "loc.rd_statue_multi_$it" }

        /**
         * The statue absent from layout [layout] (1-12), read from the twelve multilocs' transforms
         * in the cache: the one of the twelve statue types none of them shows.
         */
        fun missingStatue(layout: Int): Int? = missing[layout]

        private val missing: Map<Int, Int> by lazy {
            val all = STATUES.map { it.asRSCM(RSCMType.LOC) }.toSet()
            val transforms = MULTILOCS.map { checkNotNull(ServerCacheManager.getObject(it.asRSCM(RSCMType.LOC))).transforms.orEmpty() }
            (1..LAYOUTS).associateWith { layout ->
                val shown = transforms.mapNotNull { t -> t.getOrNull(layout)?.takeIf { it in all } }.toSet()
                (all - shown).single()
            }
        }

        fun statueName(id: Int): String = RSCM.getReverseMapping(RSCMType.LOC, id)
    }
}
