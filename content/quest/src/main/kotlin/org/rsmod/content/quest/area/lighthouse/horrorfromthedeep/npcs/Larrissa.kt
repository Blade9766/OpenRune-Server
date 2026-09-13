package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFlag
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.KEY
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.LARRISSA
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.LARRISSA_INSIDE
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_DAGANNOTH_SLAIN
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_LIGHT_FIXED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.lighthouse.horrorfromthedeep.HorrorFromTheDeepQuest.Companion.STAGE_UNLOCKED
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Larrissa waits on the causeway outside the Lighthouse; a second Larrissa stands in the wrecked
 * copy of the ground floor, where she asks the player to repair the light.
 */
class Larrissa @Inject constructor(private val horror: HorrorFromTheDeepQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(LARRISSA) { startDialogue(it.npc) { outside() } }
        onOpNpc1(LARRISSA_INSIDE) { startDialogue(it.npc) { inside() } }
    }

    private suspend fun Dialogue.outside() {
        if (horror.isComplete(player)) {
            chatNpc(happy, "Adventurer! My darling Jossik is safe, all thanks to you.")
            chatPlayer(happy, "How is his leg?")
            chatNpc(neutral, "Mending, slowly. He insists on tending the light himself, the stubborn fool.")
            return
        }
        when (horror.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> started()
            STAGE_UNLOCKED -> chatNpc(worried, "Hurry, we must get inside and find out what has happened to my Jossik!")
            STAGE_BRIEFED -> {
                chatNpc(worried, "Adventurer, the light is still out! Please do something about it!")
                chatNpc(worried, "Any ship that comes round this coast in the dark will be dashed on the rocks.")
                chatPlayer(neutral, "I'm working on it.")
            }
            STAGE_LIGHT_FIXED -> {
                if (horror.wallStarted(player)) {
                    chatPlayer(happy, "The light is burning again!")
                    chatNpc(happy, "Wonderful work, adventurer!")
                    if (horror.wallFilled(player)) {
                        chatNpc(worried, "Now you can put all your efforts into finding my Jossik!")
                    }
                    return
                }
                chatNpc(worried, "Is there any sign of my Jossik yet?")
                chatPlayer(sad, "Nothing so far.")
                chatNpc(sad, "I came outside hoping to spot him somewhere along the shore, but there is nothing. I am so frightened for him.")
                chatPlayer(neutral, "Try not to worry. I'll track him down.")
                chatNpc(happy, "Bless you.")
            }
            STAGE_DAGANNOTH_SLAIN -> {
                chatPlayer(happy, "I've found Jossik! He's alive, in a cave beneath the lighthouse.")
                chatNpc(shocked, "Oh, thank Armadyl! Then why hasn't he come back up?")
                chatPlayer(worried, "Well... there's a rather large monster down there that won't let him leave.")
                chatNpc(verymad, "A monster?! Then you have to save him! Please, go back down there!")
                chatPlayer(neutral, "Alright, alright, I'm going.")
            }
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(worried, "Oh, thank Armadyl you're here! Please, you have to help me!")
        val help = choice2("With what?", true, "Sorry, just passing through.", false)
        if (!help) {
            declined()
            return
        }
        chatPlayer(quiz, "With what?")
        chatNpc(sad, "My boyfriend keeps this lighthouse, but I haven't seen him for days. Something awful has happened, I just know it!")
        chatNpc(worried, "The light has gone out and the front door is locked. He would never do either!")
        chatNpc(worried, "Without the light this coast is a death trap for ships.")
        chatPlayer(neutral, "Maybe he's taken a holiday. It must get dull out here.")
        chatNpc(angry, "He would never be so careless, and he would never leave without a word to me!")
        chatNpc(sad, "Please, adventurer. I can feel that something is wrong.")
        val howToHelp = choice2("But how can I help?", true, "Sorry, just passing through.", false)
        if (!howToHelp) {
            declined()
            return
        }
        chatPlayer(quiz, "But how can I help?")
        chatNpc(neutral, "We need that light working again. And the storm that took out the bridge has left me stranded on this causeway.")
        chatNpc(neutral, "I left a spare key with my cousin. If you could fetch it, and mend the bridge well enough for me to reach my family in Rellekka, I'd be forever grateful.")
        val start = choice2("Yes.", true, "No.", false, title = "Start the Horror from the Deep quest?")
        if (!start) {
            declined()
            return
        }
        chatPlayer(happy, "Okay, I'll help!")
        horror.quest.advanceQuestStage(access)
        chatNpc(happy, "Oh, thank you! I know my Jossik would never have let the light go out, or left without telling me.")
        questions()
    }

    private suspend fun Dialogue.declined() {
        chatPlayer(neutral, "Sorry, just passing through.")
        chatNpc(sad, "Oh... my poor Jossik. Something terrible has happened, I'm sure of it...")
    }

    private suspend fun Dialogue.started() {
        val hasKey = player.inv.contains(KEY)
        val bridge = horror.bridgeRepaired(player)
        when {
            hasKey && bridge -> {
                chatPlayer(happy, "I've got your key!")
                chatNpc(happy, "Oh, thank you!")
                chatNpc(worried, "Quickly, let's unlock the door and find out what has happened to my Jossik!")
            }
            hasKey -> {
                chatPlayer(happy, "I've got your key!")
                chatNpc(worried, "Thank you, but I still need that bridge mended. A key is no comfort while I'm trapped out here.")
            }
            bridge -> {
                chatPlayer(happy, "I've fixed the bridge for you!")
                chatNpc(happy, "Oh, thank you so much!")
                chatNpc(worried, "Now please find that key. I can't bear to think what might have happened to Jossik...")
            }
            else -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(worried, "Please find my darling! I know something dreadful has happened!")
                questions()
            }
        }
    }

    private suspend fun Dialogue.questions() {
        while (true) {
            when (choice3("Where is your cousin?", 1, "How can I fix the bridge?", 2, "I'll see what I can do.", 3)) {
                1 -> {
                    chatPlayer(quiz, "Where is your cousin?")
                    if (horror[player, HorrorFlag.GotKey]) {
                        chatNpc(confused, "My cousin? He already gave you my spare key. Are you feeling alright, adventurer?")
                        chatPlayer(shifty, "Er... of course. I was just... testing you. Yes.")
                        return
                    }
                    chatNpc(neutral, "Gunnjorn left home in Rellekka years ago to chase his love of agility.")
                    chatNpc(neutral, "I don't know where he ended up, but I'd wager it's somewhere he can practise. Mention my name and he'll know you.")
                }
                2 -> {
                    chatPlayer(quiz, "How can I fix the bridge?")
                    chatNpc(neutral, "I'm no helpless maiden. My balance is good, so a plank on each side of the gap will be enough for me to cross.")
                    chatNpc(neutral, "You'll need a hammer, and thirty steel nails for each plank. I think there are some planks lying around nearby.")
                }
                else -> {
                    chatPlayer(neutral, "I'll see what I can do.")
                    chatNpc(happy, "Thank you so much!")
                    return
                }
            }
        }
    }

    private suspend fun Dialogue.inside() {
        val stage = horror.stage(player)
        when {
            horror.isComplete(player) || stage >= STAGE_LIGHT_FIXED -> {
                chatPlayer(happy, "The light is working again!")
                chatNpc(happy, "Wonderful, adventurer!")
                chatNpc(worried, "Now you can put all your efforts into finding my Jossik!")
            }
            stage == STAGE_UNLOCKED -> {
                chatNpc(shocked, "This is dreadful...")
                chatNpc(worried, "What could have done this? Please, you must mend the light. I won't have Jossik blamed for a shipwreck!")
                chatPlayer(neutral, "Alright, I'll see what I can do.")
                horror.advanceTo(access, STAGE_BRIEFED)
            }
            else -> {
                chatPlayer(confused, "What was I meant to be doing again?")
                chatNpc(angry, "The light! Ships are in terrible danger for as long as it stays dark!")
                chatPlayer(quiz, "Any idea how I'd go about fixing it?")
                chatNpc(sad, "I'm sorry, I know nothing about lighthouses.")
                chatNpc(neutral, "Jossik knew nothing either when he took the job. The council must have left him some sort of manual. Try looking around.")
            }
        }
    }
}
