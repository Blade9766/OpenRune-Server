package org.rsmod.content.other.castlewars

import org.rsmod.game.loc.LocAngle
import org.rsmod.map.CoordGrid

internal data class Region(val x0: Int, val z0: Int, val x1: Int, val z1: Int, val levels: IntRange = 0..3) {
    operator fun contains(coords: CoordGrid): Boolean =
        coords.x in x0..x1 && coords.z in z0..z1 && coords.level in levels
}

/** One leaf of a castle door: where it stands closed, and the locs it swaps between. */
internal data class DoorLeaf(
    val coords: CoordGrid,
    val angle: LocAngle,
    val closed: String,
    val open: String,
    val broken: String?,
    val openRotation: Int,
) {
    val openCoords: CoordGrid
        get() =
            when (angle) {
                LocAngle.West -> coords.translateX(-1)
                LocAngle.North -> coords.translateZ(1)
                LocAngle.East -> coords.translateX(1)
                LocAngle.South -> coords.translateZ(-1)
            }

    val openAngle: LocAngle
        get() = angle.turn(openRotation)
}

internal enum class Team(
    val displayName: String,
    private val key: String,
    val cloak: String,
    val banner: String,
    val standCoords: CoordGrid,
    val standAngle: LocAngle,
    val waitingRoom: CoordGrid,
    val waitingArea: Region,
    val spawnRoom: CoordGrid,
    val spawnArea: Region,
    val castle: Region,
    val tunnels: List<CoordGrid>,
    val catapultCoords: CoordGrid,
    val catapultAngle: LocAngle,
    val mainDoors: List<DoorLeaf>,
    val sideDoor: DoorLeaf,
    val wrongGodNpc: String,
    val barricadeNpc: String,
    val burningBarricadeNpc: String,
    val jingle: String,
) {
    Saradomin(
        displayName = "Saradomin",
        key = "saradomin",
        cloak = "obj.castlewars_cloak_saradomin",
        banner = "obj.castlewars_saradomin_banner",
        standCoords = CoordGrid(2429, 3074, 3),
        standAngle = LocAngle.North,
        waitingRoom = CoordGrid(2383, 9489, 0),
        waitingArea = Region(2369, 9480, 2398, 9498, 0..0),
        spawnRoom = CoordGrid(2427, 3076, 1),
        spawnArea = Region(2423, 3072, 2431, 3080, 1..1),
        castle = Region(2415, 3072, 2431, 3088),
        tunnels = listOf(CoordGrid(2401, 9494, 0), CoordGrid(2409, 9503, 0)),
        catapultCoords = CoordGrid(2413, 3088, 0),
        catapultAngle = LocAngle.South,
        mainDoors =
            listOf(
                DoorLeaf(
                    CoordGrid(2426, 3088, 0),
                    LocAngle.South,
                    "loc.castlewars_saradomin_maindoorr",
                    "loc.castlewars_saradomin_maindoorr_open",
                    "loc.castlewars_saradomin_brokendoorr",
                    openRotation = 1,
                ),
                DoorLeaf(
                    CoordGrid(2427, 3088, 0),
                    LocAngle.South,
                    "loc.castlewars_saradomin_maindoorl",
                    "loc.castlewars_saradomin_maindoorl_open",
                    "loc.castlewars_saradomin_brokendoorl",
                    openRotation = 3,
                ),
            ),
        sideDoor =
            DoorLeaf(
                CoordGrid(2415, 3073, 0),
                LocAngle.West,
                "loc.castlewars_saradomin_sidedoor",
                "loc.castlewars_saradomin_sidedoor_open",
                null,
                openRotation = 1,
            ),
        wrongGodNpc = "npc.castlewars_rabbit",
        barricadeNpc = "npc.castlewars_barricade_saradomin_render_under",
        burningBarricadeNpc = "npc.castlewars_barricade_burning_saradomin_render_under",
        jingle = "saradomin",
    ),
    Zamorak(
        displayName = "Zamorak",
        key = "zamorak",
        cloak = "obj.castlewars_cloak_zamorak",
        banner = "obj.castlewars_zamorak_banner",
        standCoords = CoordGrid(2370, 3133, 3),
        standAngle = LocAngle.South,
        waitingRoom = CoordGrid(2425, 9524, 0),
        waitingArea = Region(2417, 9515, 2432, 9535, 0..0),
        spawnRoom = CoordGrid(2372, 3131, 1),
        spawnArea = Region(2368, 3127, 2376, 3135, 1..1),
        castle = Region(2368, 3119, 2384, 3135),
        tunnels = listOf(CoordGrid(2391, 9501, 0), CoordGrid(2400, 9512, 0)),
        catapultCoords = CoordGrid(2384, 3117, 0),
        catapultAngle = LocAngle.North,
        mainDoors =
            listOf(
                DoorLeaf(
                    CoordGrid(2372, 3119, 0),
                    LocAngle.North,
                    "loc.castlewars_zamorak_maindoorl",
                    "loc.castlewars_zamorak_maindoorl_open",
                    "loc.castlewars_zamorak_brokendoorl",
                    openRotation = 3,
                ),
                DoorLeaf(
                    CoordGrid(2373, 3119, 0),
                    LocAngle.North,
                    "loc.castlewars_zamorak_maindoorr",
                    "loc.castlewars_zamorak_maindoorr_open",
                    "loc.castlewars_zamorak_brokendoorr",
                    openRotation = 1,
                ),
            ),
        sideDoor =
            DoorLeaf(
                CoordGrid(2384, 3134, 0),
                LocAngle.East,
                "loc.castlewars_zamorak_sidedoor",
                "loc.castlewars_zamorak_sidedoor_open",
                null,
                openRotation = 1,
            ),
        wrongGodNpc = "npc.castlewars_imp",
        barricadeNpc = "npc.castlewars_barricade_zamorak_render_under",
        burningBarricadeNpc = "npc.castlewars_barricade_burning_zamorak_render_under",
        jingle = "zamorak",
    );

    val opponent: Team
        get() = if (this == Saradomin) Zamorak else Saradomin

    val standLoc: String = "loc.castlewars_${key}_banner+stand"
    val emptyStandLoc: String = "loc.castlewars_${key}_stand"
    val droppedBannerLoc: String = "loc.castlewars_${key}_banner"
    val catapultLoc: String = "loc.castlewars_catapult_$key"
    val brokenCatapultLoc: String = "loc.castlewars_catapult_${key}_broken"
    val burningCatapultLoc: String = "loc.castlewars_catapult_${key}_burning"
    val overlay: String = "interface.castlewars_status_overlay_$key"
    val teamVarbit: String = "varbit.castlewars_${key}_team"
    val scoreVarbit: String = "varbit.castlewars_${key}_score"
    val flagVarbit: String = "varbit.castlewars_${key}_flag"
    val mainDoorVarbit: String = "varbit.castlewars_${key}_maindoor"
    val sideDoorVarbit: String = "varbit.castlewars_${key}_sidedoor"
    val catapultVarbit: String = "varbit.castlewars_${key}_catapult"
    val tunnelVarbits: List<String> =
        listOf("varbit.castlewars_${key}_tunnel1", "varbit.castlewars_${key}_tunnel2")
}

