package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentState
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdRiddle
import org.rsmod.content.quest.area.falador.recruitmentdrive.rdRiddleClue
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Ren Itchood's test of observation. He speaks only in rhymes whose lines start with the letters
 * of the door's four-letter password; the six riddles (BITE, TIME, FISH, MEAT, LAST and RAIN), each
 * with a first, a different and a final clue, are the transcript's. The riddle is picked once per
 * visit on `varbit.rd_riddle`, and every clue is shown with its line breaks so the acrostic stands
 * out. The door's combination lock (`interface.rd_combolock`) is checked here against this player's
 * riddle; one wrong combination fails the test.
 */
@Singleton
class AcrosticRoom @Inject constructor(private val testing: RecruitmentTesting) : PluginScript(), TestRoomScript {
    override val room = TestRoom.ACROSTIC

    override fun ScriptContext.startup() {
        testing.register(this@AcrosticRoom)
        onOpNpc1(REN) { startDialogue(it.npc) { askForClue() } }
        onOpLoc1(room.exitDoor) { openDoor() }
        for ((wheel, letter) in WHEEL_NAMES.withIndex()) {
            onIfModalButton("component.rd_combolock:rd${letter}_left") { turnWheel(wheel, -1) }
            onIfModalButton("component.rd_combolock:rd${letter}_right") { turnWheel(wheel, 1) }
        }
        onIfModalButton(LOCK_ENTER) { tryCombination() }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        if (player.rdRiddle == 0) {
            player.rdRiddle = random.of(1, RIDDLES.size)
        }
        val ren = testing.grounds.observer(player, room) ?: return
        startDialogue(ren) {
            chatNpc(neutral, GREETING)
            askForClue()
        }
    }

    fun riddle(player: Player): Riddle? = RIDDLES.getOrNull(player.rdRiddle - 1)

    private suspend fun Dialogue.askForClue() {
        if (RecruitmentState.passed(player, room) || !testing.testing(player, room)) {
            return
        }
        val riddle = riddle(player) ?: return
        val again = player.rdRiddleClue >= 1
        val second = if (again) "Can I have the final clue?" else "Can I have a different clue?"
        when (choice2("Can I have the clue for the door?", 1, second, 2)) {
            1 -> {
                chatPlayer(quiz, "Can I have the clue for the door?")
                chatNpc(neutral, riddle.clues[0])
            }
            else -> {
                chatPlayer(confused, "I don't get that riddle...")
                if (again) {
                    chatPlayer(confused, "Can I have the final clue for the door?")
                    chatNpc(neutral, riddle.clues[2])
                    player.rdRiddleClue = 2
                } else {
                    chatPlayer(confused, "Can I have a different one?")
                    chatNpc(neutral, riddle.clues[1])
                    player.rdRiddleClue = 1
                }
            }
        }
    }

    private suspend fun ProtectedAccess.openDoor() {
        if (testing.passedHere(player, room)) {
            with(testing) { proceed(room) }
            return
        }
        if (!testing.testing(player, room)) {
            return
        }
        for (wheel in WHEEL_VARBITS.indices) {
            RecruitmentState.set(player, WHEEL_VARBITS[wheel], 0)
        }
        ifOpenMainModal(LOCK_INTERFACE)
        showWheels()
        for (letter in WHEEL_NAMES) {
            ifSetEvents("component.rd_combolock:rd${letter}_left", 0..0, IfEvent.Op1)
            ifSetEvents("component.rd_combolock:rd${letter}_right", 0..0, IfEvent.Op1)
        }
        ifSetEvents(LOCK_ENTER, 0..0, IfEvent.Op1)
    }

    private fun ProtectedAccess.showWheels() {
        for ((wheel, letter) in WHEEL_NAMES.withIndex()) {
            ifSetText("component.rd_combolock:rd$letter", ('A' + player.vars[WHEEL_VARBITS[wheel]]).toString())
        }
    }

    internal fun ProtectedAccess.turnWheel(wheel: Int, step: Int) {
        if (!testing.testing(player, room)) {
            return
        }
        val value = Math.floorMod(player.vars[WHEEL_VARBITS[wheel]] + step, ALPHABET)
        RecruitmentState.set(player, WHEEL_VARBITS[wheel], value)
        showWheels()
    }

    fun combination(player: Player): String = WHEEL_VARBITS.map { 'A' + player.vars[it] }.joinToString("")

