package org.rsmod.content.quest.area.digsite

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.CHEMICAL_BOOK
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ROCK_PICK
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.SPECIMEN_JAR
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Exam Centre south of the digsite: the two tool cupboards, the shelves the other students
 * have already stripped, and the signposts that say which dig is which.
 */
class ExamCentre @Inject constructor(private val locRepo: LocRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        for (cupboard in Cupboard.entries) {
            onOpLoc1(cupboard.shut) { openCupboard(it.loc, cupboard) }
            onOpLoc1(cupboard.open) { closeCupboard(it.loc, cupboard) }
            onOpLoc2(cupboard.open) { searchCupboard(cupboard) }
        }

        onOpLoc1(CHEMICAL_BOOKCASE) { searchChemicalShelf() }
        onOpLoc1(BOOKCASE) { searchShelf() }

        for ((signpost, text) in SIGNPOSTS) {
            onOpLoc1(signpost) { readSignpost(text) }
        }

        onOpLoc1(BROKEN_CHEST) {
            arriveDelay()
            mes("The lid is split clean through; there is nothing left inside.")
        }

        for (sacks in SACKS) {
            onOpLoc1(sacks) {
                arriveDelay()
                mes("You search the sacks but find nothing of interest.")
            }
        }
    }

    private suspend fun ProtectedAccess.openCupboard(loc: BoundLocInfo, cupboard: Cupboard) {
        arriveDelay()
        anim(OPEN_SEQ)
        soundSynth(OPEN_SOUND)
        locRepo.change(loc, cupboard.open, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.closeCupboard(loc: BoundLocInfo, cupboard: Cupboard) {
        arriveDelay()
        anim(CLOSE_SEQ)
        soundSynth(CLOSE_SOUND)
        locRepo.change(loc, cupboard.shut, CUPBOARD_TICKS)
    }

    private suspend fun ProtectedAccess.searchCupboard(cupboard: Cupboard) {
        arriveDelay()
        if (inv.isFull()) {
            mes("You don't have enough inventory space to take anything.")
            return
        }
        invAdd(inv, cupboard.tool)
        objbox(cupboard.tool, cupboard.found)
    }

    /** The one shelf the chemist's book was left on; everything else has been picked clean. */
    private suspend fun ProtectedAccess.searchChemicalShelf() {
        arriveDelay()
        if (inv.contains(CHEMICAL_BOOK) || bank.count(CHEMICAL_BOOK) > 0) {
            searchShelf()
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to take anything.")
            return
        }
        anim(SEARCH_SHELF_SEQ)
        invAdd(inv, CHEMICAL_BOOK)
        objbox(CHEMICAL_BOOK, "You find a book on chemicals wedged behind the shelf.")
    }

    private suspend fun ProtectedAccess.searchShelf() {
        arriveDelay()
        anim(SEARCH_SHELF_SEQ)
        mesbox(
            "The label on this shelf reads 'Earth Sciences'; however, the helpful books have been " +
                "taken. It looks like the other students got to them first.",
        )
    }

    private suspend fun ProtectedAccess.readSignpost(text: String) {
        arriveDelay()
        mesbox(text)
    }

    /** The two tool cupboards in the Exam Centre and what each of them holds. */
    private enum class Cupboard(
        val shut: String,
        val open: String,
        val tool: String,
        val found: String,
    ) {
        Specimen(
            shut = "loc.qip_digsite_samplecupboardshut",
            open = "loc.qip_digsite_samplecupboardopen",
            tool = SPECIMEN_JAR,
            found = "You find a specimen jar.",
        ),
        RockPick(
            shut = "loc.qip_digsite_cupboardshut",
            open = "loc.qip_digsite_cupboardopen",
            tool = ROCK_PICK,
            found = "You find a rock pick.",
        ),
    }

    private companion object {
        const val CHEMICAL_BOOKCASE = "loc.qip_digsite_bookcase_low_digbookcase"
        const val BOOKCASE = "loc.qip_digsite_bookcase_low"
        const val BROKEN_CHEST = "loc.brokensamplechestclosed"

        const val OPEN_SEQ = "seq.human_opencupboard"
        const val CLOSE_SEQ = "seq.human_opencupboard"
        const val SEARCH_SHELF_SEQ = "seq.human_pickuptable"
        const val OPEN_SOUND = "synth.cupboard_open"
        const val CLOSE_SOUND = "synth.cupboard_close"
        const val CUPBOARD_TICKS = 100

        val SACKS = listOf("loc.digsitesacks", "loc.digsamplesacks", "loc.digsitesackkey")

        val SIGNPOSTS =
            listOf(
                "loc.digcentresign" to
                    "Exam Centre. Earth Sciences examinations are held here; see an examiner to sit one.",
                "loc.digsigntrain" to
                    "Training dig. Open to all. A trowel is the only tool needed here.",
                "loc.digsign1" to
                    "Level 1 dig. Earth Sciences level 1 required. Dig with a trowel; leather " +
                        "gloves and leather boots must be worn.",
                "loc.digsign2" to
                    "Level 2 dig. Earth Sciences level 2 required. Dig with a rock pick.",
                "loc.digsign3" to
                    "Level 3 dig. Earth Sciences level 3 required. Dig with a trowel; a specimen " +
                        "brush and specimen jar must be carried.",
                "loc.digsignprivate" to
                    "Private dig shafts. Authorised personnel only - see the archaeological " +
                        "expert at the Exam Centre.",
            )
    }
}
