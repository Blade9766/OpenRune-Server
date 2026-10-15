package org.rsmod.content.quest.area.taverley.druidicritual.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.KAQEMEEX
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.STAGE_GAVE_INGREDIENTS
import org.rsmod.content.quest.area.taverley.druidicritual.DruidicRitualQuest.Companion.STAGE_STARTED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Kaqemeex, at the druids' stone circle north of Taverley. He starts Druidic Ritual, sends the
 * player to Sanfew, and once the meats are delivered finishes the quest and teaches Herblore.
 */
class Kaqemeex @Inject constructor(private val druidicRitual: DruidicRitualQuest) : PluginScript() {

    private val quest
        get() = druidicRitual.quest

    override fun ScriptContext.startup() {
        onOpNpc1(KAQEMEEX) { startDialogue(it.npc) { kaqemeex() } }
    }

    private suspend fun Dialogue.kaqemeex() {
        chatPlayer(neutral, "Hello there.")
        when (druidicRitual.stage(player)) {
            0 -> notStarted()
            in STAGE_STARTED until STAGE_GAVE_INGREDIENTS -> {
                chatNpc(neutral, "Hello again, adventurer. You will need to speak to my fellow druid Sanfew in the village south of here to continue in your quest.")
                chatPlayer(happy, "Ok, thanks.")
            }
            STAGE_GAVE_INGREDIENTS -> completion()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(quiz, "What brings you to our holy monument?")
        when (
            choice3(
                "Who are you?", 1,
                "I'm in search of a quest.", 2,
                "Did you build this?", 3,
            )
        ) {
            1 -> whoAreYou()
            2 -> searchOfQuest()
            3 -> didYouBuildThis()
        }
    }

    private suspend fun Dialogue.whoAreYou() {
        chatPlayer(quiz, "Who are you?")
        chatNpc(neutral, "We are the druids of Guthix. We worship our god at our famous stone circles. You will find them located throughout these lands.")
        when (
            choice3(
                "What about the stone circle full of dark wizards?", 1,
                "So what's so good about Guthix?", 2,
                "Well, I'll be on my way now.", 3,
            )
        ) {
            1 -> darkWizards()
            2 -> aboutGuthix()
            3 -> leaving()
        }
    }

    private suspend fun Dialogue.didYouBuildThis() {
        chatPlayer(quiz, "Did you build this?")
        chatNpc(neutral, "What, personally? No, of course I didn't. However, our forefathers did. The first Druids of Guthix built many stone circles across these lands over eight hundred years ago.")
        chatNpc(sad, "Unfortunately we only know of two remaining, and of those only one is usable by us anymore.")
        when (
            choice3(
                "What about the stone circle full of dark wizards?", 1,
                "I'm in search of a quest.", 2,
                "Well, I'll be on my way now.", 3,
            )
        ) {
            1 -> darkWizards()
            2 -> searchOfQuest()
            3 -> leaving()
        }
    }

    private suspend fun Dialogue.aboutGuthix() {
        chatPlayer(quiz, "So what's so good about Guthix?")
        chatNpc(neutral, "Guthix is the oldest and most powerful god in RuneScape. His existence is vital to this world. He is the god of balance, and nature; he is also a very part of this world.")
        chatNpc(neutral, "He exists in the trees, and the flowers, the water and the rocks. He is everywhere. His purpose is to ensure balance in everything in this world, and as such we worship him.")
        chatPlayer(confused, "He sounds kind of boring...")
        chatNpc(neutral, "Some day when your mind achieves enlightenment you will see the true beauty of his power.")
    }

    private suspend fun Dialogue.leaving() {
        chatPlayer(neutral, "Well, I'll be on my way now.")
        chatNpc(neutral, "Goodbye adventurer. I feel we shall meet again.")
    }

    private suspend fun Dialogue.searchOfQuest() {
        chatPlayer(neutral, "I'm in search of a quest.")
        chatNpc(neutral, "Hmm. I think I may have a worthwhile quest for you actually. I don't know if you are familiar with the stone circle south of Varrock or not, but...")
        ourCircle()
    }

    private suspend fun Dialogue.darkWizards() {
        chatPlayer(quiz, "What about the stone circle full of dark wizards?")
        ourCircle()
    }

    private suspend fun Dialogue.ourCircle() {
        chatNpc(neutral, "That used to be OUR stone circle. Unfortunately, many many years ago, dark wizards cast a wicked spell upon it so that they could corrupt its power for their own evil ends.")
        chatNpc(neutral, "When they cursed the rocks for their rituals they made them useless to us and our magics. We require a brave adventurer to go on a quest for us to help purify the circle of Varrock.")
        when (
            choice3(
                "Ok, I will try and help.", 1,
                "No, that doesn't sound very interesting.", 2,
                "So... is there anything in this for me?", 3,
                title = "Start the Druidic Ritual quest?",
            )
        ) {
            1 -> agreeToHelp()
            2 -> notInterested()
            3 -> {
                chatPlayer(quiz, "So... is there anything in this for me?")
                chatNpc(neutral, "We druids value wisdom over wealth, so if you expect material gain, you will be disappointed. We are, however, very skilled in the art of Herblore, which we will share with you")
                chatNpc(neutral, "if you can assist us with this task. You may find such wisdom a greater reward than mere money.")
                when (
                    choice2(
                        "Ok, I will try and help.", 1,
                        "No, that doesn't sound very interesting.", 2,
                        title = "Start the Druidic Ritual quest?",
                    )
                ) {
                    1 -> agreeToHelp()
                    2 -> notInterested()
                }
            }
        }
    }

    private suspend fun Dialogue.agreeToHelp() {
        chatPlayer(neutral, "Ok, I will try to help.")
        quest.advanceQuestStage(access)
        chatNpc(neutral, "Excellent. Go to the village south of this place and speak to my fellow Sanfew who is working on the purification ritual. He knows better than I what is required to complete it.")
        chatPlayer(neutral, "Will do.")
    }

    private suspend fun Dialogue.notInterested() {
        chatPlayer(confused, "No, that doesn't sound very interesting.")
        chatNpc(neutral, "I will not try and change your mind adventurer. Some day when you have matured you may reconsider your position. We will wait until then.")
    }

    private suspend fun Dialogue.completion() {
        chatNpc(neutral, "I have word from Sanfew that you have been very helpful in assisting him with his preparations for the purification ritual. As promised I will now teach you the ancient arts of Herblore.")
        quest.completeQuest(access)
        chatNpc(happy, "I will now explain the fundamentals of Herblore:")
        fundamentals()
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(neutral, "Hello again. How is the Herblore going?")
        when (
            choice3(
                "Very well, thank you.", 1,
                "I need more practice at it.", 2,
                "Can you explain the fundamentals again?", 3,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "Very well, thank you.")
                chatNpc(neutral, "That is good to hear.")
            }
            2 -> {
                chatPlayer(neutral, "I need more practice at it...")
                chatNpc(neutral, "Persistence is key to success.")
            }
            3 -> {
                chatPlayer(confused, "Can you explain the fundamentals again?")
                chatNpc(neutral, "Indeed I will...")
                fundamentals()
            }
        }
    }

