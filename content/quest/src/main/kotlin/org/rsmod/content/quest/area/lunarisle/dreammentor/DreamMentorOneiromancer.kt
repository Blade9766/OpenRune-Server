package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.DREAMY_LAMP
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_CONQUERED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_POTION
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_RESTORED
import org.rsmod.game.entity.Player

/**
 * What the Oneiromancer has to say about Cyrisus, offered as a topic in her own conversation once
 * Lunar Diplomacy is done: the potion of shared dreaming, a spare dream vial, the end of the quest
 * and, afterwards, a spare dreamy lamp for anyone who lost theirs before rubbing it.
 */
@Singleton
class DreamMentorOneiromancer @Inject constructor(private val dreamMentor: DreamMentorQuest) {
    fun hasTopic(player: Player): Boolean {
        val stage = dreamMentor.stage(player)
        return stage in STAGE_RESTORED..STAGE_CONQUERED || dreamMentor.isComplete(player) && lampLost(player)
    }

    suspend fun Dialogue.cyrisus() {
        val stage = dreamMentor.stage(player)
        when {
            dreamMentor.isComplete(player) -> replaceLamp()
            stage == STAGE_RESTORED -> introduceDream()
            stage == STAGE_POTION -> potionProgress()
            stage == STAGE_CONQUERED -> finish()
        }
    }

    private suspend fun Dialogue.cyrisusSays(mesanim: MesAnimType, text: String) {
        chatNpcSpecific("Cyrisus", CombatStyle.of(player).cyrisusType, mesanim, text)
    }

    private suspend fun Dialogue.introduceDream() {
        chatPlayer(happy, "Hello again.")
        chatNpc(happy, "${player.displayName}! It's been a while. More astral runes to craft?")
        chatPlayer(neutral, "Not today. I'm here for a friend. You might know him: Cyrisus.")
        chatNpc(happy, "Cyrisus? A bright man. What about him?")
        cyrisusSays(happy, "Hello!")
        chatNpc(happy, "Cyrisus! Your friend was just telling me about you.")
        cyrisusSays(neutral, "We were hoping you could help me get over my fear of combat. In the Dreamland, perhaps?")
        chatNpc(
            neutral,
            "With a potion of shared dreaming, yes. It will carry you both into the same dream, and " +
                "there you can face what frightens you.",
        )
        chatPlayer(happy, "That sounds simple enough.")
        chatNpc(
            worried,
            "It is dangerous, though. The potion binds your minds together. Die in that dream and " +
                "you die for real, just as if a monster had killed you out here.",
        )
        cyrisusSays(worried, "I'm not so sure about this any more...")
        chatPlayer(neutral, "We'll be fine, Cyrisus. How do we make it?")
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "I'll give you a vial for it, but you have no room to carry one. Come back when you do.")
            return
        }
        access.invAdd(access.inv, DreamPotion.VIAL_EMPTY)
        dreamMentor.advanceTo(access, STAGE_POTION)
        player.dreamCyrisusSpot = CyrisusSpot.brazier(CombatStyle.of(player))
        objbox(DreamPotion.VIAL_EMPTY, "The Oneiromancer hands you a dream vial.")
        chatNpc(
            neutral,
            "Fill this vial with water, then add goutweed and a ground astral rune. Light the " +
                "brazier in the Dream Hall and give the potion to Cyrisus there.",
        )
        cyrisusSays(quiz, "Do we need those ceremonial robes again?")
        chatNpc(happy, "No, no. Those were for the ceremony. Wear whatever you like.")
        cyrisusSays(happy, "See you at the brazier!")
    }

    private suspend fun Dialogue.potionProgress() {
        chatPlayer(neutral, "Hello.")
        chatNpc(quiz, "How is it going?")
        when {
            player.dreamCutsceneSeen -> {
                chatPlayer(neutral, "We've been into the dream, but we haven't beaten it yet.")
                chatNpc(neutral, "Then try again. There's still some potion left for you; Cyrisus has it.")
            }
            DreamTown.POTION in player.inv -> {
                chatPlayer(happy, "We have the potion of shared dreaming. Now we just need to face Cyrisus' dream.")
                chatNpc(happy, "I like your spirit!")
            }
            DreamPotion.VIALS.any { owns(it) } -> {
                chatPlayer(quiz, "What goes in the potion again?")
                chatNpc(neutral, "Water, then goutweed, then a ground astral rune.")
            }
            else -> {
                chatPlayer(sad, "I seem to have lost the vial. Do you have another?")
                chatNpc(quiz, "And the magic word is?")
                chatPlayer(neutral, "Er... Lunar?")
                chatNpc(bored, "No.")
                chatPlayer(happy, "Please!")
                if (player.inv.freeSpace() == 0) {
                    chatNpc(neutral, "You don't have room for it. Come back when you do.")
                    return
                }
                access.invAdd(access.inv, DreamPotion.VIAL_EMPTY)
                objbox(DreamPotion.VIAL_EMPTY, "The Oneiromancer hands you another dream vial.")
            }
        }
    }

    private suspend fun Dialogue.finish() {
        chatPlayer(happy, "We did it!")
        cyrisusSays(happy, "I really did conquer my fear of combat!")
        chatNpc(happy, "That's wonderful. Congratulations, both of you.")
        cyrisusSays(happy,
            "I was terrified at first, but watching ${player.displayName} fight gave me the courage. " +
                "Please, let me pass some of what I know on to you.",
        )
        if (player.inv.freeSpace() == 0) {
            cyrisusSays(neutral, "Oh, but you'll need a free space in your pack first.")
            chatPlayer(neutral, "I'll make some room.")
            return
        }
        chatPlayer(quiz, "What do you mean?")
        cyrisusSays(neutral, "The Moon Clan taught me to pass knowledge from mind to mind. Relax and keep still.")
        access.anim(KNOWLEDGE_SEQ)
        access.delay(KNOWLEDGE_TICKS)
        chatPlayer(shocked, "Whoa. That felt very strange... but I do feel wiser!")
        cyrisusSays(happy,
            "My pleasure. Now I'm off to put my courage to the test. I'm going to become the " +
                "greatest warrior there ever was!",
        )
        chatPlayer(happy, "Be careful!")
        player.dreamCyrisusSpot = CyrisusSpot.CAVE
        dreamMentor.complete(access)
    }

    private suspend fun Dialogue.replaceLamp() {
        chatPlayer(sad, "I've lost the lamp Cyrisus gave me.")
        if (player.inv.freeSpace() == 0) {
            chatNpc(neutral, "I have it here, but you have no room for it.")
            return
        }
        chatNpc(neutral, "I know. It found its way back to me.")
        access.invAdd(access.inv, DREAMY_LAMP)
        objbox(DREAMY_LAMP, "The Oneiromancer hands you a dreamy lamp.")
    }

    private fun lampLost(player: Player): Boolean =
        !player.dreamLampUsed && DREAMY_LAMP !in player.inv && DREAMY_LAMP !in player.worn &&
            !player.bankContains(DREAMY_LAMP)

    private fun Dialogue.owns(obj: String): Boolean = obj in player.inv || access.bank.contains(obj)

    private fun Player.bankContains(obj: String): Boolean = invMap["inv.bank"]?.contains(obj) == true

    private companion object {
        const val KNOWLEDGE_SEQ = "seq.dream_player_points"
        const val KNOWLEDGE_TICKS = 3
    }
}