internal object CastleWars {
    const val GAME_MINUTES: Int = 20
    const val TICKS_PER_MINUTE: Int = 100
    const val GAME_TICKS: Int = GAME_MINUTES * TICKS_PER_MINUTE

    /** Non-dedicated worlds wait two minutes between games. */
    const val BREAK_TICKS: Int = 2 * TICKS_PER_MINUTE

    /** Time a player must spend in a game before the result earns them tickets. */
    const val REWARD_MIN_TICKS: Int = 15 * TICKS_PER_MINUTE

    const val MIN_PLAYERS_PER_TEAM: Int = 1
    const val DOOR_HITPOINTS: Int = 100
    const val BARRICADE_LIMIT: Int = 10
    const val TAKE_FROM_TICKS: Int = 3 * TICKS_PER_MINUTE

    val LOBBY: CoordGrid = CoordGrid(2440, 3088, 0)
    val LOBBY_AREA: Region = Region(2434, 3076, 2450, 3100, 0..1)
    val ARENA: Region = Region(2368, 3072, 2431, 3135)
    val TUNNELS: Region = Region(2368, 9472, 2431, 9535, 0..0)

    val STEPPING_STONES: Set<CoordGrid> =
        setOf(
            CoordGrid(2377, 3085, 0),
            CoordGrid(2377, 3086, 0),
            CoordGrid(2377, 3087, 0),
            CoordGrid(2377, 3088, 0),
            CoordGrid(2378, 3084, 0),
            CoordGrid(2378, 3085, 0),
            CoordGrid(2418, 3125, 0),
            CoordGrid(2419, 3123, 0),
            CoordGrid(2419, 3124, 0),
            CoordGrid(2419, 3125, 0),
            CoordGrid(2420, 3123, 0),
        )

