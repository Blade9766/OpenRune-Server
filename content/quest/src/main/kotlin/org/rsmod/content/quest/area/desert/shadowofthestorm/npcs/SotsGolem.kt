package org.rsmod.content.quest.area.desert.shadowofthestorm.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_CLAY_AWAY
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_REFUSED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_REPROGRAMMED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_ASKED_GOLEM
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_ASKED_MATTHEW
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.THRONE_GOLEM
import org.rsmod.content.quest.area.desert.shadowofthestorm.golemClay
import org.rsmod.content.quest.area.desert.shadowofthestorm.golemConvinced
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The clay golem's part in Shadow of the Storm.
 *
 * It watched Denath kill Josef and hide his book, which is how the player finds the tome; and it
 * is the fifth caster, once the page in its skull that forbids demon rituals has been taken out
 * with the strange implement. Its own quest script owns the Uzer spawn's Talk-to, so the branches
 * that belong to this quest are held here and called from there.
 */
@Singleton
class SotsGolem
@Inject
constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(THRONE_GOLEM, GOLEM_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { inTheCircle() } }
        }
    }

    /** True while the golem has something to say that belongs to this quest. */
    fun hasBusiness(player: org.rsmod.game.entity.Player): Boolean {
        if (!sots.inProgress(player)) {
            return false
        }
        val stage = sots.stage(player)
        return stage == STAGE_ASKED_MATTHEW ||
            (stage >= STAGE_RECRUITING && player.golemConvinced < GOLEM_RECRUITED)
    }

    suspend fun Dialogue.talk() {
        val stage = sots.stage(player)
        when {
            stage == STAGE_ASKED_MATTHEW -> lastNight()
            player.golemConvinced < GOLEM_REFUSED -> refuse()
            player.golemConvinced < GOLEM_REPROGRAMMED -> stillRefusing()
            else -> agree()
        }
    }

    /**
     * The golem does not sleep and does not lie. It saw what happened to Josef, and where the
     * book went afterwards.
     */
    private suspend fun Dialogue.lastNight() {
        chatPlayer(quiz, "Did you see anything happen last night?")
        chatNpc(neutral, "Query understood. Observation log follows.")
        chatNpc(
            neutral,
            "One human left the temple ahead of the others. A second human followed. The second " +
                "human returned alone.",
        )
        chatPlayer(shocked, "Denath killed him.")
        chatNpc(
            neutral,
            "Identity not recorded. The returning human carried an object and placed it inside " +
                "a kiln. Kiln identity not recorded; there are four.",
        )
        chatPlayer(neutral, "Four kilns. Right.")
        sots.advanceTo(access, STAGE_ASKED_GOLEM)
    }

    private suspend fun Dialogue.refuse() {
        chatPlayer(neutral, "I need you to help me summon a demon.")
        chatNpc(angry, "Refused.")
        chatPlayer(quiz, "You were built to fight demons!")
        chatNpc(
            neutral,
            "Instruction held in memory: a golem shall not take part in the summoning of demons. " +
                "The instruction is written. It cannot be argued with.",
        )
        chatPlayer(neutral, "Written. Of course it is.")
        player.golemConvinced = GOLEM_REFUSED
        mesbox(
            "Varmen's notes said it plainly enough: a golem does what the papyrus in its head " +
                "tells it to. The strange implement will open that head.",
        )
    }

    private suspend fun Dialogue.stillRefusing() {
        chatPlayer(neutral, "About that summoning.")
        chatNpc(neutral, "Refused. The instruction is written.")
    }

    private suspend fun Dialogue.agree() {
        if (player.golemConvinced >= GOLEM_RECRUITED) {
            chatNpc(neutral, "Awaiting the circle.")
            return
        }
        chatPlayer(neutral, "Let me ask you again about the summoning.")
        chatNpc(
            neutral,
            "No instruction found. Proposal evaluated on its merits: a demon inside a circle is " +
                "a demon that can be destroyed. Accepted.",
        )
        if (access.invDel(access.inv, SIGIL).failure) {
            chatNpc(neutral, "A sigil is required. I have no hands for casting one.")
            return
        }
        chatNpc(happy, "Proceeding to the temple. This is the first new thing I have done in three thousand years.")
        player.golemConvinced = GOLEM_RECRUITED
        player.golemClay = GOLEM_CLAY_AWAY
        access.mes("The golem sets off towards the temple stairs.")
    }

    /**
     * Using the strange implement on the golem during this quest takes the page about demon
     * rituals out of its skull instead of opening it for a new program.
     */
    suspend fun ProtectedAccess.removeRestriction(): Boolean {
        if (player.golemConvinced != GOLEM_REFUSED) {
            return false
        }
        anim(OPEN_SEQ)
        soundSynth(PROGRAM_SOUND)
        delay(2)
        player.golemConvinced = GOLEM_REPROGRAMMED
        mesbox(
            "You lever the golem's skull open. Inside, among a dozen sheets of ancient papyrus, " +
                "is one that reads: A GOLEM SHALL NOT SUMMON DEMONS.",
        )
        mesbox("You take that one out, and close the skull again.")
        return true
    }

    private suspend fun Dialogue.inTheCircle() {
        chatNpc(neutral, "Holding position. Holding sigil. Awaiting the words.")
    }

    private companion object {
        const val OPEN_SEQ = "seq.human_pickuptable"
        const val PROGRAM_SOUND = "synth.golem_program"
    }
}
