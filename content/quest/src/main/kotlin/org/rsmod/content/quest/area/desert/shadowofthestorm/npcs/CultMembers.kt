package org.rsmod.content.quest.area.desert.shadowofthestorm.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.DemonThroneRoom
import org.rsmod.content.quest.area.desert.shadowofthestorm.Incantation
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DENATH
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DENATH_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.ERIC
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.ERIC_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.JENNIFER
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.MATTHEW
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.PATRICK
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL_MOULD
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SOUND_CIRCLE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_ASKED_MATTHEW
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FIRST_RITUAL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_FOUND_TOME
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_INFILTRATED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_SECOND_RITUAL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_TASKED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.TOME
import org.rsmod.content.quest.area.desert.shadowofthestorm.ThroneRoom
import org.rsmod.content.quest.area.desert.shadowofthestorm.baddenAtUzer
import org.rsmod.content.quest.area.desert.shadowofthestorm.daveConvinced
import org.rsmod.content.quest.area.desert.shadowofthestorm.golemConvinced
import org.rsmod.content.quest.area.desert.shadowofthestorm.kilnSearched
import org.rsmod.content.quest.area.desert.shadowofthestorm.reenAtUzer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The cult in Thammaron's throne room: Denath, who hands out the incantation; Jennifer, who casts
 * the sigils; Matthew, who kept Josef's things; Eric and Patrick, who are only here for the
 * atmosphere.
 *
 * Every one of them is spawned into the player's own copy of the room, so their dialogue can
 * assume it is talking to the player the scene belongs to.
 */
