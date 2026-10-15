package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.content.skills.construction.data.RoomType

/**
 * The room and furniture creation menus. Both are drawn entirely by their clientscripts and answer
 * with a pause button: the room list replies with the chosen room's `poh_room` type, the furniture
 * list with the 1-based slot of the chosen entry.
 */
object PohInterfaces {
    private const val ROOM_INTERFACE = "interface.poh_add_room"
    private const val ROOM_LIST = "component.poh_add_room:list"
    private const val FURNITURE_INTERFACE = "interface.poh_furniture_creation"
    private const val FURNITURE_CONTENTS = "component.poh_furniture_creation:contents"
    private const val MAX_ROOM_TYPE = 29
    const val MAX_FURNITURE_ENTRIES: Int = 31

    private val furnitureEntryScript by lazy { script("[clientscript,poh_furniture_creation_entry]") }
    private val furnitureLayoutScript by lazy { script("[clientscript,script1406]") }

    val interfaces: List<String> = listOf(ROOM_INTERFACE, FURNITURE_INTERFACE)

    class FurnitureEntry(
        val row: Int,
        val level: Int,
        val materials: List<String>,
        val buildable: Boolean,
    )

    suspend fun selectRoom(access: ProtectedAccess): RoomType? =
        with(access) {
            ifOpenMainModal(ROOM_INTERFACE)
            ifSetEvents(ROOM_LIST, 0..MAX_ROOM_TYPE, IfEvent.PauseButton)
            val input = pauseButton()
            ifClose()
            if (!input.isComponentType(ROOM_LIST)) {
                return null
            }
            val room = RoomType.byRoomTypeId(input.subcomponent)
            if (room == null) {
                mes("That room cannot be built yet.")
            }
            room
        }

    /** Returns the 0-based index into [entries] of the chosen piece, or null if none was. */
    suspend fun selectFurniture(access: ProtectedAccess, entries: List<FurnitureEntry>): Int? =
        with(access) {
            require(entries.size <= MAX_FURNITURE_ENTRIES) { "Too many entries: ${entries.size}" }
            ifOpenMainModal(FURNITURE_INTERFACE)
            for ((index, entry) in entries.withIndex()) {
                runClientScript(
                    furnitureEntryScript,
                    index + 1,
                    entry.row,
                    entry.level,
                    entry.materials.materialText(),
                    if (entry.buildable) 1 else 0,
                )
            }
            runClientScript(furnitureLayoutScript, entries.size, 0)
            ifSetEvents(FURNITURE_CONTENTS, 1..entries.size, IfEvent.PauseButton)
            val input = pauseButton()
            ifClose()
            if (!input.isComponentType(FURNITURE_CONTENTS)) {
                return null
            }
            (input.subcomponent - 1).takeIf { it in entries.indices }
        }

    /** The entry script prints everything before the first `|` beside the icon, the rest below. */
    private fun List<String>.materialText(): String =
        if (size <= 1) firstOrNull().orEmpty() else first() + "|" + drop(1).joinToString("<br>")

    private fun script(name: String): Int = "clientscript.$name".asRSCM(RSCMType.CLIENTSCRIPT)
}