    const val GUTHIX_NPC: String = "npc.castlewars_sheep"

    const val JINGLE_VICTORY: Int = 82
    const val JINGLE_DEFEAT: Int = 81
    const val JINGLE_DRAW: Int = 80

    const val TAKE_FROM_SLOT: Int = 3
    const val TAKE_FROM_OP: String = "Take from"
    const val TAKE_FROM_DELAY: Int = 300

    const val TICKET: String = "obj.castlewars_ticket"
    const val RUNE_POUCH: String = "obj.castlewars_rune_replacement"

    /** Ava's devices by the tier Lanthus remembers in `varbit.castlewars_ava_reward_tier`. */
    val AVAS_DEVICES: Map<String, Int> =
        mapOf(
            "obj.anma_30_reward" to 1,
            "obj.anma_50_reward" to 2,
            "obj.avas_assembler" to 3,
            "obj.avas_assembler_masori" to 4,
        )

    val BRACELETS: List<String> =
        listOf(
            "obj.jewl_castlewars_bracelet3",
            "obj.jewl_castlewars_bracelet2",
            "obj.jewl_castlewars_bracelet",
        )

    val BREWS: List<String> =
        listOf(
            "obj.4dose_castlewars_skill_potion",
            "obj.3dose_castlewars_skill_potion",
            "obj.2dose_castlewars_skill_potion",
            "obj.1dose_castlewars_skill_potion",
        )

    /** Everything handed out in the arena; none of it may leave a game. */
    val GAME_ITEMS: List<String> =
        listOf(
            "obj.castlewars_saradomin_banner",
            "obj.castlewars_zamorak_banner",
            "obj.castlewars_cloak_saradomin",
            "obj.castlewars_cloak_zamorak",
            "obj.castlewars_catapult_rock",
            "obj.castlewars_explosives_potion",
            "obj.castlewars_climbing_rope",
            "obj.castlewars_bandages",
            "obj.castlewars_toolkit",
            "obj.castlewars_barricade",
            "obj.tinderbox",
            "obj.bronze_pickaxe",
            "obj.bucket_empty",
            "obj.bucket_water",
            RUNE_POUCH,
        ) + BREWS

    /** Supply tables and what one "Take" hands out. */
    val TABLE_ITEMS: Map<String, String> =
        mapOf(
            "loc.castlewars_table_toolbox" to "obj.castlewars_toolkit",
            "loc.castlewars_table_rocks" to "obj.castlewars_catapult_rock",
            "loc.castlewars_table_barricades" to "obj.castlewars_barricade",
            "loc.castlewars_table_rope" to "obj.castlewars_climbing_rope",
            "loc.castlewars_table_potion" to "obj.castlewars_explosives_potion",
            "loc.castlewars_table_pickaxes" to "obj.bronze_pickaxe",
            "loc.castlewars_table_buckets" to "obj.bucket_empty",
            "loc.castlewars_table_skill_potion" to "obj.4dose_castlewars_skill_potion",
            "loc.castlewars_table_skill_potion_saradomin" to "obj.4dose_castlewars_skill_potion",
            "loc.castlewars_table_skill_potion_zamorak" to "obj.4dose_castlewars_skill_potion",
            "loc.castlewars_table_bandages_saradomin" to "obj.castlewars_bandages",
            "loc.castlewars_table_bandages_zamorak" to "obj.castlewars_bandages",
        )

    val TEAM_TABLES: Map<String, Team> =
        mapOf(
            "loc.castlewars_table_skill_potion_saradomin" to Team.Saradomin,
            "loc.castlewars_table_bandages_saradomin" to Team.Saradomin,
            "loc.castlewars_table_runepouch_saradomin" to Team.Saradomin,
            "loc.castlewars_table_skill_potion_zamorak" to Team.Zamorak,
            "loc.castlewars_table_bandages_zamorak" to Team.Zamorak,
            "loc.castlewars_table_runepouch_zamorak" to Team.Zamorak,
        )

