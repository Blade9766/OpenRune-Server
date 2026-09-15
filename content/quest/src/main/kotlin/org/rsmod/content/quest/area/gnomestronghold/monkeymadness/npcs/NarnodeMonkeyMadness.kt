package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.NARNODE_ORDERS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.ROYAL_SEAL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_ALLIANCE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_APE_ATOLL
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_CARANOCK_MET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_CRASH_ISLAND
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HANGAR
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_HAS_ORDERS
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_STARTED

/**
 * King Narnode's side of Monkey Madness. The Grand Tree script owns the npc; once that quest is
 * done it hands every conversation here.
 */
@Singleton
class NarnodeMonkeyMadness @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest) {

    suspend fun Dialogue.talk() {
        when (monkeyMadness.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> if (player.inv.contains(ROYAL_SEAL)) awaitingShipyard() else replaceSeal()
            STAGE_CARANOCK_MET -> caranockReport()
            STAGE_HAS_ORDERS -> if (player.inv.contains(NARNODE_ORDERS)) findDaero() else replaceOrders()
            STAGE_HANGAR, STAGE_CRASH_ISLAND -> {
                chatNpc(quiz, "Any word from Daero and Waydar?")
                chatPlayer(neutral, "Not yet. We're still trying to find out where the squad went.")
                chatNpc(worried, "Please hurry. Every day without news is a day too many.")
            }
            STAGE_COMPLETE -> postQuest()
            STAGE_ALLIANCE -> if (monkeyMadness.demonSlain.get(player)) finale() else awaitingNews()
            else -> awaitingNews()
        }
    }

