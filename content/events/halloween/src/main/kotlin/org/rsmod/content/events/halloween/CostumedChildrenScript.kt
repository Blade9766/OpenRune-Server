package org.rsmod.content.events.halloween

import dev.openrune.types.MesAnimType
import org.rsmod.api.enums.HalloweenEnums
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpc3
import org.rsmod.api.script.onOpNpc4
import org.rsmod.content.areas.city.draynor.npcs.DiangoHolidayItems.Companion.openHolidayItems
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val CHILD = "Costumed child"
internal const val CAVE_HORROR = "npc.hw22_costumed_child_1"
internal const val SPOOKY_OUTFIT = "npc.hw22_costumed_child_2"
internal const val JACK_LANTERN = "npc.hw22_costumed_child_3"

class CostumedChildrenScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (child in listOf(CAVE_HORROR, SPOOKY_OUTFIT, JACK_LANTERN)) {
            onOpNpc1(child) { talk(it.npc) }
            onOpNpc3(child) { holidayItems(it.npc) }
            onOpNpc4(child) { checkCostume(it.npc) }
        }
    }

    private suspend fun ProtectedAccess.talk(npc: Npc) {
        startDialogue(npc) {
            chatPlayer(happy, "Hello there!")
            chatNpc(happy, "Hi!")
            while (true) {
                val topic =
                    choice4(
                        "What's that giant cauldron doing here?",
                        Topic.Cauldron,
                        "How do I Trick-or-Treat?",
                        Topic.HowTo,
                        "Do you have any spare costumes?",
                        Topic.Costumes,
                        "Nice costumes!",
                        Topic.Compliment,
                    )
                when (topic) {
                    Topic.Cauldron -> aboutCauldron()
                    Topic.HowTo -> if (!howToTrickOrTreat()) return@startDialogue
                    Topic.Costumes -> {
                        spareCostumes()
                        return@startDialogue
                    }
                    Topic.Compliment -> compliment()
                }
                if (!choice2("Ask something else.", true, "Goodbye.", false)) {
                    chatPlayer(happy, "Goodbye.")
                    child(SPOOKY_OUTFIT, happy, "Happy Halloween!")
                    return@startDialogue
                }
            }
        }
    }

    private suspend fun Dialogue.aboutCauldron() {
        chatPlayer(quiz, "What's that giant cauldron doing here?")
        child(JACK_LANTERN, happy, "It's collecting treats and looking spooky while doing it!")
        chatPlayer(quiz, "What happens when it's full?")
        child(CAVE_HORROR, neutral, "We don't know, but we're hoping to find out!")
        child(SPOOKY_OUTFIT, worried, "Sometimes bats come out of it.")
        child(JACK_LANTERN, happy, "Maybe a whole swarm will fly out when it's full!")
        child(SPOOKY_OUTFIT, worried, "I hope not!")
        chatPlayer(quiz, "Can anyone put treats in it?")
        child(
            CAVE_HORROR,
            happy,
            "Of course! It's massive, so we'll need as much help as we can get!",
        )
        chatPlayer(happy, "I'll do what I can!")
        chatPlayer(quiz, "Do you know how the cauldron got here?")
        child(JACK_LANTERN, neutral, "We're not sure...")
        child(CAVE_HORROR, neutral, "It was here when we came out to Trick-or-Treat.")
        chatPlayer(quiz, "So you didn't see anyone put it here?")
        child(JACK_LANTERN, happy, "Nope, but I reckon Death brought it!")
        child(SPOOKY_OUTFIT, shocked, "Do you think he'll come back to get it?!")
        child(
            CAVE_HORROR,
            neutral,
            "No way was it Death, it must've been a wizard! Maybe one of those scary " +
                "Zamorakian ones...",
        )
        child(SPOOKY_OUTFIT, worried, "That doesn't sound better!")
        chatPlayer(happy, "Well this is all very strange, but it certainly fits the season!")
    }

    private suspend fun Dialogue.howToTrickOrTreat(): Boolean {
        chatPlayer(quiz, "How do I Trick-or-Treat?")
        child(
            CAVE_HORROR,
            happy,
            "Some of the grown-ups are giving out treats to Trick-or-Treaters. Even the guards " +
                "are doing it!",
        )
        child(
            JACK_LANTERN,
            happy,
            "So you just go up to them in a costume of at least three spooky pieces and shout " +
                "'TRICK OR TREAT!!'",
        )
        child(JACK_LANTERN, happy, "Then you get treats!")
        child(
            CAVE_HORROR,
            happy,
            "And sometimes really generous grown-ups come by and give lots of treats!",
        )
        child(
            CAVE_HORROR,
            neutral,
            "Oh yeah, you'll need a Treat cauldron to put all your treats in...",
        )
        if (player.ownsTreatCauldron()) {
            child(CAVE_HORROR, happy, "Which I see you have ready to fill!")
        } else if (!handOverCauldron(CAVE_HORROR)) {
            return false
        }
        child(JACK_LANTERN, happy, "Here are some spooky things you can make a costume with.")
        unlockPreviousRewards()
        mesbox(
            "All previous Halloween rewards have been unlocked. You can claim any you don't " +
                "have from the children or Diango.",
        )
        chatPlayer(happy, "Thank you!")
        child(SPOOKY_OUTFIT, neutral, "Don't forget about the grown-ups tricking people too!")
        child(JACK_LANTERN, happy, "Well, yeah, but it's all spooky fun!")
        chatPlayer(quiz, "What kind of tricks?")
        child(
            CAVE_HORROR,
            happy,
            "Oh, things like turning you into a rat or giving you scary old junk-",
        )
        child(SPOOKY_OUTFIT, shocked, "And the ghosts!")
        child(
            CAVE_HORROR,
            happy,
            "Yeah, they sometimes make ghosts appear too. That's a very cool trick!",
        )
        child(JACK_LANTERN, neutral, "I heard some people can talk to ghosts.")
        child(SPOOKY_OUTFIT, worried, "Are they friendly?")
        chatPlayer(happy, "Some of them are!")
        return true
    }

    private suspend fun Dialogue.spareCostumes() {
        chatPlayer(quiz, "Do you have any spare costumes?")
        if (!player.ownsTreatCauldron()) {
            child(
                SPOOKY_OUTFIT,
                neutral,
                "If you're going Trick-or-Treating, first you'll need a Treat cauldron to put " +
                    "your treats in...",
            )
            if (!handOverCauldron(SPOOKY_OUTFIT)) {
                return
            }
        }
        child(
            SPOOKY_OUTFIT,
            happy,
            "We can give you any past spooky things you don't already have. If you've lost " +
                "something, Diango in Draynor Village can also help out!",
        )
        unlockPreviousRewards()
        access.openHolidayItems()
    }

    private suspend fun Dialogue.compliment() {
        chatPlayer(happy, "Nice costumes!")
        child(SPOOKY_OUTFIT, happy, "Thanks! We made them ourselves!")
        chatPlayer(happy, "Well done, they look great!")
        if (player.hasCostume()) {
            child(CAVE_HORROR, happy, "I like yours too.")
            child(SPOOKY_OUTFIT, happy, "Yeah, it's really spooky!")
            chatPlayer(happy, "Thanks!")
            return
        }
        child(JACK_LANTERN, quiz, "Where's your costume?")
        child(CAVE_HORROR, angry, "Yeah, it's Halloween don't you know?!")
        chatPlayer(neutral, "I'm working on a super spooky one!")
        child(SPOOKY_OUTFIT, worried, "Not too spooky, though...")
        child(JACK_LANTERN, laugh, "I hope so! It's supposed to be spooky, silly!")
        child(SPOOKY_OUTFIT, worried, "Uh... Yeah, I suppose...")
    }

    private suspend fun Dialogue.handOverCauldron(child: String): Boolean {
        if (access.invAdd(access.inv, TREAT_CAULDRON).failure) {
            child(
                child,
                neutral,
                "You don't have enough space to carry it though, come back when you do!",
            )
            return false
        }
        access.unlockHolidayItem(TREAT_CAULDRON_HOLIDAY_INDEX)
        objbox(TREAT_CAULDRON, "The child hands you a Treat cauldron.")
        child(
            child,
            happy,
            "There you go. We found these by the Great Cauldron and they look just like it, " +
                "even as it fills up!",
        )
        chatPlayer(happy, "Spooky, thanks!")
        return true
    }

    private fun Dialogue.unlockPreviousRewards() {
        for (index in HalloweenEnums.halloween_previous_rewards.mapNotNull { it.value }) {
            access.unlockHolidayItem(index)
        }
    }

    private suspend fun ProtectedAccess.holidayItems(npc: Npc) {
        startDialogue(npc) { spareCostumes() }
    }

    private suspend fun ProtectedAccess.checkCostume(npc: Npc) {
        startDialogue(npc) {
            chatPlayer(quiz, "How's my costume?")
            if (player.hasCostume()) {
                chatNpc(happy, "Ooh, that's a spooky costume!")
                chatPlayer(happy, "Thanks!")
            } else {
                chatNpc(
                    neutral,
                    "It's not very spooky. Try using at least 3 Halloween items.",
                )
                chatPlayer(neutral, "Okay, I'll have another go.")
            }
        }
    }

    private suspend fun Dialogue.child(type: String, mesanim: MesAnimType, text: String) {
        chatNpcSpecific(CHILD, type, mesanim, text)
    }

    private enum class Topic {
        Cauldron,
        HowTo,
        Costumes,
        Compliment,
    }

    private companion object {
        const val TREAT_CAULDRON = "obj.hw22_treat_cauldron_0"
        const val TREAT_CAULDRON_HOLIDAY_INDEX = 157
    }
}
