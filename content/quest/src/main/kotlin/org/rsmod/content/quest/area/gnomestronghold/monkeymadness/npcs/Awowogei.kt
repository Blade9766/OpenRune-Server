package org.rsmod.content.quest.area.gnomestronghold.monkeymadness.npcs

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.ChapterCards
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.Greegree
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadness
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AMULET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_HEAD
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_MONKEY_FREED
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_NOT_MET
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.AWOWOGEI_TASK_GIVEN
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.MONKEY_IN_BACKPACK
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_ALLIANCE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.STAGE_MONKEY
import org.rsmod.content.quest.area.gnomestronghold.monkeymadness.MonkeyMadnessQuest.Companion.THRONE
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * King Awowogei on his throne. The cache models the King as the throne loc itself, named
 * "Awowogei" with a Talk-to option; his chathead is the cutscene npc.
 */
class Awowogei @Inject constructor(private val monkeyMadness: MonkeyMadnessQuest, private val greegree: Greegree, private val cards: ChapterCards) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(THRONE) { audience() }
    }

    private suspend fun ProtectedAccess.audience() {
        if (!greegree.isMonkey(player)) {
            mes("The King's guards would tear a human apart before he got near the throne.")
            return
        }
        if (!player.worn.contains(AMULET)) {
            mes("You try to speak, but all that comes out is 'Ook'. You need the M'speak amulet on.")
            return
        }
        startDialogue {
            when {
                monkeyMadness.stage(player) == STAGE_COMPLETE || monkeyMadness.stage(player) >= STAGE_ALLIANCE -> {
                    king(neutral, "Our alliance stands, monkey of Karamja. Return to your people and tell them Ape Atoll remembers its friends.")
                    chatPlayer(neutral, "I will, your majesty.")
                }
                monkeyMadness.stage(player) < STAGE_MONKEY -> king(neutral, "Who let you in here? Guards!")
                else -> when (monkeyMadness.awowogeiStage.get(player)) {
                    AWOWOGEI_NOT_MET -> proposal()
                    AWOWOGEI_TASK_GIVEN -> if (player.inv.contains(MONKEY_IN_BACKPACK)) monkeyDelivered() else reminder()
                    else -> king(neutral, "Our alliance stands. Go now.")
                }
            }
        }
    }

    private suspend fun Dialogue.king(mood: MesAnimType, text: String) {
        chatNpcSpecific("Awowogei", AWOWOGEI_HEAD, mood, text)
    }

    private suspend fun Dialogue.proposal() {
        king(neutral, "Kruk tells me you come from Karamja. Speak, then. What do the monkeys of Karamja want with Ape Atoll?")
        chatPlayer(neutral, "Friendship, your majesty. The humans of Karamja grow bolder every year. My people would be stronger with allies across the water.")
        king(neutral, "An alliance. Interesting. The humans trouble us too; they keep our cousins in cages in a place they call Ardougne.")
        king(neutral, "Words are cheap, monkey of Karamja. If you want my friendship, prove it. Go to the human city of Ardougne and free one of my people from its zoo. Bring the monkey here.")
        chatPlayer(neutral, "It will be done.")
        king(neutral, "See that it is. And do not use human magic on the way back; my people fear it, and a frightened monkey runs.")
        monkeyMadness.awowogeiStage.set(player, AWOWOGEI_TASK_GIVEN)
        with(cards) { access.show(ChapterCards.CHAPTER_THREE) }
    }

    private suspend fun Dialogue.reminder() {
        king(neutral, "You return without my subject. Go to Ardougne. Free a monkey from the zoo. Bring it here. Was any part of that unclear?")
        chatPlayer(neutral, "No, your majesty.")
    }

    private suspend fun Dialogue.monkeyDelivered() {
        chatPlayer(neutral, "Your majesty, I have brought one of your people from the Ardougne Zoo.")
        access.invDel(player.inv, MONKEY_IN_BACKPACK)
        mesbox("The monkey climbs out of your backpack and runs to the foot of the throne, chattering with joy.")
        access.soundSynth(MonkeyMadness.SOUND_MONKEY_CALLS)
        king(happy, "So you have. Welcome home, little one.")
        king(neutral, "You have done what I asked, monkey of Karamja. I will consider your alliance. Return to your people; you will have my answer when I am ready to give it.")
        chatPlayer(neutral, "Thank you, your majesty.")
        monkeyMadness.awowogeiStage.set(player, AWOWOGEI_MONKEY_FREED)
        monkeyMadness.advanceTo(access, STAGE_ALLIANCE)
        monkeyMadness.syncVars(player)
    }
}