    private suspend fun Dialogue.awaitingNews() {
        chatNpc(quiz, "Have you found my 10th squad?")
        chatPlayer(neutral, "I've found them. They crashed on an island far to the south, and the monkeys there are anything but friendly.")
        chatNpc(worried, "Monkeys? Then Sergeant Garkor will need every bit of your help. Do whatever he asks of you.")
        if (monkeyMadness.stage(player) >= STAGE_APE_ATOLL) {
            chatNpc(neutral, "Daero can fly you back south whenever you are ready.")
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(happy, "Hello Traveller! Thanks to you the Grand Tree grows strong again.")
        chatPlayer(happy, "Glad I could help, your highness.")
        chatNpc(worried, "I only wish that were the end of it. I have a new problem, and I fear it may be worse than the last.")
        when (choice2("What's the problem?", 1, "I'll be off now.", 2)) {
            1 -> {
                chatPlayer(quiz, "What's the problem?")
                theMissingSquad()
            }
            2 -> {
                chatPlayer(neutral, "I'll be off now.")
                chatNpc(neutral, "Very well. My door is always open to you, Traveller.")
            }
        }
    }

    private suspend fun Dialogue.theMissingSquad() {
        chatNpc(neutral, "After Glough was arrested I sent my finest soldiers, the 10th squad of the Royal Guard, to Karamja to close down his shipyard.")
        chatNpc(neutral, "It was a simple assignment: fly over, dismiss the workers, make sure no more warships are built.")
        chatNpc(worried, "That was weeks ago. I have not heard a single word from them since. Not a message, not a glider, nothing.")
        chatPlayer(quiz, "Could they have deserted?")
        chatNpc(angry, "The 10th squad? Never! Sergeant Garkor would sooner die than disobey an order.")
        chatNpc(sad, "That is exactly what I am afraid of.")
        chatNpc(neutral, "I need someone the shipyard does not know. Someone who can find out whether the squad ever arrived. Will you go, Traveller?")
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT."
            )
        }
        when (choice2("Yes.", 1, "No.", 2, title = "Start Monkey Madness I?")) {
            1 -> {
                chatPlayer(happy, "I'll find them for you.")
                if (player.inv.freeSpace() < 1) {
                    chatNpc(neutral, "You will need room in your pack for my seal first.")
                    return
                }
                access.invAdd(player.inv, ROYAL_SEAL)
                monkeyMadness.advanceTo(access, STAGE_STARTED)
                monkeyMadness.syncVars(player)
                objbox(ROYAL_SEAL, "King Narnode hands you the gnome royal seal.")
                chatNpc(happy, "Take my royal seal. Show it at the shipyard and they will know you speak with my voice.")
                chatNpc(neutral, "Fly to Karamja on the glider and look for the shipyard south of the landing. Come straight back with whatever you learn.")
            }
            2 -> {
                chatPlayer(neutral, "I'm sorry, I have other things to do.")
                chatNpc(sad, "Then I must hope they return on their own. Come back if you change your mind.")
            }
        }
    }

    private suspend fun Dialogue.awaitingShipyard() {
        chatNpc(quiz, "Have you been to the shipyard yet?")
        chatPlayer(neutral, "Not yet.")
        chatNpc(neutral, "Show them my seal. It's south of the Karamja glider landing, on the east coast. Please hurry.")
    }

    private suspend fun Dialogue.replaceSeal() {
        chatPlayer(sad, "I seem to have lost your seal.")
        chatNpc(neutral, "Then you had better take another. Guard it well; it carries my authority.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "You will need room in your pack for it first.")
            return
        }
        access.invAdd(player.inv, ROYAL_SEAL)
        objbox(ROYAL_SEAL, "King Narnode hands you another gnome royal seal.")
    }

    private suspend fun Dialogue.caranockReport() {
        chatNpc(quiz, "You're back! What did you find at the shipyard?")
        chatPlayer(neutral, "The squad never arrived. Your liaison officer there, G.L.O. Caranock, thinks strong southerly winds blew their gliders off course.")
        chatNpc(confused, "Off course? Every one of them? The 10th squad has the finest pilots in my kingdom.")
        chatPlayer(neutral, "That's what he said. He seemed very keen for me to leave.")
        chatNpc(neutral, "Hmm. Caranock was one of Glough's appointments. I will take his word for now, but I will not rely on it.")
        chatNpc(neutral, "I have written orders for Daero, my new head tree guardian. They are in an old military cipher, so do not try to read them yourself.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Make some room in your pack and I will give them to you.")
            return
        }
        access.invAdd(player.inv, NARNODE_ORDERS)
        monkeyMadness.advanceTo(access, STAGE_HAS_ORDERS)
        monkeyMadness.syncVars(player)
        objbox(NARNODE_ORDERS, "King Narnode hands you a sealed set of orders.")
        chatNpc(neutral, "You will find Daero on the first floor of the Grand Tree, near the Blurberry Bar. Give the orders to nobody else.")
    }

    private suspend fun Dialogue.findDaero() {
        chatNpc(quiz, "Have you given my orders to Daero?")
        chatPlayer(neutral, "Not yet.")
        chatNpc(neutral, "He is on the first floor by the Blurberry Bar. He will know what to do once he has read them.")
    }

    private suspend fun Dialogue.replaceOrders() {
        chatPlayer(sad, "I've lost the orders you gave me.")
        chatNpc(neutral, "I feared as much, so I wrote them on paper that destroys itself if it is left lying about. Here is a fresh copy.")
        if (player.inv.freeSpace() < 1) {
            chatNpc(neutral, "Make some room in your pack first.")
            return
        }
        access.invAdd(player.inv, NARNODE_ORDERS)
        objbox(NARNODE_ORDERS, "King Narnode hands you another set of orders.")
    }

    private suspend fun Dialogue.finale() {
        chatNpc(quiz, "Traveller! Daero tells me the squad is safe. Is it true?")
        chatPlayer(happy, "It's true. Garkor and his men are free, and the monkeys' demon is dead.")
        chatNpc(shocked, "A demon! So Caranock's story about the wind was a lie after all.")
        chatPlayer(neutral, "Caranock was working for Glough. Between them they had made a deal with Awowogei, the king of the monkeys, and raised a Jungle Demon to attack the stronghold.")
        chatNpc(angry, "Glough! Even in disgrace he plots against us. I will have Caranock arrested and the shipyard torn down to its last plank.")
        chatNpc(happy, "But today is a day for gratitude. You have saved my 10th squad and my kingdom twice over. Please, take this.")
        if (player.inv.freeSpace() < 2) {
            chatNpc(neutral, "You will need two free spaces in your pack for your reward.")
            return
        }
        monkeyMadness.quest.completeQuest(access)
        monkeyMadness.syncVars(player)
        chatNpc(happy, "Daero also wishes to see you. He has offered to train you in the ways of the Royal Guard as thanks for what you did for his soldiers.")
        chatNpc(happy, "And the scimitar sellers of Ape Atoll will be glad of your custom. Few humans have ever earned the right to wield a dragon scimitar.")
    }

    private suspend fun Dialogue.postQuest() {
        chatNpc(happy, "Hello Traveller! The 10th squad send their regards. Sergeant Garkor speaks very highly of you.")
        chatPlayer(happy, "Give him my regards too.")
        if (!monkeyMadness.trainingClaimed.get(player)) {
            chatNpc(neutral, "Don't forget that Daero has offered to train you. You will find him where you left him, by the Blurberry Bar.")
        }
    }
}
