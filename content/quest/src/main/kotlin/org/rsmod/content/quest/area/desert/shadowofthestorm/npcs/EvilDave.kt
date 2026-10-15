package org.rsmod.content.quest.area.desert.shadowofthestorm.npcs

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.BLACK_ITEMS_REQ
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE_PASSAGE_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE_PORTAL_SPAWN
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.DAVE_SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.IN_UZER
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.RECRUITED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.SIGIL
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_INFILTRATED
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.STAGE_RECRUITING
import org.rsmod.content.quest.area.desert.shadowofthestorm.SotsItems
import org.rsmod.content.quest.area.desert.shadowofthestorm.daveConvinced
import org.rsmod.content.quest.area.desert.shadowofthestorm.reenAtUzer
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Evil Dave: the cult's doorman before the first ritual, and the only one of the four casters who
 * survives it. He is not brave, and the player has to make going back sound safer than staying in
 * the passage.
 */
@Singleton
class EvilDave
@Inject
constructor(private val sots: ShadowOfTheStormQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        for (name in listOf(DAVE_PORTAL_SPAWN, DAVE_PASSAGE_SPAWN, DAVE, DAVE_SIGIL)) {
            onOpNpc1(name) { startDialogue(it.npc) { dave() } }
        }
    }

    private suspend fun Dialogue.dave() {
        val stage = sots.stage(player)
        when {
            sots.isComplete(player) -> afterQuest()
            stage >= STAGE_RECRUITING && player.daveConvinced < RECRUITED -> inThePassage()
            stage >= STAGE_INFILTRATED -> alreadyIn()
            stage >= STAGE_BRIEFED -> doorman()
            else -> suspicious()
        }
    }

    private suspend fun Dialogue.suspicious() {
        chatNpc(shifty, "Nothing to see down here. Go and look at the pottery.")
    }

    private suspend fun Dialogue.doorman() {
        chatNpc(shifty, "You lost, or are you one of us?")
        val join =
            choice2(
                "I want to join your group.",
                true,
                "I'm just looking around.",
                false,
            )
        if (!join) {
            chatPlayer(neutral, "I'm just looking around.")
            chatNpc(neutral, "Then look somewhere else.")
            return
        }
        chatPlayer(neutral, "I want to join your group.")
        chatNpc(quiz, "Yeah? And what makes you think you'd fit in?")
        chatPlayer(neutral, "I'm evil!")
        if (!SotsItems.dressedInBlack(player)) {
            chatNpc(laugh, "In that? You look like someone's uncle.")
            chatNpc(
                neutral,
                "$BLACK_ITEMS_REQ bits of black, minimum. That's the rule. I didn't make it up.",
            )
            return
        }
        if (!SotsItems.carriesDyedSilverlight(player)) {
            chatNpc(neutral, "And lose the shiny sword. Bring something that doesn't glow.")
            return
        }
        chatNpc(happy, "Huh. You know, you actually do look the part.")
        chatNpc(
            neutral,
            "Go on through then. Denath's by the throne. Don't stare at him, he hates that.",
        )
        sots.advanceTo(access, STAGE_INFILTRATED)
        // Father Reen leaves Al Kharid for the ruins once someone is inside the cult; the cache
        // stops drawing his Al Kharid spawn past this stage.
        player.reenAtUzer = IN_UZER
        access.mes("The portal behind Evil Dave opens for you.")
    }

    private suspend fun Dialogue.alreadyIn() {
        chatNpc(neutral, "Straight through. Denath's waiting.")
    }

    /**
     * After the circle breaks, Dave is in the passage with Eric's sigil in his pocket and no
     * intention of going back down. He has to be talked round; he hands the sigil over either way.
     */
    private suspend fun Dialogue.inThePassage() {
        chatNpc(shocked, "Don't. Don't talk to me. Don't even look at me.")
        chatPlayer(neutral, "Dave.")
        chatNpc(
            worried,
            "Two years. Two years I've been chanting for a bloke who turned out to be the thing " +
                "we were chanting at.",
        )
        chatPlayer(neutral, "You've got to get back to the throne room!")
        chatNpc(shocked, "Are you MAD?")
        val argument =
            choice3(
                "He is still out there, and he knows your face.",
                Argument.Threat,
                "Eric and Tanya are dead. Don't waste that.",
                Argument.Guilt,
                "Nothing. Never mind.",
                Argument.Drop,
            )
        when (argument) {
            Argument.Drop -> {
                chatPlayer(neutral, "Nothing. Never mind.")
                chatNpc(worried, "Good. Good, that's the right answer.")
                return
            }
            Argument.Threat -> {
                chatPlayer(neutral, "He is still out there, and he knows your face.")
                chatNpc(worried, "...he does, doesn't he.")
            }
            Argument.Guilt -> {
                chatPlayer(neutral, "Eric and Tanya are dead. Don't waste that.")
                chatNpc(sad, "I picked Eric's sigil up off the floor. I don't know why I did that.")
            }
        }
        chatNpc(
            neutral,
            "Fine. FINE. But you're standing between me and it, and if it so much as looks at me " +
                "I'm going up those stairs and not stopping until Gielinor runs out.",
        )
        player.daveConvinced = RECRUITED
        if (access.inv.hasFreeSpace()) {
            access.invAdd(access.inv, SIGIL)
            access.objbox(SIGIL, "Evil Dave hands you Eric's sigil.")
        } else {
            access.mes("Evil Dave has Eric's sigil for you, but your hands are full.")
        }
        access.mes("Evil Dave slouches off towards the portal.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatNpc(happy, "Did you see me? I held my bit of the circle the whole time.")
        chatPlayer(laugh, "You did.")
        chatNpc(neutral, "I'm going home. Mum's doing a roast.")
    }

    private enum class Argument {
        Threat,
        Guilt,
        Drop,
    }
}
