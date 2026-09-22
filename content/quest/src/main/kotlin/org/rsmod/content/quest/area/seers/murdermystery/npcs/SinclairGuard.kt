package org.rsmod.content.quest.area.seers.murdermystery.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest
import org.rsmod.content.quest.area.seers.murdermystery.MurderMysteryQuest.Companion.POISON_DISPROVED
import org.rsmod.content.quest.area.seers.murdermystery.Suspect
import org.rsmod.content.quest.area.seers.murdermystery.murderFoundPrints
import org.rsmod.content.quest.area.seers.murdermystery.murderFoundThread
import org.rsmod.content.quest.area.seers.murdermystery.murderPoisonProgress
import org.rsmod.content.quest.area.seers.murdermystery.murderer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The guards at the Sinclair Mansion: the one at the front gate and the two inside the house. They
 * start the case, and close it once the player can show all three pieces of evidence - the thread,
 * the poison lie and the fingerprints.
 */
class SinclairGuard @Inject constructor(private val murder: MurderMysteryQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (guard in GUARDS) {
            onOpNpc1(guard) { startDialogue(it.npc) { guard() } }
        }
    }

    private suspend fun Dialogue.guard() {
        when {
            murder.isComplete(player) -> {
                chatNpc(
                    happy,
                    "Excellent work on solving the murder! All of the guards I know are very " +
                        "impressed, and don't worry, we have the murderer under guard until they " +
                        "can be taken to trial.",
                )
                chatPlayer(confused, "Is there anything else I can do?")
                chatNpc(happy, "No, your work here is done.")
            }
            murder.isInvestigating(player) -> investigating()
            else -> notStarted()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(neutral, "What's going on here?")
        chatNpc(
            sad,
            "Oh, it's terrible! Lord Sinclair has been murdered and we don't have any clues as " +
                "to who or why. We're totally baffled!",
        )
        chatNpc(sad, "If you can help us we will be very grateful.")
        val help =
            choice2(
                "Sure, I'll help.",
                true,
                "You should do your own dirty work.",
                false,
                title = "Start the Murder Mystery quest?",
            )
        if (!help) {
            chatPlayer(neutral, "You should do your own dirty work.")
            chatNpc(
                neutral,
                "Get lost then, this is private property! ...Unless you'd like to be taken in for " +
                    "questioning yourself?",
            )
            return
        }
        chatPlayer(happy, "Sure, I'll help!")
        murder.start(access)
        chatNpc(happy, "Thanks a lot!")
        whatToDo()
    }

    private suspend fun Dialogue.investigating() {
        when (
            choice3(
                "What should I be doing to help again?",
                0,
                "How did Lord Sinclair die?",
                1,
                "I know who did it!",
                2,
            )
        ) {
            0 -> whatToDo()
            1 -> howHeDied()
            else -> accuse()
        }
    }

    private suspend fun Dialogue.whatToDo() {
        chatPlayer(neutral, "What should I be doing to help?")
        chatNpc(
            neutral,
            "Look around and investigate who might be responsible. The Sarge said every murder " +
                "leaves clues to who done it, but frankly we're out of our depth here.",
        )
    }

    private suspend fun Dialogue.howHeDied() {
        chatPlayer(neutral, "How did Lord Sinclair die?")
        chatNpc(
            confused,
            "Well, it's all very mysterious. Mary, the maid, found the body in the study next to " +
                "his bedroom on the east wing of the ground floor.",
        )
        chatNpc(
            neutral,
            "The door was found locked from the inside, and he seemed to have been stabbed, but " +
                "there was an odd smell in the room. Frankly, I'm stumped.",
        )
    }

    private suspend fun Dialogue.accuse() {
        chatPlayer(happy, "I know who did it!")
        val thread = player.murderFoundThread
        val prints = player.murderFoundPrints
        val poison = player.murderPoisonProgress >= POISON_DISPROVED
        val killer = player.murderer
        if (killer == null) {
            quickWork()
            return
        }
        when {
            thread && prints && poison -> conclusiveProof(killer)
            thread && poison ->
                if (
                    choice2(
                        "I have proof that it wasn't any of the servants.",
                        true,
                        "I have proof one of the family lied about the poison.",
                        false,
                    )
                ) {
                    threadProof()
                } else {
                    poisonProof(killer)
                }
            prints && poison ->
                if (
                    choice2(
                        "I have proof one of the family lied about the poison.",
                        true,
                        "I have the finger prints of the culprit.",
                        false,
                    )
                ) {
                    poisonProof(killer)
                } else {
                    printProof(killer)
                }
            thread && prints ->
                if (
                    choice2(
                        "I have proof that it wasn't any of the servants.",
                        true,
                        "I have the finger prints of the culprit.",
                        false,
                    )
                ) {
                    threadProof()
                } else {
                    printProof(killer)
                }
            prints -> printProof(killer)
            poison -> poisonProof(killer)
            thread -> threadProof()
            else -> quickWork()
        }
    }

    private suspend fun Dialogue.quickWork() {
        chatNpc(happy, "Really? That was quick work! Who?")
        when (
            choice4(
                "It was an intruder!",
                0,
                "The butler did it!",
                1,
                "It was one of the servants!",
                2,
                "It was one of his family!",
                3,
            )
        ) {
            0 -> {
                chatPlayer(neutral, "It was an intruder!")
                chatNpc(
                    neutral,
                    "That's what we were thinking too. That someone broke in to steal something, " +
                        "was discovered by Lord Sinclair, stabbed him and ran.",
                )
                chatNpc(
                    confused,
                    "It's odd that apparently nothing was stolen though... Find out something " +
                        "has been stolen,",
                )
                chatNpc(
                    neutral,
                    "and the case is closed, but the murdered man was a friend of the King, and " +
                        "it's more than my job's worth not to investigate fully.",
                )
            }
            1 -> butler()
            2 -> accuseServant()
            else -> accuseFamily()
        }
    }

    private suspend fun Dialogue.accuseServant() {
        chatPlayer(neutral, "It was one of the servants!")
        chatNpc(neutral, "Oh really? Which one?")
        val women = choice2("It was one of the women...", true, "It was one of the men...", false)
        if (women) {
            chatPlayer(neutral, "It was one of the women...")
            chatNpc(confused, "Oh really? Which one?")
            choice2("It was SO obviously Louisa the cook.", 0, "It MUST have been Mary the maid.", 1)
            accuseSuspect()
            return
        }
        chatPlayer(neutral, "It was one of the men...")
        chatNpc(confused, "Oh really? Which one?")
        val butler =
            choice4(
                "It can ONLY Be Donovan the handyman.",
                false,
                "Pierre the dog handler. No question.",
                false,
                "Hobbes the butler. The butler ALWAYS did it.",
                true,
                "You MUST know it was Stanford the gardener...",
                false,
            )
        if (butler) butler() else accuseSuspect()
    }

    private suspend fun Dialogue.accuseFamily() {
        chatPlayer(neutral, "It was one of his family!")
        chatNpc(neutral, "Oh really? Which one?")
        val women = choice2("It was one of the women...", true, "It was one of the men...", false)
        if (women) {
            chatPlayer(neutral, "It was one of the women...")
            chatNpc(confused, "Oh really? Which one?")
            choice3(
                "I KNOW it was Anna.",
                0,
                "I am SO sure it was Carol.",
                1,
                "I'll bet you ANYTHING it was Elizabeth.",
                2,
            )
        } else {
            chatPlayer(neutral, "It was one of the men...")
            chatNpc(confused, "Oh really? Which one?")
            choice3(
                "I'm certain it was Bob.",
                0,
                "It was David. No doubt about it.",
                1,
                "If it wasn't Frank I'll eat my shoes!",
                2,
            )
        }
        accuseSuspect()
    }

    private suspend fun Dialogue.butler() {
        chatPlayer(happy, "The butler did it!")
        chatNpc(
            confused,
            "I hope you have proof to that effect. We have to arrest someone for this and it " +
                "seems to me that only the actual murderer would gain by falsely accusing someone.",
        )
        access.ifClose()
        delay(3)
        chatNpc(neutral, "Although having said that the butler is kind of shifty looking...")
    }

    private suspend fun Dialogue.accuseSuspect() {
        mesbox("You tell the guard who you suspect of the crime.")
        chatNpc(happy, "Great work. Show me the evidence, and we'll take them to the dungeons.")
        chatNpc(confused, "You DO have evidence of their crime, right?")
        chatPlayer(confused, "Uh....")
        chatNpc(
            neutral,
            "Tch. You wouldn't last a day in the guards with sloppy thinking like that. Come see " +
                "me when you have some proof of your accusations.",
        )
    }

    private suspend fun Dialogue.threadProof() {
        chatPlayer(happy, "I have proof that it wasn't any of the servants!")
        mesbox("You show the guard the thread you found on the window.")
        chatPlayer(neutral, "All the servants dress in black so it couldn't have been one of them.")
        chatNpc(
            neutral,
            "That's some good work there. I guess it wasn't a servant. You still haven't proved " +
                "who did do it though.",
        )
    }

    private suspend fun Dialogue.printProof(killer: Suspect) {
        chatPlayer(happy, "I have the finger prints of the culprit!")
        chatPlayer(
            neutral,
            "I have ${killer.displayName}'s finger prints here. You can see for yourself they " +
                "match the finger prints on the murder weapon exactly.",
        )
        mesbox("You show the guard the finger prints evidence.")
        chatNpc(
            neutral,
            "... I'm impressed. How on earth did you think of something like that? I've never " +
                "heard of such a technique for finding criminals before!",
        )
        chatNpc(
            neutral,
            "This will come in very handy in the future but we can't arrest someone on just " +
                "this. I'm afraid you'll still need to find more evidence before we can close " +
                "this case completely.",
        )
    }

    private suspend fun Dialogue.poisonProof(killer: Suspect) {
        chatPlayer(
            confused,
            "I have proof that ${killer.displayName} is lying about the poison.",
        )
        chatNpc(neutral, "Oh really? How did you get that?")
        when (killer) {
            Suspect.Elizabeth ->
                mesbox("You tell the guard about the mosquitos at the ${killer.poisonLocName}.")
            Suspect.Frank -> mesbox("You tell the guard about the tarnished ${killer.poisonLocName}.")
            else -> mesbox("You tell the guard about the ${killer.poisonLocName}.")
        }
        chatNpc(
            neutral,
            "Hmm. That's some good detective work there. We need more evidence before we can " +
                "close the case though. Keep up the good work!",
        )
    }

    private suspend fun Dialogue.conclusiveProof(killer: Suspect) {
        val name = killer.displayName
        chatPlayer(happy, "I have conclusive proof who the killer was.")
        chatNpc(happy, "You do? That's excellent work. Let's hear it then.")
        chatPlayer(
            neutral,
            "I don't think it was an intruder, and I don't think Lord Sinclair was killed by " +
                "being stabbed.",
        )
        chatNpc(neutral, "Hmmm? Really? Why not?")
        chatPlayer(
            happy,
            "Nobody heard the guard dog barking, which it would have if it had been an intruder " +
                "who was responsible.",
        )
        chatPlayer(
            neutral,
            "Nobody heard any signs of a struggle either. I think the knife was there to throw " +
                "suspicion away from the real culprit.",
        )
        chatNpc(neutral, "Yes, that makes sense. But who did do it then?")
        mesbox("You prove to the guard the thread matches $name's clothes.")
        chatNpc(neutral, "Yes, I'd have to agree with that... but we need more evidence!")
        mesbox("You prove to the guard $name did not use poison on the ${killer.poisonLocName}.")
        chatNpc(
            neutral,
            "Excellent work - have you considered a career as a detective? But I'm afraid it's " +
                "still not quite enough...",
        )
        mesbox(
            "You match $name's finger prints with those on the dagger found in the body of Lord " +
                "Sinclair.",
        )
        chatNpc(
            happy,
            "Yes. There's no doubt about it. It must have been $name who killed " +
                "${killer.possessivePronoun} father. All of the guards must congratulate you on " +
                "your excellent work in helping us to solve this case.",
        )
        chatNpc(
            neutral,
            "We don't have many murders here in RuneScape and I'm afraid we wouldn't have been " +
                "able to solve it by ourselves. We will hold ${killer.objectPronoun} here under " +
                "house arrest until such time as we bring ${killer.objectPronoun} to trial.",
        )
        chatNpc(
            neutral,
            "You have our gratitude, and I'm sure the rest of the family's as well, in helping " +
                "to apprehend the murderer. I'll just take the evidence from you now.",
        )
        mesbox("You hand over all the evidence.")
        murder.handOverEvidence(access)
        chatNpc(happy, "Please accept this reward from the family!")
        access.ifClose()
        murder.complete(access)
    }

    private companion object {
        val GUARDS = listOf("npc.murderguard", "npc.kr_murderguard_house_multi")
    }
}
