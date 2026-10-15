package org.rsmod.content.quest.area.lumbridge.sheepshearer.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest.Companion.BALL_OF_WOOL
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest.Companion.SHEARS
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest.Companion.WOOL
import org.rsmod.content.quest.area.lumbridge.sheepshearer.SheepShearerQuest.Companion.WOOL_REQUIRED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** Fred the Farmer, in his house by the sheep pen north of Lumbridge. Starts and ends Sheep Shearer. */
class FredTheFarmer @Inject constructor(private val sheepShearer: SheepShearerQuest) : PluginScript() {

    private val quest
        get() = sheepShearer.quest

    override fun ScriptContext.startup() {
        onOpNpc1("npc.fred_the_farmer") { startDialogue(it.npc) { fred() } }
    }

    private suspend fun Dialogue.fred() {
        when {
            quest.isQuestCompleted(player) -> afterQuest()
            quest.isQuestInProgress(player) -> duringQuest()
            else -> beforeQuest()
        }
    }

    private suspend fun Dialogue.beforeQuest() {
        chatNpc(angry, "What are you doing on my land? You're not the one who keeps leaving all my gates open and letting out all my sheep, are you?")
        when (
            choice3(
                "I'm looking for a quest.", 1,
                "I'm looking for something to kill.", 2,
                "I'm lost.", 3,
            )
        ) {
            1 -> offerQuest()
            2 -> {
                chatPlayer(angry, "I'm looking for something to kill.")
                chatNpc(worried, "Well, you'll find nothing of the sort on my land, so you can take your sword elsewhere! Unless... you could try The Thing.")
                chatPlayer(quiz, "The Thing?")
                chatNpc(shifty, "Ask me about a quest and I'll tell you all about it.")
            }
            3 -> {
                chatPlayer(confused, "I'm lost.")
                chatNpc(neutral, "You're on my farm, north of Lumbridge. Follow the road east and you'll come to the castle. Now off you go.")
            }
        }
    }

    private suspend fun Dialogue.offerQuest() {
        chatPlayer(happy, "I'm looking for a quest.")
        chatNpc(neutral, "You're after a quest, you say? Actually, I could do with a bit of help.")
        chatNpc(neutral, "My sheep are getting mighty woolly. I'd be much obliged if you could shear them. And while you're at it, spin the wool for me too.")
        chatNpc(neutral, "Yes, that's it. Bring me $WOOL_REQUIRED balls of wool. I'm sure I could sort out some sort of payment. Of course, there's the small matter of The Thing.")
        if (player.inv.count(BALL_OF_WOOL) >= WOOL_REQUIRED) {
            alreadyHasWool()
            return
        }
        chatPlayer(quiz, "What do you mean, The Thing?")
        chatNpc(worried, "Well now, no one has ever seen The Thing. That's why we call it The Thing, 'cos we don't know what it is.")
        chatNpc(worried, "Some say it's a black-hearted shapeshifter, hungering for the souls of hard-working, decent folk like me. Others say it's just a sheep.")
        chatNpc(angry, "Well, I don't have all day to stand around and gossip. Are you going to shear my sheep or what?")
        when (
            choice2(
                "Yes.", 1,
                "No.", 2,
                title = "Start the Sheep Shearer quest?",
            )
        ) {
            1 -> {
                chatPlayer(happy, "Yes, okay. I can do that.")
                quest.advanceQuestStage(access)
                chatNpc(quiz, "Good! Now one more thing. Do you actually know how to shear a sheep?")
                chatPlayer(confused, "Err. No, I don't know, actually.")
                shearingLesson()
                chatNpc(quiz, "Do you know how to spin wool?")
                chatPlayer(neutral, "I don't know how to spin wool, sorry.")
                chatNpc(happy, "Don't worry, it's quite simple!")
                spinningLesson()
            }
            2 -> {
                chatPlayer(neutral, "No, I'll give it a miss.")
                chatNpc(bored, "Suit yourself.")
            }
        }
    }

