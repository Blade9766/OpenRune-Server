package org.rsmod.content.quest.area.lumbridge.losttribe

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.BOOK
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_READ_BOOK
import org.rsmod.content.quest.area.lumbridge.losttribe.LostTribeQuest.Companion.STAGE_SHOWN_BROOCH
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * "A History of the Goblin Race", filed on the north-west bookcase of the Varrock Palace library.
 *
 * The book is interface 183: a cover, three pages of history, the tribal symbols (the brooch's
 * Dorgeshuun among them) and the four direction tribes whose symbols mark the tunnels under
 * Lumbridge. The open page is kept in `varbit.lost_tribe_bookmark`.
 */
class GoblinSymbolBook
@Inject
constructor(private val lostTribe: LostTribeQuest, private val objRepo: ObjRepository) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(BOOKCASE) { searchBookcase() }
        onOpHeld1(BOOK) { openBook(COVER) }
        onIfModalButton(RIGHT_ARROW) { turnPage(player.lostTribeBookmark + 1) }
        onIfModalButton(LEFT_ARROW) { turnPage(player.lostTribeBookmark - 1) }
    }

    private suspend fun ProtectedAccess.searchBookcase() {
        arriveDelay()
        val stage = lostTribe.stage(player)
        if (stage < STAGE_SHOWN_BROOCH || lostTribe.ownsItem(this, BOOK)) {
            mes("You search the bookcase but find nothing of interest.")
            return
        }
        startDialogue { chatPlayer(happy, "'A History of the Goblin Race'. This must be it.") }
        invAddOrDrop(objRepo, BOOK)
        mes("You take the book from the bookcase.")
    }

    private fun ProtectedAccess.openBook(page: Int) {
        ifOpenMainModal(INTERFACE)
        showPage(page)
    }

    private suspend fun ProtectedAccess.turnPage(page: Int) {
        if (page !in COVER..DIRECTIONS) {
            return
        }
        soundSynth(PAGE_SOUND)
        showPage(page)
        if (page == SYMBOLS && lostTribe.stage(player) == STAGE_SHOWN_BROOCH) {
            ifClose()
            lostTribe.advanceTo(this, STAGE_READ_BOOK)
            startDialogue {
                mesbox("You flip through the book's pages. Fortunately, there are pictures.")
                chatPlayer(
                    shocked,
                    "Hey... The symbol of the 'Dorgeshuun' tribe looks just like the symbol on the brooch " +
                        "I found.",
                )
            }
            openBook(SYMBOLS)
        }
    }

    private fun ProtectedAccess.showPage(page: Int) {
        player.lostTribeBookmark = page
        ifSetHide(COVER_MODEL, page != COVER)
        ifSetHide(TITLES, page != COVER)
        ifSetHide(PAGES_MODEL, page == COVER)
        ifSetHide(PAGE_1, page != HISTORY_1)
        ifSetHide(PAGE_2, page != HISTORY_2)
        ifSetHide(PAGE_3, page != HISTORY_3)
        ifSetHide(SYMBOL_LAYER, page != SYMBOLS)
        ifSetHide(DIRECTION_LAYER, page != DIRECTIONS)
        ifSetHide(LEFT_ARROW_LAYER, page == COVER)
        ifSetHide(RIGHT_ARROW_LAYER, page == DIRECTIONS)
    }

    private companion object {
        const val BOOKCASE = "loc.lost_tribe_bookcase"
        const val INTERFACE = "interface.lost_tribe_symbol_book"
        const val PAGE_SOUND = "synth.turn_book_page"

        const val COVER_MODEL = "component.lost_tribe_symbol_book:lost_tribe_book_cover"
        const val PAGES_MODEL = "component.lost_tribe_symbol_book:lost_tribe_pages"
        const val TITLES = "component.lost_tribe_symbol_book:lost_tribe_titles"
        const val PAGE_1 = "component.lost_tribe_symbol_book:lost_tribe_page_1"
        const val PAGE_2 = "component.lost_tribe_symbol_book:lost_tribe_page_2"
        const val PAGE_3 = "component.lost_tribe_symbol_book:lost_tribe_page_3"
        const val SYMBOL_LAYER = "component.lost_tribe_symbol_book:lost_tribe_symbols"
        const val DIRECTION_LAYER = "component.lost_tribe_symbol_book:lost_tribe_directions"
        const val LEFT_ARROW = "component.lost_tribe_symbol_book:lost_tribe_left_arrow"
        const val RIGHT_ARROW = "component.lost_tribe_symbol_book:lost_tribe_right_arrow"
        const val LEFT_ARROW_LAYER = "component.lost_tribe_symbol_book:lost_tribe_l_arrow_layer"
        const val RIGHT_ARROW_LAYER = "component.lost_tribe_symbol_book:lost_tribe_r_arrow_layer"

        const val COVER = 0
        const val HISTORY_1 = 1
        const val HISTORY_2 = 2
        const val HISTORY_3 = 3
        const val SYMBOLS = 4
        const val DIRECTIONS = 5
    }
}
