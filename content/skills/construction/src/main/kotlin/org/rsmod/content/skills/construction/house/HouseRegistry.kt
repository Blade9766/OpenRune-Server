package org.rsmod.content.skills.construction.house

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.region.RegionRepository
import org.rsmod.api.repo.region.RegionTemplate
import org.rsmod.content.generic.locs.doors.DoorTranslations
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Chapel
import org.rsmod.content.skills.construction.data.Combat
import org.rsmod.content.skills.construction.data.Dungeon
import org.rsmod.content.skills.construction.data.Floor
import org.rsmod.content.skills.construction.data.Furniture
import org.rsmod.content.skills.construction.data.Gallery
import org.rsmod.content.skills.construction.data.Heraldry
import org.rsmod.content.skills.construction.data.Heraldry.Crest
import org.rsmod.content.skills.construction.data.HotspotGroup
import org.rsmod.content.skills.construction.data.HouseDoors
import org.rsmod.content.skills.construction.data.HouseStyle
import org.rsmod.content.skills.construction.data.Leagues
import org.rsmod.content.skills.construction.data.Nexus
import org.rsmod.content.skills.construction.data.Oubliette
import org.rsmod.content.skills.construction.data.Portals
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.content.skills.construction.data.Side
import org.rsmod.content.skills.construction.data.Topiary
import org.rsmod.content.skills.construction.data.Trophies
import org.rsmod.content.skills.construction.scripts.familyCrest
import org.rsmod.game.entity.Player
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.game.region.Region
import org.rsmod.game.region.zone.RegionZoneCopy
import org.rsmod.map.CoordGrid
import org.rsmod.map.zone.ZoneKey

/**
 * A house that is currently standing in a runtime region. Its [owner] is swapped for the owner's
 * new player when they come back to a house they left standing for its guests after logging out.
 */
class ActiveHouse(
    val region: Region,
    val state: HouseState,
    val buildMode: Boolean,
    owner: Player,
    val size: Int = HouseLayout.MAX_SIZE,
) {
    var owner: Player = owner
        internal set

    /** The dungeon's traps, by the tile each one lies on, while the house is not being built. */
    val traps: MutableMap<CoordGrid, Dungeon.Trap> = HashMap()

    /** When each sprung trap is ready to catch someone again, in map cycles. */
    val trapRest: MutableMap<CoordGrid, Int> = HashMap()

    /** Where each guard stands, and which npc it is, while the house is not being built. */
    val guards: MutableMap<CoordGrid, String> = HashMap()

    /**
     * Which hotspot group each furnished tile was dressed from. Some groups in a room build the same
     * locs - a dungeon room's two doors, the portal chamber's three frames - so a built loc's name
     * alone can't say which one it is.
     */
    val hotspotAt: MutableMap<CoordGrid, String> = HashMap()

    /** Each oubliette's pit, by its cell key, in the order the house lists its rooms. */
    val pits: MutableMap<Int, Pit> = LinkedHashMap()

    /** Each throne room's trap floor, by its cell key. */
    val mats: MutableMap<Int, ThroneMat> = HashMap()

    /** Each combat room's ring, by its cell key. */
    val rings: MutableMap<Int, CombatRing> = HashMap()
}

/** A combat room's ring: the floor its fighters stand on, which differs by [ring]. */
class CombatRing(val ring: Combat.Ring) {
    val tiles: MutableSet<CoordGrid> = HashSet()

    /**
     * The tile just outside the ring from [coords], across the nearest edge of the ring's floor -
     * where a fighter who dies in it comes back.
     */
    fun exitFrom(coords: CoordGrid): CoordGrid {
        val minX = tiles.minOf { it.x }
        val maxX = tiles.maxOf { it.x }
        val minZ = tiles.minOf { it.z }
        val maxZ = tiles.maxOf { it.z }
        val x = coords.x.coerceIn(minX, maxX)
        val z = coords.z.coerceIn(minZ, maxZ)
        val edges =
            listOf(
                (x - minX) to CoordGrid(minX - 1, z, coords.level),
                (maxX - x) to CoordGrid(maxX + 1, z, coords.level),
                (z - minZ) to CoordGrid(x, minZ - 1, coords.level),
                (maxZ - z) to CoordGrid(x, maxZ + 1, coords.level),
            )
        return edges.minBy { it.first }.second
    }
}

