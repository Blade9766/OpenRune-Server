package org.rsmod.content.quest.area.karamja.legendsquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.BULLROARER
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.NOTES_COMPLETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GOT_BULLROARER
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The jungle foresters who work the edge of the Kharazi Jungle. Neither has managed to get far
 * into it, and they will trade their bullroarer for a copy of a finished map of it.
 */
class JungleForester
@Inject
constructor(private val legends: LegendsQuest, private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (forester in FORESTERS) {
            onOpNpc1(forester) { startDialogue(it.npc) { greeting() } }
            onOpNpcU(forester) { startDialogue(it.npc) { shown(it.objType.internalName) } }
        }
    }

    private suspend fun Dialogue.greeting() {
        chatNpc(neutral, "Hello friend. You're a long way from civilisation!")
        if (legends.isStarted(player)) {
            when (
                choice3(
                    "How do I get into the Kharazi Jungle?", 1,
                    "What do you do here?", 2,
                    "Have you seen any natives in the jungle?", 3,
                )
            ) {
                1 -> howDoIGetIn()
                2 -> whatDoYouDo()
                else -> natives()
            }
            return
        }
        when (
            choice3(
                "What do you do here?", 1,
                "How do I get into the Kharazi Jungle?", 2,
                "Who are you?", 3,
            )
        ) {
            1 -> whatDoYouDo()
            2 -> howDoIGetIn()
            else -> whoAreYou()
        }
    }

    private suspend fun Dialogue.shown(obj: String) {
        if (obj != NOTES_COMPLETE) {
            chatNpc(neutral, "Sorry, but I have no use for that.")
            return
        }
        if (legends.owns(access, BULLROARER)) {
            mesbox("You show the completed map of Kharazi Jungle to the Forester.")
            chatNpc(neutral, "It's a great map, thanks for letting me take a copy! It has helped me out a number of times now.")
            return
        }
        chatNpc(neutral, "*Gasp* <col=0000ff>-- The jungle forester looks speechless. --</col>")
        chatNpc(
            neutral,
            "This is very impressive! I'm amazed, it's just great! Do you mind if I make a copy of " +
                "it, and I'll give you an item in return.",
        )
        when (
            choice3(
                "Yes, go ahead make a copy!", 1,
                "What will you give me in return?", 2,
                "Sorry, I must complete my quest.", 3,
            )
        ) {
            1 -> copyMap()
            2 -> inReturn()
            else -> mustComplete()
        }
    }

    private suspend fun Dialogue.inReturn() {
        chatPlayer(neutral, "What will you give me in return?")
        chatNpc(neutral, "Well, I can offer you this?")
        objbox(
            BULLROARER,
            "The Jungle Forester takes out a strange looking object. It looks like a wooden pole, " +
                "with string attached to one end. And at the other end of the string is a shaped " +
                "piece of wood.",
        )
        chatNpc(
            neutral,
            "If you swing this above your head, it makes a strange sound. I noticed that it " +
                "attracts the attention of the natives. Is it a deal? Can I make a copy of your map?",
        )
        if (choice2("Yes, go ahead make a copy!", true, "Sorry, I must complete my quest.", false)) {
            copyMap()
        } else {
            mustComplete()
        }
    }

    private suspend fun Dialogue.copyMap() {
        chatPlayer(neutral, "Yes, go ahead make a copy!")
        chatNpc(neutral, "Many thanks friend.")
        val pronoun = if (npc?.isType(FORESTER_FEMALE) == true) "She" else "He"
        mesbox("The Jungle Forester takes out some parchment and some charcoal. $pronoun studiously renders another copy of your map.")
        chatNpc(neutral, "Many thanks friend.")
        objbox(BULLROARER, "$pronoun takes out a strange looking object and hands it to you.")
        access.invAddOrDrop(objRepo, BULLROARER)
        legends.raiseTo(access, STAGE_GOT_BULLROARER)
        chatNpc(
            neutral,
            "Here, I won't be needing this any longer, and it may help you. Whenever I've used it " +
                "before, it attracted the attention of jungle natives.",
        )
    }

    private suspend fun Dialogue.mustComplete() {
        chatPlayer(neutral, "Sorry, I must complete my quest.")
        chatNpc(
            sad,
            "Very well friend, I understand, I must be on my way as well. <col=0000ff>-- The " +
                "Jungle Forester seems a bit annoyed... and wanders off. --</col>",
        )
    }

    private suspend fun Dialogue.natives() {
        chatPlayer(neutral, "Have you seen any natives in the jungle?")
        chatNpc(
            neutral,
            "Well, I've heard some funny sounds and I think I've seen a native... but I'm not sure. " +
                "They generally don't like to be seen I guess.",
        )
        chatNpc(
            neutral,
            "But I found an item that you might be interested in. You swing it above your head and " +
                "it makes a strange sound, it seems to attract their attention.",
        )
        when (
            choice3(
                "Can I have the item please?", 1,
                "How do I get into the Kharazi jungle?", 2,
                "Ok thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Can I have the item please?")
                chatNpc(
                    neutral,
                    "Well, I wish I could give it to you. However, I have grown fond of it. And it " +
                        "may help me in case I get lost in the jungle.",
                )
                if (choice2("Will you trade something for it?", true, "Ok thanks.", false)) {
                    chatPlayer(neutral, "Will you trade something for it?")
                    chatNpc(
                        neutral,
                        "Well, if you have something interesting, let me have a look at it and I'll " +
                            "offer you something in return. OK, I have to go now, but it's been nice " +
                            "talking with you.",
                    )
                } else {
                    thanks()
                }
            }
            2 -> howDoIGetIn()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.whatDoYouDo() {
        chatPlayer(neutral, "What do you do here?")
        chatNpc(
            neutral,
            "I'm a forester, and I specialise in exotic woods. I've not managed to penetrate the " +
                "Kharazi Jungle very far but I have found some interesting tree specimens.",
        )
        chatNpc(
            neutral,
            "If you do happen to get into the Kharazi Jungle, do come and let me know. I'd love to " +
                "be able to safely navigate my own way in and out.",
        )
        when (choice3("How do I get into the Kharazi Jungle?", 1, "Who are you?", 2, "Ok thanks.", 3)) {
            1 -> howDoIGetIn()
            2 -> whoAreYou()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(neutral, "Who are you?")
        chatNpc(neutral, "I'm a jungle forester. Names mean little in this part of the world.")
        if (choice2("What do you do here?", true, "How do I get into the Kharazi Jungle?", false)) {
            whatDoYouDo()
        } else {
            howDoIGetIn()
        }
    }

    private suspend fun Dialogue.howDoIGetIn() {
        chatPlayer(neutral, "How do I get into the Kharazi Jungle?")
        chatNpc(
            neutral,
            "Well, I've not managed it yet but I heard that someone managed to find a way in. But " +
                "they only just managed to escape the jungle with their lives.",
        )
        chatNpc(neutral, "Apparently he was on a mission to map the area. How foolish is that?")
        if (legends.isStarted(player)) {
            when (
                choice5(
                    "Well, in fact I plan to map that area myself.", 1,
                    "Are you calling me foolish?", 2,
                    "What do you do here?", 3,
                    "Have you seen any natives in the jungle?", 4,
                    "Ok thanks.", 5,
                )
            ) {
                1 -> planToMap()
                2 -> {
                    chatPlayer(neutral, "Are you calling me foolish?")
                    chatNpc(neutral, "No, of course not... Sorry, I have to be on my way...")
                }
                3 -> whatDoYouDo()
                4 -> natives()
                else -> thanks()
            }
            return
        }
        when (
            choice3(
                "So someone managed to get into the jungle?", 1,
                "What do you do here?", 2,
                "Ok thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "So someone managed to get into the Jungle?")
                chatNpc(
                    neutral,
                    "Yes, he said he was from some place... near the Sorcerer's Tower. Mentioned " +
                        "something about a legend? It meant nothing to me though.",
                )
                when (choice3("How do I get into the Kharazi Jungle?", 1, "What do you do here?", 2, "Ok thanks.", 3)) {
                    1 -> howDoIGetIn()
                    2 -> whatDoYouDo()
                    else -> thanks()
                }
            }
            2 -> whatDoYouDo()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.planToMap() {
        chatPlayer(neutral, "Well, in fact I plan to map that area myself.")
        chatNpc(
            neutral,
            "<col=0000ff>--The forester looks very interested--</col> Oh, well, that sounds quite " +
                "good actually... Sorry if I sounded rude before, it just didn't seem like a good idea to me.",
        )
        chatNpc(
            neutral,
            "I guess I just wouldn't want to do it myself. But a map of that area would certainly " +
                "be a big task. And it would certainly be very useful... <col=0000ff>-- The forester " +
                "looks very thoughtful --</col>",
        )
        chatNpc(
            neutral,
            "Hey, if you manage to complete it, be sure to let me take a look! Well, best of luck " +
                "with it, I'm sure you're going to need it.",
        )
        when (
            choice3(
                "Do you have any other tips about the Kharazi Jungle?", 1,
                "Have you seen any natives in the jungle?", 2,
                "Ok thanks.", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Do you have any other tips about the Kharazi Jungle?")
                chatNpc(neutral, "Not really, but I would say be careful, it's a dangerous place. And good luck.")
            }
            2 -> natives()
            else -> thanks()
        }
    }

    private suspend fun Dialogue.thanks() {
        chatPlayer(neutral, "Ok thanks.")
        chatNpc(neutral, "You're welcome! See you around...")
    }

    private companion object {
        const val FORESTER_FEMALE = "npc.jungleforester_f"
        val FORESTERS = listOf("npc.jungleforester_m", FORESTER_FEMALE)
    }
}
