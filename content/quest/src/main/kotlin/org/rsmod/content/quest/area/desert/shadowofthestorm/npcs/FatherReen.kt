package org.rsmod.content.quest.area.desert.shadowofthestorm.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.craftingLvl
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.COMBAT_XP_REWARD
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.CRAFTING_REQ
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DARKLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DEMON_SLAYER_QUEST
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DYED_SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.GOLEM_QUEST
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.IN_UZER
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN_AL_KHARID_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.REEN_UZER_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SILVERLIGHT
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_SLAIN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.desert.shadowofthestorm.baddenAtUzer
import org.rsmod.content.quest.area.desert.shadowofthestorm.reenAtUzer
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Father Reen, who starts the quest outside the Al Kharid bank and finishes it in the throne room
 * once the demon is down. He is also where a player who has lost Silverlight gets another, since
 * the quest cannot be finished without it.
 */
@Singleton
class FatherReen
@Inject
constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(REEN_AL_KHARID_SPAWN, REEN_UZER_SPAWN, REEN, REEN_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { reen() } }
        }
    }

    private suspend fun Dialogue.reen() {
        val stage = sots.stage(player)
        when {
            sots.isComplete(player) -> afterQuest()
            stage == 0 -> offerQuest()
            stage == STAGE_SLAIN -> finish()
            stage >= STAGE_RECRUITING && player.reenAtUzer < RECRUITED -> recruit()
            else -> inProgress()
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatNpc(worried, "You there. You have the look of someone who has seen a demon before.")
        val listen =
            choice2(
                "I have. What's this about?",
                true,
                "You have me confused with someone else.",
                false,
            )
        if (!listen) {
            chatPlayer(neutral, "You have me confused with someone else.")
            chatNpc(sad, "Perhaps I do. Forgive an old man his fears.")
            return
        }
        chatPlayer(quiz, "I have. What's this about?")
        chatNpc(
            worried,
            "My brother Badden writes to me from the ruins of Uzer, out past the Shantay Pass. " +
                "He has been watching a gathering out there. Men in black, going down into the " +
                "old temple at night and coming back up changed.",
        )
        chatNpc(sad, "His last letter did not arrive. The one before it used the word 'demon'.")
        if (!requirementsMet()) {
            return
        }
        chatPlayer(quiz, "And you want me to go and look?")
        chatNpc(
            neutral,
            "I want you to go and stop it. Take Silverlight - whatever is down there will know " +
                "the difference.",
        )
        val accept = choice2("I'll go.", true, "Not today.", false, title = "Start Shadow of the Storm?")
        if (!accept) {
            chatPlayer(neutral, "Not today.")
            chatNpc(sad, "Then I will pray, and hope that is enough.")
            return
        }
        chatPlayer(neutral, "I'll go.")
        sots.advanceTo(access, STAGE_STARTED)
        player.baddenAtUzer = IN_UZER
        chatNpc(happy, "Bless you. Find Badden in the ruins; he will know more than I do.")
        access.offerSilverlight()
    }

    /** The Golem and Demon Slayer, and enough Crafting to cast a sigil later on. */
    private suspend fun Dialogue.requirementsMet(): Boolean {
        val missing = buildList {
            if (!QuestRequirements.hasCompleted(player, DEMON_SLAYER_QUEST)) {
                add("completed <col=7f0000>Demon Slayer</col>")
            }
            if (!QuestRequirements.hasCompleted(player, GOLEM_QUEST)) {
                add("completed <col=7f0000>The Golem</col>")
            }
            if (player.craftingLvl < CRAFTING_REQ) {
                add("level $CRAFTING_REQ <col=7f0000>Crafting</col>")
            }
        }
        if (missing.isEmpty()) {
            return true
        }
        chatNpc(sad, "But you are not ready for this, and I will not send you to your death.")
        mesbox("You need ${missing.joinToString(", and ")} before Father Reen will send you to Uzer.")
        return false
    }

    private suspend fun Dialogue.inProgress() {
        chatNpc(worried, "Badden is in the ruins of Uzer, east of the Shantay Pass. Hurry.")
        access.offerSilverlight()
    }

    /** Four sigils need four willing hands, and Reen's are the first the player should ask for. */
    private suspend fun Dialogue.recruit() {
        chatPlayer(
            neutral,
            "Denath was the demon. His name is Agrith-Naar, and speaking the words backwards was " +
                "enough to unmake the shape he was wearing - but not enough to kill him.",
        )
        chatNpc(shocked, "Then he is still out there. Wearing someone else by now, I should think.")
        chatPlayer(
            neutral,
            "Not if we call him back into the circle in his own skin. I need five casters. I " +
                "have a spare sigil.",
        )
        chatNpc(worried, "You are asking a priest to help summon a demon.")
        val push =
            choice2(
                "Oh, don't be so simple-minded!",
                true,
                "Forget I asked.",
                false,
            )
        if (!push) {
            chatPlayer(neutral, "Forget I asked.")
            chatNpc(sad, "I am sorry. Ask me again when you have thought of nothing better.")
            return
        }
        chatPlayer(neutral, "Oh, don't be so simple-minded!")
        chatPlayer(
            neutral,
            "We are not summoning him to worship him. We are summoning him somewhere he can be " +
                "hit with a sword.",
        )
        chatNpc(neutral, "...")
        if (access.invDel(player.inv, SIGIL).failure) {
            chatNpc(worried, "Bring me one of those sigils and I will carry it into the circle.")
            return
        }
        chatNpc(neutral, "Very well. Give me the sigil. I will meet you below.")
        player.reenAtUzer = RECRUITED
        access.mes("Father Reen takes the sigil and starts down towards the temple.")
    }

    private suspend fun Dialogue.finish() {
        chatNpc(happy, "It is over. I felt it go.")
        chatPlayer(neutral, "The sword took most of him with it.")
        chatNpc(
            neutral,
            "So it did. That is not Silverlight any more - it has had a taste of something older " +
                "than it was made for. Darklight, I think. Keep it away from demons you do not " +
                "intend to kill.",
        )
        chatNpc(happy, "You have earned more than my thanks. Where would you like the learning to go?")
        val stat =
            choice5(
                "Attack", "stat.attack",
                "Strength", "stat.strength",
                "Defence", "stat.defence",
                "Hitpoints", "stat.hitpoints",
                "More...", null,
                title = "Which skill?",
            ) ?: choice2("Ranged", "stat.ranged", "Magic", "stat.magic", title = "Which skill?")
        access.statAdvance(stat, COMBAT_XP_REWARD)
        player.reenAtUzer = IN_UZER
        player.baddenAtUzer = IN_UZER
        sots.complete(access)
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Whenever I hear the wind get up over the desert I think of that room.")
        chatPlayer(neutral, "So do I.")
        chatNpc(neutral, "Keep the black sword sharp. There are others like him.")
    }

    private suspend fun ProtectedAccess.offerSilverlight() {
        if (carriesAnySilverlight()) {
            return
        }
        if (inv.isFull()) {
            mes("Father Reen has a spare Silverlight for you, but your hands are full.")
            return
        }
        invAdd(inv, SILVERLIGHT)
        objbox(SILVERLIGHT, "Father Reen presses a spare Silverlight into your hands.")
    }

    private fun ProtectedAccess.carriesAnySilverlight(): Boolean =
        playerContainsObj(SILVERLIGHT) ||
            playerContainsObj(DYED_SILVERLIGHT) ||
            playerContainsObj(DARKLIGHT)
}