@Singleton
class CultMembers
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val throneRoom: DemonThroneRoom,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(DENATH, DENATH_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { denath() } }
        }
        onOpNpc1(JENNIFER) { startDialogue(it.npc) { jennifer() } }
        onOpNpc1(MATTHEW) { startDialogue(it.npc) { matthew() } }
        for (name in listOf(ERIC, ERIC_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { eric() } }
        }
        onOpNpc1(PATRICK) { startDialogue(it.npc) { patrick() } }
    }

    /**
     * Denath dictates the five words. What he hands over is the incantation reversed, which he
     * calls a summoning and the tome calls nothing of the kind.
     */
    private suspend fun Dialogue.denath() {
        val stage = sots.stage(player)
        if (stage >= STAGE_TASKED) {
            chatNpc(neutral, "You have your words. Learn them, and be at the circle when I call it.")
            reciteIncantation()
            return
        }
        chatNpc(neutral, "So you are Josef's replacement.")
        chatPlayer(quiz, "What happened to Josef?")
        chatNpc(
            shifty,
            "Josef lost his nerve and went home to his mother. It happens. It will not happen " +
                "to you.",
        )
        val ask =
            choice2(
                "What do I have to do?",
                true,
                "I'd like to know more about Josef first.",
                false,
            )
        if (!ask) {
            chatPlayer(neutral, "I'd like to know more about Josef first.")
            chatNpc(angry, "You would like to be useful. Ask me again when you are.")
            return
        }
        chatPlayer(quiz, "What do I have to do?")
        chatNpc(
            neutral,
            "Five of us stand on the marked floor, each holding a sigil of the demon's name, and " +
                "each speaks the same five words. Jennifer will give you a mould for the sigil; " +
                "silver will do for the metal.",
        )
        chatNpc(
            neutral,
            "The words are the difficult part, because every caster speaks them in a different " +
                "order. Listen. Do not write them down where anyone else can read them.",
        )
        assignIncantation()
        reciteIncantation()
        sots.advanceTo(access, STAGE_TASKED)
    }

    /** Rolls this player's permutation once and keeps it for the rest of the quest. */
    private fun Dialogue.assignIncantation() {
        if (Incantation.assigned(player)) {
            return
        }
        Incantation.assign(player, shuffledWords())
        player.kilnSearched = random.of(UZER_KILN_COUNT)
    }

    /** Fisher-Yates over the game's own random, so the order is server-rolled like any drop. */
    private fun shuffledWords(): List<Int> {
        val order = Incantation.WORDS.indices.toMutableList()
        for (i in order.lastIndex downTo 1) {
            val swap = random.of(i + 1)
            val held = order[i]
            order[i] = order[swap]
            order[swap] = held
        }
        return order
    }

    private suspend fun Dialogue.reciteIncantation() {
        mesbox(
            "Denath speaks five words, slowly, and waits until you can say them back:" +
                "<br><br><col=7f0000>${Incantation.render(Incantation.denathOrder(player))}</col>",
        )
    }

    /**
     * Jennifer keeps handing out moulds for as long as the quest runs; the second circle needs
     * four sigils and losing one must not be a dead end.
     */
    private suspend fun Dialogue.jennifer() {
        val stage = sots.stage(player)
        if (stage < STAGE_TASKED) {
            chatNpc(shifty, "Denath speaks to newcomers. Not me.")
            return
        }
        if (stage >= STAGE_RECRUITING) {
            chatNpc(worried, "I keep casting them. I don't know what else to do with my hands.")
            if (access.playerContainsObj(SIGIL_MOULD)) {
                return
            }
        }
        chatNpc(neutral, "You will want a sigil, then.")
        val ask =
            choice2(
                "Do you have the demonic sigil mould?",
                true,
                "Not just yet.",
                false,
            )
        if (!ask) {
            chatPlayer(neutral, "Not just yet.")
            chatNpc(neutral, "It will be here.")
            return
        }
        chatPlayer(quiz, "Do you have the demonic sigil mould?")
        if (access.playerContainsObj(SIGIL_MOULD)) {
            chatNpc(neutral, "You already have it. A silver bar and any furnace will do the rest.")
            return
        }
        if (access.inv.isFull()) {
            chatNpc(neutral, "I would give it to you, but you are carrying half the desert.")
            return
        }
        access.invAdd(access.inv, SIGIL_MOULD)
        access.objbox(SIGIL_MOULD, "Jennifer hands you a demonic sigil mould.")
        chatNpc(
            neutral,
            "A silver bar, and any furnace. Do not lose it - I am not casting you a second one.",
        )
    }

    /**
     * Matthew is the trail to the tome at the start of the quest and the one who calls the second
     * circle at the end of it.
     */
    private suspend fun Dialogue.matthew() {
        val stage = sots.stage(player)
        when {
            stage < STAGE_TASKED -> chatNpc(shifty, "New, are you? Keep your head down.")
            stage == STAGE_FOUND_TOME && access.playerContainsObj(TOME) -> handOverTome()
            stage >= STAGE_SECOND_RITUAL -> chatNpc(worried, "Get on the circle. Please.")
            stage == STAGE_RECRUITING -> startSecondRitual()
            stage < STAGE_ASKED_MATTHEW -> aboutJosef()
            else -> chatNpc(neutral, "That book of his has to be somewhere. Ask the golem outside - it never sleeps.")
        }
    }

    private suspend fun Dialogue.aboutJosef() {
        chatNpc(sad, "You have Josef's place. I hope you have better luck with it.")
        val ask =
            choice2(
                "Do you know what happened to Josef?",
                true,
                "I'm sure I will.",
                false,
            )
        if (!ask) {
            chatPlayer(neutral, "I'm sure I will.")
            return
        }
        chatPlayer(quiz, "Do you know what happened to Josef?")
        chatNpc(
            worried,
            "Denath says he went home. Josef would not have gone home without his book. He was " +
                "never without that book.",
        )
        chatNpc(
            neutral,
            "He dropped it the night he disappeared, somewhere up in the ruins. Nobody has seen " +
                "it since.",
        )
        sots.advanceTo(access, STAGE_ASKED_MATTHEW)
    }

    private suspend fun Dialogue.handOverTome() {
        chatPlayer(neutral, "Josef's book. I found it in one of the kilns.")
        chatNpc(shocked, "In a kiln? Who puts a book in a -")
        chatNpc(worried, "...someone who did not want it found. Give it here.")
        if (access.invDel(access.inv, TOME).failure) {
            return
        }
        chatNpc(neutral, "Denath will want to see this. Wait by the circle.")
        sots.advanceTo(access, STAGE_FIRST_RITUAL)
        access.callCircle(
            "Denath is already walking to the marked floor. There is one point left open, on the " +
                "north-west side of the circle.",
        )
    }

    private suspend fun Dialogue.startSecondRitual() {
        if (!allCastersReady()) {
            chatNpc(
                worried,
                "Four of you, and you. That is what the floor wants. Come back when you have them.",
            )
            return
        }
        chatPlayer(neutral, "They're all here. Call it.")
        chatNpc(shocked, "You want to call him BACK?")
        chatPlayer(neutral, "Into a room with five casters and a sword. Yes.")
        val ready = choice2("Yes.", true, "Not yet.", false, title = "Begin the summoning?")
        if (!ready) {
            chatPlayer(neutral, "Not yet.")
            return
        }
        chatNpc(worried, "Then stand on the north point, and do not stop halfway through.")
        sots.advanceTo(access, STAGE_SECOND_RITUAL)
        access.callCircle(
            "The others take their places around the marked floor. The north point is yours.",
        )
    }

    private fun Dialogue.allCastersReady(): Boolean =
        player.daveConvinced >= RECRUITED &&
            player.baddenAtUzer >= RECRUITED &&
            player.reenAtUzer >= RECRUITED &&
            player.golemConvinced >= GOLEM_RECRUITED

    private suspend fun Dialogue.eric() {
        if (sots.stage(player) < STAGE_INFILTRATED) {
            return
        }
        chatNpc(happy, "First time? You'll love it. The chanting gets right into your chest.")
        chatPlayer(quiz, "And what exactly are we chanting at?")
        chatNpc(shifty, "Denath says we'll know when it gets here.")
    }

    private suspend fun Dialogue.patrick() {
        if (sots.stage(player) < STAGE_INFILTRATED) {
            return
        }
        chatNpc(happy, "I'm not a caster. I just do the candles. Someone has to do the candles.")
    }

    /** Rearranges the room for whichever circle the stage now calls for. */
    private suspend fun ProtectedAccess.callCircle(message: String) {
        throneRoom.populate(player)
        soundSynth(SOUND_CIRCLE)
        faceSquare(throneRoom.at(player, ThroneRoom.CIRCLE_CENTRE))
        mesbox(message)
    }

    private companion object {
        /** `varbit.agrith_kiln` indexes the four kilns of Uzer. */
        const val UZER_KILN_COUNT = 4
    }
}
