package org.rsmod.content.other.castlewars.bots

import org.rsmod.content.other.castlewars.CastleWars
import org.rsmod.content.other.castlewars.DoorState
import org.rsmod.content.other.castlewars.Region
import org.rsmod.content.other.castlewars.Team
import org.rsmod.map.CoordGrid

/** The parts of the arena a bot can be in; moving between two of them takes a [Hop]. */
internal sealed interface Place {
    data class Floor(val team: Team, val level: Int) : Place

    data class Spawn(val team: Team) : Place

    /** The raised walkway along the castle walls that leads to the catapult. */
    data class Wall(val team: Team) : Place

    data object Field : Place

    data object Tunnels : Place

    data object Elsewhere : Place
}

/** The one action that takes a bot from its current [Place] into the next one on its route. */
internal sealed interface Hop {
    data class Walk(val coords: CoordGrid) : Hop

    data class UseLoc(val loc: String, val coords: CoordGrid, val op: Int = 1) : Hop
}

/** A flight of stairs, a ladder or a barrier: the loc to operate and the places it joins. */
private data class Link(val from: Place, val to: Place, val loc: String, val coords: CoordGrid, val owner: Team? = null)

/** One castle's ground-floor interior and gate, tile for tile, and its wall walkway. */
private data class Keep(
    val interior: Region,
    val gateInside: CoordGrid,
    val gateOutside: CoordGrid,
    val wall: List<Region>,
)

internal object BotMap {
    private val keeps =
        mapOf(
            Team.Saradomin to
                Keep(
                    interior = Region(2415, 3072, 2431, 3087, 0..0),
                    gateInside = CoordGrid(2426, 3087, 0),
                    gateOutside = CoordGrid(2426, 3089, 0),
                    wall =
                        listOf(
                            Region(2416, 3087, 2424, 3089, 0..0),
                            Region(2412, 3087, 2416, 3091, 0..0),
                            Region(2414, 3073, 2416, 3086, 0..0),
                        ),
                ),
            Team.Zamorak to
                Keep(
                    interior = Region(2368, 3120, 2384, 3135, 0..0),
                    gateInside = CoordGrid(2373, 3120, 0),
                    gateOutside = CoordGrid(2373, 3118, 0),
                    wall =
                        listOf(
                            Region(2375, 3118, 2383, 3120, 0..0),
                            Region(2383, 3116, 2387, 3120, 0..0),
                            Region(2383, 3121, 2385, 3134, 0..0),
                        ),
                ),
        )

    /**
     * The ground-floor staircases only reach a closed-off corridor of the first floor, so the ladder
     * is the way between the ground and first floors; stairs carry on from the first floor up.
     */
    private val links: List<Link> =
        stairs(
            Team.Saradomin,
            ladder = CoordGrid(2421, 3073, 0),
            up = listOf(CoordGrid(2428, 3081, 1), CoordGrid(2425, 3074, 2)),
            down = listOf(CoordGrid(2430, 3081, 2), CoordGrid(2425, 3074, 3)),
        ) +
            stairs(
                Team.Zamorak,
                ladder = CoordGrid(2378, 3134, 0),
                up = listOf(CoordGrid(2369, 3126, 1), CoordGrid(2374, 3131, 2)),
                down = listOf(CoordGrid(2369, 3126, 2), CoordGrid(2374, 3133, 3)),
            ) +
            listOf(
                spawnExit(Team.Saradomin, CoordGrid(2426, 3080, 1)),
                spawnExit(Team.Zamorak, CoordGrid(2373, 3127, 1)),
                Link(
                    Place.Spawn(Team.Saradomin),
                    Place.Floor(Team.Saradomin, 2),
                    "loc.castlewars_saradomin_spawnladder",
                    CoordGrid(2429, 3074, 1),
                    owner = Team.Saradomin,
                ),
                Link(
                    Place.Spawn(Team.Zamorak),
                    Place.Floor(Team.Zamorak, 2),
                    "loc.castlewars_zamorak_spawnladder",
                    CoordGrid(2370, 3133, 1),
                    owner = Team.Zamorak,
                ),
                Link(Place.Tunnels, Place.Field, "loc.ladder_from_cellar_directional", CoordGrid(2399, 9499, 0)),
            ) +
            Team.entries.flatMap { team ->
                val stairs = CastleWars.LINKED_STAIRS.keys.first { it in team.castle }
                val loc = "loc.castlewars_outsidestairs_${team.name.lowercase()}_linked"
                listOf(
                    Link(Place.Floor(team, 0), Place.Wall(team), loc, stairs),
                    Link(Place.Wall(team), Place.Floor(team, 0), loc, stairs),
                )
            }