    /** Twenty balls of wool before the quest has even started: Fred is not impressed. */
    private suspend fun Dialogue.alreadyHasWool() {
        chatPlayer(happy, "In fact, Fred, funnily enough, I actually have $WOOL_REQUIRED balls of wool on me already.")
        chatNpc(angry, "Have you been shearing my sheep without permission!?")
        chatPlayer(shifty, "No! Well, maybe... They just looked a little woolly! Surely you like a shave once in a while, too?")
        chatNpc(angry, "It's rude to shave another person without permission. Don't be coming at me with them shears!")
        chatPlayer(sad, "I'm sorry. I'll ask permission next time.")
        chatNpc(neutral, "I guess no real 'arm was done. Hand the balls over and we can put this whole thing behind us.")
        quest.advanceQuestStage(access)
        deliverWool()
    }

    private suspend fun Dialogue.shearingLesson() {
        if (player.inv.count(SHEARS) > 0) {
            chatNpc(happy, "Well, you're halfway there already! You have a set of shears with you. Just use those on a sheep to shear it.")
            chatPlayer(quiz, "That's all I have to do?")
            chatNpc(neutral, "Well, once you've collected some wool you'll need to spin it into balls.")
            return
        }
        chatNpc(neutral, "Well, first things first, you need a pair of shears. I've got some here you can use.")
        if (access.invAdd(access.inv, SHEARS).failure) {
            chatNpc(neutral, "Or I would, if you had room in your pack for them. Clear a space and ask me again.")
        } else {
            access.soundSynth("synth.pick2")
            objbox(SHEARS, "Fred gives you a set of sharp shears.")
        }
        chatNpc(neutral, "You just need to go and use them on the sheep out in my field.")
        chatPlayer(happy, "Sounds easy!")
        chatNpc(laugh, "That's what they all say!")
        chatNpc(neutral, "Some of the sheep don't like it too much... Persistence is the key.")
        chatNpc(neutral, "Once you've collected some wool you can spin it into balls.")
    }

    private suspend fun Dialogue.spinningLesson() {
        chatNpc(neutral, "The nearest spinning wheel can be found on the first floor of Lumbridge Castle.")
        chatNpc(neutral, "To get to Lumbridge Castle, just follow the road east.")
        mesbox("Look for the spinning wheel icon on the world map.")
        chatPlayer(happy, "Thank you!")
    }

    private suspend fun Dialogue.duringQuest() {
        chatNpc(angry, "What are you doing on my land?")
        if (sheepShearer.seenTheThing.get(player)) {
            when (
                choice2(
                    "I need to talk to you about shearing these sheep!", 1,
                    "Fred! Fred! I've seen The Thing!", 2,
                )
            ) {
                1 -> woolProgress()
                2 -> seenTheThing()
            }
            return
        }
        woolProgress()
    }

