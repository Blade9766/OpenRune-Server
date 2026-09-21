package org.rsmod.content.quest.area.varrock.familycrest.npcs

import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.AVAN
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.AVAN_CREST
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.GEM_TRADER
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.PERFECT_NECKLACE
import org.rsmod.content.quest.area.varrock.familycrest.FamilyCrestQuest.Companion.PERFECT_RING
import org.rsmod.content.quest.area.varrock.familycrest.avanAsked
import org.rsmod.content.quest.area.varrock.familycrest.avanDone
import org.rsmod.content.quest.area.varrock.familycrest.avanFound
import org.rsmod.content.quest.area.varrock.familycrest.bootTold
import org.rsmod.content.quest.area.varrock.familycrest.knowsBrothers
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The middle Fitzharmon, working the Al Kharid mine in a yellow cape. Until the gem trader in
 * town names him he is just another miner, which the `npc.avan` multinpc shows by wearing the
 * anonymous "Man" form below [FamilyCrestQuest.STAGE_AVAN_FOUND].
 *
 * He wants a ring and a necklace made from "perfect" gold; neither he nor the trader knows where
 * any is left, which is what sends the player to [Boot] in the Dwarven Mine.
 */
class Avan @Inject constructor(private val familyCrest: FamilyCrestQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(AVAN) { startDialogue(it.npc) { avan() } }
        onOpNpc1(GEM_TRADER) { startDialogue(it.npc) { gemTrader() } }
    }

    private suspend fun Dialogue.avan() {
        when {
            !player.avanFound -> stranger()
            player.avanDone -> chatNpc(happy, "Back to the rock face with me. Good luck with my brothers.")
            !player.avanAsked -> firstMeeting()
            else -> jewellery()
        }
    }

    /** Before the gem trader points him out, Avan passes himself off as any other miner. */
    private suspend fun Dialogue.stranger() {
        chatPlayer(quiz, "Hello there.")
        chatNpc(bored, "Hello. I'm working, if it's all the same to you.")
        if (familyCrest.isStarted(player) && player.knowsBrothers) {
            chatPlayer(quiz, "I'm looking for a man called Avan Fitzharmon.")
            chatNpc(shifty, "Never heard of him. Try asking in town.")
        }
    }

    private suspend fun Dialogue.firstMeeting() {
        chatPlayer(neutral, "The gem trader tells me you're Avan Fitzharmon.")
        chatNpc(angry, "That old gossip. Yes, I'm Avan. What of it?")
        chatPlayer(neutral, "Your father wants the family crest put back together.")
        chatNpc(
            neutral,
            "Father can want. I'm not handing my third over for nothing - I left that house to " +
                "make my own name, and gold is how I'll make it.",
        )
        chatNpc(
            happy,
            "Bring me jewellery worthy of the name. A ring and a necklace, both cut from " +
                "'perfect' gold and set with rubies. Do that and the crest's yours.",
        )
        chatPlayer(quiz, "Where would I find 'perfect' gold?")
        chatNpc(
            neutral,
            "If I knew that I'd have mined it myself. Ask a dwarf - they were pulling it out of " +
                "the ground long before any of us.",
        )
        player.avanAsked = true
        familyCrest.syncStage(access)
    }

    private suspend fun Dialogue.jewellery() {
        val hasRing = access.inv.contains(PERFECT_RING)
        val hasNecklace = access.inv.contains(PERFECT_NECKLACE)
        if (!hasRing || !hasNecklace) {
            chatNpc(quiz, "Have you got my jewellery?")
            chatPlayer(sad, "Not yet.")
            if (!player.bootTold) {
                chatNpc(
                    neutral,
                    "A 'perfect' ring and a 'perfect' necklace, both with rubies in them. Ask a " +
                        "dwarf about the gold.",
                )
            } else {
                chatNpc(
                    neutral,
                    "A 'perfect' ring and a 'perfect' necklace, both with rubies in them. Mind " +
                        "you don't make the plain gold ones by mistake.",
                )
            }
            return
        }
        chatPlayer(happy, "A 'perfect' ring and a 'perfect' necklace, as asked.")
        if (access.invDel(access.inv, PERFECT_RING).failure) {
            return
        }
        if (access.invDel(access.inv, PERFECT_NECKLACE).failure) {
            return
        }
        chatNpc(happy, "Now that is gold. A deal's a deal - here's my third of the crest.")
        access.invAdd(access.inv, AVAN_CREST)
        objbox(AVAN_CREST, "Avan hands over his piece of the family crest.")
        player.avanDone = true
        familyCrest.syncStage(access)
    }

    private suspend fun Dialogue.gemTrader() {
        if (!familyCrest.isStarted(player) || !player.knowsBrothers || player.avanFound) {
            chatNpc(neutral, "Gems, rare and rich. Come and see.")
            return
        }
        chatPlayer(quiz, "I'm looking for a man called Avan Fitzharmon.")
        chatNpc(
            happy,
            "Avan? Oh, I know Avan. He sells me the gold he digs up, and complains about the " +
                "price every single time.",
        )
        chatNpc(
            neutral,
            "He's out at the mine north of here, in the scorpion pit. Yellow cape, sour face - " +
                "you can't miss him once you know to look.",
        )
        player.avanFound = true
        familyCrest.syncStage(access)
    }
}
