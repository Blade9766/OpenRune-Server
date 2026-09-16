package org.rsmod.content.skills.construction.scripts

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.script.onOpLoc5
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Buildable
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.house.ActiveHouse
import org.rsmod.content.skills.construction.house.HouseAccess
import org.rsmod.content.skills.construction.house.HouseRegistry
import org.rsmod.content.skills.construction.house.HouseState
import org.rsmod.content.skills.construction.house.HouseStore
import org.rsmod.content.skills.construction.house.Room
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Building and removing, in build mode.
 *
 * Both halves hang off op five, because that is where the cache puts "Build" on a hotspot and
 * "Remove" on the thing it turns into. A door hotspot builds a whole room into the empty cell on
 * the other side of it; every other hotspot builds furniture where it stands.
 */
class HouseBuildScript
@Inject
constructor(
    private val registry: HouseRegistry,
    private val store: HouseStore,
    private val access: HouseAccess,
    private val xpMods: XpModifiers,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (style in HouseStyle.entries) {
            onOpLoc5(style.doorLeft) { buildRoom(it.loc) }
            onOpLoc5(style.doorRight) { buildRoom(it.loc) }
        }
        for (loc in hotspotLocs()) {
            onOpLoc5(loc) { buildFurniture(it.loc, loc) }
        }
        for (loc in builtLocs()) {
            onOpLoc5(loc) { removeFurniture(it.loc, loc) }
        }
    }

    private fun hotspotLocs(): Set<String> =
        RoomType.entries.flatMapTo(LinkedHashSet()) { room -> room.hotspots.flatMap { it.locs } }

    private fun builtLocs(): Set<String> {
        val hotspots = hotspotLocs()
        return RoomType.entries
            .flatMap { room -> room.hotspots.flatMap { group -> group.options.flatMap { it.built } } }
            .filterTo(LinkedHashSet()) { it !in hotspots }
    }

    // ------------------------------------------------------------------------- rooms

    private suspend fun ProtectedAccess.buildRoom(loc: BoundLocInfo) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only build while your house is in building mode.")
            return
        }
        val cell = registry.cellOf(house, loc.coords) ?: return
        val (floor, gx, gz) = cell
        val side = loc.angleId
        val targetX = gx + Side.deltaX(side)
        val targetZ = gz + Side.deltaZ(side)
        if (!HouseState.inBounds(targetX, targetZ)) {
            mes("You cannot build any further out in that direction.")
            return
        }
        if (house.state[floor, targetX, targetZ] != null) {
            mes("There is already a room there.")
            return
        }

        val facing = Side.opposite(side)
        val candidates =
            RoomType.entries.filter {
                floor in it.floors && it.rotationsFacing(facing).isNotEmpty()
            }
        if (candidates.isEmpty()) {
            mes("Nothing can be built on the ${floor.label} from here.")
            return
        }

        val choice =
            menu(
                "Build to the ${Side.label(side)}",
                hotkeys = true,
                choices = candidates.map { "${it.label} - ${it.cost} coins (level ${it.level})" },
            )
        val room = candidates.getOrNull(choice) ?: return
        if (player.constructionLvl < room.level) {
            mes("You need a Construction level of ${room.level} to build a ${room.label.lowercase()}.")
            return
        }
        if (invCoinTotal() < room.cost) {
            mes("You need ${room.cost} coins to build a ${room.label.lowercase()}.")
            return
        }

        anim(Construction.BUILD_ANIM)
        soundSynth(Construction.BUILD_WOOD_SOUND)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        if (!invTakeFee(room.cost)) {
            return
        }
        val rotation = room.rotationsFacing(facing).first()
        store.update(player) { it[floor, targetX, targetZ] = Room(room, rotation) }
        mes("You build a ${room.label.lowercase()}.")
        access.rebuild(this)
    }

    // --------------------------------------------------------------------- furniture

    private suspend fun ProtectedAccess.buildFurniture(loc: BoundLocInfo, hotspot: String) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only build while your house is in building mode.")
            return
        }
        val room = roomAt(house, loc) ?: return
        val group = room.type.hotspots.firstOrNull { hotspot in it.locs } ?: return

        val affordable = group.options.filter { player.constructionLvl >= it.level }
        if (affordable.isEmpty()) {
            val lowest = group.options.minOf { it.level }
            mes("You need a Construction level of $lowest to build anything here.")
            return
        }

        val choice =
            menu(
                group.label,
                hotkeys = true,
                choices = affordable.map { "${it.label} (level ${it.level})" },
            )
        val option = affordable.getOrNull(choice) ?: return
        if (!hasMaterials(option)) {
            mes("You do not have the materials to build that.")
            mes(option.materials.joinToString(", ") { "${it.count} x ${objName(it.obj)}" })
            return
        }

        anim(Construction.BUILD_ANIM)
        soundSynth(option.sound.synth)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        if (!takeMaterials(option)) {
            return
        }
        val index = group.options.indexOf(option)
        val cell = registry.cellOf(house, loc.coords)
        store.update(player) {
            room.furniture[group.key] = index
            if (cell != null) {
                it.raiseFloorFor(option, room, cell)
            }
        }
        statAdvance(Construction.STAT, option.xp * xpMods.get(player, Construction.STAT))
        mes("You build a ${option.label.lowercase()}.")
        access.rebuild(this)
    }

    private suspend fun ProtectedAccess.removeFurniture(loc: BoundLocInfo, built: String) {
        val house = registry.active(player) ?: return
        if (!house.buildMode) {
            mes("You can only remove furniture while your house is in building mode.")
            return
        }
        val room = roomAt(house, loc) ?: return
        val group =
            room.type.hotspots.firstOrNull { group ->
                group.options.any { built in it.built } && room.furniture.containsKey(group.key)
            } ?: return
        if (built in Furniture.STAIRS_DOWN && houseAbove(house, loc)) {
            mes("You must remove the rooms above before you can take out the staircase.")
            return
        }

        val confirm = choice2("Yes, remove it.", true, "No.", false)
        if (!confirm) {
            return
        }

        anim(Construction.BUILD_ANIM)
        delay(Construction.BUILD_CYCLE)
        resetAnim()

        store.update(player) { room.furniture.remove(group.key) }
        mes("You remove the ${group.label.lowercase()}.")
        access.rebuild(this)
    }

    private fun roomAt(house: ActiveHouse, loc: BoundLocInfo): Room? {
        val (floor, gx, gz) = registry.cellOf(house, loc.coords) ?: return null
        return house.state[floor, gx, gz]
    }

    private fun houseAbove(house: ActiveHouse, loc: BoundLocInfo): Boolean {
        val (floor, _, _) = registry.cellOf(house, loc.coords) ?: return false
        val above = Floor.entries.getOrNull(floor.ordinal + 1) ?: return false
        return house.state.rooms.keys.any { HouseState.floorOf(it) == above }
    }

    /**
     * Builds the matching room directly above a new staircase. Rooms can only be attached to a door
     * that already exists, so without this first landing the upper floor would be unreachable no
     * matter how many staircases the house had.
     */
    private fun HouseState.raiseFloorFor(
        option: Buildable,
        room: Room,
        cell: Triple<Floor, Int, Int>,
    ) {
        if (option.built.none { it in Furniture.STAIRS_DOWN }) {
            return
        }
        val (floor, gx, gz) = cell
        val above = Floor.entries.getOrNull(floor.ordinal + 1) ?: return
        if (this[above, gx, gz] != null) {
            return
        }
        this[above, gx, gz] = Room(room.type, room.rotation)
    }

    private fun ProtectedAccess.hasMaterials(option: Buildable): Boolean =
        option.materials.all { invTotal(inv, it.obj) >= it.count }

    private fun ProtectedAccess.takeMaterials(option: Buildable): Boolean {
        if (!hasMaterials(option)) {
            return false
        }
        for (material in option.materials) {
            if (invDel(inv, material.obj, material.count).failure) {
                return false
            }
        }
        return true
    }

    private fun objName(obj: String): String =
        obj.removePrefix("obj.").replace('_', ' ')
}
