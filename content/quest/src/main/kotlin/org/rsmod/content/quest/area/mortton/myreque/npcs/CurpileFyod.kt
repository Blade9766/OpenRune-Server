package org.rsmod.content.quest.area.mortton.myreque.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.fadeFromBlack
import org.rsmod.content.quest.area.ardougne.fadeToBlack
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.CURPILE
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_BRIDGE_REPAIRED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_GUARD_PASSED
import org.rsmod.content.quest.area.mortton.myreque.InSearchOfTheMyrequeQuest.Companion.STAGE_REACHED_HOLLOWS
import org.rsmod.content.quest.area.mortton.myreque.MyrequeCoords
import org.rsmod.content.quest.manager.menu
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Curpile Fyod, the guard on the Myreque's door in the Hollows.
 *
 * He asks [QUESTIONS_ASKED] questions drawn at random from [QUESTIONS]; every answer was given
 * away by Cyreg or Vanstrom. One wrong answer and he knocks the player out: they wake by the boat
 * with everything they carried and the bridge still mended, free to row back and try again. The
 * quiz has no partial progress, so a dialogue closed half way simply starts over.
 */
class CurpileFyod @Inject constructor(private val myq: InSearchOfTheMyrequeQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CURPILE) { talk(it.npc) }
        onOpLoc1(SURFACE_DOOR_LEFT) { enterTunnels() }
        onOpLoc1(SURFACE_DOOR_RIGHT) { enterTunnels() }
        onOpLoc1(TUNNEL_DOOR_LEFT) { leaveTunnels() }
        onOpLoc1(TUNNEL_DOOR_RIGHT) { leaveTunnels() }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        if (myq.stage(player) in STAGE_REACHED_HOLLOWS until STAGE_BRIDGE_REPAIRED && myq.isBridgeRepaired(player)) {
            myq.advanceTo(this, STAGE_BRIDGE_REPAIRED)
        }
        var failed = false
        startDialogue(npc) { failed = !curpile() }
        if (failed) {
            knockOut(npc)
        }
    }

    /** Returns false only when a question was answered wrongly. */
    private suspend fun Dialogue.curpile(): Boolean {
        val stage = myq.stage(player)
        when {
            stage < STAGE_REACHED_HOLLOWS -> {
                chatNpc(neutral, "Nothing to see here. Just a man enjoying the swamp air.")
                return true
            }
            stage >= STAGE_GUARD_PASSED -> {
                chatNpc(neutral, "Go on through, friend. Veliaf's down there somewhere. Mind the stalagmites, it's a tight squeeze.")
                return true
            }
        }
        chatNpc(angry, "Halt! Who goes there? Cyreg wouldn't row just anybody out here, but I'll be the judge of who goes through this door.")
        chatNpc(neutral, "If you're a friend to those below, you'll know the answers to a few questions.")
        if (!choice2("Ask away.", true, "I'll come back later.", false)) {
            chatPlayer(neutral, "I'll come back later.")
            return true
        }
        chatPlayer(neutral, "Ask away.")
        val pool = QUESTIONS.toMutableList()
        val asked = List(QUESTIONS_ASKED) { pool.removeAt(access.random.of(pool.size)) }
        for (question in asked) {
            chatNpc(quiz, question.text)
            val answer = menu(question.options.map { it to it })
            chatPlayer(neutral, answer)
            if (answer != question.answer) {
                chatNpc(angry, "Wrong! You're no friend of ours. Spy!")
                return false
            }
            chatNpc(neutral, "Hmm. That's right.")
        }
        myq.advanceTo(access, STAGE_GUARD_PASSED)
        chatNpc(happy, "You know your stuff. All right, the door's open to you. Veliaf leads them; find him below.")
        return true
    }

    private suspend fun ProtectedAccess.knockOut(npc: Npc) {
        npc.facePlayer(player)
        npc.anim(GUARD_HIT_SEQ)
        delay(1)
        anim(KNOCKED_DOWN_SEQ)
        delay(2)
        fadeToBlack()
        telejump(MyrequeCoords.HOLLOWS_LANDING, TeleportType.Exempt)
        resetAnim()
        fadeFromBlack()
        mes("You wake up beside Cyreg's boat with a sore head. Your belongings are untouched.")
        mes("Perhaps you should learn more about the Myreque before facing that guard again.")
    }

    private suspend fun ProtectedAccess.enterTunnels() {
        arriveDelay()
        if (myq.stage(player) < STAGE_GUARD_PASSED) {
            mes("The doors are barred from below. The guard nearby watches you closely.")
            return
        }
        soundSynth(DOOR_SOUND)
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        telejump(MyrequeCoords.TUNNEL_DOORS_INSIDE, TeleportType.Exempt)
        mes("You climb down into a damp tunnel.")
    }

    private suspend fun ProtectedAccess.leaveTunnels() {
        arriveDelay()
        soundSynth(DOOR_SOUND)
        telejump(MyrequeCoords.SURFACE_DOORS_OUTSIDE, TeleportType.Exempt)
        mes("You climb back out into the Hollows.")
    }

    internal class Question(val text: String, val answer: String, val options: List<String>)

    internal companion object {
        const val SURFACE_DOOR_LEFT = "loc.freedomfighterentrancel"
        const val SURFACE_DOOR_RIGHT = "loc.freedomfighterentrancer"
        const val TUNNEL_DOOR_LEFT = "loc.freedomfighterundergroundentrancel"
        const val TUNNEL_DOOR_RIGHT = "loc.freedomfighterundergroundentrancer"

        const val GUARD_HIT_SEQ = "seq.human_unarmedpunch"
        const val KNOCKED_DOWN_SEQ = "seq.human_death_backwards"
        const val CLIMB_DOWN_SEQ = "seq.human_reachforladder"
        const val DOOR_SOUND = "synth.big_wooden_door_open"

        const val QUESTIONS_ASKED = 3

        val QUESTIONS =
            listOf(
                Question(
                    "Who is the leader of the Myreque?",
                    "Veliaf Hurtz",
                    listOf("Cyreg Paddlehorn", "Veliaf Hurtz", "Vanstrom Klause", "Harold Evans"),
                ),
                Question(
                    "Who is the only woman in the Myreque?",
                    "Sani Piliu",
                    listOf("Sani Piliu", "Vanescula Drakan", "Polmafi Ferdygris", "Ivan Strom"),
                ),
                Question(
                    "Who is the youngest member of the Myreque?",
                    "Ivan Strom",
                    listOf("Radigad Ponfit", "Harold Evans", "Ivan Strom", "Veliaf Hurtz"),
                ),
                Question(
                    "Which of the Myreque was once a scholar?",
                    "Polmafi Ferdygris",
                    listOf("Radigad Ponfit", "Sani Piliu", "Curpile Fyod", "Polmafi Ferdygris"),
                ),
                Question(
                    "Which vampyre family rules Morytania?",
                    "Drakan",
                    listOf("Klause", "Drakan", "Paddlehorn", "Ferdygris"),
                ),
                Question(
                    "What is the name of the boatman who brought you here?",
                    "Cyreg Paddlehorn",
                    listOf("Cyreg Paddlehorn", "Curpile Fyod", "Radigad Ponfit", "Vanstrom Klause"),
                ),
                Question(
                    "What does 'Myreque' mean?",
                    "Hidden in Myre",
                    listOf("Swamp folk", "Hidden in Myre", "Free Morytania", "Steel and silver"),
                ),
            )
    }
}
