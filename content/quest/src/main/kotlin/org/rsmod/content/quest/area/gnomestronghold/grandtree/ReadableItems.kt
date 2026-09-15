package org.rsmod.content.quest.area.gnomestronghold.grandtree

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpHeld1
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.INVASION_PLANS
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.JOURNAL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.LUMBER_ORDER
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.SCROLL
import org.rsmod.content.quest.area.gnomestronghold.grandtree.GrandTreeQuest.Companion.TRANSLATION_BOOK
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/** The quest's readable items: the King's book, Hazelmere's scroll and Glough's paperwork. */
class ReadableItems @Inject constructor() : PluginScript() {

    override fun ScriptContext.startup() {
        onOpHeld1(TRANSLATION_BOOK) { translationBook() }
        onOpHeld1(SCROLL) { scroll() }
        onOpHeld1(JOURNAL) { journal() }
        onOpHeld1(LUMBER_ORDER) { lumberOrder() }
        onOpHeld1(INVASION_PLANS) { invasionPlans() }
    }

    private suspend fun ProtectedAccess.translationBook() {
        mesbox("The book pairs the glyphs of the old gnome tongue with their common letters, and lists a few of the old words.")
        mesbox("Among them: 'Ka-Lu-Min' - the sea, 'Daconia' - the killing stone, and 'Tuzo' - open.")
    }

    private suspend fun ProtectedAccess.scroll() {
        mesbox("The scroll is covered in Hazelmere's spidery glyphs. Checking them against the translation book, they seem to read:")
        mesbox("'A man came to me with the King's seal. I gave the man Daconia rocks. And Daconia rocks will kill the tree!'")
    }

    private suspend fun ProtectedAccess.journal() {
        mesbox("Glough's journal. The pages rant about humans and how the King has grown soft on them.")
        mesbox("The latest entries mention a human named Charlie fetching 'the stones' from 'the old fool on the hill', gold sent to Karamja, and a fleet that will 'settle the matter for good'.")
    }

    private suspend fun ProtectedAccess.lumberOrder() {
        mesbox("Karamja Shipyard - lumber order. For the construction of thirty battleships: lumber to be supplied by the customer, Glough, head tree guardian of the Tree Gnome Stronghold.")
    }

    private suspend fun ProtectedAccess.invasionPlans() {
        mesbox("Invasion. Troops board three fleets of battleships at Karamja.")
        mesbox("Fleet 1 attacks Misthalin from the south. Fleet 2 groups at Crandor and attacks Asgarnia from the west.")
        mesbox("Fleet 3 sails north to attack Kandarin from the south, reinforced by gnome foot soldiers leaving the gnome stronghold. Take no prisoners!")
    }
}