    private suspend fun Dialogue.fundamentals() {
        chatNpc(neutral, "Herblore is the skill of working with herbs and other ingredients, to make useful potions and poison.")
        chatNpc(neutral, "First you will need a vial, which can be found or made with the crafting skill.")
        chatNpc(neutral, "Then you must gather the herbs needed to make the potion you want.")
        chatNpc(neutral, "You must fill your vial with water and add the ingredients you need. There are normally 2 ingredients to each type of potion.")
        chatNpc(neutral, "Bear in mind, you must first identify each herb, to see what it is.")
        chatNpc(neutral, "You may also have to grind some herbs before you can use them. You will need a pestle and mortar in order to do this.")
        chatNpc(neutral, "Herbs can be found on the ground, and are also dropped by some monsters when you kill them.")
        chatNpc(neutral, "Let's try an example Attack potion: The first ingredient is Guam leaf; the next is Eye of Newt.")
        chatNpc(neutral, "Mix these in your water-filled vial, and you will produce an Attack potion.")
        chatNpc(neutral, "Drink this potion to increase your Attack level.")
        chatNpc(neutral, "Different potions also require different Herblore levels before you can make them.")
        chatNpc(happy, "Good luck with your Herblore practices, Good day adventurer.")
        chatPlayer(happy, "Thanks for your help.")
    }
}
