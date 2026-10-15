package org.rsmod.content.quest.area.gnomestronghold.grandtree

import dev.openrune.types.MesAnimType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.FEMI
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.FEMI_FEE
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.GNOME_GUARD
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ESCAPE_BY_GLIDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_HAS_LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_KING_DOUBTS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.STAGE_ON_KARAMJA
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The stronghold's south gate and Femi's cart outside it. While Glough has the gate guarded the
 * guards turn the player back; Femi will smuggle them in, free if they once helped her with her
 * barrel and for a fee otherwise. At any other time the gate lets the player through and shuts
 * behind them.
 */
class StrongholdGate
@Inject
constructor(
    private val grandTree: GrandTreeQuest,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(GATE) { gate(it.loc, it.type) }
        onOpNpc1(FEMI) { startDialogue(it.npc) { femi() } }
    }

    private suspend fun ProtectedAccess.gate(loc: BoundLocInfo, type: ObjectServerType) {
        val stage = grandTree.stage(player)
        val outside = player.coords.z < loc.coords.z
        when {
            stage == STAGE_ESCAPE_BY_GLIDER -> startDialogue { halt() }
            outside && stage in STAGE_ON_KARAMJA..STAGE_HAS_LUMBER_ORDER -> startDialogue { refused() }
            else -> with(passages) { walkThrough(loc, type) }
        }
    }

    private suspend fun Dialogue.guard(mood: MesAnimType, text: String) {
        chatNpcSpecific(GrandTree.GUARD_NAME, GNOME_GUARD, mood, text)
    }

    private suspend fun Dialogue.halt() {
        guard(angry, "Halt, human!")
        chatPlayer(confused, "What? Why?")
        guard(neutral, "By order of the head tree guardian! You can not leave!")
        chatPlayer(angry, "That's crazy! Why?!")
        guard(neutral, "Humans are planning to attack our stronghold. You could be a spy!")
        chatPlayer(angry, "That's ridiculous!")
        guard(sad, "Maybe, but that's the orders, I'm sorry.")
    }

    private suspend fun Dialogue.refused() {
        guard(neutral, "I'm afraid that we have orders not to let you in.")
        chatPlayer(quiz, "Orders from who?")
        guard(neutral, "The head tree guardian, he says you're a spy!")
        chatPlayer(angry, "Glough!")
        guard(neutral, "I'm sorry but you'll have to leave.")
    }

    private suspend fun Dialogue.femi() {
        val stage = grandTree.stage(player)
        val outside = player.coords.z < GATE_Z
        when {
            outside && stage in STAGE_ON_KARAMJA..STAGE_HAS_LUMBER_ORDER -> sneakIn()
            grandTree.femiSneakedIn.get(player) && stage in STAGE_HAS_LUMBER_ORDER..STAGE_KING_DOUBTS -> {
                chatNpc(neutral, "Now, to get this lot to the Grand Tree!")
                when (choice2("Can I help?", 1, "I'd better get going!", 2)) {
                    1 -> {
                        chatPlayer(happy, "Can I help?")
                        chatNpc(happy, "No, you're okay, traveller. I can manage from here.")
                    }
                    2 -> chatPlayer(neutral, "I'd better get going!")
                }
            }
            grandTree.femiHelped.get(player) -> {
                chatPlayer(happy, "Hello Femi.")
                chatNpc(happy, "Hello again, traveller. Thanks again for helping me with that barrel.")
            }
            else -> barrel()
        }
    }

    private suspend fun Dialogue.barrel() {
        chatNpc(worried, "Excuse me, traveller. Could you give me a hand? This barrel is far too heavy for me to lift onto the cart.")
        when (choice2("Sure, let me help.", 1, "Sorry, I'm busy.", 2)) {
            1 -> {
                chatPlayer(happy, "Sure, let me help.")
                mesbox("You heave the barrel up onto the back of Femi's cart.")
                grandTree.femiHelped.set(player, true)
                chatNpc(happy, "Thanks a lot, traveller! I won't forget that.")
            }
            2 -> {
                chatPlayer(neutral, "Sorry, I'm busy.")
                chatNpc(sad, "Well, thanks for nothing.")
            }
        }
    }

    private suspend fun Dialogue.sneakIn() {
        if (grandTree.femiHelped.get(player)) {
            chatPlayer(angry, "I can't believe they won't let me in!")
            chatNpc(neutral, "I don't believe all this rubbish about an invasion. If mankind wanted to, they could have invaded before now.")
            chatPlayer(neutral, "I really need to see King Narnode. Could you help sneak me in?")
            chatNpc(neutral, "Well, as you helped me I suppose I could. We'll have to be careful. If I get caught I'll be in the cage!")
            chatPlayer(quiz, "OK, what should I do?")
            chatNpc(neutral, "Jump in the back of the cart. It's a food delivery, we should be fine.")
            access.smuggle()
            return
        }
        chatNpc(angry, "Why should I help you, you wouldn't help me!")
        chatPlayer(worried, "Erm I know, but this is an emergency!")
        chatNpc(neutral, "So was lifting that barrel! Tell you what, call it a round $FEMI_FEE gold pieces.")
        chatPlayer(confused, "$FEMI_FEE gold pieces!")
        chatNpc(neutral, "That's right, $FEMI_FEE and I'll sneak you in.")
        when (choice2("No chance!", 1, "Ok then, here you go.", 2)) {
            1 -> chatPlayer(angry, "No chance!")
            2 -> {
                chatPlayer(neutral, "OK then, here you go.")
                if (!access.invTakeFee(FEMI_FEE)) {
                    chatNpc(neutral, "You don't even have $FEMI_FEE coins! Come back when you do.")
                    return
                }
                mesbox("You give Femi $FEMI_FEE coins.")
                chatNpc(neutral, "Alright, jump in the back of the cart. It's a food delivery, we should be fine.")
                access.smuggle()
            }
        }
    }

    private suspend fun ProtectedAccess.smuggle() {
        mesbox("Femi sneaks you inside the stronghold.")
        fadeTeleport(GrandTree.INSIDE_GATE)
        grandTree.femiSneakedIn.set(player, true)
        startDialogue {
            chatNpcSpecific("Femi", FEMI, happy, "Okay, traveller, you'd better get going.")
            chatPlayer(happy, "Thanks again!")
            chatNpcSpecific("Femi", FEMI, happy, "That's okay, all the best.")
        }
    }

    private companion object {
        const val GATE = "loc.gnome_areagate"
        const val GATE_Z = 3383
    }
}
