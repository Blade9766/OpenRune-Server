package org.rsmod.content.quest.area.tirannwn.templeoflight.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.tirannwn.mourningsend.swap
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.CHARGED_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NEW_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.NEW_KEY
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.SAMPLE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_CRYSTAL
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_FOUND
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_KEY
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_RESTORED
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_SAMPLE
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.tirannwn.templeoflight.MourningsEndPart2Quest.Companion.TRINKET
import org.rsmod.content.quest.area.tirannwn.templeoflight.ownsAnywhere
import org.rsmod.content.quest.manager.startQuestPrompt

/**
 * Arianwyn's side of Mourning's End Part II, from Lletya: he starts it once Part I is done, hears
 * the player's reports, has Eluned turn the blackened sample into a new crystal, replaces a lost
 * crystal or trinket, and completes the quest once the safeguards are restored. Part I's own
 * Arianwyn script hands over to this one as soon as Part I counts as done.
 *
 * The sample becomes the new crystal in one swap, and the stage moves in the same tick, so a
 * conversation cut short before the swap leaves the sample, after it the crystal, never both or
 * neither. The quest completes only here, after the restoration, and the quest manager hands out
 * the experience and the trinket once.
 */
@Singleton
class ArianwynTempleTalk @Inject constructor(private val quest: MourningsEndPart2Quest) {

    suspend fun Dialogue.arianwyn() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> afterQuest()
            stage == 0 -> offer()
            stage == STAGE_STARTED -> {
                chatPlayer(quiz, "What do I need to do again?")
                chatNpc(neutral, "Go back to the Mourner Headquarters and get into their mine. Once you're there, see what you can find out about the Temple of Light.")
                chatPlayer(neutral, "I'm on it.")
                chatNpc(neutral, "Thank you, friend. Don't forget your disguise.")
            }
            stage < STAGE_FOUND -> {
                chatNpc(quiz, "How goes it, ${player.displayName}?")
                chatPlayer(neutral, "Well. I've been given a key to the mine beneath West Ardougne, so I can start searching it.")
                chatNpc(happy, "Very good. Let me know when you find something.")
            }
            stage == STAGE_FOUND -> reportDigTeam()
            stage == STAGE_SAMPLE -> sampleCheck()
            stage == STAGE_CRYSTAL -> crystalAdvice()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.offer() {
        chatPlayer(quiz, "Hello Arianwyn. What can we do about the Temple of Light?")
        chatNpc(neutral, "First we must know how close the Iorwerth elves are to finding it. Go back to the Mourner Headquarters and get into their mine. See what you can learn about the temple down there.")
        if (!startQuestPrompt(quest.quest)) {
            chatPlayer(neutral, "I don't have time for that right now.")
            chatNpc(worried, "Don't leave it too long. Every moment we waste is a risk.")
            return
        }
        quest.advanceTo(access, STAGE_STARTED)
        chatPlayer(neutral, "I'll head over there right away.")
        chatNpc(neutral, "Thank you, friend. Don't forget your disguise.")
    }

    private suspend fun Dialogue.reportDigTeam() {
        chatNpc(quiz, "${player.displayName}, what news?")
        chatPlayer(neutral, "I think I've found the way into the temple. One of the dig crews found it, but something like shadows killed them, so the other mourners don't know yet.")
        chatNpc(worried, "Then it's only a matter of time before they find it.")
        chatPlayer(quiz, "So what do we do?")
        chatNpc(neutral, "I've been reading while you were away. The temple was built with safeguards to keep out all but Seren's chosen.")
        chatNpc(neutral, "If shadows roam outside it, those safeguards must have long since failed. But I believe we can restore them.")
        chatPlayer(quiz, "How?")
        chatNpc(neutral, "The temple guards an ancient altar with power over life and death. I suspect that altar is why Lord Iorwerth wants the temple so badly.")
        chatPlayer(quiz, "To summon the Dark Lord?")
        chatNpc(neutral, "Indeed. The altar is also the key to the safeguards, and we will need it to restore them.")
        chatNpc(neutral, "First, find the crystal that powers the safeguards. As they no longer work, I expect it will have turned black. Bring me a sample of it.")
        quest.advanceTo(access, STAGE_SAMPLE)
        if (player.inv.contains(SAMPLE)) {
            showSample()
            return
        }
        chatPlayer(neutral, "Find a black crystal in the temple and bring you a piece of it. Got it. I'll be back soon.")
    }

