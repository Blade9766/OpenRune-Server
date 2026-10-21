package org.rsmod.content.quest.area.lunarisle.dreammentor

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_CONQUERED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_POTION
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_RESTORED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.lunarBrazierLit
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.entity.Npc

/**
 * Cyrisus once he is out of the cave: waiting by the Oneiromancer, and at the brazier in the Dream
 * Hall, where the dream potion and a lit brazier send the two of them into his dream. The first
 * time uses up the potion; after that there is always a little left for another try.
 */
@Singleton
class DreamTown
@Inject
constructor(
    private val dreamMentor: DreamMentorQuest,
    private val arena: DreamArena,
    private val fight: DreamFight,
) {
    fun isTownCyrisus(npc: Npc): Boolean = npc.isType(BY_ONEIROMANCER) || npc.isType(BY_BRAZIER)

    suspend fun ProtectedAccess.talkInTown(npc: Npc) {
        if (npc.isType(BY_BRAZIER)) {
            var entry = Entry.None
            startDialogue(npc) { entry = atBrazier() }
            enter(entry)
            return
        }
        val stage = dreamMentor.stage(player)
        startDialogue(npc) {
            when {
                stage == STAGE_RESTORED -> {
                    chatPlayer(happy, "Hi, Cyrisus.")
                    chatNpc(worried, "Hello, ${player.displayName}. I'm a bit nervous. Would you speak to the Oneiromancer for me? I'd like to get this over with.")
                }
                stage == STAGE_CONQUERED -> {
                    chatPlayer(happy, "You did it!")
                    chatNpc(happy, "We did it. I couldn't have managed without you. Let's tell the Oneiromancer.")
                }
                else -> chatNpc(happy, "Hello again, ${player.displayName}!")
            }
        }
    }

    fun ProtectedAccess.potionOnBrazier() {
        if (dreamMentor.stage(player) != STAGE_POTION) {
            mes("Nothing interesting happens.")
            return
        }
        mes("Cyrisus is the one who knows what to do with the potion. Talk to him by the brazier.")
    }

    private enum class Entry {
        None,
        First,
        Again,
    }

    private suspend fun Dialogue.atBrazier(): Entry {
        if (dreamMentor.stage(player) != STAGE_POTION) {
            chatNpc(happy, "Hello, ${player.displayName}.")
            return Entry.None
        }
        if (player.dreamCutsceneSeen) {
            chatPlayer(neutral, "We need to go back into the dream.")
            chatNpc(worried, "Must we?")
            chatPlayer(neutral, "We must.")
            if (!player.lunarBrazierLit) {
                chatNpc(neutral, "Then you'll need to light the brazier with a tinderbox first.")
                return Entry.None
            }
            chatNpc(neutral, "Well, there's a little potion left in the bottle.")
            return if (offerDream(consumePotion = false)) Entry.Again else Entry.None
        }
        if (POTION !in player.inv) {
            chatPlayer(neutral, "So we need to make the potion of shared dreaming.")
            if (DreamPotion.VIALS.any { it in player.inv }) {
                chatNpc(neutral, "That's right: water, goutweed and a ground astral rune, all in that little vial.")
            } else {
                chatNpc(confused, "Water, goutweed and an astral rune... hang on, where's the vial?")
                chatPlayer(sad, "Oops.")
                chatNpc(neutral, "Ask the Oneiromancer nicely. I'm sure she has another.")
            }
            return Entry.None
        }
        chatPlayer(happy, "I have the potion of shared dreaming. What now?")
        chatNpc(neutral, "Light the brazier with a tinderbox, then hand me the potion.")
        if (!player.lunarBrazierLit) {
            return Entry.None
        }
        return if (offerDream(consumePotion = true)) Entry.First else Entry.None
    }

    private suspend fun Dialogue.offerDream(consumePotion: Boolean): Boolean {
        mesbox(
            "<col=800000>Warning:</col> You can enter the dream, but if you die there you will lose " +
                "your items and have to make your own way back to the island. The lectern in the " +
                "dream has a 'Leave' option for a quick exit.",
        )
        val go = choice2("Yes, let's go!", true, "Not yet.", false, title = "Proceed into the dream?")
        if (!go) {
            chatPlayer(neutral, "Not yet.")
            chatNpc(neutral, "Tell me when you're ready.")
            return false
        }
        chatPlayer(happy, "Yes, let's go!")
        return !consumePotion || access.invDel(access.inv, POTION).success
    }

    private suspend fun ProtectedAccess.enter(entry: Entry) {
        if (entry == Entry.None || dreamMentor.stage(player) != STAGE_POTION) {
            return
        }
        val firstTime = entry == Entry.First
        ifClose()
        player.lunarBrazierLit = false
        if (firstTime) {
            player.dreamCutsceneSeen = true
        }
        fadeToBlack()
        val entered = with(arena) { enterDream() }
        if (!entered) {
            fadeFromBlack()
            closeFadeOverlay()
            return
        }
        val encounter = fight.start(player)
        softTimer(DreamFight.FIGHT_TIMER, 1)
        delay(1)
        fadeFromBlack()
        closeFadeOverlay()
        val cyrisus = encounter.cyrisus
        if (firstTime && cyrisus != null) {
            startDialogue(cyrisus) { introduction() }
        } else if (cyrisus != null) {
            cyrisus.say("Here we go again...")
        }
        fight.engage(player)
    }

    private suspend fun Dialogue.introduction() {
        chatPlayer(shocked, "Aaargh! What in Gielinor is that?")
        chatPlayer(worried, "Cyrisus?")
        chatNpc(happy, "What a lovely lectern.")
        chatPlayer(worried, "Cyrisus. Turn. Around.")
        chatNpc(shocked, "Waaah! What is THAT?")
        chatPlayer(neutral, "Don't ask me, it's your dream! You really are scared of fighting.")
        chatNpc(worried, "So we have to fight my fears, don't we?")
        chatPlayer(neutral, "We do. Come on, Cyrisus!")
        chatNpc(worried, "I'm still too frightened. Let me watch you fight; it might give me the courage.")
    }

    companion object {
        const val BY_ONEIROMANCER = "npc.dream_cyrisus_outsidemulti"
        const val BY_BRAZIER = "npc.dream_cyrisus_outsidebraziermulti"
        const val BRAZIER = "loc.lunar_moonclan_brazier_multi"
        const val POTION = "obj.dream_vial_full"
    }
}
