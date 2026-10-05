package org.rsmod.content.skills.construction.scripts

import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.interf.IfButtonOp
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.table.PohRoomRow
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Costumes
import org.rsmod.content.skills.construction.data.Costumes.costumeStorage
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.FurnitureRows
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseLayout
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.content.skills.construction.house.HouseViewer
import org.rsmod.content.skills.construction.house.Room
import org.rsmod.content.skills.construction.house.freeBuild
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The house viewer (`poh_viewer`), opened from the house options in building mode.
 *
 * Its map and side panel are drawn by clientscripts: each room is sent with
 * `poh_viewer_setroom` and the map laid out by `script1382`; after that the panel follows the
 * `poh_viewer_*` varbits. The client changes those varbits itself as its buttons are clicked, so
 * the server makes the same change on each click and keeps the true values.
 *
 * Selecting a room offers Move, Rotate and Delete. Move then asks for an empty map cell on the
 * same floor, and both Move and Rotate end with the clockwise and anticlockwise arrows and Done. An
 * empty cell's Add room opens the room menu, then places the new room the same way; it is paid for
 * on Done. Every change goes through [HouseLayout], and the house is rebuilt and the viewer
 * redrawn after it.
 */
class HouseViewerScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val store: HouseStore,
    private val access: HouseAccess,
) : PluginScript() {
    private val roomRows: Map<Int, PohRoomRow> by lazy { PohRoomRow.all().associateBy { it.roomType } }

    override fun ScriptContext.startup() {
        onIfOverlayButton("component.poh_options:viewer") { open() }
        for (slot in 1..HouseViewer.MAX_ROOMS) {
            onIfModalButton(roomComponent(slot)) { select(slot) }
        }
        onIfModalButton(MAP) { if (it.op == IfButtonOp.Op6) addRoom(it.comsub) else pickDestination(it.comsub) }
        onIfModalButton(MOVE) { startMove() }
        onIfModalButton(ROTATE) { startRotate() }
        onIfModalButton(CLOCKWISE) { turn(1) }
        onIfModalButton(ANTICLOCKWISE) { turn(3) }
        onIfModalButton(CANCEL) { cancel() }
        onIfModalButton(DONE) { done() }
        onIfModalButton(DELETE) { delete() }
        onIfModalButton(PORTAL) { goToPortal() }
    }

    private fun ProtectedAccess.ownHouse(): ActiveHouse? {
        val house = registry.active(player)
        if (house == null || !registry.isInside(player) || !house.buildMode) {
            mes("You can only use the house viewer in your own house, in building mode.")
            return null
        }
        return house
    }

    private fun ProtectedAccess.open() {
        val house = ownHouse() ?: return
        if (HouseViewer.layout(house.state) == null) {
            mes("Your house has rooms outside the area the house viewer can show.")
            return
        }
        draw(house)
    }

    private fun ProtectedAccess.draw(house: ActiveHouse) {
        val layout = HouseViewer.layout(house.state) ?: return ifClose()
        ifOpenMainModal(INTERFACE)
        clearSelection()
        for (placed in layout.rooms) {
            val row = roomRows[placed.room.type.roomTypeId] ?: continue
            val (first, second, third) = HouseViewer.pack(layout, placed, hotspotValues(row, placed.room)).toList()
            runClientScript(SET_ROOM, placed.slot, row.rowId, 0, first, second, third)
        }
        val (southWest, northEast) = layout.corners()
        val floor = registry.cellOf(house, player.coords)?.first ?: Floor.GROUND
        runClientScript(DRAW_MAP, layout.rooms.size, southWest, northEast, floor.ordinal)
        for (slot in 1..layout.rooms.size) {
            ifSetEvents(roomComponent(slot), -1..-1, IfEvent.Op1)
        }
        ifSetEvents(MAP, 0 until HouseViewer.CELLS * Floor.entries.size, IfEvent.Op1, IfEvent.Op6)
        for (button in listOf(MOVE, ROTATE, CLOCKWISE, ANTICLOCKWISE, DELETE, CANCEL, DONE, PORTAL)) {
            ifSetEvents(button, -1..-1, IfEvent.Op1)
        }
    }

    /**
     * For each of the room's hotspots in the cache's order, the 1-based place in its build list of
     * what is built there, which the map's tooltip names.
     */
    private fun hotspotValues(row: PohRoomRow, room: Room): IntArray {
        val built =
            room.furniture.mapNotNull { (key, option) ->
                room.type.hotspot(key)?.options?.getOrNull(option)?.let(FurnitureRows::of)?.rowId
            }
        return IntArray(row.hotspot.size) { index ->
            val list = row.hotspot[index].builddata
            built.firstNotNullOfOrNull { id -> list.indexOfFirst { it.rowId == id }.takeIf { it >= 0 } }?.plus(1) ?: 0
        }
    }

    // ------------------------------------------------------------------------ choosing

    private fun ProtectedAccess.select(slot: Int) {
        val house = ownHouse() ?: return
        val placed = HouseViewer.layout(house.state)?.slot(slot) ?: return
        set(SELECTED, slot)
        set(TYPE, placed.room.type.roomTypeId)
        set(ROT, placed.room.rotation)
        set(ENABLE_ROT, 0)
        set(DESTINATION, 0)
        set(SELECTED_DOORS, HouseViewer.doorBits(placed.room.type))
        set(ADJACENT_DOORS, HouseViewer.adjacentDoors(house.state, placed.floor, placed.gx, placed.gz))
    }

    private fun ProtectedAccess.startMove() {
        val house = ownHouse() ?: return
        val placed = selected(house) ?: return
        val state = house.state
        val problem =
            when {
                HouseLayout.stairsLinked(state, placed.floor, placed.gx, placed.gz) ->
                    "You can't move a room that a staircase runs through."
                state.supportsRoomAbove(placed.floor, placed.gx, placed.gz) ->
                    "You must move the room above before you can move this one."
                else -> null
            }
        if (problem != null) {
            mes(problem)
            return
        }
        set(DESTINATION, HouseViewer.CHOOSING)
    }

    private fun ProtectedAccess.pickDestination(index: Int) {
        val house = ownHouse() ?: return
        if (vars(DESTINATION) != HouseViewer.CHOOSING) {
            return
        }
        val placed = selected(house) ?: return
        val (floor, gx, gz) = HouseViewer.layout(house.state)?.cellAt(index) ?: return
        if (floor != placed.floor) {
            mes("A room can only be moved about on its own floor.")
            return
        }
        val problem =
            if (house.state[floor, gx, gz] != null) "There is already a room there." else HouseLayout.areaProblem(gx, gz, houseSize())
        if (problem != null) {
            mes(problem)
            return
        }
        set(DESTINATION, index + 1)
        set(ENABLE_ROT, 1)
        set(ADJACENT_DOORS, HouseViewer.adjacentDoors(house.state, floor, gx, gz, placed.gx, placed.gz))
    }

    private fun ProtectedAccess.startRotate() {
        val house = ownHouse() ?: return
        val placed = selected(house) ?: return
        if (HouseLayout.stairsLinked(house.state, placed.floor, placed.gx, placed.gz)) {
            mes("You can't turn a room that a staircase runs through.")
            return
        }
        set(ENABLE_ROT, 1)
    }

    private fun ProtectedAccess.turn(by: Int) {
        if (vars(ENABLE_ROT) == 1) {
            set(ROT, (vars(ROT) + by) and 3)
        }
    }

    private fun ProtectedAccess.cancel() {
        val house = ownHouse() ?: return
        set(DESTINATION, 0)
        set(ENABLE_ROT, 0)
        val placed = selected(house)
        if (placed == null) {
            clearSelection()
            return
        }
        set(ROT, placed.room.rotation)
        set(ADJACENT_DOORS, HouseViewer.adjacentDoors(house.state, placed.floor, placed.gx, placed.gz))
    }

    // ------------------------------------------------------------------------- changes

    private suspend fun ProtectedAccess.addRoom(index: Int) {
        val house = ownHouse() ?: return
        if (vars(DESTINATION) != 0 || vars(ENABLE_ROT) != 0) {
            return
        }
        val (floor, gx, gz) = HouseViewer.layout(house.state)?.cellAt(index) ?: return
        if (house.state[floor, gx, gz] != null) {
            return
        }
        val outside = HouseLayout.areaProblem(gx, gz, houseSize())
        if (outside != null) {
            mes(outside)
            return
        }
        if (!roomToSpare(house)) {
            return
        }
        val selected = PohInterfaces.selectRoom(this)
        val room = selected?.let { HouseLayout.menagerieFor(house.state, floor, gx, gz, it) }
        if (room == null || !canAfford(room)) {
            draw(house)
            return
        }
        val size = houseSize()
        val rotation = (0..3).firstOrNull { HouseLayout.addProblem(house.state, floor, gx, gz, room, it, size) == null }
        if (rotation == null) {
            mes(HouseLayout.addProblem(house.state, floor, gx, gz, room, 0, size) ?: "That room can't go there.")
            draw(house)
            return
        }
        draw(house)
        set(TYPE, room.roomTypeId)
        set(ROT, rotation)
        set(DESTINATION, index + 1)
        set(ENABLE_ROT, 1)
        set(SELECTED_DOORS, HouseViewer.doorBits(room))
        set(ADJACENT_DOORS, HouseViewer.adjacentDoors(house.state, floor, gx, gz))
    }

    private suspend fun ProtectedAccess.done() {
        val house = ownHouse() ?: return
        if (vars(ENABLE_ROT) != 1) {
            return
        }
        val rotation = vars(ROT)
        val layout = HouseViewer.layout(house.state) ?: return
        val target = vars(DESTINATION).takeIf { it in 1..HouseViewer.CELLS * Floor.entries.size }?.let { layout.cellAt(it - 1) }
        val placed = selected(house)
        when {
            placed == null && target != null -> {
                val room = RoomType.byRoomTypeId(vars(TYPE)) ?: return
                build(house, target, room, rotation)
            }
            placed != null && target != null -> move(house, placed, target, rotation)
            placed != null -> rotate(house, placed, rotation)
        }
    }

    private fun ProtectedAccess.rotate(house: ActiveHouse, placed: HouseViewer.Placed, rotation: Int) {
        if (rotation == placed.room.rotation) {
            cancel()
            return
        }
        val problem = HouseLayout.rotateProblem(house.state, placed.floor, placed.gx, placed.gz, rotation, houseSize())
        if (problem != null) {
            mes(problem)
            return
        }
        store.update(player) { it[placed.floor, placed.gx, placed.gz]?.rotation = rotation }
        mes("You turn the ${placed.room.type.label.lowercase()}.")
        redraw()
    }

    private fun ProtectedAccess.move(
        house: ActiveHouse,
        placed: HouseViewer.Placed,
        target: Triple<Floor, Int, Int>,
        rotation: Int,
    ) {
        val (floor, gx, gz) = target
        val problem =
            HouseLayout.moveProblem(house.state, placed.floor, placed.gx, placed.gz, gx, gz, rotation, houseSize())
        if (problem != null) {
            mes(problem)
            return
        }
        store.update(player) {
            val room = it[placed.floor, placed.gx, placed.gz] ?: return@update
            it[placed.floor, placed.gx, placed.gz] = null
            it[floor, gx, gz] = Room(room.type, rotation, room.furniture)
        }
        mes("You move the ${placed.room.type.label.lowercase()}.")
        redraw()
    }

    private suspend fun ProtectedAccess.build(house: ActiveHouse, target: Triple<Floor, Int, Int>, room: RoomType, rotation: Int) {
        val (floor, gx, gz) = target
        val problem =
            HouseLayout.addProblem(house.state, floor, gx, gz, room, rotation, houseSize())
        if (problem != null) {
            mes(problem)
            return
        }
        if (!roomToSpare(house) || !canAfford(room)) {
            return
        }
        ifClose()
        anim(Construction.BUILD_ANIM)
        soundSynth(Construction.BUILD_WOOD_SOUND)
        delay(Construction.BUILD_CYCLE)
        resetAnim()
        if (!player.freeBuild && !invTakeFee(room.cost)) {
            return
        }
        store.update(player) { it[floor, gx, gz] = Room(room, rotation) }
        mes("You build a ${room.label.lowercase()}.")
        redraw()
    }

    private suspend fun ProtectedAccess.delete() {
        val house = ownHouse() ?: return
        val placed = selected(house) ?: return
        val costumes = Costumes.Store.entries.any { Costumes.setsStored(it, player.costumeStorage) > 0 }
        val problem = HouseLayout.removalProblem(house.state, placed.floor, placed.gx, placed.gz, costumes, houseSize())
        if (problem != null) {
            mes(problem)
            return
        }
        val label = placed.room.type.label.lowercase()
        ifClose()
        if (!choice2("Yes, remove the $label.", true, "No.", false)) {
            draw(house)
            return
        }
        anim(Construction.BUILD_ANIM)
        soundSynth(Construction.BUILD_WOOD_SOUND)
        delay(Construction.BUILD_CYCLE)
        resetAnim()
        store.update(player) { it[placed.floor, placed.gx, placed.gz] = null }
        mes("You remove the $label.")
        redraw()
    }

    private fun ProtectedAccess.goToPortal() {
        val house = ownHouse() ?: return
        ifClose()
        telejump(registry.entranceOf(house))
    }

    private fun ProtectedAccess.roomToSpare(house: ActiveHouse): Boolean {
        val problem = HouseLayout.roomLimitProblem(house.state, player) ?: return true
        mes(problem)
        return false
    }

    private fun ProtectedAccess.canAfford(room: RoomType): Boolean {
        if (player.freeBuild) {
            return true
        }
        if (player.constructionLvl < room.level) {
            mes("You need a Construction level of ${room.level} to build a ${room.label.lowercase()}.")
            return false
        }
        if (invCoinTotal() < room.cost) {
            mes("You need ${room.cost} coins to build a ${room.label.lowercase()}.")
            return false
        }
        return true
    }

    private fun ProtectedAccess.houseSize(): Int = HouseLayout.sizeOf(player)

    private fun ProtectedAccess.redraw() {
        access.rebuild(this)
        registry.active(player)?.let { draw(it) }
    }

    private fun ProtectedAccess.selected(house: ActiveHouse): HouseViewer.Placed? =
        vars(SELECTED).takeIf { it > 0 }?.let { HouseViewer.layout(house.state)?.slot(it) }

    private fun ProtectedAccess.clearSelection() {
        for (varbit in listOf(SELECTED, DESTINATION, ROT, ENABLE_ROT, TYPE, SELECTED_DOORS, ADJACENT_DOORS)) {
            set(varbit, 0)
        }
    }

    private fun ProtectedAccess.vars(varbit: String): Int = player.vars[varbit]

    private fun ProtectedAccess.set(varbit: String, value: Int) = VarPlayerIntMapSetter.set(player, varbit, value)

    private companion object {
        const val INTERFACE = "interface.poh_viewer"
        const val MAP = "component.poh_viewer:map"
        const val MOVE = "component.poh_viewer:move"
        const val ROTATE = "component.poh_viewer:rotate"
        const val CLOCKWISE = "component.poh_viewer:clockwise"
        const val ANTICLOCKWISE = "component.poh_viewer:anticlockwise"
        const val DELETE = "component.poh_viewer:delete"
        const val CANCEL = "component.poh_viewer:cancel"
        const val DONE = "component.poh_viewer:done"
        const val PORTAL = "component.poh_viewer:portal"

        const val SELECTED = "varbit.poh_viewer_selectedroom"
        const val DESTINATION = "varbit.poh_viewer_destination"
        const val ROT = "varbit.poh_viewer_rot"
        const val ENABLE_ROT = "varbit.poh_viewer_enable_rot"
        const val TYPE = "varbit.poh_viewer_type"
        const val SELECTED_DOORS = "varbit.poh_viewer_selecteddoors"
        const val ADJACENT_DOORS = "varbit.poh_viewer_adjacentdoors"

        val SET_ROOM by lazy { "clientscript.[clientscript,poh_viewer_setroom]".asRSCM(RSCMType.CLIENTSCRIPT) }
        val DRAW_MAP by lazy { "clientscript.[clientscript,script1382]".asRSCM(RSCMType.CLIENTSCRIPT) }

        fun roomComponent(slot: Int): String = "component.poh_viewer:%02d".format(slot)
    }
}
