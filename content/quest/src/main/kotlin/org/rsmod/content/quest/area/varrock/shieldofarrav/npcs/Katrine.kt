package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_JOINED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_TASKED
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_TOLD_BY_CHARLIE
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.CROSSBOW
import org.rsmod.content.quest.area.varrock.shieldofarrav.blackArmGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.weaponsmasterDead
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Katrine, who runs the Black Arm Gang from their hideout in the alley by Varrock's south gate.
 * She lets a Shield of Arrav player join once they bring her two crossbows stolen from the
 * Phoenix Gang.
 */
class Katrine @Inject constructor(private val arrav: ShieldOfArravQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(KATRINE) { startDialogue(it.npc) { katrine() } }
    }

    private suspend fun Dialogue.katrine() {
        when {
            arrav.isPhoenix(player) -> {
                chatNpc(
                    angry,
                    "You've got some nerve showing your face here, Phoenix scum! Clear off, " +
                        "before I make sure you can't!",
                )
            }
            arrav.isBlackArm(player) -> member()
            player.blackArmGang == BLACKARM_TASKED -> crossbows()
            else -> stranger()
        }
    }

    private suspend fun Dialogue.stranger() {
        chatPlayer(quiz, "What is this place?")
        chatNpc(neutral, "A private business. Is there something I can help you with?")
        val gangOption = player.blackArmGang == BLACKARM_TOLD_BY_CHARLIE
        val topic =
            if (gangOption) {
                choice3(
                    "I've heard you're the Black Arm Gang.",
                    Topic.Gang,
                    "What sort of business?",
                    Topic.Business,
                    "I'm looking for fame and riches.",
                    Topic.Fame,
                )
            } else {
                choice2("What sort of business?", Topic.Business, "I'm looking for fame and riches.", Topic.Fame)
            }
        when (topic) {
            Topic.Business -> {
                chatPlayer(quiz, "What sort of business?")
                chatNpc(
                    neutral,
                    "A small family firm. We advise other companies on their finances. Now, if " +
                        "you'll excuse me.",
                )
            }
            Topic.Fame -> {
                chatPlayer(neutral, "I'm looking for fame and riches.")
                chatNpc(bored, "In the back streets of Varrock? Dream on.")
            }
            Topic.Gang -> heardOfGang()
        }
    }

    private suspend fun Dialogue.heardOfGang() {
        chatPlayer(quiz, "I've heard you're the Black Arm Gang.")
        chatNpc(angry, "And who told you that?")
        val source =
            choice3(
                "I'd rather not reveal my sources.",
                1,
                "It was Charlie, the tramp outside.",
                2,
                "Everyone knows. It's no great secret.",
                3,
            )
        when (source) {
            1 -> {
                chatPlayer(neutral, "I'd rather not reveal my sources.")
                chatNpc(neutral, "Hmm... fair enough, I suppose.")
            }
            2 -> {
                chatPlayer(neutral, "It was Charlie, the tramp outside.")
                chatNpc(
                    angry,
                    "Is he still hanging about out there? He's becoming a nuisance. Maybe it's " +
                        "time he had a little accident...",
                )
                chatNpc(neutral, "Anyway.")
            }
            else -> {
                chatPlayer(neutral, "Everyone knows. It's no great secret.")
                chatNpc(shocked, "What?! You can't be serious!")
                chatPlayer(
                    shifty,
                    "Oh, completely. It's so obvious! Even the city guards have worked it out...",
                )
                chatNpc(worried, "I see... well.")
            }
        }
        chatNpc(quiz, "Suppose we were the Black Arm Gang. What would you want with us?")
        val want =
            choice3(
                "I want to become a member of your gang.",
                1,
                "I want some hints on becoming a thief.",
                2,
                "I'm looking for the door out of here.",
                3,
            )
        when (want) {
            1 -> {
                chatPlayer(neutral, "I want to become a member of your gang.")
                recruit()
            }
            2 -> {
                chatPlayer(neutral, "I want some hints on becoming a thief.")
                chatNpc(quiz, "Oh, do you? And why would I hand out anything like that?")
                chatPlayer(shifty, "How about I join your gang in return?")
                recruit()
            }
            else -> {
                chatPlayer(neutral, "I'm looking for the door out of here.")
                chatNpc(bored, "Try... the one you just walked through?")
            }
        }
    }

    private suspend fun Dialogue.recruit() {
        chatNpc(
            neutral,
            "How unusual. Do you really think we take on anyone who strolls in here asking to " +
                "join?",
        )
        chatPlayer(quiz, "I don't know. How else would you do it?")
        chatNpc(
            neutral,
            "By watching the local thugs and thieves at work, maybe. How do I know I can trust " +
                "someone who just wandered in off the street?",
        )
        val honest =
            choice2(
                "Well, you can give me a try can't you?",
                false,
                "Well, people tell me I have an honest face.",
                true,
            )
        if (honest) {
            chatPlayer(happy, "Well, people tell me I have an honest face.")
            chatNpc(
                bored,
                "An honest person wanting to join a gang of thieves? You'll forgive me if I'm " +
                    "not convinced...",
            )
        } else {
            chatPlayer(neutral, "Well, you can give me a try can't you?")
            chatNpc(neutral, "I'm not so sure...")
        }
        chatNpc(
            neutral,
            "Actually... there may be a way. You'll have heard of the Phoenix Gang. They keep a " +
                "very nice stash of weapons not far from here.",
        )
        chatNpc(
            neutral,
            "We've run out of crossbows, and they've plenty. Bring me a couple of theirs and " +
                "we'd be most grateful.",
        )
        chatPlayer(quiz, "Sounds easy enough. What do you need them for?")
        chatNpc(
            shifty,
            "There's a merchant nearby who won't pay his very reasonable 'keep-your-life-" +
                "pleasant' insurance.",
        )
        chatPlayer(shocked, "So you're going to kill him?")
        chatNpc(
            shifty,
            "Him? No. But the guards might just find a Phoenix Gang crossbow in his house... and " +
                "another one might turn up at a murder across town.",
        )
        chatNpc(quiz, "So, are you going to keep asking questions, or fetch me those crossbows?")
        if (choice2("Okay, no problem.", true, "Sounds a little tricky. Got anything easier?", false)) {
            chatPlayer(happy, "Okay, no problem.")
            acceptTask()
            return
        }
        chatPlayer(worried, "Sounds a little tricky. Got anything easier?")
        chatNpc(angry, "If you can't handle a bit of danger, you've got nothing to offer us.")
        if (choice2("Okay, I'll do it.", true, "It's a no from me then.", false)) {
            chatPlayer(neutral, "Okay, I'll do it.")
            acceptTask()
            return
        }
        chatPlayer(neutral, "It's a no from me then.")
        chatNpc(angry, "Then get lost!")
    }

    private suspend fun Dialogue.acceptTask() {
        player.blackArmGang = BLACKARM_TASKED
        chatNpc(
            happy,
            "Good! The Phoenix Gang's stash is somewhere east of here, apparently. Ask that " +
                "tramp outside; he'll know more.",
        )
    }

    private suspend fun Dialogue.crossbows() {
        chatNpc(quiz, "Got those crossbows for me yet?")
        when (access.inv.count(CROSSBOW)) {
            0 -> {
                chatPlayer(sad, "No, I haven't found them yet. Where should I be looking?")
                chatNpc(
                    neutral,
                    "The Phoenix Gang's stash is somewhere east of here, apparently. Ask that " +
                        "tramp outside; he'll know more.",
                )
            }
            1 -> {
                chatPlayer(neutral, "I've got one...")
                chatNpc(neutral, "I asked for two. Come back when you've got them both.")
            }
            else -> {
                chatPlayer(happy, "Yes, I have.")
                access.invDel(access.inv, CROSSBOW, CROSSBOWS_NEEDED)
                doubleobjbox(CROSSBOW, CROSSBOW, "You give the crossbows to Katrine.")
                player.blackArmGang = BLACKARM_JOINED
                player.weaponsmasterDead = false
                chatNpc(
                    happy,
                    "Well, you're handier than I gave you credit for. Welcome to the Black Arm " +
                        "Gang. Go and have a look around the rest of the ganghouse.",
                )
            }
        }
    }

    private suspend fun Dialogue.member() {
        chatPlayer(neutral, "Hey.")
        chatNpc(neutral, "Hey.")
        val topic =
            choice3(
                "So I'm a part of the gang now?",
                1,
                "Who are all those people in there?",
                2,
                "Teach me to be a top class criminal!",
                3,
            )
        when (topic) {
            1 -> {
                chatPlayer(quiz, "So I'm part of the gang now?")
                if (arrav.isComplete(player)) {
                    chatNpc(
                        neutral,
                        "You are - so don't make me regret it. Somebody stole something very " +
                            "precious from us recently, so we're watching who we let in.",
                    )
                    chatPlayer(shifty, "Imagine doing a thing like that!")
                } else {
                    chatNpc(neutral, "You are. Go on and get to know the rest of the ganghouse.")
                    chatPlayer(happy, "Will do! Thanks!")
                }
            }
            2 -> {
                chatPlayer(quiz, "Who are all those people in there?")
                chatNpc(neutral, "Rogues and thieves, mostly.")
                chatPlayer(quiz, "They're not very chatty...")
                chatNpc(neutral, "Nope.")
            }
            else -> {
                chatPlayer(neutral, "Teach me to be a top class criminal.")
                chatNpc(bored, "Teach yourself.")
            }
        }
    }

    private enum class Topic {
        Gang,
        Business,
        Fame,
    }

    private companion object {
        const val KATRINE = "npc.katrine"
        const val CROSSBOWS_NEEDED = 2
    }
}
