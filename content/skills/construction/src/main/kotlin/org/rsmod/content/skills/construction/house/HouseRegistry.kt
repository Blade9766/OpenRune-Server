package org.rsmod.content.skills.construction.house

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.HotspotGroup
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/** A house that is currently standing in a runtime region. */
class ActiveHouse(val region: Region, val state: HouseState, val buildMode: Boolean)

/**
 * Assembles houses into runtime regions.
 *
 * A house is one 8x8 zone copy per room, taken from the style's template block, plus a pass over
 * every copied zone to turn the template's hotspots into whatever the owner has actually built.
 * Nothing about a standing house is authoritative - the region is discarded whenever the layout or
 * the build mode changes and put back together from [HouseState].
 */
@Singleton
class HouseRegistry
@Inject
constructor(private val regionRepo: RegionRepository, private val locRepo: LocRepository) {
    private val active = HashMap<Long, ActiveHouse>()

    fun active(player: Player): ActiveHouse? = active[player.houseKey()]

    fun isInside(player: Player): Boolean {
        val house = active(player) ?: return false
        return player.coords in house.region
    }

    /**
     * Stands the house up in a fresh region and returns the tile to drop the player on, or `null`
     * when no region slot was free.
     */
    fun open(player: Player, state: HouseState, buildMode: Boolean): CoordGrid? {
        val region = regionRepo.add(template(state)) ?: return null
        regionRepo.protect(region)

        val house = ActiveHouse(region, state, buildMode)
        close(player)
        active[player.houseKey()] = house
        dress(house)
        return entrance(house)
    }

    /** Rebuilds the house in place, keeping the player where they were standing. */
    fun reopen(player: Player, buildMode: Boolean): CoordGrid? {
        val current = active(player) ?: return null
        val offset = current.region.offsetOf(player.coords)
        val entry = open(player, current.state, buildMode) ?: return null
        val rebuilt = active(player) ?: return entry
        val restored = rebuilt.region.southWest.translate(offset.first, offset.second, 0)
        if (!rebuilt.region.holds(restored)) {
            return entry
        }
        val coords = CoordGrid(restored.x, restored.z, player.coords.level)
        return if (roomAt(rebuilt, coords) != null) coords else entry
    }

    /**
     * Drops the standing house. The region is only unprotected, not deleted: the registry clears
     * regions with nobody in them the next time one is allocated, and doing it here would pull the
     * ground out from under a player who is still being moved out of it.
     */
    fun close(player: Player) {
        val house = active.remove(player.houseKey()) ?: return
        regionRepo.unprotect(house.region)
    }

    fun zoneOf(house: ActiveHouse, floor: Floor, gx: Int, gz: Int): ZoneKey =
        ZoneKey(
            house.region.southWestZone.x + GRID_ORIGIN + gx,
            house.region.southWestZone.z + GRID_ORIGIN + gz,
            floor.regionLevel,
        )

    /** Which grid cell [coords] falls in, or `null` when it is outside the built area. */
    fun cellOf(house: ActiveHouse, coords: CoordGrid): Triple<Floor, Int, Int>? {
        val zone = ZoneKey.from(coords)
        val gx = zone.x - house.region.southWestZone.x - GRID_ORIGIN
        val gz = zone.z - house.region.southWestZone.z - GRID_ORIGIN
        if (!HouseState.inBounds(gx, gz)) {
            return null
        }
        val floor = Floor.entries.firstOrNull { it.regionLevel == coords.level } ?: return null
        return Triple(floor, gx, gz)
    }

    private fun template(state: HouseState): RegionTemplate =
        RegionTemplate.create {
            for ((key, room) in state.rooms) {
                val floor = floorOf(key)
                val gx = gxOf(key)
                val gz = gzOf(key)
                val stairsBelow = state.hasStairsBelow(floor, gx, gz)
                this[GRID_ORIGIN + gx, GRID_ORIGIN + gz, floor.regionLevel] =
                    RegionZoneCopy(
                        templateZone(room.type, state.style, stairsBelow),
                        room.rotation,
                        null,
                    )
                if (state.needsRoof(floor, gx, gz)) {
                    this[GRID_ORIGIN + gx, GRID_ORIGIN + gz, floor.regionLevel + 1] =
                        RegionZoneCopy(roofZone(state.style), 0, null)
                }
            }
        }

    private fun templateZone(room: RoomType, style: HouseStyle, stairsBelow: Boolean): ZoneKey {
        val offsetX =
            if (stairsBelow) room.stairsTopZoneOffsetX ?: room.zoneOffsetX else room.zoneOffsetX
        return ZoneKey(style.blockZoneX + offsetX, room.zoneZ, style.templateLevel)
    }

    private fun roofZone(style: HouseStyle): ZoneKey =
        ZoneKey(style.blockZoneX + ROOF_ZONE_OFFSET_X, ROOF_ZONE_Z, style.templateLevel)

    /**
     * Turns the raw template copies into this owner's house: doorways are opened or walled up, and
     * every hotspot either becomes the furniture built on it or disappears outside build mode.
     */
    private fun dress(house: ActiveHouse) {
        val stairs = ArrayList<PlacedStairs>()
        for ((key, room) in house.state.rooms) {
            val floor = floorOf(key)
            val gx = gxOf(key)
            val gz = gzOf(key)
            val zone = zoneOf(house, floor, gx, gz)
            val base = zone.toCoords()
            for (loc in locRepo.findAll(zone).toList()) {
                val name = locName(loc.id) ?: continue
                when {
                    name == DYNAMIC_WINDOW -> replace(loc, room, house.state.style.window)
                    name == house.state.style.doorLeft || name == house.state.style.doorRight ->
                        dressDoor(house, loc, room, floor, gx, gz)
                    else -> {
                        val group = room.type.hotspots.firstOrNull { name in it.locs }
                        if (group == null) {
                            // A hotspot the plugin has no table for still carries its ghost model,
                            // so a finished house has to have it taken out even though nothing can
                            // ever be built on it.
                            if (!house.buildMode && isHotspot(loc.id)) {
                                locRepo.del(loc, PERMANENT)
                            }
                            continue
                        }
                        val placed = dressHotspot(house, room, group, name, loc)
                        if (placed != null && placed in Furniture.STAIRS_DOWN) {
                            stairs += PlacedStairs(floor, gx, gz, loc, base, placed)
                        }
                    }
                }
            }
        }
        for (placed in stairs) {
            placeStairsAbove(house, placed)
        }
    }

    private fun dressDoor(
        house: ActiveHouse,
        loc: LocInfo,
        room: Room,
        floor: Floor,
        gx: Int,
        gz: Int,
    ) {
        // Every doorway keeps its hotspot in building mode, joined or not: an unjoined one is
        // what you click to add a room, and a joined one is what you click to take the room on
        // the far side back out again.
        if (house.buildMode) {
            return
        }
        val side = turned(loc, room)
        // A garden is open to the sky and its template has no wall to speak of, so a doorway
        // leading nowhere is simply left open rather than filled with a slab of the house's wall.
        if (house.state.connected(floor, gx, gz, side) || room.type.outdoors) {
            locRepo.del(loc, PERMANENT)
        } else {
            replace(loc, room, house.state.style.wall)
        }
    }

    /** Returns the loc the hotspot became, or `null` when nothing was built there. */
    private fun dressHotspot(
        house: ActiveHouse,
        room: Room,
        group: HotspotGroup,
        name: String,
        loc: LocInfo,
    ): String? {
        val option = room.furniture[group.key]
        if (option == null) {
            if (!house.buildMode) {
                locRepo.del(loc, PERMANENT)
            }
            return null
        }
        val buildable = group.options.getOrNull(option) ?: return null
        val built = buildable.built[group.locs.indexOf(name)]
        replace(loc, room, built)
        return built
    }

    /**
     * Spawns [into] over a template loc.
     *
     * The region registry translates a copied loc's coordinates but leaves its angle as authored,
     * so a loc read back out of a rotated zone still carries the template's angle. A loc spawned on
     * top of it is stored exactly as given, so the rotation has to be applied here or the
     * replacement faces the wrong way in every room that is not placed at rotation zero.
     */
    private fun replace(loc: LocInfo, room: Room, into: String) {
        locRepo.add(loc.coords, into, PERMANENT, LocAngle[turned(loc, room)], loc.shape)
    }

    private fun turned(loc: LocInfo, room: Room): Int = (loc.angleId + room.rotation) and 3

    /**
     * Drops the matching downward staircase into the room above. Without it the upper floor would
     * be reachable but not leavable, because the template only authors the upward hotspot.
     */
    private fun placeStairsAbove(house: ActiveHouse, placed: PlacedStairs) {
        val above = Floor.entries.getOrNull(placed.floor.ordinal + 1) ?: return
        val upstairs = house.state[above, placed.gx, placed.gz] ?: return
        val down = Furniture.STAIRS_DOWN[placed.built] ?: return
        val zone = zoneOf(house, above, placed.gx, placed.gz)
        val local = placed.loc.coords
        val coords =
            zone.toCoords().translate(local.x - placed.base.x, local.z - placed.base.z, 0)
        locRepo.add(
            coords,
            down,
            PERMANENT,
            LocAngle[turned(placed.loc, upstairs)],
            placed.loc.shape,
        )
    }

    /** The room standing on [coords], or `null` when that cell is empty. */
    fun roomAt(house: ActiveHouse, coords: CoordGrid): Room? {
        val (floor, gx, gz) = cellOf(house, coords) ?: return null
        return house.state[floor, gx, gz]
    }

    private fun entrance(house: ActiveHouse): CoordGrid {
        val portal =
            house.state.rooms.entries.firstOrNull { (_, room) ->
                house.state.isEntrance(room)
            } ?: house.state.rooms.entries.firstOrNull() ?: return house.region.southWest
        val floor = floorOf(portal.key)
        val zone = zoneOf(house, floor, gxOf(portal.key), gzOf(portal.key))
        return zone.toCoords().translate(ENTRANCE_OFFSET_X, ENTRANCE_OFFSET_Z, 0)
    }

    private fun isHotspot(id: Int): Boolean =
        ServerCacheManager.getObject(id)?.actions?.getOpOrNull(BUILD_OP_INDEX) == BUILD_OP

    private fun locName(id: Int): String? =
        runCatching { RSCM.getReverseMapping(RSCMType.LOC, id) }.getOrNull()

    private class PlacedStairs(
        val floor: Floor,
        val gx: Int,
        val gz: Int,
        val loc: LocInfo,
        val base: CoordGrid,
        val built: String,
    )

    private companion object {
        /** Leaves a one-zone margin so the grid never touches the region's outer border. */
        const val GRID_ORIGIN = 1

        const val PERMANENT = Int.MAX_VALUE

        const val DYNAMIC_WINDOW = "loc.poh_dynamic_window"

        /** The style block's roof template, laid over the top storey of every indoor room. */
        const val ROOF_ZONE_OFFSET_X = 1
        const val ROOF_ZONE_Z = 882

        const val BUILD_OP_INDEX = 4
        const val BUILD_OP = "Build"

        /** Beside the garden centrepiece, which occupies the middle of its zone. */
        const val ENTRANCE_OFFSET_X = 2
        const val ENTRANCE_OFFSET_Z = 3

        private const val AXIS_BITS = 5
        private const val AXIS_MASK = (1 shl AXIS_BITS) - 1

        fun floorOf(key: Int): Floor = Floor.entries[key shr (AXIS_BITS * 2)]

        fun gxOf(key: Int): Int = (key shr AXIS_BITS) and AXIS_MASK

        fun gzOf(key: Int): Int = key and AXIS_MASK

        fun Region.offsetOf(coords: CoordGrid): Pair<Int, Int> =
            (coords.x - southWest.x) to (coords.z - southWest.z)

        fun Region.holds(coords: CoordGrid): Boolean =
            coords.x in southWest.x..northEast.x && coords.z in southWest.z..northEast.z

        operator fun Region.contains(coords: CoordGrid): Boolean = holds(coords)

        fun Player.houseKey(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }
    }
}
