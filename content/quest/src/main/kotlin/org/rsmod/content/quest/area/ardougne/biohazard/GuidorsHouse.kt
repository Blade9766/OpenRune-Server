package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.QuestDoors
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.PLAGUE_SAMPLE
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GOT_TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.STAGE_GUIDOR_TESTED
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.TOUCH_PAPER
import org.rsmod.content.quest.area.ardougne.biohazard.BiohazardQuest.Companion.VIALS
import org.rsmod.content.quest.area.ardougne.wearingPriestGown
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Guidor's house in the south-east corner of Varrock. His wife Julie will only let a priest in
 * to see him; Guidor, a man of science, runs Elena's samples through the touch paper and finds
 * nothing at all.
 */
class GuidorsHouse
@Inject
constructor(
    private val biohazard: BiohazardQuest,
    private val doors: QuestDoors,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(JULIE) { startDialogue(it.npc) { julie() } }
        onOpNpc1(GUIDOR) { startDialogue(it.npc) { guidor() } }
        onOpLoc1(BEDROOM_DOOR) { bedroomDoor(it.loc) }
    }

    private suspend fun Dialogue.julie() {
        val stage = biohazard.stage(player)
        when {
            biohazard.quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "Hello.")
                chatNpc(worried, "Oh hello, I can't chat now. I have to keep an eye on my husband. He's very ill!")
                chatPlayer(sad, "I'm sorry to hear that!")
            }
            stage < STAGE_GOT_TOUCH_PAPER -> chatNpc(worried, "Oh dear! Oh dear! I don't have time to chat!")
            stage == STAGE_GOT_TOUCH_PAPER && player.wearingPriestGown() -> {
                chatNpc(happy, "A priest! thank goodness! My husband is very ill! Perhaps you could read him his last rites?")
                chatPlayer(neutral, "I'll see what I can do.")
                rememberJulie()
            }
            stage == STAGE_GOT_TOUCH_PAPER -> {
                chatPlayer(neutral, "Hello. I'm a friend of Elena, here to see Guidor.")
                chatNpc(sad, "I'm afraid that Guidor is not long for this world! So I'm not letting people see him now.")
                chatPlayer(sad, "I'm really sorry to hear about Guidor.")
                chatPlayer(neutral, "But I do have some very important business to attend to!")
                chatNpc(angry, "You heartless rogue! What could be more important than Guidor's life? A life spent well, if not always wisely... I just hope that Saradomin shows mercy on his soul!")
                chatPlayer(quiz, "Guidor is a religious man?")
                chatNpc(neutral, "Oh goodness no! But I am! If only I could get him to see a priest!")
                chatPlayer(quiz, "A priest? Hmmm...")
                rememberJulie()
            }
            else -> {
                chatPlayer(happy, "Hello again.")
                chatNpc(worried, "Hello there. I fear Guidor may not be long for this world!")
            }
        }
    }

    private fun Dialogue.rememberJulie() {
        if (!biohazard.metJulie.get(player)) {
            biohazard.metJulie.set(player, true)
            biohazard.syncVars(player)
        }
    }

    private suspend fun ProtectedAccess.bedroomDoor(door: BoundLocInfo) {
        arriveDelay()
        faceLoc(door)
        val inside = player.coords.x > door.coords.x
        val stage = biohazard.stage(player)
        when {
            inside || stage >= STAGE_GUIDOR_TESTED -> doors.open(this, door, BEDROOM_DOOR_OPEN)
            stage < STAGE_GOT_TOUCH_PAPER ->
                startDialogue { chatPlayer(neutral, "That's someone's bedroom. I'm not going in there without a reason.") }
            player.wearingPriestGown() -> doors.open(this, door, BEDROOM_DOOR_OPEN)
            else ->
                startDialogue {
                    chatNpcSpecific("Julie", JULIE, worried, "Please leave my husband alone. He's very sick, and I don't want anyone bothering him.")
                    chatPlayer(sad, "I'm sorry to hear that. Is there anything I can do?")
                    chatNpcSpecific("Julie", JULIE, neutral, "Thank you, but I just want him to see a priest.")
                    chatPlayer(quiz, "A priest? Hmmm...")
                    rememberJulie()
                }
        }
    }

    private suspend fun Dialogue.guidor() {
        val stage = biohazard.stage(player)
        when {
            biohazard.quest.isQuestCompleted(player) -> {
                chatPlayer(happy, "Hello again Guidor. How are you doing?")
                chatNpc(neutral, "I'm hanging in there.")
                chatPlayer(happy, "Good for you.")
            }
            stage == STAGE_GUIDOR_TESTED || stage > STAGE_GUIDOR_TESTED -> {
                chatPlayer(happy, "Hello again Guidor.")
                chatNpc(confused, "Well, hello traveller. I still can't understand why they would lie about the plague.")
                chatPlayer(quiz, "It's strange, anyway how are you doing?")
                chatNpc(neutral, "I'm hanging in there.")
                chatPlayer(happy, "Good for you.")
            }
            stage < STAGE_GOT_TOUCH_PAPER || !player.wearingPriestGown() ->
                chatNpc(angry, "I don't really want any visitors just now.")
            else -> testSamples()
        }
    }

    private suspend fun Dialogue.testSamples() {
        chatPlayer(neutral, "Hello, you must be Guidor. I understand that you are unwell.")
        chatNpc(angry, "Is my wife asking priests to visit me now? I'm a man of science for god's sake. I know she means well but it's only a little cough!")
        chatPlayer(neutral, "She made out it was something more serious.")
        chatNpc(neutral, "Well it's not killed me yet. So what do you want?")
        when (
            choice2(
                "I've come to ask your assistance in stopping a plague.", 1,
                "I was just going to bless your room and I've done that now.", 2,
            )
        ) {
            1 -> {
                chatPlayer(neutral, "I've come to ask your assistance in stopping a plague.")
                chatNpc(quiz, "You mean the plague of West Ardougne?")
                chatPlayer(neutral, "That's the one. I have a sample here from your former student, Elena.")
                chatNpc(quiz, "Elena eh?")
                chatPlayer(neutral, "Yes, she wants you to analyse it. You might be the only one who can help.")
                chatNpc(happy, "Right then, sounds like we'd better get to work!")
                chatPlayer(neutral, "I have the plague sample.")
                chatNpc(neutral, "Now I'll be needing some liquid honey, some sulphuric broline, and then...")
                chatPlayer(quiz, "... some ethenea?")
                chatNpc(happy, "Indeed!")
                when {
                    VIALS.any { !player.inv.contains(it) } ->
                        chatNpc(neutral, "Look, I need all three reagents to test the plague sample. Come back when you've got them.")
                    !player.inv.contains(TOUCH_PAPER) ->
                        chatNpc(sad, "Oh. You don't have any touch paper. I won't be able to help after all.")
                    !player.inv.contains(PLAGUE_SAMPLE) ->
                        chatNpc(neutral, "Seems like you don't actually have the plague sample. It's a long way to come empty handed... and quite a long way back too.")
                    else -> {
                        for (item in VIALS + TOUCH_PAPER + PLAGUE_SAMPLE) {
                            access.invDel(player.inv, item)
                        }
                        access.soundSynth(TEST_SOUND)
                        chatNpc(confused, "Now I'll just apply these to the sample and... I don't get it... the touch paper has remained the same.")
                        chatPlayer(neutral, "That's why Elena wanted you to do it, because she wasn't sure what was happening.")
                        chatNpc(neutral, "Well that's just it, nothing has happened. I don't know what this sample is, but it certainly isn't toxic.")
                        chatPlayer(quiz, "So what about the plague?")
                        chatNpc(shocked, "This result can only mean one thing... there is no plague. It seems that someone has been lying about all of it. The only question is... why?")
                        chatPlayer(worried, "Well this is worrying. I'd better go and tell Elena right away.")
                        biohazard.advanceTo(access, STAGE_GUIDOR_TESTED)
                    }
                }
            }
            2 -> {
                chatPlayer(neutral, "I was just going to bless your room and I've done that now.")
                chatNpc(neutral, "Oh. Goodbye then.")
            }
        }
    }

    private companion object {
        const val JULIE = "npc.guidors_wife"
        const val GUIDOR = "npc.guidor"
        const val BEDROOM_DOOR = "loc.guidordoor"
        const val BEDROOM_DOOR_OPEN = "loc.guidordooropen"
        const val TEST_SOUND = "synth.bubbling_vials"
    }
}
