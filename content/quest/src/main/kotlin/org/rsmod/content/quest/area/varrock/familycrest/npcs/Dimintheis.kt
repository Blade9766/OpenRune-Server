package org.rsmod.content.quest.area.varrock.familycrest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.ASSEMBLE_SOUND
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.DIMINTHEIS
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.FAMILY_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.RECOMMENDED_COMBAT
import org.rsmod.content.quest.area.varrock.familycrest.Gauntlets
import org.rsmod.content.quest.area.varrock.familycrest.gauntletsKind
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The head of the Fitzharmon family, in the fenced house above the Fancy Clothes Store in
 * south-east Varrock. He starts the quest, takes the reassembled crest, and replaces the reward
 * gauntlets when they are lost, still carrying whichever enchantment they had.
 */
class Dimintheis @Inject constructor(private val familyCrest: FamilyCrestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(DIMINTHEIS) { startDialogue(it.npc) { dimintheis() } }
    }

    private suspend fun Dialogue.dimintheis() {
        when {
            familyCrest.isComplete(player) -> afterQuest()
            familyCrest.isStarted(player) -> inProgress()
            else -> notStarted()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatNpc(sad, "Oh, woe is my family.")
        val asked =
            choice2(
                "What's wrong with your family?",
                true,
                "That's nice. Goodbye.",
                false,
                title = "What would you like to say?",
            )
        if (!asked) {
            chatPlayer(neutral, "That's nice. Goodbye.")
            return
        }
        chatPlayer(quiz, "What's wrong with your family?")
        chatNpc(
            sad,
            "My name is Dimintheis Fitzharmon. My family were nobles of Varrock once, and our " +
                "crest was known from here to Falador.",
        )
        chatNpc(
            sad,
            "Then my three sons fell to squabbling over who should inherit it. They broke it in " +
                "three, took a piece each and went their separate ways.",
        )
        chatNpc(
            sad,
            "Without a whole crest the King will not recognise our name. My sons will not speak " +
                "to me, but perhaps they would speak to a stranger.",
        )
        if (player.combatLevel < RECOMMENDED_COMBAT) {
            mesbox(
                "Before starting this quest, be aware that your combat level is lower than the " +
                    "recommended level of $RECOMMENDED_COMBAT.",
            )
        }
        val accept =
            choice2(
                "Yes, I'll find your sons.",
                true,
                "No, family squabbles aren't my business.",
                false,
                title = "Start the Family Crest quest?",
            )
        if (!accept) {
            chatPlayer(neutral, "No, family squabbles aren't my business.")
            chatNpc(sad, "I understand. Few care for an old man's troubles.")
            return
        }
        chatPlayer(neutral, "Yes, I'll find your sons.")
        familyCrest.start(access)
        chatNpc(
            happy,
            "Bless you. Caleb, the eldest, cooks somewhere over in Catherby. Find him first; he " +
                "always knew where his brothers ran off to.",
        )
    }

    private suspend fun Dialogue.inProgress() {
        if (access.inv.contains(FAMILY_CREST)) {
            handInCrest()
            return
        }
        chatNpc(quiz, "Have you found anything of my crest?")
        chatPlayer(neutral, "Not the whole of it yet.")
        chatNpc(
            neutral,
            "Caleb cooks in Catherby, Avan digs for gold out at Al Kharid, and Johnathon drinks " +
                "his inheritance at the Jolly Boar. Bring me all three pieces together.",
        )
    }

    private suspend fun Dialogue.handInCrest() {
        chatPlayer(happy, "I have your family crest.")
        chatNpc(shocked, "The whole of it? After all these years?")
        if (access.invDel(access.inv, FAMILY_CREST).failure) {
            return
        }
        access.soundSynth(ASSEMBLE_SOUND)
        chatNpc(
            happy,
            "The Fitzharmon name is ours again. Take these gauntlets; my sons will each put " +
                "something of their own craft into them, if you ask.",
        )
        player.gauntletsKind = Gauntlets.STEEL.ordinal
        familyCrest.complete(access)
    }

    private suspend fun Dialogue.afterQuest() {
        val worn = Gauntlets.wornBy(access)
        val held = Gauntlets.heldBy(access)
        if (worn != null || held != null) {
            chatNpc(happy, "Wear them well. The family owes you a great deal.")
            return
        }
        val kind = Gauntlets.of(player)
        chatNpc(
            neutral,
            "Lost your gauntlets? They always find their way back to me. Here, take them again.",
        )
        access.invAdd(access.inv, kind.obj)
        objbox(kind.obj, "Dimintheis hands you your ${kind.displayName.lowercase()} back.")
    }
}
