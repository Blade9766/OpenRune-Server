package org.rsmod.content.quest.area.varrock.romeojuliet.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.CADAVA_POTION
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.DRAUL
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_HAS_MESSAGE
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_JULIET_CRYPT
import org.rsmod.content.quest.area.varrock.romeojuliet.RomeoJulietQuest.Companion.STAGE_SEEN_APOTHECARY
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Draul Leptoc, Juliet's overbearing father, who guards the ground floor of his mansion west of
 * Varrock. He notices the quest items the player carries past him.
 */
class DraulLeptoc @Inject constructor(private val quest: RomeoJulietQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DRAUL) { startDialogue(it.npc) { draul() } }
    }

    private suspend fun Dialogue.draul() {
        val stage = quest.stage(player)
        when {
            stage < STAGE_HAS_MESSAGE -> snooping()
            stage == STAGE_HAS_MESSAGE -> carryingMessage()
            stage < STAGE_SEEN_APOTHECARY -> {
                chatNpc(
                    angry,
                    "Do you live here? If so, how about a couple of hundred gold towards the rent, " +
                        "eh? Pay your way, I say - don't be a freeloader like that Romeo!",
                )
            }
            stage == STAGE_SEEN_APOTHECARY -> carryingPotion()
            stage == STAGE_JULIET_CRYPT -> {
                chatNpc(
                    sad,
                    "My poor Juliet... dead... dead! I was far too hard on her... my lovely " +
                        "daughter. Boo hoo hoooo!",
                )
            }
            else -> {
                chatNpc(
                    angry,
                    "Juliet's back from the dead and sulking in her room, and that fool Romeo has " +
                        "taken up with my niece! This family will be the death of me.",
                )
            }
        }
    }

    private suspend fun Dialogue.snooping() {
        chatNpc(angry, "What are you doing in here? Snooping about...")
        val answer =
            choice4(
                "I've come to see Juliet on Romeo's behalf.",
                1,
                "I've just come to have a chat with Juliet.",
                2,
                "Oh... just looking around...",
                3,
                "Okay, thanks.",
                4,
            )
        when (answer) {
            1 -> {
                chatPlayer(neutral, "I've come to see Juliet on Romeo's behalf.")
                chatNpc(
                    angry,
                    "What... WHAT... Romeo! That good-for-nothing wretch is forever chasing after " +
                        "my daughter. That soppy, half-witted nincompoop will never win her heart!",
                )
                chatNpc(angry, "She deserves a man of character, wit and poise.")
                chatPlayer(quiz, "What's so wrong with Romeo?")
                chatNpc(
                    angry,
                    "Wrong with him?! Have you actually spoken to him? He's a dim-witted, " +
                        "upper-class twit, utterly useless!",
                )
                chatNpc(
                    angry,
                    "He'd miss the ground if he threw a stone at it! He's so wet you can't even " +
                        "see him when it rains!",
                )
                chatNpc(angry, "Start with what's RIGHT with him and you'll be done a lot sooner!")
                chatPlayer(neutral, "Well, I'll admit he's not the sharpest sword in the armoury...")
                chatNpc(
                    angry,
                    "Sharp? I've met turnips with more wit. Now stop changing the subject and get " +
                        "out! And don't you dare sneak up those stairs to Juliet, or you'll be for it!",
                )
                chatPlayer(neutral, "That seems a bit harsh...")
                chatNpc(angry, "Harsh but fair, I think you'll find. Now GET OUT!")
            }
            2 -> {
                chatPlayer(neutral, "I've just come to have a chat with Juliet.")
                chatNpc(
                    angry,
                    "What on earth about? I hope you're not in league with that layabout Romeo!",
                )
                chatPlayer(shifty, "Err... no, of course not... why would I be?")
                chatNpc(
                    angry,
                    "He's been trying to woo my daughter for ages. Until now she's had the good " +
                        "sense to ignore him. I don't know what's got into her lately.",
                )
                chatPlayer(happy, "Well, love is a mystery! Perhaps one day someone might even love you!")
                chatNpc(angry, "What?! Someone might fall in love with ME? What are you implying?")
                chatPlayer(worried, "Err... nothing... I think I'd better be going...")
            }
            3 -> {
                chatPlayer(neutral, "Oh... just looking around...")
                chatNpc(
                    angry,
                    "Just looking around?! This is MY house! You might at least have ASKED to see " +
                        "my finely furnished home, but no - you barged in with all the grace of " +
                        "a troll at a tea party.",
                )
                chatPlayer(neutral, "I can see you're busy ranting, so I'll just pop off and have a look about.")
            }
            else -> chatPlayer(neutral, "Okay, thanks.")
        }
    }

    private suspend fun Dialogue.carryingMessage() {
        chatNpc(angry, "What are you doing in my house? Up to no good, I'll bet!")
        chatPlayer(happy, "Just running a little errand for Juliet. What a lovely daughter you have, sir.")
        chatNpc(happy, "Oh... why, thank you... I've always done my best...")
        chatNpc(
            angry,
            "...Wait a minute! Enough of the sweet talk. I know my daughter. Don't even think " +
                "about going behind my back - I have the eyes of a hawk!",
        )
        if (access.inv.count(MESSAGE) == 0) {
            return
        }
        objbox(MESSAGE, "Draul notices the message!")
        chatNpc(
            angry,
            "Hey! What's that in your hand? Looks like a letter to me... with Juliet's scrawl " +
                "all over it...",
        )
        chatPlayer(neutral, "Yes, that'll be why I can't read it!")
        chatPlayer(
            shifty,
            "I mean, yes sir, that's right. It's a shopping list. I'm just nipping out to get " +
                "Juliet some groceries.",
        )
        chatPlayer(neutral, "Right, must dash... thanks...")
        chatNpc(angry, "Groceries!")
        chatNpc(angry, "Groceries, at a time like this! Does that girl have any idea what she puts me through?")
    }

    private suspend fun Dialogue.carryingPotion() {
        chatNpc(angry, "Hey, what are you doing here?")
        chatPlayer(
            neutral,
            "Nothing much, sir, I promise... I'm just an innocent friend of Juliet's, doing her " +
                "a few favours... as a friend.",
        )
        chatNpc(angry, "Well, just make sure there's no funny business, that's all. Do you know why?")
        chatPlayer(neutral, "I think I can guess, sir...")
        chatNpc(
            angry,
            "No need to guess! You can see it in my hawk-like eyes, my cat-like ears and my " +
                "dog-like nose...",
        )
        chatPlayer(quiz, "Are you saying you look like an animal, sir?")
        chatNpc(
            angry,
            "NO! I have the keen SENSES of an animal, and nothing gets past me. Don't even think " +
                "about trying anything!",
        )
        if (access.inv.count(CADAVA_POTION) == 0) {
            return
        }
        objbox(CADAVA_POTION, "Draul notices the potion!")
        chatNpc(angry, "Hey! What's that in your hand? Looks like some sort of potion to me!")
        chatPlayer(
            shifty,
            "Err... no! Not a potion! It's medicine... I have a terrible cough... cough... " +
                "cough... see?",
        )
        chatPlayer(
            neutral,
            "Only I can't take too much or it makes me really drowsy. One sip and it's lights " +
                "out... I mean, I'm fast asleep in no time.",
        )
        chatNpc(angry, "Sleep!")
        chatNpc(
            angry,
            "Sleep?! How can you think of sleeping at a time like this? And you'd better not be " +
                "planning to nap in MY house!",
        )
        chatNpc(angry, "Catch you dozing off under my roof and you'll get a bill for the rent!")
    }
}