    private suspend fun Dialogue.sampleCheck() {
        chatNpc(quiz, "${player.displayName}, do you have that sample yet?")
        if (player.inv.contains(SAMPLE)) {
            showSample()
            return
        }
        chatPlayer(neutral, "Not yet.")
        chatNpc(neutral, "Don't take too long. Search the temple for a blackened crystal and bring a piece of it back here.")
    }

    private suspend fun Dialogue.showSample() {
        chatPlayer(quiz, "Is this it?")
        objbox(SAMPLE, "You show the blackened crystal to Arianwyn.")
        chatNpc(happy, "Yes! Good work.")
        chatPlayer(neutral, "Thanks. I found it on the top floor of the temple.")
        chatNpc(neutral, "With this, a good crystal singer should be able to make a replacement. One moment, I'll call for Eluned.")
        chatNpc(neutral, "Thank you for answering my call, Eluned.")
        eluned(happy, "Any time, Arianwyn. How can I help?")
        chatNpc(neutral, "${player.displayName} has an old crystal sample. We need you to make a replacement for it.")
        eluned(neutral, "Hand it here and I'll take a look.")
        if (!access.swap(listOf(SAMPLE to 1), listOf(NEW_CRYSTAL to 1))) {
            return
        }
        quest.advanceTo(access, STAGE_CRYSTAL)
        objbox(SAMPLE, "You hand Eluned the blackened crystal.")
        eluned(neutral, "Now, let me see... hmm... ah, there we go.")
        objbox(NEW_CRYSTAL, "Eluned gives you a newly formed crystal.")
        chatPlayer(happy, "Thanks.")
        eluned(neutral, "No problem. Arianwyn will tell you what to do with it. I'd better be going; see you around.")
        crystalAdvice()
    }

    private suspend fun Dialogue.crystalAdvice() {
        if (!access.ownsAnywhere(NEW_CRYSTAL) && !access.ownsAnywhere(CHARGED_CRYSTAL)) {
            chatPlayer(worried, "I've lost the crystal Eluned made.")
            if (player.inv.freeSpace() < 1) {
                chatNpc(neutral, "Eluned left me a spare. Make some room in your pack and I'll give it to you.")
                return
            }
            if (access.invAdd(player.inv, NEW_CRYSTAL).failure) {
                return
            }
            chatNpc(neutral, "Luckily Eluned left me a spare. Try to keep hold of this one.")
            objbox(NEW_CRYSTAL, "Arianwyn gives you a newly formed crystal.")
            return
        }
        chatPlayer(quiz, "I have the new crystal. What am I meant to do with it?")
        chatNpc(neutral, "Take it to the far end of the temple and place it on the altar there. That will give it the power it needs to guard the temple.")
        chatPlayer(worried, "This is starting to sound hard.")
        chatNpc(neutral, "It should be simple enough. Once it is charged, go back to the blackened crystal and place the new shard with the others. That should restore the safeguards.")
        chatPlayer(quiz, "And that's it?")
        chatNpc(neutral, "Yes, that is all.")
        chatPlayer(neutral, "Right then, I'll get to it.")
    }

    private suspend fun Dialogue.thanks() {
        if (quest.stage(player) < STAGE_RESTORED) {
            return
        }
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Welcome back, ${player.displayName}. Make some room in your pack, I have something for you.")
            return
        }
        chatPlayer(happy, "Good news, Arianwyn. I've repaired the safeguards on the Temple of Light.")
        chatNpc(happy, "That is good news indeed, ${player.displayName}. Nobody should get in for another thousand years.")
        chatPlayer(worried, "Well... not quite. Some dwarves have tunnelled into the back of the temple.")
        chatNpc(confused, "Dwarves?")
        chatPlayer(neutral, "Yes. They've set up an outpost in the Underground Pass.")
        chatNpc(neutral, "Interesting. I'll send an envoy to speak with them. Every way into the temple must be secure.")
        chatNpc(neutral, "Even so, I can't see Lord Iorwerth finding their tunnel any time soon. For now he has no way to summon the Dark Lord.")
        chatPlayer(quiz, "So what happens next?")
        chatNpc(neutral, "This is far from the end. The Iorwerth forces are still at large and we are not strong enough to face them. But with the temple secure, the greatest danger has passed.")
        chatNpc(neutral, "I will let you know if anything changes. For now, your work is done.")
        chatNpc(neutral, "I can't offer much of a reward, I'm afraid, but I had Eluned make this for you. It will let you past the temple's defences.")
        quest.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterQuest() {
        if (!access.ownsAnywhere(TRINKET)) {
            chatPlayer(worried, "I've lost the crystal trinket you gave me.")
            if (player.inv.freeSpace() < 1) {
                chatNpc(neutral, "I have another. Make some room in your pack and it's yours.")
                return
            }
            if (access.invAdd(player.inv, TRINKET).failure) {
                return
            }
            chatNpc(neutral, "Eluned made a few. Here, take another.")
            objbox(TRINKET, "Arianwyn gives you a crystal trinket.")
            return
        }
        chatNpc(neutral, "Welcome back, ${player.displayName}. Thanks to you the temple is safe, for now.")
        chatNpc(neutral, "We are still watching Lord Iorwerth's forces. I will send word if we need you again.")
    }

