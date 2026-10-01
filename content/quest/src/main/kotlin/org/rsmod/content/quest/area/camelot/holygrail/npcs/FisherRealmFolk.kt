package org.rsmod.content.quest.area.camelot.holygrail.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.holygrail.FisherRealm
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FISHERMAN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.FISHER_KING
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.GRAIL_MAIDEN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HAPPY_PEASANT
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.HOLY_GRAIL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.KING_PERCIVAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_GRAIL_TAKEN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_HEIR_NEEDED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_ENTERED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_RESTORED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.UNHAPPY_PEASANT
import org.rsmod.content.quest.area.camelot.holygrail.castleEntered
import org.rsmod.content.quest.area.camelot.holygrail.heardHealth
import org.rsmod.content.quest.area.camelot.holygrail.heardSon
import org.rsmod.content.quest.area.camelot.holygrail.titanDefeated
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The people of the Fisher Realm, in both its states. The Fisher King must be heard out on both
 * his failing health and his lost son before the quest moves on; each is remembered on its own,
 * so walking away halfway loses nothing. Everyone in the healed realm notices the change.
 */
class FisherRealmFolk @Inject constructor(private val quest: HolyGrailQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(FISHERMAN) { startDialogue(it.npc) { fisherman() } }
        onOpNpc1(FISHER_KING) { startDialogue(it.npc) { fisherKing() } }
        onOpNpc1(GRAIL_MAIDEN) { startDialogue(it.npc) { maiden() } }
        onOpNpc1(UNHAPPY_PEASANT) { startDialogue(it.npc) { unhappyPeasant() } }
        onOpNpc1(HAPPY_PEASANT) { startDialogue(it.npc) { happyPeasant() } }
        onOpNpc1(KING_PERCIVAL) { startDialogue(it.npc) { kingPercival() } }
    }

    private suspend fun Dialogue.fisherman() {
        chatNpc(sad, "Hello. Don't mind me. I only sit here out of habit; nothing has bitten in years.")
        if (quest.stage(player) < STAGE_REALM_ENTERED || quest.stage(player) >= STAGE_REALM_RESTORED) {
            return
        }
        val topic =
            choice2(
                "How do I get into the castle?",
                true,
                "Why does nothing bite?",
                false,
            )
        if (!topic) {
            chatPlayer(quiz, "Why does nothing bite?")
            chatNpc(
                sad,
                "Because the king is sick. When he was young the river was so thick with fish " +
                    "you could walk across on their backs. As he fails, so does everything.",
            )
            return
        }
        chatPlayer(quiz, "How do I get into the castle?")
        if (!player.titanDefeated) {
            chatNpc(confused, "Past the titan? You got past the titan? ...No, I see you haven't. Well, after that, then.")
        }
        chatNpc(
            neutral,
            "Nobody knocks at that castle. There is a bell lying on the ground by its north wall. " +
                "Ring it, and the maidens will let you in.",
        )
        chatNpc(
            worried,
            "And don't go blowing whistles once you're inside. Folk who do that find themselves " +
                "back where they came from.",
        )
    }

    private suspend fun Dialogue.fisherKing() {
        player.castleEntered = true
        val stage = quest.stage(player)
        if (stage >= STAGE_HEIR_NEEDED) {
            chatNpc(sad, "Have you found my son? I am so tired.")
            chatPlayer(neutral, "Not yet, Your Majesty. Hold on a little longer.")
            return
        }
        chatNpc(sad, "A visitor. It has been so long. Forgive me if I do not rise.")
        if (stage < STAGE_REALM_ENTERED) {
            return
        }
        while (true) {
            val topic =
                choice4(
                    "Are you unwell, Your Majesty?",
                    Topic.Health,
                    "Who will rule here after you?",
                    Topic.Son,
                    "I have come for the Holy Grail.",
                    Topic.Grail,
                    "I must go.",
                    Topic.Leave,
                )
            when (topic) {
                Topic.Health -> health()
                Topic.Son -> son()
                Topic.Grail -> grail()
                Topic.Leave -> {
                    chatPlayer(neutral, "I must go.")
                    return
                }
            }
            if (player.heardHealth && player.heardSon) {
                quest.advanceTo(access, STAGE_HEIR_NEEDED)
                chatNpc(
                    neutral,
                    "Go, then, Grail-seeker, and bring my son home. There is not much time.",
                )
                return
            }
        }
    }

    private suspend fun Dialogue.health() {
        chatPlayer(quiz, "Are you unwell, Your Majesty?")
        chatNpc(
            sad,
            "I am dying. Slowly, as kings do here. This land and I are bound: my blood is its " +
                "river, my breath its wind. You have seen what it has become.",
        )
        chatNpc(sad, "The fields grey with me. The fish go where my strength goes.")
        player.heardHealth = true
    }

    private suspend fun Dialogue.son() {
        chatPlayer(quiz, "Who will rule here after you?")
        chatNpc(
            sad,
            "My son should. He was taken from here as a child, in a bad year, and raised in your " +
                "world. I hear he became a knight. I do not even know the name they gave him.",
        )
        chatNpc(
            neutral,
            "If he came home, the land would know him. It would heal under him as it is dying " +
                "under me. Bring him home, and you will have done more than any Grail-seeker " +
                "before you.",
        )
        player.heardSon = true
    }

    private suspend fun Dialogue.grail() {
        chatPlayer(neutral, "I have come for the Holy Grail.")
        chatNpc(
            neutral,
            "It is kept at the top of the eastern tower, as it always was. But you will find the " +
                "table bare.",
        )
        chatNpc(
            sad,
            "The Grail does not stay with a dying line. It has withdrawn, and it will not come " +
                "back to anyone's hand until this realm has a king who will live.",
        )
    }

    private suspend fun Dialogue.maiden() {
        if (FisherRealm.isInHealedRealm(player.coords)) {
            chatNpc(happy, "The king is home! Can you hear the birds? I had forgotten the birds.")
            return
        }
        chatNpc(sad, "Please speak softly. The king is upstairs, and he is very tired.")
    }

    private suspend fun Dialogue.unhappyPeasant() {
        chatNpc(sad, "Nothing grows. We plant and plant, and the earth gives back ash.")
        chatNpc(sad, "The king is ill, and the land is ill with him. Everyone knows it.")
    }

    private suspend fun Dialogue.happyPeasant() {
        chatNpc(happy, "Have you seen the barley? Taller than me! King Percival came home and the rain came with him.")
    }

    private suspend fun Dialogue.kingPercival() {
        val stage = quest.stage(player)
        when {
            stage < STAGE_REALM_RESTORED -> chatNpc(neutral, "Welcome to my father's house.")
            quest.isComplete(player) ->
                chatNpc(happy, "My friend! Come and go as you please. This realm owes you its life.")
            stage == STAGE_GRAIL_TAKEN && HOLY_GRAIL in player.inv ->
                chatNpc(happy, "Take the Grail to King Arthur, with my blessing. Tell him Percival sends his love.")
            stage == STAGE_GRAIL_TAKEN ->
                chatNpc(neutral, "The Grail went back to its tower when you set it down. Fetch it again; it knows you now.")
            else -> restoredThanks()
        }
    }

    private suspend fun Dialogue.restoredThanks() {
        chatNpc(happy, "There you are! Look at it, look out of the window. Did you ever see such green?")
        chatPlayer(quiz, "King Percival, now?")
        chatNpc(
            laugh,
            "So they tell me. My father held on until I knelt by his bed. He knew me at once. He " +
                "put his hand on my head, and the river started running clear while we watched.",
        )
        chatNpc(
            neutral,
            "He is at peace now, and the realm is mine to keep. And the Grail has come back: it is " +
                "at the top of the east tower. It is yours to take to Arthur. Camelot earned it.",
        )
    }

    private enum class Topic {
        Health,
        Son,
        Grail,
        Leave,
    }
}
