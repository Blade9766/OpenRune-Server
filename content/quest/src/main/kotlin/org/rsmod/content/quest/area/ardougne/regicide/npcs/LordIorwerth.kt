package org.rsmod.content.quest.area.ardougne.regicide.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.regicide.CrystalPendant
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BOOK
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_MESSAGE
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.IORWERTH_VIS
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_DENSE_FOREST
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_FOUND_CAMP
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GOT_PENDANT
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_GUARD_KILLED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_LETTER
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MAKE_BOMB
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_ELVES
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_MET_IORWERTH
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_HELPING
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TRACKER_REFUSED
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.STAGE_TYRAS_DEAD
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Ingredient
import org.rsmod.content.quest.area.ardougne.regicide.catapultChat
import org.rsmod.content.quest.area.ardougne.regicide.owns
import org.rsmod.content.quest.manager.menu
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Lord Iorwerth in his camp in north-western Isafdar. He sends the player to his tracker, vouches
 * for them with his crystal pendant, hands over the Big Book of Bangs and answers questions about
 * its ingredients, and once Tyras is dead writes the letter for King Lathas and opens the pass
 * through Arandar. Items he gives are only handed over with room for them, and a lost book or
 * letter is replaced on request without repeating the step that first gave it.
 */
class LordIorwerth
@Inject
constructor(private val regicide: RegicideQuest, private val pendant: CrystalPendant) : PluginScript() {

    override fun ScriptContext.startup() {
        for (type in listOf(IORWERTH, IORWERTH_VIS)) {
            onOpNpc1(type) { startDialogue(it.npc) { iorwerth() } }
        }
    }

    private suspend fun Dialogue.iorwerth() {
        val stage = regicide.stage(player)
        if (stage >= STAGE_FOUND_CAMP && !regicide.isComplete(player)) {
            with(pendant) { access.confiscate() }
        }
        when {
            regicide.isComplete(player) -> {
                chatPlayer(neutral, "Good day Lord Iorwerth.")
                chatNpc(neutral, "Ah, ${player.displayName}. Tirannwn is a little quieter without Tyras. Good day to you.")
            }
            stage < STAGE_STARTED -> chatNpc(neutral, "I have no business with you, human.")
            stage <= STAGE_MET_ELVES -> firstMeeting()
            stage == STAGE_MET_IORWERTH -> whereIsTracker()
            stage == STAGE_TRACKER_REFUSED -> givePendant()
            stage == STAGE_GOT_PENDANT -> if (access.owns(PENDANT)) whereIsTracker() else givePendant()
            stage in STAGE_TRACKER_HELPING..STAGE_GUARD_KILLED -> searching()
            stage == STAGE_FOUND_CAMP -> campFound()
            stage == STAGE_MAKE_BOMB -> makingBomb()
            stage == STAGE_TYRAS_DEAD -> tyrasDead()
            else -> letterSent()
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(neutral, "Hello there, I'm ${player.displayName}. Your scouts said that I should come and see you.")
        chatNpc(neutral, "Ahh... ${player.displayName}. Welcome to Tirannwn. I understand you've been sent here to rid us of that criminal Tyras. I offer my full support.")
        chatNpc(neutral, "Unfortunately, most of my troops are currently dealing with a group of renegade elves. That does mean we're a bit more limited than we otherwise would be.")
        chatPlayer(neutral, "I see. I'll need to play this smart then.")
        chatNpc(neutral, "Indeed. Now, Tyras and his forces moved camp recently so your first step will be to find their new camp. I have a tracker currently working in that area. I suggest you join up with him.")
        regicide.advanceTo(access, STAGE_MET_IORWERTH)
        chatNpc(neutral, "You'll find him at Tyras's old camp. It's south east of here, just north of that poisoned lake. Once you've found the camp, come and let me know.")
        chatPlayer(neutral, "Thank you. I shall return when my task is complete.")
    }

    private suspend fun Dialogue.whereIsTracker() {
        chatPlayer(quiz, "Where can I find that tracker again?")
        chatNpc(neutral, "He should be at Tyras's old camp. It's south east of here, just north of that poisoned lake. Once you've found the camp, come and let me know.")
    }

    private suspend fun Dialogue.givePendant() {
        chatNpc(neutral, "Good day ${player.displayName}. How may I help you now?")
        chatPlayer(neutral, "Your scout refused to help. He asked for proof that you sent me.")
        chatNpc(neutral, "Bless his loyalty but curse his suspicion. Here he will recognise this.")
        if (access.invAdd(player.inv, PENDANT).failure) {
            objbox(PENDANT, "Lord Iorwerth tries to give you a crystal pendant but you don't have enough room for it.")
            return
        }
        regicide.advanceTo(access, STAGE_GOT_PENDANT)
        objbox(PENDANT, "Lord Iorwerth gives you a crystal pendant.")
    }

    private suspend fun Dialogue.searching() {
        chatNpc(quiz, "${player.displayName}, how goes your search? Any luck hunting down that brigand?")
        chatPlayer(neutral, "I've found no trace of him as yet.")
        if (regicide.stage(player) >= STAGE_DENSE_FOREST) {
            chatNpc(neutral, "Keep at it. Come find me when you've located the camp.")
            return
        }
        chatNpc(neutral, "I'm sure you will find something at his old camp that can help you.")
        chatPlayer(neutral, "I'll take a look and see what I can find.")
        chatNpc(neutral, "Good. Come find me when you've located the camp.")
    }

    private suspend fun Dialogue.campFound() {
        chatNpc(quiz, "${player.displayName} how goes your search... Any luck hunting down that brigand?")
        chatPlayer(happy, "I've finally tracked the brute down. His camp is in a secluded bit of forest, almost directly south of here.")
        chatNpc(neutral, "Good job. As we don't have the troops available to take on his forces directly, I suggest we try some alternative tactics.")
        chatNpc(neutral, "I have this book here which details an explosive reaction. We should be able to use it to create a bomb.")
        chatPlayer(happy, "Well that should make short work of Tyras.")
        chatNpc(neutral, "Indeed. Have a read through and see what you think. Once you have, let me know if you have any questions.")
        if (access.invAdd(player.inv, BOOK).failure) {
            objbox(BOOK, "Lord Iorwerth tries to give you a book but you don't have enough room for it.")
            return
        }
        regicide.advanceTo(access, STAGE_MAKE_BOMB)
        objbox(BOOK, "Lord Iorwerth gives you a book.")
    }

    private suspend fun Dialogue.makingBomb() {
        if (access.owns(BARREL_BOMB)) {
            finishedBomb()
            return
        }
        if (!access.owns(BOOK)) {
            chatPlayer(sad, "I lost my copy of The Big Book of Bangs.")
            chatNpc(neutral, "Its lucky I had a scribe make a few copies then, is it not?")
            if (access.invAdd(player.inv, BOOK).failure) {
                objbox(BOOK, "Lord Iorwerth tries to give you a book but you don't have enough room for it.")
                return
            }
            objbox(BOOK, "Lord Iorwerth gives you a book.")
            chatPlayer(happy, "Thank you.")
            return
        }
        chatPlayer(neutral, "Hello.")
        chatNpc(quiz, "Good day ${player.displayName}. Have you had any luck with that book?")
        ingredients()
    }

    /**
     * The five questions about the book. Six answers do not fit one menu, so the fuse and the
     * goodbye share a second page.
     */
    private suspend fun Dialogue.ingredients() {
        while (true) {
            val choice =
                menu(
                    "I need some quicklime." to Ingredient.QUICKLIME,
                    "I need some sulphur." to Ingredient.SULPHUR,
                    "I need some naphtha." to Ingredient.NAPHTHA,
                    "I need a barrel." to Ingredient.BARREL,
                    "More..." to null,
                ) ?: menu("I need a fuse." to Ingredient.FUSE, "That's all thanks." to null)
            if (choice == null) {
                chatPlayer(neutral, "That's all thanks.")
                chatNpc(neutral, "Let me know if you need anything else.")
                return
            }
            regicide.markAsked(player, choice)
            when (choice) {
                Ingredient.QUICKLIME -> {
                    chatPlayer(neutral, "I need some quicklime.")
                    chatNpc(quiz, "Quicklime?")
                    chatPlayer(neutral, "Apparently it's made by heating limestone.")
                    chatNpc(neutral, "Ah, I see. There's a mine directly east of here where you can mine limestone.")
                }
                Ingredient.SULPHUR -> {
                    chatPlayer(neutral, "I need some sulphur.")
                    chatNpc(neutral, "Check the shore south of the old camp you were at earlier. I've seen sulphur there in the past, it comes from that poisoned lake.")
                    chatPlayer(happy, "Sounds good, thanks.")
                }
                Ingredient.NAPHTHA -> {
                    chatPlayer(neutral, "I need some naphtha.")
                    chatNpc(quiz, "Naphtha?")
                    chatPlayer(neutral, "According to the book, you get it by distilling coal tar in a still.")
                    chatNpc(neutral, "Well you should be able to get some coal tar from that poisoned lake to the south. As for the rest, I'm afraid I don't have the knowledge to understand it.")
                    chatPlayer(neutral, "Hmm... I know someone from Ardougne who might be able to help. She knows a thing or two about chemistry.")
                    chatNpc(neutral, "Perfect! Take the book to her and see if she can help. Just don't tell her what it's for. Even if she's trustworthy, we can't take any risks.")
                }
                Ingredient.BARREL -> {
                    chatPlayer(neutral, "I need a barrel.")
                    chatNpc(neutral, "Have a look around the camp, there should be some empty barrels lying around.")
                    chatPlayer(happy, "Will do, thanks.")
                }
                Ingredient.FUSE -> {
                    chatPlayer(neutral, "I need a fuse.")
                    chatNpc(neutral, "Some sort of fabric should work. You can use the loom here if needed.")
                }
            }
            chatNpc(quiz, "Is there anything else I can help with?")
        }
    }

    private suspend fun Dialogue.finishedBomb() {
        chatPlayer(neutral, "Hello.")
        chatNpc(quiz, "Good day ${player.displayName}. Have you had any luck with that book?")
        chatPlayer(happy, "I have finished the bomb.")
        chatNpc(happy, "Excellent work. Now we can finally deal with Tyras.")
        chatPlayer(quiz, "So what should I do? Plant it in his camp?")
        chatNpc(neutral, "No, you'll be spotted by the guards if you try that. You'll need to get it in from outside.")
        chatPlayer(quiz, "Any suggestions?")
        chatNpc(neutral, "Tyras and his men have used catapults against us in the past. Maybe you can turn one of them against him.")
        player.catapultChat = 1
        chatNpc(neutral, "Now you'd better get going. Don't forget, you'll need a tinderbox to light that fuse. Return to me once the deed is done.")
    }

    /**
     * The letter and the pass through Arandar come together: without room for the letter neither
     * is given, so Arandar only ever opens with the letter in hand.
     */
    private suspend fun Dialogue.tyrasDead() {
        chatPlayer(happy, "Lord Iorwerth, it is done.")
        chatNpc(happy, "Good good... One of my scouts reported that they saw his tent in a ball of flames. You have done well.")
        chatNpc(neutral, "I'm sure you will want to get back to your king and tell him. He will undoubtedly want some proof. He should trust me enough in this matter, give him this letter, it verifies that the job is done.")
        if (access.invAdd(player.inv, IORWERTH_MESSAGE).failure) {
            objbox(IORWERTH_MESSAGE, "Lord Iorwerth tries to give you a scroll but you don't have enough room for it.")
            return
        }
        regicide.advanceTo(access, STAGE_LETTER)
        objbox(IORWERTH_MESSAGE, "Lord Iorwerth gives you a scroll.")
        chatNpc(neutral, "As a token of my own thanks, I give you access to the pass through Arandar. You'll find the start of the trail in the far north east of the woods.")
        chatPlayer(happy, "Thank you my lord.")
    }

    private suspend fun Dialogue.letterSent() {
        if (!access.owns(IORWERTH_MESSAGE)) {
            chatPlayer(sad, "Lord Iorwerth, I have lost the message for King Lathas.")
            chatNpc(neutral, "Here have another copy, but don't let it fall into the wrong hands.")
            if (access.invAdd(player.inv, IORWERTH_MESSAGE).failure) {
                objbox(IORWERTH_MESSAGE, "Lord Iorwerth tries to give you a scroll but you don't have enough room for it.")
                return
            }
            objbox(IORWERTH_MESSAGE, "Lord Iorwerth gives you a scroll.")
            chatPlayer(happy, "Thank you my lord.")
            return
        }
        chatPlayer(neutral, "Good day Lord Iorwerth.")
        chatNpc(quiz, "Have you delivered the message to King Lathas yet?")
        chatPlayer(neutral, "Not yet my lord.")
        chatNpc(neutral, "There is no time for delay, he'll need to see it.")
    }
}