/** An oubliette's pit: the tiles a victim can land on, and what waits at the bottom. */
class Pit(val hazard: Oubliette.Hazard?) {
    val tiles: MutableSet<CoordGrid> = HashSet()
}

/** A throne room's trap floor: the mat's tiles, and who its cage is holding while it is down. */
class ThroneMat(val floor: Oubliette.ThroneFloor) {
    val tiles: MutableSet<CoordGrid> = HashSet()
    val held: MutableSet<Player> = HashSet()

    /** Where the cage was dropped, while it is down. */
    var cage: LocInfo? = null

    /** While a cage is being lifted, another pull of the lever does nothing. */
    var lifting: Boolean = false

    /** The mat's south-west tile, where a cage is dropped over it. */
    val corner: CoordGrid
        get() = CoordGrid(tiles.minOf { it.x }, tiles.minOf { it.z }, tiles.first().level)
}

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
constructor(
    private val regionRepo: RegionRepository,
    private val locRepo: LocRepository,
    private val servants: HouseServants,
    private val guards: HouseGuards,
    private val pets: HousePets,
    private val games: HouseGames,
) {
    private val active = HashMap<Long, ActiveHouse>()

    /** Houses whose owner has gone, still standing for the guests left in them. */
    private val vacated = ArrayList<ActiveHouse>()

    /** Where each player scrying from a house left it, and whose house that is. */
    private val scrying = HashMap<Player, Scry>()

    /** A scry under way; [sendOut] is set when its house put its guests out meanwhile. */
    class Scry(val origin: CoordGrid, val owner: Player) {
        var sendOut: Boolean = false
    }

    /** Marks everyone scrying from [house] to come back outside it, as its guests were sent. */
    fun sendOutScriers(house: ActiveHouse) {
        scrying.filter { (player, scry) -> player !== house.owner && scry.origin in house.region }.values.forEach { it.sendOut = true }
    }

    /** Notes that [player] is off scrying from [house], so it stays up for them while they look. */
    fun beginScry(player: Player, house: ActiveHouse) {
        scrying[player] = Scry(player.coords, house.owner)
    }

    fun endScry(player: Player): Scry? = scrying.remove(player)

    fun isScrying(player: Player): Boolean = player in scrying

    fun active(player: Player): ActiveHouse? = active[player.houseKey()]

    /** The standing house [coords] is in, whoever owns it and whether or not they are still in it. */
    fun houseAt(coords: CoordGrid): ActiveHouse? =
        active.values.firstOrNull { coords in it.region } ?: vacated.firstOrNull { coords in it.region }

    fun isVacated(house: ActiveHouse): Boolean = house in vacated

    /** [owner]'s house while it stands, whether they are in it or have left it to its guests. */
    fun standingHouseOf(owner: Player): ActiveHouse? =
        active(owner) ?: vacated.firstOrNull { it.owner.houseKey() == owner.houseKey() }

    /**
     * The owner has gone. As the wiki has it, guests stay: the house goes on standing while any of
     * them is in it, and comes down once the last one has left (see [sweep]).
     */
    fun vacate(owner: Player, players: Iterable<Player>) {
        val house = active.remove(owner.houseKey()) ?: return
        if (guests(house, players).isEmpty() && scrying.values.none { it.origin in house.region }) {
            takeDown(house)
        } else {
            vacated += house
        }
    }

    /** Hands a house [owner] left standing for its guests back to them, as their active house. */
    fun reclaim(owner: Player): ActiveHouse? {
        val house = vacated.firstOrNull { it.owner.houseKey() == owner.houseKey() } ?: return null
        vacated -= house
        house.owner = owner
        active[owner.houseKey()] = house
        return house
    }

    /** Takes down every vacated house nobody is left in. */
    fun sweep(players: Iterable<Player>) {
        vacated.removeAll { house ->
            val empty = guests(house, players).isEmpty() && scrying.values.none { it.origin in house.region }
            if (empty) {
                takeDown(house)
            }
            empty
        }
    }

    /** Everyone in [house] apart from its owner. */
    fun guests(house: ActiveHouse, players: Iterable<Player>): List<Player> =
        players.filter { it !== house.owner && it.coords in house.region }

    /** Where [coords] in [from] lands in [to], the same house rebuilt, or null if it is off the map. */
    fun carry(from: ActiveHouse, to: ActiveHouse, coords: CoordGrid): CoordGrid? {
        val (dx, dz) = from.region.offsetOf(coords)
        val moved = to.region.southWest.translate(dx, dz, 0)
        val landed = CoordGrid(moved.x, moved.z, coords.level)
        return landed.takeIf { to.region.holds(it) && standsIn(to, it) }
    }

    fun entranceOf(house: ActiveHouse): CoordGrid = entrance(house)

    fun isInside(player: Player): Boolean {
        val house = active(player) ?: return false
        return player.coords in house.region
    }

    /**
     * Stands the house up in a fresh region and returns the tile to drop the player on, or `null`
     * when no region slot was free.
     */
    fun open(player: Player, state: HouseState, buildMode: Boolean): CoordGrid? {
        val size = HouseLayout.sizeOf(player)
        val region = regionRepo.add(template(state, size)) ?: return null
        regionRepo.protect(region)

        val house = ActiveHouse(region, state, buildMode, player, size)
        close(player)
        active[player.houseKey()] = house
        dress(house, player)
        val entrance = entrance(house)
        servants.spawn(player, state, entrance)
        guards.spawn(player, house.guards)
        if (!buildMode) {
            pets.spawn(player, petTiles(house))
        }
        return entrance
    }

    /**
     * Rebuilds the house in place, keeping the player where they were standing. [state] is what to
     * build it from: the house's own, or a copy for a preview that must not be saved.
     */
    fun reopen(player: Player, buildMode: Boolean, state: HouseState? = null): CoordGrid? {
        val current = active(player) ?: return null
        val offset = current.region.offsetOf(player.coords)
        val entry = open(player, state ?: current.state, buildMode) ?: return null
        val rebuilt = active(player) ?: return entry
        val restored = rebuilt.region.southWest.translate(offset.first, offset.second, 0)
        if (!rebuilt.region.holds(restored)) {
            return entry
        }
        val coords = CoordGrid(restored.x, restored.z, player.coords.level)
        return if (standsIn(rebuilt, coords)) coords else entry
    }

    /**
     * Drops the standing house. The region is only unprotected, not deleted: the registry clears
     * regions with nobody in them the next time one is allocated, and doing it here would pull the
     * ground out from under a player who is still being moved out of it.
     */
    fun close(player: Player) {
        val house = active.remove(player.houseKey()) ?: return
        takeDown(house)
    }

    private fun takeDown(house: ActiveHouse) {
        val owner = house.owner
        servants.despawn(owner)
        guards.despawn(owner)
        pets.despawn(owner)
        games.close(owner)
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

    /**
     * Every room's template, a roof over each top storey, and the style's filler over every empty
     * cell: the lawn the house stands on at ground level, only as far as the owner's yard reaches
     * (see [HouseLayout.inYard]), and solid rock around a dungeon.
     */
    private fun template(state: HouseState, size: Int): RegionTemplate =
        RegionTemplate.create {
            val dungeon = state.rooms.keys.any { floorOf(it) == Floor.DUNGEON }
            for (gx in 0 until Construction.GRID) {
                for (gz in 0 until Construction.GRID) {
                    if (state[Floor.GROUND, gx, gz] == null && HouseLayout.inYard(gx, gz, size)) {
                        this[GRID_ORIGIN + gx, GRID_ORIGIN + gz, Floor.GROUND.regionLevel] =
                            RegionZoneCopy(state.style.grassZone, 0, null)
                    }
                    if (dungeon && state[Floor.DUNGEON, gx, gz] == null) {
                        this[GRID_ORIGIN + gx, GRID_ORIGIN + gz, Floor.DUNGEON.regionLevel] =
                            RegionZoneCopy(state.style.rockZone, 0, null)
                    }
                }
            }
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
    private fun dress(house: ActiveHouse, owner: Player) {
        val stairs = ArrayList<PlacedStairs>()
        for ((key, room) in house.state.rooms) {
            val floor = floorOf(key)
            val gx = gxOf(key)
            val gz = gzOf(key)
            val zone = zoneOf(house, floor, gx, gz)
            val base = zone.toCoords()
            for (loc in locRepo.findAll(zone).toList()) {
                val name = locName(loc.id) ?: continue
                if (!house.buildMode) {
                    recordTrapTile(house, key, room, name, loc.coords)
                }
                when {
                    name == DYNAMIC_WINDOW -> dressWindow(house, loc, room, floor, gx, gz)
                    name == house.state.style.doorLeft || name == house.state.style.doorRight ->
                        dressDoor(house, loc, room, floor, gx, gz, owner)
                    name == DUNGEON_DOOR -> dressDungeonDoor(house, loc, room, floor, gx, gz)
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
                        val placed = dressHotspot(house, room, group, name, loc, owner)
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
        owner: Player,
    ) {
        // Every doorway keeps its hotspot in building mode, joined or not: an unjoined one is
        // what you click to add a room, and a joined one is what you click to take the room on
        // the far side back out again.
        if (house.buildMode) {
            return
        }
        val side = turned(loc, room)
        val nx = gx + Side.deltaX(side)
        val nz = gz + Side.deltaZ(side)
        val neighbour = house.state[floor, nx, nz]
        // On the ground floor an empty cell in the yard is lawn, so a doorway onto it leads outside.
        val outside = floor == Floor.GROUND && neighbour == null && HouseLayout.inYard(nx, nz, house.size)
        if (!outside && !house.state.connected(floor, gx, gz, side)) {
            // A garden is open to the sky and its template has no wall to speak of, so a doorway
            // leading nowhere is simply left open rather than filled with a slab of the house's wall.
            if (room.type.outdoors) {
                locRepo.del(loc, PERMANENT)
            } else {
                replace(loc, room, house.state.style.wall)
            }
            return
        }
        val doors = owner.vars[DOORS_VARBIT]
        if (doors == DOORS_NONE || !hangsDoor(room, neighbour, side)) {
            locRepo.del(loc, PERMANENT)
            return
        }
        hangDoor(loc, side, house.state.style.doors, open = doors == DOORS_OPEN)
    }

    /**
     * A window only ever looks outside: on a wall shared with another indoor room it would show
     * straight into that room, so the wall there is left solid. A garden alongside counts as outside.
     */
    private fun dressWindow(house: ActiveHouse, loc: LocInfo, room: Room, floor: Floor, gx: Int, gz: Int) {
        val side = turned(loc, room)
        val neighbour = house.state[floor, gx + Side.deltaX(side), gz + Side.deltaZ(side)]
        val style = house.state.style
        replace(loc, room, if (neighbour != null && !neighbour.type.outdoors) style.wall else style.window)
    }

    /**
     * A dungeon's walls are solid blocks rather than wall edges, so a dungeon doorway is either left
     * open into the room it joins or filled with a block of wall. The oubliette is the exception:
     * its walls are edges, so its doorway gets an edge of the same wall. Build mode keeps every
     * hotspot, as it does above ground.
     */
    private fun dressDungeonDoor(house: ActiveHouse, loc: LocInfo, room: Room, floor: Floor, gx: Int, gz: Int) {
        if (house.buildMode) {
            return
        }
        locRepo.del(loc, PERMANENT)
        if (house.state.connected(floor, gx, gz, turned(loc, room))) {
            return
        }
        if (room.type == RoomType.OUBLIETTE) {
            replace(loc, room, OUBLIETTE_WALL)
        } else {
            locRepo.add(loc.coords, DUNGEON_WALL, PERMANENT, LocAngle.West, LocShape.CentrepieceStraight)
        }
    }

    /**
     * Two joined rooms author their doorways on the same wall line, so only one of them may hang
     * the door: the indoor room when the other side is a garden, otherwise the room to the west or
     * south of the join.
     */
    private fun hangsDoor(room: Room, neighbour: Room?, side: Int): Boolean =
        when {
            room.type.outdoors -> false
            neighbour == null || neighbour.type.outdoors -> true
            else -> side == Side.EAST || side == Side.NORTH
        }

    /**
     * Hangs one panel of the style's double door over a door hotspot. Which panel it is follows the
     * double door script: a left panel's partner is at [DoorTranslations.translateClose], so the
     * panel is the left one when that tile is the other half of this doorway. An open door is moved
     * and turned exactly as the script would open it.
     */
    private fun hangDoor(loc: LocInfo, side: Int, doors: HouseDoors, open: Boolean) {
        val angle = LocAngle[side]
        val partner = DoorTranslations.translateClose(loc.coords, loc.shape, angle)
        val along = if (side == Side.WEST || side == Side.EAST) partner.z else partner.x
        val left = (along and ZONE_MASK) in DOORWAY
        locRepo.del(loc, PERMANENT)
        if (!open) {
            locRepo.add(loc.coords, if (left) doors.left else doors.right, PERMANENT, angle, loc.shape)
            return
        }
        val turn = if (left) LEFT_OPEN_TURN else RIGHT_OPEN_TURN
        locRepo.add(
            DoorTranslations.translateOpen(loc.coords, loc.shape, angle),
            if (left) doors.leftOpen else doors.rightOpen,
            PERMANENT,
            LocAngle[(side + turn) and 3],
            loc.shape,
        )
    }

    private fun recordTrapTile(house: ActiveHouse, key: Int, room: Room, name: String, coords: CoordGrid) {
        when (room.type) {
            RoomType.OUBLIETTE ->
                if (name in Oubliette.PIT_HOTSPOTS) {
                    val hazard = room.furniture[FLOOR]?.let(Oubliette.Hazard.entries::getOrNull)
                    house.pits.getOrPut(key) { Pit(hazard) }.tiles += coords
                }
            RoomType.THRONE_ROOM -> {
                val floor = room.furniture[FLOOR]?.let(Oubliette.ThroneFloor.entries::getOrNull) ?: return
                if (name in room.type.hotspot(FLOOR)?.locs.orEmpty()) {
                    house.mats.getOrPut(key) { ThroneMat(floor) }.tiles += coords
                }
            }
            RoomType.COMBAT_ROOM -> {
                val ring = room.furniture[RING]?.let(Combat.Ring.entries::getOrNull) ?: return
                val index = Combat.RING_HOTSPOTS.indexOf(name)
                if (name in Combat.RING_FLOORS && ring.built[index] != name) {
                    house.rings.getOrPut(key) { CombatRing(ring) }.tiles += coords
                }
            }
            else -> Unit
        }
    }

    /** The combat ring whose floor [coords] is on, if any. */
    fun ringAt(house: ActiveHouse, coords: CoordGrid): CombatRing? = house.rings.values.firstOrNull { coords in it.tiles }

    /** The oubliette pit [coords] lies in, if any. */
    fun pitAt(house: ActiveHouse, coords: CoordGrid): Pit? = house.pits.values.firstOrNull { coords in it.tiles }

    /** The pit of the oubliette directly beneath the throne room [coords] is in. */
    fun pitBelow(house: ActiveHouse, coords: CoordGrid): Pit? {
        val (floor, gx, gz) = cellOf(house, coords) ?: return null
        if (floor != Floor.GROUND) {
            return null
        }
        return house.pits[HouseState.key(Floor.DUNGEON, gx, gz)]
    }

    /** The trap floor of the throne room [coords] is in. */
    fun matAt(house: ActiveHouse, coords: CoordGrid): ThroneMat? {
        val (floor, gx, gz) = cellOf(house, coords) ?: return null
        return house.mats[HouseState.key(floor, gx, gz)]
    }

    /** The first loc named one of [names] in the room one floor up or down from [coords]. */
    fun locAcross(house: ActiveHouse, coords: CoordGrid, up: Boolean, names: Collection<String>): LocInfo? {
        val (floor, gx, gz) = cellOf(house, coords) ?: return null
        val other = Floor.entries.getOrNull(floor.ordinal + if (up) 1 else -1) ?: return null
        house.state[other, gx, gz] ?: return null
        return locRepo.findAll(zoneOf(house, other, gx, gz)).firstOrNull { locName(it.id) in names }
    }

    /** The middle of the room one floor up or down from [coords], if there is a room there. */
    fun centreAcross(house: ActiveHouse, coords: CoordGrid, up: Boolean): CoordGrid? {
        val (floor, gx, gz) = cellOf(house, coords) ?: return null
        val other = Floor.entries.getOrNull(floor.ordinal + if (up) 1 else -1) ?: return null
        house.state[other, gx, gz] ?: return null
        return zoneOf(house, other, gx, gz).toCoords().translate(ROOM_MIDDLE, ROOM_MIDDLE, 0)
    }

    /** The free tiles of the house's first menagerie, where its pets roam. */
    /** The clear inner tiles of every room in [house], on every floor, shuffled. */
    fun hidingTiles(house: ActiveHouse): List<CoordGrid> =
        Floor.entries
            .flatMap { floor -> house.state.cells(floor).map { (gx, gz) -> zoneOf(house, floor, gx, gz).toCoords() } }
            .flatMap { base -> PET_TILES.map { (x, z) -> base.translate(x, z, 0) } }
            .filter { tile -> locRepo.findAll(tile).none() }
            .shuffled()

    private fun petTiles(house: ActiveHouse): List<CoordGrid> {
        val key =
            house.state.rooms.entries
                .firstOrNull { it.value.type == RoomType.MENAGERIE_INDOOR || it.value.type == RoomType.MENAGERIE_OUTDOOR }
                ?.key ?: return emptyList()
        val base = zoneOf(house, floorOf(key), gxOf(key), gzOf(key)).toCoords()
        return PET_TILES.map { (x, z) -> base.translate(x, z, 0) }
            .filter { tile -> locRepo.findAll(tile).none() }
            .shuffled()
    }

    /** Returns the loc the hotspot became, or `null` when nothing was built there. */
    private fun dressHotspot(
        house: ActiveHouse,
        room: Room,
        group: HotspotGroup,
        name: String,
        loc: LocInfo,
        owner: Player,
    ): String? {
        house.hotspotAt[loc.coords] = group.key
        val option = room.furniture[group.key]
        if (option == null) {
            if (!house.buildMode) {
                locRepo.del(loc, PERMANENT)
            }
            return null
        }
        val buildable = group.options.getOrNull(option) ?: return null
        val built = Chapel.dedicate(buildable.built[group.locs.indexOf(name)], room.furniture[ICON])
        if (built in group.locs) {
            if (!house.buildMode) {
                locRepo.del(loc, PERMANENT)
            }
            return null
        }
        val guard = Dungeon.guardNpc(built)
        if (guard != null && !house.buildMode) {
            house.guards[loc.coords] = guard
            locRepo.del(loc, PERMANENT)
            return built
        }
        val trap = Dungeon.Trap.of(built)
        if (trap != null && !house.buildMode) {
            house.traps[loc.coords] = trap
            replace(loc, room, trap.hidden)
            return built
        }
        val shown =
            trophyOn(built, owner)
                ?: portalOn(built, group.key, owner)
                ?: Combat.shownDummy(built, owner.vars[Combat.VARIANTS_VARP])
                ?: Nexus.Amulet.entries.firstOrNull { it.loc == built }?.let { it.shown(owner.vars[it.varbit]) }
                ?: Leagues.shown(built) { owner.vars[it] }
                ?: Gallery.shown(built, owner.vars[Gallery.LAIR_VARP], owner.vars[Gallery.CAPE_VARP])
                ?: Topiary.shown(built, owner.vars[Topiary.VARP])
                ?: Heraldry.crestDecor(built, Crest.of(owner.familyCrest))
                ?: built
        replace(loc, room, shown)
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
    /** The portal an empty frame shows, once the owner has directed its space somewhere. */
    private fun portalOn(frame: String, groupKey: String, owner: Player): String? {
        if (frame !in Portals.FRAMES) {
            return null
        }
        val space = groupKey.removePrefix(PORTAL_KEY_PREFIX).toIntOrNull() ?: return null
        val varbit = Portals.VARBITS.getOrNull(space - 1) ?: return null
        return Portals.Destination.of(owner.vars[varbit])?.portal(frame)
    }

    /** The trophy an empty display shows, from the owner's mounted trophies. */
    private fun trophyOn(display: String, owner: Player): String? {
        val (kind, tier) = Trophies.DISPLAYS[display] ?: return null
        if (display != kind.blank(tier)) {
            return null
        }
        return Trophies.shownOn(kind, tier, owner.shownTrophy(kind), owner.mountedTrophies(kind))
    }

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
        // Stairs under a garden come up through its dungeon entrance instead.
        if (upstairs.type.outdoors) {
            return
        }
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
    /** True when [coords] is somewhere to stand: inside a room, or out on the yard's lawn. */
    private fun standsIn(house: ActiveHouse, coords: CoordGrid): Boolean {
        if (roomAt(house, coords) != null) {
            return true
        }
        val (floor, gx, gz) = cellOf(house, coords) ?: return false
        return floor == Floor.GROUND && HouseLayout.inYard(gx, gz, house.size)
    }

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

        const val DOORS_VARBIT = "varbit.poh_doors_option"
        const val DUNGEON_DOOR = "loc.poh_hotspot_door_dungeon"
        const val DUNGEON_WALL = "loc.dungeon_walltop"
        const val OUBLIETTE_WALL = "loc.dungeon_outsidewall"
        const val DOORS_OPEN = 1
        const val DOORS_NONE = 2

        /** The two tiles of a doorway along its wall, local to the room's zone. */
        val DOORWAY = 3..4
        const val ZONE_MASK = 7

        /** How far each panel turns as it swings open, as the double door script turns it. */
        const val LEFT_OPEN_TURN = 3
        const val RIGHT_OPEN_TURN = 1
        const val ICON = "icon"
        const val FLOOR = "floor"
        const val RING = "ring"
        const val PORTAL_KEY_PREFIX = "portal_"

        /** A menagerie's inner tiles, clear of its walls. */
        val PET_TILES: List<Pair<Int, Int>> = (1..6).flatMap { x -> (1..6).map { z -> x to z } }
        const val ROOM_MIDDLE = 3

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

        /** [Region.northEast] is the first tile past the region, which belongs to the next one. */
        fun Region.holds(coords: CoordGrid): Boolean =
            coords.x in southWest.x until northEast.x && coords.z in southWest.z until northEast.z

        operator fun Region.contains(coords: CoordGrid): Boolean = holds(coords)

        fun Player.houseKey(): Long = requireNotNull(uuid) { "Player has no uuid: $this" }
    }
}