    internal suspend fun ProtectedAccess.tryCombination() {
        if (!testing.testing(player, room)) {
            return
        }
        val attempt = testing.attempt(player)
        val word = combination(player)
        ifClose()
        val riddle = riddle(player) ?: return
        if (word == riddle.answer) {
            with(testing) {
                passRoom(room, attempt) {
                    chatNpc(happy, SOLVED)
                }
            }
            return
        }
        mes("You have failed to solve the riddle.")
        with(testing) { failRoom(room, attempt, FAILED) }
    }

    data class Riddle(val answer: String, val clues: List<String>)

    companion object {
        const val REN = "npc.rd_observer_room_5"
        const val LOCK_INTERFACE = "interface.rd_combolock"
        const val LOCK_ENTER = "component.rd_combolock:rdenter"
        const val ALPHABET = 26

        val WHEEL_NAMES = listOf("a", "b", "c", "d")
        val WHEEL_VARBITS = listOf("varbit.rd_lock_a", "varbit.rd_lock_b", "varbit.rd_lock_c", "varbit.rd_lock_d")

        const val GREETING =
            "Greetings friend, and welcome here,<br>you'll find my puzzle not so clear.<br>" +
                "Hidden amongst my words, it's true,<br>the password for the door as a clue."

        const val SOLVED =
            "Your wit is sharp, your brains quite clear;<br>You solved my puzzle with no fear.<br>" +
                "At puzzles I rank you quite the best,<br>now enter the portal for your next test."

        const val FAILED = "It's sad to say, this test beat you. I'll send you to Tiffy, what to do?"

        private fun rhyme(vararg lines: String): String = lines.joinToString("<br>")

        val RIDDLES =
            listOf(
                Riddle(
                    "BITE",
                    listOf(
                        rhyme("Better than me, you'll not find", "In rhyming and in puzzles.", "This clue so clear will tax your mind", "Entirely as it confuzzles!"),
                        rhyme("Before you hurry through that door", "Inspect the words i spoke.", "There is a simple hidden flaw", "Ere you think my rhyme a joke."),
                        rhyme("Betrayed by words the answer is", "In that what i say is the key", "There is no more help after this", "Especially no more from me."),
                    ),
                ),
                Riddle(
                    "TIME",
                    listOf(
                        rhyme("This riddle of mine may confuse,", "I am quite sure of that.", "Mayhap you should closely peruse", "Every word i have spat?"),
                        rhyme("Twice it is now, i have stated", "In a rhyme, what is the pass.", "Maybe my words obfuscated", "Entirely beyond your class."),
                        rhyme("Three times now, my riddle said;", "I hope you finally see the clue.", "Maybe think on what you've read;", "Easy when you know what to do."),
                    ),
                ),
                Riddle(
                    "FISH",
                    listOf(
                        rhyme("Feel the aching of your mind", "In puzzlement, confused.", "See the clue hidden behind", "His words, as you perused."),
                        rhyme("First my clue you did not see,", "I really wish you had.", "Such puzzling wordplay devilry", "Has left you kind of mad!"),
                        rhyme("For the last time i will state", "In simple words, the clue.", "Such tricky words make you irate", "Having no idea what to do..."),
                    ),
                ),
                Riddle(
                    "MEAT",
                    listOf(
                        rhyme("More than words, i have not for you", "Except the things i say today.", "Aware are you, this is a clue?", "Take note of what i say!"),
                        rhyme("Many types have passed through here", "Even such as you amongst their sort.", "And in the end, the puzzles clear;", "The hidden word you saught."),
                        rhyme("Maybe i will do my best;", "Especially at giving clues.", "Attempt to help you pass the test!", "That should help me pay my dues."),
                    ),
                ),
                Riddle(
                    "LAST",
                    listOf(
                        rhyme("Look closely at the words i speak;", "And study closely every part.", "See for yourself the word you seek", "Trapped for you if you're smart."),
                        rhyme("Last time my puzzle did not help", "Apparently, so you've bidden.", "Study my speech carefully, whelp", "To find the answer, hidden."),
                        rhyme("Lo! my final speech is now", "Attended to by you.", "Study my words, and find out how", "To understand my clue!"),
                    ),
                ),
                Riddle(
                    "RAIN",
                    listOf(
                        rhyme("Rare it is that you will see", "A puzzle such as this!", "In many ways it tickles me", "Now, watching you hit and miss!"),
                        rhyme("Repetition, once again", "Against good sense it goes.", "In my words, the answers plain", "Now that you see rhyme flows."),
                        rhyme("Repeat myself to give the clue", "Again, i now do speak.", "It's hidden in my words - hey you!", "Now find the answer you seek."),
                    ),
                ),
            )
    }
}
