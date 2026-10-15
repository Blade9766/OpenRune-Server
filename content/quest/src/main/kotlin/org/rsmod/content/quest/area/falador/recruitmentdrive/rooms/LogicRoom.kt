package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdLogicRiddle
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Ms. Hynn Terprett's test of logic: one of the transcript's five riddles, picked once per visit on
 * `varbit.rd_logic_riddle`, worded and answered exactly as in game. Two take a number typed in the
 * count box, three a choice from four options; the answer is checked here, and a wrong one fails.
 */
@Singleton
class LogicRoom @Inject constructor(private val testing: RecruitmentTesting) : PluginScript(), TestRoomScript {
    override val room = TestRoom.LOGIC

    enum class LogicRiddle {
        FINGERS,
        DAUGHTER,
        FALSE_STATEMENTS,
        FATE,
        BUCKETS,
    }

    override fun ScriptContext.startup() {
        testing.register(this@LogicRoom)
        onOpNpc1(HYNN) { test(it.npc, greeting = false) }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        if (player.rdLogicRiddle == 0) {
            player.rdLogicRiddle = random.of(1, LogicRiddle.entries.size)
        }
        val hynn = testing.grounds.observer(player, room) ?: return
        test(hynn, greeting = true)
    }

    private suspend fun ProtectedAccess.test(hynn: Npc, greeting: Boolean) {
        if (RecruitmentState.passed(player, room) || !testing.testing(player, room)) {
            return
        }
        val attempt = testing.attempt(player)
        var answer: Int? = null
        startDialogue(hynn) { answer = askRiddle(greeting) }
        val riddle = riddle(player) ?: return
        val given = answer ?: return
        with(testing) {
            if (given == ANSWERS[riddle]) passRoom(room, attempt) else failRoom(room, attempt)
        }
    }

    fun riddle(player: Player): LogicRiddle? = LogicRiddle.entries.getOrNull(player.rdLogicRiddle - 1)

    /** Puts the riddle and returns the count typed or the option picked. */
    private suspend fun Dialogue.askRiddle(withGreeting: Boolean): Int? {
        val riddle = riddle(player) ?: return null
        if (withGreeting) {
            chatNpc(neutral, "Greetings, ${player.displayName}. I am here to test your wits with a simple riddle.")
        }
        return when (riddle) {
                LogicRiddle.FINGERS -> {
                    chatNpc(quiz, "Here is my riddle: I estimate there to be 1 million inhabitants in the world of Gielinor, creatures and people both.")
                    chatNpc(quiz, "What would be the number you would get if you multiply the number of fingers on everythings left hand, to the nearest million?")
                    access.countDialog("Enter amount:")
                }
                LogicRiddle.DAUGHTER -> {
                    chatNpc(quiz, "Here is my riddle: I have both a husband and daughter.")
                    chatNpc(quiz, "My husband is four times older than my daughter. In twenty years time, he will be twice as old as my daughter.")
                    chatNpc(quiz, "How old is my daughter now?")
                    access.countDialog("Enter amount:")
                }
                LogicRiddle.FALSE_STATEMENTS -> {
                    chatNpc(quiz, "Here is my riddle: Which of the following statements is true?")
                    choice4(
                        "The number of false statements here is one", 1,
                        "The number of false statements here is two", 2,
                        "The number of false statements here is three.", 3,
                        "The number of false statements here is four.", 4,
                        title = "Which of the following is true?",
                    )
                }
                LogicRiddle.FATE -> {
                    chatNpc(quiz, "Here is my riddle: Imagine that you have been captured by an enemy. You are to be killed, but in a moment of mercy, the enemy has allowed you to pick your own demise.")
                    chatNpc(quiz, "Your first choice is to be drowned in a lake of acid.")
                    chatNpc(quiz, "Your second choice is to be burned on a fire.")
                    chatNpc(quiz, "Your third choice is to be thrown to a pack of wolves that have not been fed in over a month.")
                    chatNpc(quiz, "Your final choice of fate is to be thrown from the walls of a castle, many hundreds of feet high.")
                    chatNpc(quiz, "Which fate would you be wise to choose?")
                    choice4(
                        "The lake of acid.", 1,
                        "The large fire.", 2,
                        "The wolves.", 3,
                        "The castle walls.", 4,
                        title = "Select your fate",
                    )
                }
                LogicRiddle.BUCKETS -> {
                    chatNpc(quiz, "Here is my riddle: I dropped four identical stones, into four identical buckets, each containing an identical amount of water.")
                    chatNpc(quiz, "The first bucket's water was at 32 degrees Fahrenheit, the second was at 33 degrees, the third at 34 and the fourth was at 35 degrees.")
                    chatNpc(quiz, "Which bucket's stone dropped to the bottom of the bucket last?")
                    choice4(
                        "Bucket A (32 degrees)", 1,
                        "Bucket B (33 degrees)", 2,
                        "Bucket C (34 degrees)", 3,
                        "Bucket D (35 degrees)", 4,
                        title = "Which bucket's stone dropped last?",
                    )
                }
            }
    }

    companion object {
        const val HYNN = "npc.rd_observer_room_7"

        /** The answer each riddle wants, as the count typed or the option picked. */
        val ANSWERS =
            mapOf(
                LogicRiddle.FINGERS to 0,
                LogicRiddle.DAUGHTER to 10,
                LogicRiddle.FALSE_STATEMENTS to 3,
                LogicRiddle.FATE to 3,
                LogicRiddle.BUCKETS to 1,
            )
    }
}
