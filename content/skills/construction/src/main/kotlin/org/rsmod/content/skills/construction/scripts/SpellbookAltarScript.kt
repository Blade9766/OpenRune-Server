package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLoc3
import org.rsmod.api.script.onOpLoc4
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.api.table.QuestRow
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The achievement gallery's spellbook altars, open to the owner and guests alike.
 *
 * The ancient, lunar and dark altars each Venerate between their own book and the standard one.
 * The occult altar is a multiloc on `varbit.spellbook`: ops 2 to 4 name the three books the player
 * is not on, in book order, and its Venerate asks which of them to take. A book is only open to
 * someone who could cast its spells: the quest its lowest-level spell needs, which the cache gives
 * Ancient Magicks and the Lunar spellbook and not the Arceuus spellbook.
 */
class SpellbookAltarScript
@Inject
constructor(
    private val spellbooks: MagicSpellbookManager,
    private val spells: MagicSpellRegistry,
) : PluginScript() {
    private val quests: Map<Spellbook, String> by lazy {
        listOf(Spellbook.Ancients, Spellbook.Lunars)
            .mapNotNull { book ->
                spells.allSpells()
                    .filter { it.spellbook == book && it.questReq != null }
                    .minByOrNull { it.levelReq }
                    ?.let { book to it.questReq!! }
            }
            .toMap()
    }

    override fun ScriptContext.startup() {
        onOpLoc1(ANCIENT_ALTAR) { venerate(Spellbook.Ancients) }
        onOpLoc1(LUNAR_ALTAR) { venerate(Spellbook.Lunars) }
        onOpLoc1(DARK_ALTAR) { venerate(Spellbook.Arceuus) }
        onOpLoc1(OCCULT_ALTAR) { venerateOccult() }
        onOpLoc2(OCCULT_ALTAR) { switchTo(otherBooks()[0]) }
        onOpLoc3(OCCULT_ALTAR) { switchTo(otherBooks()[1]) }
        onOpLoc4(OCCULT_ALTAR) { switchTo(otherBooks()[2]) }
    }

    private fun ProtectedAccess.otherBooks(): List<Spellbook> =
        Spellbook.entries - spellbooks.activeSpellbook(player)

    /** A single altar swaps its own book for the standard one, and back. */
    private fun ProtectedAccess.venerate(book: Spellbook) {
        val target = if (spellbooks.activeSpellbook(player) == book) Spellbook.Standard else book
        switchTo(target)
    }

    private suspend fun ProtectedAccess.venerateOccult() {
        val books = otherBooks()
        val chosen =
            choice4(
                bookName(books[0]),
                books[0],
                bookName(books[1]),
                books[1],
                bookName(books[2]),
                books[2],
                "Cancel",
                null,
                title = "Which spellbook?",
            ) ?: return
        switchTo(chosen)
    }

    private fun ProtectedAccess.switchTo(book: Spellbook) {
        val quest = quests[book]
        if (quest != null && !QuestRequirements.hasCompleted(player, quest)) {
            mes("You need to complete ${QuestRow.getRow("dbrow.$quest").displayname} to use that spellbook.")
            return
        }
        anim(PRAY_ANIM)
        if (spellbooks.setSpellbook(player, book) is MagicSpellbookManager.ChangeResult.Changed) {
            mes("You switch to the ${bookName(book)}.")
        }
    }

    private fun bookName(book: Spellbook): String =
        when (book) {
            Spellbook.Standard -> "standard spellbook"
            Spellbook.Ancients -> "Ancient Magicks"
            Spellbook.Lunars -> "Lunar spellbook"
            Spellbook.Arceuus -> "Arceuus spellbook"
        }

    private companion object {
        const val ANCIENT_ALTAR = "loc.poh_altar_ancient"
        const val LUNAR_ALTAR = "loc.poh_altar_lunar"
        const val DARK_ALTAR = "loc.poh_altar_dark"
        const val OCCULT_ALTAR = "loc.poh_altar_occult"
        const val PRAY_ANIM = "seq.human_pray"
    }
}