    private fun stairs(team: Team, ladder: CoordGrid, up: List<CoordGrid>, down: List<CoordGrid>): List<Link> {
        val upLoc = "loc.castlewars_outsidestairs_${team.name.lowercase()}"
        val ladders =
            listOf(
                Link(Place.Floor(team, 0), Place.Floor(team, 1), "loc.ladder", ladder),
                Link(Place.Floor(team, 1), Place.Floor(team, 0), "loc.castlewars_laddertop", CoordGrid(ladder.x, ladder.z, 1)),
            )
        return ladders +
            up.map { coords -> Link(Place.Floor(team, coords.level), Place.Floor(team, coords.level + 1), upLoc, coords) } +
            down.map { coords ->
                Link(Place.Floor(team, coords.level), Place.Floor(team, coords.level - 1), "loc.castlewars_topstair", coords)
            }
    }

    private fun spawnExit(team: Team, barrier: CoordGrid): Link =
        Link(
            Place.Spawn(team),
            Place.Floor(team, 1),
            "loc.castlewars_${team.name.lowercase()}_spawndoor",
            barrier,
            owner = team,
        )

    fun placeOf(coords: CoordGrid): Place {
        for (team in Team.entries) {
            if (coords in team.spawnArea) {
                return Place.Spawn(team)
            }
            if (keeps.getValue(team).wall.any { coords in it }) {
                return Place.Wall(team)
            }
            if (coords.level == 0 && coords in keeps.getValue(team).interior) {
                return Place.Floor(team, 0)
            }
            if (coords.level > 0 && coords in team.castle) {
                return Place.Floor(team, coords.level)
            }
        }
        return when {
            coords in CastleWars.TUNNELS -> Place.Tunnels
            coords.level == 0 && coords in CastleWars.ARENA -> Place.Field
            else -> Place.Elsewhere
        }
    }

    /**
     * The first hop from [from] towards [to] for a bot on [team], by a breadth-first search over
     * the stairs, barriers and castle gates. A gate hop walks straight through when the gate is open
     * or broken; a closed gate is opened by its own team and battered down by the other.
     */
    fun nextHop(from: Place, to: Place, team: Team, gate: (Team) -> DoorState): Hop? {
        if (from == to) {
            return null
        }
        val firstHop = HashMap<Place, Hop>()
        val queue = ArrayDeque<Place>()
        queue += from
        val seen = hashSetOf(from)
        while (queue.isNotEmpty()) {
            val place = queue.removeFirst()
            for ((next, hop) in neighbours(place, team, gate)) {
                if (!seen.add(next)) {
                    continue
                }
                firstHop[next] = firstHop[place] ?: hop
                if (next == to) {
                    return firstHop[next]
                }
                queue += next
            }
        }
        return null
    }

    private fun neighbours(place: Place, team: Team, gate: (Team) -> DoorState): List<Pair<Place, Hop>> {
        val result = ArrayList<Pair<Place, Hop>>()
        for (link in links) {
            if (link.from == place && (link.owner == null || link.owner == team)) {
                result += link.to to Hop.UseLoc(link.loc, link.coords)
            }
        }
        for ((owner, keep) in keeps) {
            val ground = Place.Floor(owner, 0)
            val outwards = place == ground
            if (place != Place.Field && !outwards) {
                continue
            }
            val next = if (outwards) Place.Field else ground
            val walkTo = if (outwards) keep.gateOutside else keep.gateInside
            val hop =
                when (gate(owner)) {
                    DoorState.Open, DoorState.Broken -> Hop.Walk(walkTo)
                    DoorState.Closed ->
                        Hop.UseLoc(owner.mainDoors.first().closed, owner.mainDoors.first().coords, if (owner == team) 1 else 2)
                }
            result += next to hop
        }
        return result
    }