    /** Cave walls beside each tunnel mouth; collapsing any of them fills that tunnel. */
    val CAVE_WALLS: Map<CoordGrid, CoordGrid> =
        mapOf(
            CoordGrid(2390, 9500, 0) to CoordGrid(2391, 9501, 0),
            CoordGrid(2390, 9503, 0) to CoordGrid(2391, 9501, 0),
            CoordGrid(2393, 9500, 0) to CoordGrid(2391, 9501, 0),
            CoordGrid(2393, 9503, 0) to CoordGrid(2391, 9501, 0),
            CoordGrid(2399, 9511, 0) to CoordGrid(2400, 9512, 0),
            CoordGrid(2399, 9514, 0) to CoordGrid(2400, 9512, 0),
            CoordGrid(2402, 9511, 0) to CoordGrid(2400, 9512, 0),
            CoordGrid(2402, 9514, 0) to CoordGrid(2400, 9512, 0),
            CoordGrid(2400, 9493, 0) to CoordGrid(2401, 9494, 0),
            CoordGrid(2400, 9496, 0) to CoordGrid(2401, 9494, 0),
            CoordGrid(2403, 9493, 0) to CoordGrid(2401, 9494, 0),
            CoordGrid(2403, 9496, 0) to CoordGrid(2401, 9494, 0),
            CoordGrid(2408, 9502, 0) to CoordGrid(2409, 9503, 0),
            CoordGrid(2408, 9505, 0) to CoordGrid(2409, 9503, 0),
            CoordGrid(2411, 9502, 0) to CoordGrid(2409, 9503, 0),
            CoordGrid(2411, 9505, 0) to CoordGrid(2409, 9503, 0),
        )

    val TUNNEL_ANGLES: Map<CoordGrid, LocAngle> =
        mapOf(
            CoordGrid(2391, 9501, 0) to LocAngle.North,
            CoordGrid(2400, 9512, 0) to LocAngle.West,
            CoordGrid(2401, 9494, 0) to LocAngle.West,
            CoordGrid(2409, 9503, 0) to LocAngle.North,
        )

    /**
     * Where each castle staircase going up lets out. The first-floor flight meets the roof beside
     * a walled walkway, and the generic stair landing picks whichever side is nearest the climber,
     * which could strand them on the walkway; the top flight has the same trouble with the roof
     * beside the standard room.
     */
    val CASTLE_STAIRS: Map<CoordGrid, CoordGrid> =
        mapOf(
            CoordGrid(2380, 3127, 0) to CoordGrid(2379, 3126, 1),
            CoordGrid(2369, 3126, 1) to CoordGrid(2369, 3127, 2),
            CoordGrid(2374, 3131, 2) to CoordGrid(2372, 3132, 3),
            CoordGrid(2419, 3078, 0) to CoordGrid(2420, 3081, 1),
            CoordGrid(2428, 3081, 1) to CoordGrid(2430, 3080, 2),
            CoordGrid(2425, 3074, 2) to CoordGrid(2427, 3075, 3),
        )

    /**
     * The stairs from the castle floor up onto the wall walkway that leads to the catapult: the
     * tile inside the castle and the tile on the wall a player is moved between. Both stay on the
     * same level, the walkway's raised tiles being bridged down onto it.
     */
    val LINKED_STAIRS: Map<CoordGrid, Pair<CoordGrid, CoordGrid>> =
        mapOf(
            CoordGrid(2417, 3074, 0) to (CoordGrid(2417, 3077, 0) to CoordGrid(2416, 3074, 0)),
            CoordGrid(2382, 3131, 0) to (CoordGrid(2382, 3130, 0) to CoordGrid(2383, 3133, 0)),
        )

    /** Energy barriers: the tile just inside the spawn room and the one outside it. */
    val SPAWN_BARRIERS: Map<CoordGrid, Pair<CoordGrid, CoordGrid>> =
        mapOf(
            CoordGrid(2423, 3076, 1) to (CoordGrid(2423, 3076, 1) to CoordGrid(2422, 3076, 1)),
            CoordGrid(2426, 3080, 1) to (CoordGrid(2426, 3080, 1) to CoordGrid(2426, 3081, 1)),
            CoordGrid(2373, 3127, 1) to (CoordGrid(2373, 3127, 1) to CoordGrid(2373, 3126, 1)),
            CoordGrid(2376, 3131, 1) to (CoordGrid(2376, 3131, 1) to CoordGrid(2377, 3131, 1)),
        )

    /** Items that show allegiance to a god, for the waiting-room transformations. */
    val GOD_NAMES: Map<String, List<String>> =
        mapOf(
            "saradomin" to listOf("saradomin", "holy book", "blessed"),
            "zamorak" to listOf("zamorak", "unholy book", "unholy"),
            "guthix" to listOf("guthix", "book of balance", "balance"),
        )
}
