package org.rsmod.content.quest.area.tirannwn.mourningsend

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpHeldU
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.burthorpe.eadgarsruse.npcs.TegidEadgarsRuse
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BLOODY_TOP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BUCKET
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.BUCKET_OF_WATER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.LETTER
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.MOURNER_TOP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.RIPPED_LEGS
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.SOAP
import org.rsmod.content.quest.area.tirannwn.mourningsend.MourningsEndQuest.Companion.STAGE_BRIEFED
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cleaning the bloody mourner top, and the clothing items' own options.
 *
 * Tegid, washing his robes by the lake in Taverley, boasts of his soap while the player is after
 * a disguise (`varbit.mourning_tegid_chat`); after that the soap sits on top of his laundry basket
 * for the taking, whenever the player does not already own one. There is no Thieving roll: the
 * quest's level 50 Thieving is checked when it starts. Soap on the bloody top with a bucket of
 * water in the pack scrubs it clean in one transaction (the bucket comes back empty and the soap
 * is kept).
 */
class MournerClothing @Inject constructor(private val mourning: MourningsEndQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(TEGID) { startDialogue(it.npc) { tegid() } }
        onOpLoc1(LAUNDRY_BASKET) { searchBasket() }
        onOpHeldU(SOAP, BLOODY_TOP) { scrub() }
        onOpHeldU(BUCKET_OF_WATER, BLOODY_TOP) { mes("Just wetting the top will not remove the bloodstains.") }
        onOpHeld1(BLOODY_TOP) { mes("The top is coated in blood from your fight with the mourner. It will need cleaning before it can be used.") }
        onOpHeld1(RIPPED_LEGS) { mes("The trousers were ripped in your fight with the mourner. They will need mending before they can be used.") }
        onOpHeld1(LETTER) { readLetter() }
    }

    private fun wantsSoap(access: ProtectedAccess): Boolean {
        val p = access.player
        return mourning.stage(p) >= STAGE_BRIEFED || mourning.isComplete(p)
    }

    private suspend fun Dialogue.tegid() {
        chatPlayer(neutral, "So, you're doing laundry, eh?")
        chatNpc(neutral, "Yes. What's it to you?")
        chatPlayer(happy, "Nice day for it.")
        chatNpc(neutral, "I suppose it is.")
        if (with(TegidEadgarsRuse) { askForRobe() }) {
            return
        }
        if (!wantsSoap(access) || access.ownsAnywhere(MOURNER_TOP) && !access.ownsAnywhere(BLOODY_TOP)) {
            return
        }
        chatPlayer(quiz, "Do you know any way to get rid of blood stains?")
        chatNpc(neutral, "Blood stains, is it? The soap I use will shift almost any stain.")
        chatPlayer(happy, "That sounds like just what I need! Could I use some?")
        chatNpc(neutral, "No. I'm nearly out of it and I've still got plenty to wash.")
        chatPlayer(quiz, "Then where could I buy some?")
        MourningsEndQuest.setVarBit(player, "varbit.mourning_tegid_chat", 1)
        chatNpc(neutral, "You can't. I make it myself, to my own secret recipe.")
        if (QuestRequirements.hasCompleted(player, EADGARS_RUSE)) {
            chatPlayer(angry, "Could you be any less helpful?")
            chatNpc(angry, "Like you were, helping yourself to the robes on my washing line? If you know a way I can be less helpful, tell me and I'll give it a try.")
        }
    }

    private suspend fun ProtectedAccess.searchBasket() {
        arriveDelay()
        val soapOnTop = player.tegidChat == 1 && wantsSoap(this) && !ownsAnywhere(SOAP)
        if (!soapOnTop) {
            mesbox("You search the laundry basket... It's full of dirty robes.")
            return
        }
        mesbox("You search the laundry basket... It's full of dirty robes. On top you see a bar of soap.")
        if (!choice2("Steal the soap.", true, "Leave the soap.", false)) {
            mesbox("You leave the soap where it is.")
            return
        }
        if (inv.freeSpace() < 1) {
            mesbox("You don't have enough room to take the soap.")
            return
        }
        if (ownsAnywhere(SOAP) || invAdd(inv, SOAP).failure) {
            return
        }
        objbox(SOAP, "You wait until Tegid is looking the other way... and steal the soap!")
    }

    private suspend fun ProtectedAccess.scrub() {
        if (!inv.contains(BUCKET_OF_WATER)) {
            mes("The dry soap has no effect.")
            return
        }
        if (!swap(listOf(BLOODY_TOP to 1, BUCKET_OF_WATER to 1), listOf(MOURNER_TOP to 1, BUCKET to 1))) {
            return
        }
        objbox(MOURNER_TOP, "You give the top a good scrub with the soap and rinse away the bloody suds with water.")
    }

    private suspend fun ProtectedAccess.readLetter() {
        mesbox("To whom it may concern: the bearer has met every requirement for entry into the Death Guard. <col=800000>Death Guard HQ</col>")
    }

    companion object {
        const val TEGID = "npc.eadgar_druid_washing"
        const val LAUNDRY_BASKET = "loc.eadgar_laundry_basket"
        const val EADGARS_RUSE = "quest_eadgarsruse"
    }
}