    /** The tile beside [team]'s catapult a bot works it from. */
    fun catapultPost(team: Team): CoordGrid =
        when (team) {
            Team.Saradomin -> CoordGrid(2416, 3089, 0)
            Team.Zamorak -> CoordGrid(2383, 3118, 0)
        }

    /** [team]'s rock table and barricade table in its castle's supply room. */
    fun rockTable(team: Team): Pair<String, CoordGrid> =
        "loc.castlewars_table_rocks" to
            when (team) {
                Team.Saradomin -> CoordGrid(2423, 3075, 0)
                Team.Zamorak -> CoordGrid(2376, 3131, 0)
            }

    fun barricadeTable(team: Team): Pair<String, CoordGrid> =
        "loc.castlewars_table_barricades" to
            when (team) {
                Team.Saradomin -> CoordGrid(2429, 3073, 0)
                Team.Zamorak -> CoordGrid(2370, 3133, 0)
            }

    fun toolboxTable(team: Team): Pair<String, CoordGrid> =
        "loc.castlewars_table_toolbox" to
            when (team) {
                Team.Saradomin -> CoordGrid(2429, 3075, 0)
                Team.Zamorak -> CoordGrid(2370, 3131, 0)
            }

    fun bucketTable(team: Team): Pair<String, CoordGrid> =
        "loc.castlewars_table_buckets" to
            when (team) {
                Team.Saradomin -> CoordGrid(2428, 3079, 0)
                Team.Zamorak -> CoordGrid(2371, 3127, 0)
            }

    fun tap(team: Team): CoordGrid =
        when (team) {
            Team.Saradomin -> CoordGrid(2431, 3077, 0)
            Team.Zamorak -> CoordGrid(2368, 3130, 0)
        }

    /** Where [team]'s respawn room tinderbox lies when nobody has taken it. */
    fun tinderboxSpawn(team: Team): CoordGrid =
        when (team) {
            Team.Saradomin -> CoordGrid(2423, 3080, 1)
            Team.Zamorak -> CoordGrid(2376, 3127, 1)
        }

    /** Field tiles either side of the lane out of [team]'s gate, left open so the gate stays usable. */
    fun barricadeSpots(team: Team): List<CoordGrid> =
        when (team) {
            Team.Saradomin ->
                listOf(CoordGrid(2429, 3091, 0), CoordGrid(2428, 3091, 0), CoordGrid(2424, 3091, 0), CoordGrid(2423, 3091, 0))
            Team.Zamorak ->
                listOf(CoordGrid(2370, 3116, 0), CoordGrid(2371, 3116, 0), CoordGrid(2375, 3116, 0), CoordGrid(2376, 3116, 0))
        }

    /** [team]'s bandage table in its respawn room. */
    fun bandageTable(team: Team): Pair<String, CoordGrid> =
        when (team) {
            Team.Saradomin -> "loc.castlewars_table_bandages_saradomin" to CoordGrid(2423, 3078, 1)
            Team.Zamorak -> "loc.castlewars_table_bandages_zamorak" to CoordGrid(2376, 3128, 1)
        }

    /** Where to stand to look after [team]'s standard. */
    fun guardPost(team: Team): CoordGrid =
        when (team) {
            Team.Saradomin -> CoordGrid(2427, 3077, 3)
            Team.Zamorak -> CoordGrid(2372, 3130, 3)
        }

    /** The middle of the arena, between the two castles. */
    val midfield: CoordGrid = CoordGrid(2400, 3103, 0)
}