    private suspend fun Dialogue.eluned(mesanim: MesAnimType, text: String) {
        chatNpcSpecific("Eluned", ELUNED_VIS, mesanim, text)
    }

    companion object {
        const val ELUNED_VIS = "npc.roving_female_woodelf_2op"
    }
}

/**
 * Essyllt's side of Part II: the new key for the mines once Arianwyn has sent the player, his
 * questions about the missing dig team, and a replacement key whenever the player has lost theirs
 * (also from his desk).
 */
@Singleton
class EssylltTempleTalk @Inject constructor(private val quest: MourningsEndPart2Quest) {

    suspend fun Dialogue.essyllt() {
        val stage = quest.stage(player)
        when {
            stage == 0 && !quest.unlocked(player) -> {
                chatPlayer(quiz, "Do you have the key to the mines yet?")
                chatNpc(neutral, "The guard is taking his time, but we should have it soon.")
                chatPlayer(neutral, "Alright, I'll report in again later.")
            }
            stage == STAGE_STARTED -> handOverKey()
            stage in STAGE_KEY until STAGE_FOUND -> {
                chatNpc(quiz, "Any news yet on our missing dig team?")
                chatPlayer(neutral, "Not yet.")
                lostKey()
            }
            !quest.unlocked(player) -> {
                chatNpc(quiz, "Any news yet on our missing dig team?")
                chatPlayer(shifty, "No. I've looked everywhere, but there's no sign of them.")
                chatNpc(worried, "This is not good. Did you find anything else down there?")
                chatPlayer(neutral, "Nothing but the beasts you warned me about.")
                chatNpc(neutral, "Then go back and keep searching. And tell me if you see anything elven-made down there; any clue to where the temple is would help.")
                chatPlayer(neutral, "I'll let you know as soon as I find something.")
                chatNpc(neutral, "Good. Keep at it.")
                lostKey()
            }
            else -> lostKey(always = true)
        }
    }

    private suspend fun Dialogue.handOverKey() {
        chatPlayer(quiz, "Do you have the key to the excavation site yet?")
        val hasKey = access.ownsAnywhere(NEW_KEY)
        if (!hasKey && player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Free up some space in your pack, then we can talk about keys.")
            return
        }
        chatNpc(neutral, "The guard has finally come back. He took so long because he had ten times as many cut as we needed.")
        chatPlayer(laugh, "That's handy. I'm always losing keys.")
        chatNpc(neutral, "Well, there are plenty now.")
        chatPlayer(quiz, "So... you mentioned a task for me in the mines?")
        chatNpc(neutral, "That's right. One of our dig teams went missing while searching for the temple. I need you to find out what happened to them.")
        chatPlayer(neutral, "Sounds easy enough.")
        chatNpc(neutral, "You say that now, but the beasts in the mines are deadly. You'll have to be very careful down there.")
        chatPlayer(neutral, "I'll be on my guard.")
        if (hasKey) {
            quest.advanceTo(access, STAGE_KEY)
            chatNpc(neutral, "Good. You already have a key, so get to it.")
            return
        }
        if (access.invAdd(player.inv, NEW_KEY).failure) {
            return
        }
        quest.advanceTo(access, STAGE_KEY)
        chatNpc(neutral, "Good. You'll need this.")
        objbox(NEW_KEY, "Essyllt hands you a newly cut key.")
        chatPlayer(neutral, "Thanks, I'll get to work.")
        chatNpc(neutral, "Good luck down there.")
    }

    private suspend fun Dialogue.lostKey(always: Boolean = false) {
        if (access.ownsAnywhere(NEW_KEY)) {
            if (always) {
                chatNpc(neutral, "Back again? There's work to be done.")
            }
            return
        }
        chatPlayer(worried, "I've lost the key to the mine.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Free up some space in your pack and take another from my desk.")
            return
        }
        if (access.invAdd(player.inv, NEW_KEY).failure) {
            return
        }
        chatNpc(neutral, "Have another one, then. If you lose that, help yourself to one from my desk.")
        objbox(NEW_KEY, "Essyllt hands you a newly cut key.")
    }
}
