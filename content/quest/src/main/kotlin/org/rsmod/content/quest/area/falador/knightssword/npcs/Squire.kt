package org.rsmod.content.quest.area.falador.knightssword.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.BLURITE_SWORD
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.PORTRAIT
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.SQUIRE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_DESIGN_SHOWN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PICTURE_NEEDED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PIE_GIVEN
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_PORTRAIT_LOCATED
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_RELDO
import org.rsmod.content.quest.area.falador.knightssword.KnightsSwordQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Sir Vyvin's squire, who starts and finishes The Knight's Sword in the castle courtyard. */
class Squire @Inject constructor(private val ks: KnightsSwordQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(SQUIRE) { startDialogue(it.npc) { squire() } }
    }

    private suspend fun Dialogue.squire() {
        when (ks.stage(player)) {
            0 -> beforeQuest()
            STAGE_STARTED -> {
                chatNpc(worried, "Any luck finding someone to make the sword? Sir Vyvin asked after it at breakfast. I told him it was being polished.")
                chatPlayer(neutral, "Not yet. Who would know about old swords like that?")
                chatNpc(quiz, "Reldo, perhaps? The librarian in Varrock Palace. He's read every book worth reading, and most that aren't.")
            }
            STAGE_RELDO, STAGE_PIE_GIVEN -> {
                chatNpc(worried, "Any news?")
                chatPlayer(happy, "Reldo says the sword was made by the Imcando dwarves, and one of them, Thurgo, still lives south of Port Sarim.")
                chatNpc(happy, "A real Imcando smith? Then there's hope for me yet!")
            }
            STAGE_PICTURE_NEEDED -> portraitLocation()
            STAGE_PORTRAIT_LOCATED -> {
                if (PORTRAIT in access.inv) {
                    chatNpc(shocked, "Is that... the portrait? Put it away before somebody sees it! Take it straight to Thurgo.")
                    return
                }
                chatNpc(worried, "The portrait's in the cupboard in Sir Vyvin's room, at the top of the castle. Please be discreet.")
                chatNpc(neutral, "He spends half the day staring out of his window at the park. That's your moment.")
            }
            STAGE_DESIGN_SHOWN -> handOver()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatPlayer(happy, "Hello. You look troubled.")
        chatNpc(worried, "Troubled? I'm doomed! Finished! My career as a squire is about to end before it began.")
        when (choice2("What's the matter?", true, "Well, good luck with that.", false)) {
            false -> {
                chatPlayer(neutral, "Well, good luck with that.")
                chatNpc(sad, "Thank you. I'll need all of it.")
                return
            }
            true -> chatPlayer(quiz, "What's the matter?")
        }
        chatNpc(sad, "Sir Vyvin trusted me to look after his sword. Not just any sword, mind you: it has been in his family for generations.")
        chatNpc(worried, "And I've lost it. I put it down for one moment, and now it's gone, and I have searched every barrel in Falador.")
        chatPlayer(quiz, "Couldn't you just tell him?")
        chatNpc(shocked, "Tell him?! He'd have me scrubbing the Falador sewers until I'm older than he is!")
        chatNpc(neutral, "No. What I need is a new sword, so good that he never notices the difference. But I don't know of any smith alive who could make one.")
        if (!startQuestPrompt(ks.quest)) {
            chatPlayer(neutral, "I'm afraid I can't help you right now.")
            chatNpc(sad, "I understand. If you change your mind, I'll be here. Trying to look calm.")
            return
        }
        chatPlayer(happy, "I'll help you find someone who can make it.")
        ks.advanceTo(access, STAGE_STARTED)
        chatNpc(happy, "You will? Oh, thank you! I'll keep Sir Vyvin talking about anything but swords.")
        chatNpc(quiz, "If anyone knows about old smithing, it'll be Reldo, the librarian at Varrock Palace. Try him.")
    }

    private suspend fun Dialogue.portraitLocation() {
        chatPlayer(neutral, "I found an Imcando dwarf called Thurgo. He'll make the sword, but he needs a picture of it to copy.")
        chatNpc(confused, "A picture? I don't have... wait. Wait!")
        chatNpc(happy, "Sir Vyvin keeps a portrait of his father in the cupboard in his room. The old knight is holding the very same sword!")
        chatPlayer(quiz, "Could you get it for me?")
        chatNpc(shocked, "Me? He'd ask why I wanted it. Then he'd ask where his sword is. Then I'd faint.")
        chatNpc(worried, "It's on the top floor of the castle. Just make sure he doesn't see you take it. Sir Vyvin can't abide anyone touching his things.")
        ks.advanceTo(access, STAGE_PORTRAIT_LOCATED)
        chatNpc(neutral, "He spends half the day staring out of his window at the park. That's your moment.")
    }

    private suspend fun Dialogue.handOver() {
        if (BLURITE_SWORD !in access.inv) {
            chatNpc(worried, "How's the sword coming along?")
            if (ks.hasForgedSword(player)) {
                chatPlayer(sad, "Thurgo made it, but I don't have it with me.")
                chatNpc(shocked, "Don't you start losing swords as well! Thurgo will surely make another if you bring him what he needs.")
            } else {
                chatPlayer(neutral, "Thurgo knows the design now. He needs a blurite ore and two iron bars.")
                chatNpc(neutral, "Blurite? I've heard it's only found in the ice caves near Port Sarim. Wrap up warm.")
            }
            return
        }
        chatPlayer(happy, "I have the sword.")
        chatNpc(shocked, "Let me see it!")
        if (access.invDel(access.inv, BLURITE_SWORD, 1).failure) {
            return
        }
        objbox(BLURITE_SWORD, zoom = 400, "You give the blurite sword to the squire.")
        chatNpc(happy, "It's perfect! The balance, the blue sheen, even that little nick on the crossguard. Sir Vyvin will never know.")
        chatNpc(laugh, "I could kiss you! I won't, I'm on duty. But I could.")
        chatNpc(happy, "From now on this sword goes nowhere without me. Thank you, friend.")
        ks.advanceTo(access, STAGE_COMPLETE)
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello. How's the sword?")
        chatNpc(happy, "Safe and sound, and chained to my belt. Sir Vyvin even said it looked better than ever.")
    }
}