    private suspend fun Dialogue.woolProgress() {
        chatPlayer(neutral, "I need to talk to you about shearing these sheep!")
        chatNpc(quiz, "Oh. How are you doing getting those balls of wool?")
        if (player.inv.count(BALL_OF_WOOL) > 0) {
            chatPlayer(happy, "I have some.")
            chatNpc(neutral, "Give 'em here then.")
            deliverWool()
            return
        }
        chatPlayer(quiz, "How many more do I need to give you?")
        chatNpc(neutral, "You need to collect ${sheepShearer.remaining(player)} more balls of wool.")
        if (player.inv.count(WOOL) > 0) {
            chatPlayer(neutral, "I've got some wool. I've not managed to make it into a ball, though.")
            chatNpc(neutral, "Well, go and find a spinning wheel then. You can find one on the first floor of Lumbridge Castle. Just walk east on the road outside my house and you'll find Lumbridge.")
            return
        }
        chatPlayer(sad, "I haven't got any at the moment.")
        chatNpc(neutral, "Ah well, at least you haven't been eaten. You know what you're doing, right?")
        when (
            choice3(
                "How do I shear sheep, again?", 1,
                "Remind me how to spin wool.", 2,
                "Yeah, I think so.", 3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "How do I shear sheep, again?")
                shearingLesson()
                chatNpc(quiz, "Do you know how to spin wool?")
                when (
                    choice2(
                        "Yes, I know how to spin wool.", 1,
                        "I don't know how to spin wool, sorry.", 2,
                    )
                ) {
                    1 -> {
                        chatPlayer(happy, "Yes, I know how to spin wool.")
                        chatNpc(happy, "Great!")
                    }
                    2 -> {
                        chatPlayer(neutral, "I don't know how to spin wool, sorry.")
                        chatNpc(happy, "Don't worry, it's quite simple!")
                        spinningLesson()
                    }
                }
            }
            2 -> {
                chatPlayer(quiz, "Remind me how to spin wool.")
                spinningLesson()
            }
            3 -> {
                chatPlayer(neutral, "Yeah, I think so.")
                chatNpc(neutral, "You can get to it, then!")
            }
        }
    }

    /** Hands over as many balls as Fred still needs; the twentieth completes the quest. */
    private suspend fun Dialogue.deliverWool() {
        val carried = player.inv.count(BALL_OF_WOOL)
        val handing = minOf(carried, sheepShearer.remaining(player))
        if (handing <= 0) {
            return
        }
        if (access.invDel(access.inv, BALL_OF_WOOL, handing).failure) {
            return
        }
        access.soundSynth("synth.put_down")
        val left = sheepShearer.remaining(player) - handing
        if (left > 0) {
            mesbox("You give Fred $handing ${if (handing == 1) "ball" else "balls"} of wool.")
            quest.advanceQuestStage(access, handing)
            chatPlayer(neutral, "That's all I've got so far.")
            chatNpc(neutral, "I need $left more before I can pay you.")
            chatPlayer(neutral, "Okay, I'll work on it.")
            return
        }
        mesbox("You give Fred $handing ${if (handing == 1) "ball" else "balls"} of wool.")
        chatPlayer(happy, "That's the last of them.")
        chatNpc(happy, "I guess I'd better pay you then.")
        access.soundSynth("synth.coins_jingle_1")
        quest.advanceQuestStage(access, handing)
    }

    private suspend fun Dialogue.seenTheThing() {
        chatPlayer(shocked, "Fred! Fred! I've seen The Thing!")
        chatNpc(shocked, "You... you actually saw it?")
        chatNpc(shocked, "Run for the hills! ${player.displayName}, grab as many chickens as you can! We have to...")
        chatPlayer(neutral, "Fred!")
        chatNpc(worried, "...flee! Oh, woe is me! The shapeshifter is coming! We're all...")
        chatPlayer(angry, "FRED!")
        chatNpc(confused, "...doomed. What!")
        chatPlayer(neutral, "It's not a shapeshifter or any other kind of monster!")
        chatNpc(quiz, "Well then, what is it?")
        chatPlayer(confused, "Well... it's just two penguins. Penguins disguised as a sheep.")
        chatNpc(silent, "...")
        chatNpc(quiz, "Have you been out in the sun too long?")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(neutral, "What are you doing on my land?")
        if (sheepShearer.seenTheThing.get(player)) {
            when (
                choice2(
                    "How are the sheep doing?", 1,
                    "Fred! Fred! I've seen The Thing!", 2,
                )
            ) {
                1 -> sheepDoingFine()
                2 -> seenTheThing()
            }
            return
        }
        chatPlayer(happy, "How are the sheep doing?")
        sheepDoingFine()
    }

    private suspend fun Dialogue.sheepDoingFine() {
        chatNpc(happy, "A good deal cooler, thanks to you. The wool's already at market. Mind the gate on your way out, and keep an eye open for The Thing.")
    }
}
