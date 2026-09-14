package org.rsmod.content.quest.area.paterdomus.priestinperil

import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.DREZEL_CELL
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_AGREED_TO_KILL_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_KILLED_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_STARTED
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The large doors of Paterdomus. Until King Roald has sent the player back in a fury, the monks
 * keep them barred and only answer a knock, pretending to be Drezel; after that the doors open.
 *
 * The voices behind the door use Drezel's cell npc for their chathead: it has no visible form
 * before the player meets him, so the chatbox shows no face.
 */
class TempleDoors
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val doors: PaterdomusDoors,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(DOOR_LEFT) { useDoor() }
        onOpLoc1(DOOR_RIGHT) { useDoor() }
    }

    private suspend fun ProtectedAccess.useDoor() {
        arriveDelay()
        val inside = coords.x > PaterdomusCoords.TEMPLE_DOOR_X
        when (priestInPeril.stage(player)) {
            0 -> if (inside) enter(inside) else startDialogue { nobodyHome() }
            STAGE_STARTED -> if (inside) enter(inside) else startDialogue { firstKnock() }
            STAGE_AGREED_TO_KILL_DOG -> if (inside) enter(inside) else startDialogue { dogNotDealtWith() }
            STAGE_KILLED_DOG -> if (inside) enter(inside) else startDialogue { dogDealtWith() }
            else -> enter(inside)
        }
    }

    private fun ProtectedAccess.enter(inside: Boolean) {
        priestInPeril.syncHoodedMonk(player)
        val z = coords.z.coerceIn(PaterdomusCoords.TEMPLE_DOOR_RIGHT.z, PaterdomusCoords.TEMPLE_DOOR_LEFT.z)
        val x = if (inside) PaterdomusCoords.TEMPLE_DOOR_X else PaterdomusCoords.TEMPLE_DOOR_X + 1
        doors.walkThrough(
            this,
            listOf(PaterdomusCoords.TEMPLE_DOOR_LEFT to DOOR_LEFT, PaterdomusCoords.TEMPLE_DOOR_RIGHT to DOOR_RIGHT),
            CoordGrid(x, z, 0),
            OPEN_SOUND,
            CLOSE_SOUND,
        )
    }

    private suspend fun Dialogue.knock() {
        access.soundSynth(KNOCK_SOUND)
        mesbox("You knock at the door...")
    }

    private suspend fun Dialogue.voice(mood: MesAnimType, text: String) {
        chatNpcSpecific(VOICE, DREZEL_CELL, mood, text)
    }

    private suspend fun Dialogue.nobodyHome() {
        knock()
        delay(KNOCK_PAUSE)
        mesbox("Doesn't seem like anyone's home.")
    }

    private suspend fun Dialogue.firstKnock() {
        knock()
        voice(angry, "Who are you and what do you want?")
        chatPlayer(confused, "Er...")
        var askedSomethingElse = false
        while (true) {
            val leave = if (askedSomethingElse) "Actually, never mind." else "Nothing. Never mind."
            when (
                choice5(
                    "King Roald sent me to check on Drezel.",
                    ROALD,
                    "Hi, I just moved in next door.",
                    NEIGHBOUR,
                    "I hear this place is of historic interest.",
                    HISTORY,
                    "The council sent me to check your pipes.",
                    PIPES,
                    leave,
                    LEAVE,
                )
            ) {
                ROALD -> {
                    roaldSentMe()
                    return
                }
                NEIGHBOUR -> {
                    chatPlayer(happy, "Hi, I just moved in next door.")
                    voice(confused, "What?")
                    chatPlayer(happy, "Yeah, and I was just wondering if I could borrow a cup of coffee?")
                    voice(angry, "Next door? Coffee? What are you on about? Who are you?")
                }
                HISTORY -> {
                    chatPlayer(happy, "I hear this place is of historic interest.")
                    voice(neutral, "Right... And?")
                    chatPlayer(
                        happy,
                        "Can I come in and have a wander around? Possibly look at some antiques or " +
                            "buy something from your gift shop?",
                    )
                    voice(angry, "Gift shop? What are you on about? This isn't a museum! Clear off!")
                }
                PIPES -> {
                    chatPlayer(neutral, "The council sent me to check your pipes.")
                    voice(angry, "Pipes? We don't have any pipes! Now get lost!")
                }
                else -> {
                    chatPlayer(neutral, leave)
                    return
                }
            }
            askedSomethingElse = true
        }
    }

    private suspend fun Dialogue.roaldSentMe() {
        chatPlayer(neutral, "King Roald sent me to check on Drezel.")
        voice(shifty, "Drezel? Right... I, er... Just give me a moment.")
        mesbox("You hear someone moving around followed by what sounds like panicked muttering.")
        voice(worried, "Okay, he's just coming! Wait a second!")
        voice(happy, "Hello, my name is Drevil.")
        voice(angry, "Drezel!")
        voice(worried, "I mean Drezel. How can I help?")
        chatPlayer(quiz, "Er... Is everything okay in there?")
        voice(happy, "Yes, totally fine. Now, what can I do for you?")
        chatPlayer(
            neutral,
            "Well, the King hadn't heard from you for a few days. He sent me to make sure " +
                "everything's okay.",
        )
        voice(shifty, "I see... And, uh, what would you do if everything wasn't okay?")
        chatPlayer(neutral, "I'm not sure. Ask you what help you need I suppose.")
        voice(neutral, "Ah, good, well, I don't think...")
        voice(shocked, "Hey... The dog!")
        voice(happy, "Ah, actually there is something I could use some help with. Will you do me a favour, adventurer?")
        chatPlayer(quiz, "What kind of favour?")
        voice(
            worried,
            "Well, there's this horrible big... dog-like thing that's moved into the mausoleum " +
                "outside. It's causing all kinds of problems. Could you get rid of it for me?",
        )
        chatPlayer(
            shifty,
            "I don't know... Something about all this seems a bit suspicious... I think I'd better " +
                "go and check in with the King.",
        )
        voice(
            worried,
            "No, no, there's no need for that. He's a very busy man! Besides, you said yourself " +
                "that he sent you here to help me, right?",
        )
        chatPlayer(shifty, "Right...")
        priestInPeril.advanceTo(access, STAGE_AGREED_TO_KILL_DOG)
        voice(happy, "Then please, deal with that dog for me.")
        chatPlayer(shifty, "Hmm...")
    }

    private suspend fun Dialogue.dogNotDealtWith() {
        knock()
        voice(quiz, "Hello? Is that you again, adventurer? Have you dealt with that dog yet?")
        chatPlayer(shifty, "Not yet...")
        voice(happy, "But you're going to, right? For good old Delzig?")
        voice(angry, "Drezel!")
        voice(worried, "Sorry, for good old Drezel, right?")
        chatPlayer(shifty, "Hmm...")
    }

    private suspend fun Dialogue.dogDealtWith() {
        knock()
        if (player.returnedToFakeDrezel) {
            voice(
                happy,
                "Is that you, adventurer? Once again, thank you so much for dealing with that dog. " +
                    "It's really... quite something.",
            )
            chatPlayer(shifty, "Don't mention it... Now, I guess I'd better report back to the King.")
            return
        }
        voice(quiz, "Is that you, adventurer? Have you dealt with that dog?")
        chatPlayer(neutral, "Yes, it's done.")
        voice(happy, "Really? Well... thank you! That's really... quite something.")
        voice(laugh, "Ha! Brilliant! Good work, adventurer.")
        chatPlayer(shifty, "Don't mention it... Is there anything else you need?")
        voice(happy, "I think it's safe to say that you've done more than enough, adventurer.")
        player.returnedToFakeDrezel = true
        chatPlayer(shifty, "Okay... I guess I'd better go and let the King know.")
    }

    private companion object {
        const val DOOR_LEFT = "loc.priestperiltempledoorl"
        const val DOOR_RIGHT = "loc.priestperiltempledoorr"
        const val VOICE = "Mysterious Voice"

        const val KNOCK_SOUND = "synth.knock_knock"
        const val OPEN_SOUND = "synth.big_wooden_door_open"
        const val CLOSE_SOUND = "synth.big_wooden_door_close"
        const val KNOCK_PAUSE = 2

        const val ROALD = 1
        const val NEIGHBOUR = 2
        const val HISTORY = 3
        const val PIPES = 4
        const val LEAVE = 5
    }
}
