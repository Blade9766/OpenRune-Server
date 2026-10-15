package org.rsmod.content.quest.area.varrock.ragandboneman.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.ODD_OLD_MAN
import org.rsmod.content.quest.area.varrock.ragandboneman.RagAndBoneManQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.varrock.ragandboneman.Specimen
import org.rsmod.content.quest.area.varrock.ragandboneman.SpecimenState
import org.rsmod.content.quest.manager.menu
import org.rsmod.content.quest.manager.startQuestPrompt
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import toRs

/**
 * The Odd Old Man, who camps among his bones north of the Digsite with a sack on his back that
 * occasionally has opinions. He hands out the wish-list, explains the vinegar cleaning, reports
 * what is still missing, and takes the collection only when all eight specimens are polished.
 */
class OddOldMan @Inject constructor(private val rb: RagAndBoneManQuest) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(ODD_OLD_MAN) { startDialogue(it.npc) { oddOldMan() } }
        onOpLoc1(WISH_LIST) { readWishList() }
    }

    private suspend fun Dialogue.oddOldMan() {
        when {
            rb.isComplete(player) -> afterwards()
            rb.isStarted(player) && rb.unfinished(player).isEmpty() -> handOver()
            rb.isStarted(player) -> progress()
            else -> introduction()
        }
    }

    private suspend fun Dialogue.introduction() {
        chatNpc(shocked, "Eh? Oh! A visitor. Do mind the femurs, they're arranged.")
        val topic =
            menu(
                "What are all these bones for?" to Intro.BONES,
                "Is that sack on your back moving?" to Intro.SACK,
                "I'll leave you to it." to Intro.LEAVE,
            )
        when (topic) {
            Intro.BONES -> bones()
            Intro.SACK -> {
                chatPlayer(quiz, "Is that sack on your back moving?")
                chatNpc(shifty, "Moving? Sacks don't move. That would be ridiculous. It's the wind.")
                mesbox("There is no wind.")
                chatNpc(neutral, "A very localised wind. Now, was there something else?")
            }
            Intro.LEAVE -> chatPlayer(neutral, "I'll leave you to it.")
        }
    }

    private suspend fun Dialogue.bones() {
        chatPlayer(quiz, "What are all these bones for?")
        chatNpc(happy, "For? They're a collection! Every bone has a story, and I am assembling a library.")
        chatNpc(sad, "Alas, my library has gaps. Eight of them. Specimens I simply cannot get away from camp to fetch.")
        chatNpc(quiz, "You look like someone who gets about. Would you fill them for me?")
        if (!startQuestPrompt(rb.quest)) {
            chatPlayer(neutral, "Not right now, thanks.")
            chatNpc(neutral, "No hurry. The bones aren't going anywhere. Well. Most of them.")
            return
        }
        chatPlayer(happy, "I'll help with your collection.")
        rb.advanceTo(access, STAGE_STARTED)
        wishList()
        cleaning()
        chatNpc(neutral, "The whole list is pinned up on my stall here if you forget. Off you go, then!")
        access.mes("The Odd Old Man's wish-list has been added to your quest journal.")
    }

    private suspend fun Dialogue.wishList() {
        chatNpc(happy, "Splendid! I need a giant rat bone, a unicorn bone, some bear ribs and a ram skull.")
        chatNpc(happy, "Also a goblin skull, a big frog leg - big, mind, not little and not giant - a monkey paw and a giant bat wing.")
        chatPlayer(confused, "That's quite a shopping list. Why those eight?")
        chatNpc(shifty, "Why does anyone collect anything? Next question.")
    }

    private suspend fun Dialogue.cleaning() {
        chatNpc(neutral, "Now, they must be clean. I'll not have dirty bones in the collection, they attract... comment.")
        chatNpc(neutral, "Pour a jug of vinegar into an empty pot, then pop the specimen in to soak.")
        chatNpc(neutral, "Then use my pot-boiler, right here: an ordinary log underneath, the pot on top, light the log with a tinderbox and wait for it to boil dry.")
        chatNpc(neutral, "One specimen at a time, one log each time. Out comes a polished bone, and you get your pot back.")
        chatNpc(happy, "Fortunato, the wine merchant in Draynor Village, sells vinegar. Mention my name and he'll know what you're after.")
    }

    private suspend fun Dialogue.progress() {
        chatNpc(quiz, "Back already? Have you got my specimens?")
        val topic =
            menu(
                "I've brought some specimens." to Progress.REPORT,
                "What do you need again?" to Progress.LIST,
                "How do I clean them again?" to Progress.CLEAN,
                "Where should I look?" to Progress.WHERE,
                "Not yet." to Progress.LEAVE,
            )
        when (topic) {
            Progress.REPORT -> report()
            Progress.LIST -> {
                chatPlayer(quiz, "What do you need again?")
                wishList()
                chatNpc(neutral, "It's all on the wish-list on my stall.")
            }
            Progress.CLEAN -> {
                chatPlayer(quiz, "How do I clean them again?")
                cleaning()
            }
            Progress.WHERE -> where()
            Progress.LEAVE -> {
                chatPlayer(neutral, "Not yet.")
                chatNpc(neutral, "The bones and I shall wait.")
            }
        }
    }

    private suspend fun Dialogue.report() {
        chatPlayer(happy, "I've brought some specimens.")
        val unfinished = rb.unfinished(player)
        val ready = Specimen.entries.size - unfinished.size
        if (ready == 0) {
            chatNpc(confused, "Have you? I don't see a single polished specimen about you.")
        } else {
            chatNpc(happy, "Ooh, $ready polished! Lovely. But I'll take the collection all at once, or not at all.")
        }
        val lines =
            unfinished.map { specimen ->
                val state = rb.specimenState(player, specimen, access.bank)
                val where = if (state == SpecimenState.POLISHED) "polished, but not on you" else state.label
                "${specimen.label}: $where."
            }
        for (page in lines.chunked(MESBOX_LINES)) {
            mesbox("Still needed:<br>" + page.joinToString("<br>"))
        }
        if (unfinished.any { rb.specimenState(player, it, access.bank) in UNCLEAN }) {
            chatNpc(angry, "And I'll not take raw or half-soaked bones. Boil them clean first!")
        }
    }

    private suspend fun Dialogue.where() {
        chatPlayer(quiz, "Where should I look?")
        val missing =
            Specimen.entries.filter { rb.specimenState(player, it, access.bank) == SpecimenState.MISSING }
        if (missing.isEmpty()) {
            chatNpc(happy, "Look? You've found them all! Now get them clean.")
            return
        }
        chatNpc(neutral, "Hmm, let me think where I'd go, if I went anywhere.")
        for (specimen in missing) {
            chatNpc(neutral, "${specimen.label}: ${specimen.habitat}")
        }
    }

    private suspend fun Dialogue.handOver() {
        chatNpc(quiz, "Is that... do I smell vinegar? Have you got them all?")
        chatPlayer(happy, "All eight, boiled and polished.")
        val removed =
            player.invTransaction(access.inv) {
                val inventory = select(access.inv)
                for (specimen in Specimen.entries) {
                    delete {
                        from = inventory
                        obj = specimen.polished.asRSCM()
                        strictCount = 1
                    }
                }
            }.success
        if (!removed) {
            chatNpc(confused, "Wait, where did they go? Come back when you have all eight.")
            return
        }
        mesbox("You hand over the eight polished specimens. The Odd Old Man arranges them lovingly among his bones.")
        mesbox("The sack on his back gives a muffled grumble.")
        chatNpc(happy, "Magnificent! The gaps are filled. The library is whole!")
        chatNpc(shifty, "For now. A collection is never truly finished, you understand. Never mind the sack.")
        rb.quest.completeQuest(access)
    }

    private suspend fun Dialogue.afterwards() {
        chatNpc(happy, "Ah, my favourite supplier! The collection has never looked so polished.")
        chatPlayer(quiz, "What will you do with it now?")
        chatNpc(shifty, "Admire it. Catalogue it. Wonder what else is out there with interesting bones in it.")
        mesbox("The sack on his back stirs, as if it heard something it didn't like.")
    }

    private fun ProtectedAccess.readWishList() {
        val lines =
            if (rb.isStarted(player) || rb.isComplete(player)) {
                Specimen.entries.map { specimen ->
                    val state = rb.specimenState(player, specimen, bank)
                    "<col=${state.journalColour}>${specimen.label}</col> - ${state.label}"
                } + listOfNotNull("", rb.boilerLine(player))
            } else {
                listOf(
                    "A giant rat bone, a unicorn bone, bear ribs, a ram skull, a goblin skull, a big " +
                        "frog leg, a monkey paw and a giant bat wing.",
                    "",
                    "Must be CLEAN. Vinegar. Boil.",
                )
            }
        val text = lines.flatMap { it.toRs(wrapAt = SCROLL_WRAP).split("<br>") }
        ifOpenMain("interface.questjournal")
        runClientScript(SCROLL_RESET.asRSCM(RSCMType.CLIENTSCRIPT))
        ifSetText("component.questjournal:title", "<col=7f0000>The Odd Old Man's Wish-list</col>")
        for (line in 1..SCROLL_LINES) {
            ifSetText("component.questjournal:qj$line", text.getOrElse(line - 1) { "" })
        }
    }

    private enum class Intro { BONES, SACK, LEAVE }

    private enum class Progress { REPORT, LIST, CLEAN, WHERE, LEAVE }

    companion object {
        const val WISH_LIST = "loc.rag_shopping_list"

        const val SCROLL_RESET = "clientscript.[clientscript,quest_journal_reset]"
        private const val SCROLL_WRAP = 64
        private const val SCROLL_LINES = 24
        private const val MESBOX_LINES = 3

        private val UNCLEAN = setOf(SpecimenState.RAW, SpecimenState.IN_VINEGAR)
    }
}
