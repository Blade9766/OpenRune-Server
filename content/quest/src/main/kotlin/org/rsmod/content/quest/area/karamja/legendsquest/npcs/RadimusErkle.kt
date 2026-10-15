package org.rsmod.content.quest.area.karamja.legendsquest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.GILDED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.NOTES
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.NOTES_COMPLETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_GOT_GILDED_TOTEM
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_RETURNED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_TRAINED_FOUR
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.STAGE_TRAINING_STEP
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.TRAINING_XP
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.YOMMI_TOTEM
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Radimus Erkle, Grand Vizier of the Legends' Guild. He sets the quest from his study and keeps
 * a copy of the Kharazi map; once the gilded totem pole and the finished map are handed in he
 * moves to the main hall (his two spawns are multinpcs on the quest varp) and trains the new
 * member in four skills of their choice, the last of which completes the quest.
 */
class RadimusErkle @Inject constructor(private val legends: LegendsQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(HUT) { startDialogue(it.npc) { inStudy() } }
        onOpNpc1(GUILD) { startDialogue(it.npc) { inHall() } }
        onOpNpcU(HUT) { startDialogue(it.npc) { shown(it.objType.internalName) } }
        onOpNpcU(GUILD) { startDialogue(it.npc) { shown(it.objType.internalName) } }
    }

    private suspend fun Dialogue.inStudy() {
        val stage = legends.stage(player)
        when {
            stage >= STAGE_RETURNED ->
                chatNpc(
                    neutral,
                    "Hello again! Do go through to the main Legends Guild Hall I'll meet you in " +
                        "there and we can discuss your reward!",
                )
            stage == STAGE_GOT_GILDED_TOTEM && holds(GILDED_TOTEM) && holds(NOTES_COMPLETE) -> handIn()
            stage >= STAGE_STARTED -> {
                chatNpc(neutral, "Hello there, how is the quest going?")
                midQuest()
            }
            else -> {
                chatNpc(
                    happy,
                    "Good day to you ${sirOrLady()}! No doubt you are keen to become a member of " +
                        "the Legends Guild?",
                )
                when (
                    choice3(
                        "Yes actually, what's involved?", 1,
                        "Maybe some other time.", 2,
                        "Who are you?", 3,
                    )
                ) {
                    1 -> whatsInvolved()
                    2 -> declineTalk()
                    else -> whoAreYou()
                }
            }
        }
    }

    private suspend fun Dialogue.inHall() {
        val stage = legends.stage(player)
        when {
            stage == STAGE_RETURNED -> {
                chatNpc(
                    neutral,
                    "Welcome to the Legends Guild Main Hall. We've placed your Totem Pole as pride " +
                        "of place. All members of the Legends Guild will see it as they walk in. " +
                        "They will know that it was you who brought it back.",
                )
                chatNpc(
                    neutral,
                    "Congratulations, you're now a fully fledged member. I would like to offer you " +
                        "some training which will increase your experience and abilities in four " +
                        "areas. Would you like to train now?",
                )
                offerTraining()
            }
            stage in (STAGE_RETURNED + 1) until STAGE_TRAINED_FOUR -> {
                chatNpc(neutral, "Hello again... Would you like to continue with your training?")
                offerTraining()
            }
            stage in STAGE_TRAINED_FOUR until STAGE_COMPLETE -> legends.complete(access)
            else -> postQuest()
        }
    }

    private suspend fun Dialogue.postQuest() {
        chatNpc(neutral, "Hello there! How are you enjoying the Legends Guild?")
        mesbox("Radimus looks busy...")
        chatNpc(neutral, "Excuse me a moment, won't you? Do feel free to explore the rest of the building.")
    }

    private suspend fun Dialogue.offerTraining() {
        if (choice2("Yes, I'll train now.", 1, "No, I've got something else to do at the moment.", 2) == 1) {
            training()
            return
        }
        chatPlayer(neutral, "No, I've got something else to do at the moment.")
        chatNpc(
            neutral,
            "Very well young ${if (access.isBodyTypeA()) "man" else "lady"}, return when you are " +
                "able, but don't leave it too long. You'll benefit considerably from this training.",
        )
        chatNpc(
            neutral,
            "Now, please excuse me for a while. I have other matters to attend to. Do feel free to " +
                "explore the rest of the building.",
        )
    }

    private suspend fun Dialogue.training() {
        while (legends.stage(player) < STAGE_TRAINED_FOUR) {
            val remaining = (STAGE_TRAINED_FOUR - legends.stage(player)) / STAGE_TRAINING_STEP
            if (remaining > 1) {
                chatNpc(neutral, "You can choose $remaining areas to increase your abilities in.")
            } else {
                chatNpc(neutral, "You can choose 1 area to increase your abilities in.")
            }
            val skill = chooseSkill()
            legends.setStage(access, legends.stage(player) + STAGE_TRAINING_STEP)
            access.statAdvance(skill.first, TRAINING_XP)
            mesbox("You receive some training and increase experience to your ${skill.second}.")
        }
        chatNpc(
            neutral,
            "Right, that's all the training I can offer you. I hope you're happy with your new " +
                "skills. Do feel free to explore the rest of the building.",
        )
        access.ifClose()
        legends.complete(access)
    }

    private suspend fun Dialogue.chooseSkill(): Pair<String, String> {
        var menu = 0
        while (true) {
            val page = SKILL_MENUS[menu]
            val choice =
                choice4(
                    "* ${page[0].second} *", 0,
                    "* ${page[1].second} *", 1,
                    "* ${page[2].second} *", 2,
                    "--- Go to Skill Menu ${(menu + 1) % SKILL_MENUS.size + 1} ----", 3,
                )
            if (choice < 3) {
                return page[choice]
            }
            menu = (menu + 1) % SKILL_MENUS.size
        }
    }

    private suspend fun Dialogue.handIn() {
        chatNpc(
            happy,
            "Well, it looks as if you have some interesting items there. Let's have a look at " +
                "them! <col=0000ff>--Sir Radimus sees the gilded totem pole.--</col>",
        )
        mesbox("You show the totem pole to Sir Radimus.")
        chatNpc(neutral, "${sirOrMadam()}, this is truly amazing...")
        chatNpc(
            neutral,
            "That totem pole will take pride of place in the Legends Guild as a reminder of your " +
                "quest to gain entry. And so that many other great adventurers can admire your bravery.",
        )
        objbox(GILDED_TOTEM, "Sir Radimus Erkle orders some guards to take the totem pole into the main Legends hall.")
        chatNpc(
            neutral,
            "Well, you've completed the tasks I set you. That map of the Kharazi jungle will be " +
                "very helpful for future expeditions. <col=0000ff>-- Sir Radimus takes the map and " +
                "the totem pole --</col>",
        )
        access.invDel(access.inv, GILDED_TOTEM, 1)
        access.invDel(access.inv, NOTES_COMPLETE, 1)
        legends.setStage(access, STAGE_RETURNED)
        chatNpc(
            neutral,
            "Congratulations, welcome to the Legends Guild. Please, go through to the main Legends " +
                "Guild building and I will join you shortly.",
        )
    }

    private suspend fun Dialogue.midQuest() {
        val hasMap = legends.owns(access, NOTES) || legends.owns(access, NOTES_COMPLETE)
        val choice =
            if (!hasMap) {
                choice5(
                    "Terrible, I lost my map of the Kharazi Jungle.", LOST_MAP,
                    "It's ok, but I have forgotten what to do.", FORGOT,
                    "I need another machete.", MACHETE,
                    "I've run out of charcoal.", CHARCOAL,
                    "I've run out of papyrus.", PAPYRUS,
                )
            } else {
                choice5(
                    "It's ok, but I have forgotten what to do.", FORGOT,
                    "I need another machete.", MACHETE,
                    "I've run out of charcoal.", CHARCOAL,
                    "I've run out of papyrus.", PAPYRUS,
                    "I've completed the quest.", COMPLETED,
                )
            }
        when (choice) {
            LOST_MAP -> lostMap()
            FORGOT -> {
                chatPlayer(neutral, "It's ok, but I have forgotten what to do.")
                chatNpc(
                    neutral,
                    "Tut! How forgetful! You need to find a way into the Kharazi Jungle. Then you " +
                        "need to explore and map that entire area.",
                )
                chatNpc(
                    neutral,
                    "While you're there you need to make contact with any jungle natives. Bring back " +
                        "a tribal gift from the natives so that we can display it in the Legends " +
                        "Guild. I hope that answers your question!",
                )
            }
            MACHETE -> {
                chatPlayer(neutral, "I need another machete.")
                chatNpc(neutral, "Well, just get another one from the cupboard.")
            }
            CHARCOAL -> {
                chatPlayer(neutral, "I've run out of charcoal.")
                chatNpc(neutral, "Well, get some more! Be proactive and get some more from somewhere.")
                chatNpc(neutral, "It's hardly legendary if you fail a quest because you can't find some charcoal!")
            }
            PAPYRUS -> {
                chatPlayer(neutral, "I've run out of papyrus.")
                chatNpc(neutral, "Well, get some more! Be proactive and try to find some!")
                chatNpc(neutral, "It's hardly legendary if you fail a quest because you can't find some papyrus!")
            }
            else -> {
                chatPlayer(neutral, "I've completed the quest.")
                chatNpc(
                    neutral,
                    "Well, if you have, show me the gift the Kharazi people gave you! Becoming a " +
                        "legend is more than just fighting you know. It also requires some careful " +
                        "diplomacy and problem solving.",
                )
                chatNpc(neutral, "Also complete the map of Kharazi Jungle and we will admit you to the Guild.")
            }
        }
    }

    private suspend fun Dialogue.lostMap() {
        chatPlayer(neutral, "Terrible, I lost my map of the Kharazi Jungle.")
        chatNpc(
            neutral,
            "That's awful, well, luckily I have a copy here. But I need to charge you a copy fee " +
                "of 30 gold pieces.",
        )
        if (access.inv.count(COINS) < COPY_FEE) {
            chatNpc(neutral, "It looks as if you don't have the funds for it at the moment. How irritating...")
            return
        }
        chatNpc(neutral, "Do you agree to pay?")
        if (choice2("Yes, I'll pay for it.", 1, "No, I won't pay for it.", 2) == 2) {
            chatPlayer(neutral, "No, I won't pay for it.")
            chatNpc(
                neutral,
                "Well, that's your decision, of course.. but you won't be able to complete the " +
                    "quest without it. Excuse, me now won't you, I have other business to attend to.",
            )
            return
        }
        chatPlayer(neutral, "Yes, I'll pay for it.")
        access.invDel(access.inv, COINS, COPY_FEE)
        access.mes("You hand over 30 Gold Pieces.")
        access.invAdd(access.inv, NOTES, 1)
        chatNpc(neutral, "Ok, please don't lose this one.")
    }

    private suspend fun Dialogue.whatsInvolved() {
        chatPlayer(neutral, "Yes actually, what's involved?")
        chatNpc(
            neutral,
            "Well, you need to complete a quest for us. You need to map an area called the " +
                "Kharazi Jungle. It is the unexplored southern part of Karamja Island.",
        )
        chatNpc(
            neutral,
            "We'd also like you to befriend a native from the Kharazi tribe in order to get a gift " +
                "or token of friendship. We want to display it in the Legends Guild Main hall. Are " +
                "you interested in this quest?",
        )
        if (choice2("Yes, it sounds great!", 1, "Not just at the moment.", 2) == 2) {
            chatPlayer(neutral, "Not just at the moment.")
            chatNpc(neutral, "Very well, if you change your mind, please come back and see me.")
            return
        }
        chatPlayer(neutral, "Yes, it sounds great!")
        chatNpc(neutral, "Excellent!")
        if (access.inv.isFull()) {
            chatNpc(neutral, "Please make yourself some space, I have something for you.")
            return
        }
        chatNpc(neutral, "Ok, you'll need this starting map of the Kharazi Jungle.")
        objbox(NOTES, "Grand Vizier Erkle gives you some notes and a map.")
        legends.start(access)
        access.invAdd(access.inv, NOTES, 1)
        chatNpc(
            neutral,
            "Complete this map when you get to the Kharazi Jungle. It's towards the southern most " +
                "part of Karamja.",
        )
        mesbox("Radimus shuffles around the back of his desk and gives you a stern look.")
        chatNpc(
            confused,
            "It is likely to be very tough going. You'll need an axe and machete to cut through " +
                "the dense Kharazi Jungle, collect a machete from the cupboard before you leave. " +
                "You'll need to find your own axe, preferably a good one.",
        )
        chatNpc(
            confused,
            "Finally, we really do need some sort of display item for the Guild hall. Bring back " +
                "some sort of token which we can display in the Guild. And very good luck to you!",
        )
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(neutral, "Who are you?")
        chatNpc(
            neutral,
            "My name is Radimus Erkle. I am the Grand Vizier of the Legends' Guild. Are you " +
                "interested in becoming a member?",
        )
        if (choice2("Yes actually, what's involved?", 1, "Maybe some other time.", 2) == 1) {
            whatsInvolved()
        } else {
            declineTalk()
        }
    }

    private suspend fun Dialogue.declineTalk() {
        chatPlayer(neutral, "Maybe some other time.")
        chatNpc(neutral, "As you wish...")
    }

    private suspend fun Dialogue.shown(obj: String) {
        val stage = legends.stage(player)
        if (stage >= STAGE_RETURNED) {
            chatNpc(neutral, "Sorry old bean, but I don't really need that!")
            return
        }
        when (obj) {
            NOTES_COMPLETE -> {
                if (stage == STAGE_GOT_GILDED_TOTEM) {
                    if (holds(GILDED_TOTEM)) {
                        handIn()
                    } else {
                        chatNpc(
                            neutral,
                            "It's very nice, ${sirOrMadam()}, but we also need something special to " +
                                "display in the Legends Guild.",
                        )
                    }
                    return
                }
                chatNpc(
                    neutral,
                    "Well done, ${sirOrMadam()}, very well done. However, you'll probably need it " +
                        "while you search for natives of the Kharazi tribe in the Kharazi Jungle.",
                )
                chatNpc(
                    neutral,
                    "Remember, we would like a special token of friendship from them to place in " +
                        "the Legends Guild. I'll take the map off your hands once we get the proof " +
                        "that you have met the natives.",
                )
            }
            YOMMI_TOTEM -> {
                chatNpc(
                    neutral,
                    "Hmmm, well, it is very impressive. Especially since it looks very heavy... " +
                        "However, it lacks a certain authenticity, my guess is that you made it, " +
                        "though I'm not sure why.",
                )
                chatNpc(
                    neutral,
                    "We would like to have a really nice item to put on display in the Legends " +
                        "Guild main hall. Do you think you could get something more authentic?",
                )
            }
            GILDED_TOTEM -> {
                if (stage != STAGE_GOT_GILDED_TOTEM) {
                    chatNpc(neutral, "Sorry old bean, but I don't really need that!")
                } else if (holds(NOTES_COMPLETE)) {
                    handIn()
                } else {
                    chatNpc(neutral, "It's very nice, ${sirOrMadam()}, but I need you to complete the map as well.")
                }
            }
            else -> chatNpc(neutral, "Sorry old bean, but I don't really need that!")
        }
    }

    private fun Dialogue.holds(obj: String): Boolean = access.inv.count(obj) > 0

    private fun Dialogue.sirOrLady(): String = if (access.isBodyTypeA()) "Sir" else "m'Lady"

    private fun Dialogue.sirOrMadam(): String = if (access.isBodyTypeA()) "Sir" else "Madam"

    private companion object {
        const val HUT = "npc.radimus_erkle_hut"
        const val GUILD = "npc.radimus_erkle_guild"
        const val COINS = "obj.coins"
        const val COPY_FEE = 30

        const val LOST_MAP = 1
        const val FORGOT = 2
        const val MACHETE = 3
        const val CHARCOAL = 4
        const val PAPYRUS = 5
        const val COMPLETED = 6

        val SKILL_MENUS =
            listOf(
                listOf("stat.attack" to "Attack", "stat.defence" to "Defence", "stat.strength" to "Strength"),
                listOf("stat.hitpoints" to "Hitpoints", "stat.prayer" to "Prayer", "stat.magic" to "Magic"),
                listOf("stat.woodcutting" to "Woodcutting", "stat.crafting" to "Crafting", "stat.smithing" to "Smithing"),
                listOf("stat.herblore" to "Herblore", "stat.agility" to "Agility", "stat.thieving" to "Thieving"),
            )
    }
}
