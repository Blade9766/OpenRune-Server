package org.rsmod.content.skills.construction.data

/**
 * The hotspots each room offers and what can be built on them.
 *
 * Hotspot loc names and the locs they turn into come from the cache; the levels, materials and
 * experience come from the Old School wiki. A hotspot whose data could not be sourced - the skill
 * hall's trophy spaces, the chapel statues - is simply left out, and the build script hides it
 * rather than offering something invented.
 */
object Furniture {
    private const val PLANK = "obj.woodplank"
    private const val OAK = "obj.plank_oak"
    private const val TEAK = "obj.plank_teak"
    private const val MAHOGANY = "obj.plank_mahogany"
    private const val NAILS = "obj.nails"
    private const val CLOTH = "obj.cloth"
    private const val SOFT_CLAY = "obj.softclay"
    private const val LIMESTONE = "obj.limestonebrick"
    private const val MARBLE = "obj.marble_block"
    private const val COINS = "obj.coins"
    private const val GOLD_LEAF = "obj.gold_leaf"
    private const val STEEL_BAR = "obj.steel_bar"
    private const val GOLD_BAR = "obj.gold_bar"
    private const val IRON_BAR = "obj.iron_bar"
    private const val GLASS = "obj.molten_glass"
    private const val ORB = "obj.stafforb"
    private const val CLOCKWORK = "obj.poh_clockwork_mechanism"
    private const val ROPE = "obj.rope"
    private const val CANDLE = "obj.unlit_candle"
    private const val PAINTING = "obj.poh_unframed_painting_"
    private const val MAGIC_STONE = "obj.poh_magic_crystal"

    private fun mats(vararg pairs: Pair<String, Int>) = pairs.map { Material(it.first, it.second) }

    // ------------------------------------------------------------------ shared hotspots

    private val RUG_LOCS_PARLOUR =
        listOf("loc.poh_parlour_4_middle", "loc.poh_parlour_4_side", "loc.poh_parlour_4_corner")

    private fun rugOptions() =
        listOf(
            Buildable(
                "Brown rug",
                2,
                30.0,
                mats(CLOTH to 2),
                listOf("loc.poh_rugmiddle1", "loc.poh_rugside1", "loc.poh_rugcorner1"),
            ),
            Buildable(
                "Rug",
                13,
                60.0,
                mats(CLOTH to 4),
                listOf("loc.poh_rugmiddle2", "loc.poh_rugside2", "loc.poh_rugcorner2"),
            ),
            Buildable(
                "Opulent rug",
                65,
                360.0,
                mats(CLOTH to 4, GOLD_LEAF to 1),
                listOf("loc.poh_rugmiddle3", "loc.poh_rugside3", "loc.poh_rugcorner3"),
            ),
        )

    private fun rug(middle: String, side: String, corner: String) =
        HotspotGroup("rug", "Rug space", listOf(middle, side, corner), rugOptions())

    private fun curtains(loc: String) =
        HotspotGroup(
            "curtains",
            "Curtain space",
            loc,
            listOf(
                Buildable(
                    "Torn curtains",
                    2,
                    132.0,
                    mats(PLANK to 3, CLOTH to 3, NAILS to 3),
                    "loc.poh_curtains_1",
                ),
                Buildable("Curtains", 18, 225.0, mats(OAK to 3, CLOTH to 3), "loc.poh_curtains_2"),
                Buildable(
                    "Opulent curtains",
                    40,
                    315.0,
                    mats(TEAK to 3, CLOTH to 3),
                    "loc.poh_curtains_3",
                ),
            ),
        )

    private fun fireplace(loc: String) =
        HotspotGroup(
            "fireplace",
            "Fireplace space",
            loc,
            listOf(
                Buildable(
                    "Clay fireplace",
                    3,
                    30.0,
                    mats(SOFT_CLAY to 3),
                    "loc.poh_fireplace_1",
                    BuildSound.STONE,
                ),
                Buildable(
                    "Stone fireplace",
                    33,
                    40.0,
                    mats(LIMESTONE to 2),
                    "loc.poh_fireplace_2",
                    BuildSound.STONE,
                ),
                Buildable(
                    "Marble fireplace",
                    63,
                    500.0,
                    mats(MARBLE to 1),
                    "loc.poh_fireplace_3",
                    BuildSound.STONE,
                ),
            ),
        )

    private fun bookcase(key: String, loc: String) =
        HotspotGroup(
            key,
            "Bookcase space",
            loc,
            listOf(
                Buildable(
                    "Wooden bookcase",
                    4,
                    115.0,
                    mats(PLANK to 4, NAILS to 4),
                    "loc.poh_bookcase1",
                ),
                Buildable("Oak bookcase", 29, 180.0, mats(OAK to 3), "loc.poh_bookcase2"),
                Buildable("Mahogany bookcase", 40, 420.0, mats(MAHOGANY to 3), "loc.poh_bookcase3"),
            ),
        )

    private fun staircase(loc: String) =
        HotspotGroup(
            "stairs",
            "Stair space",
            loc,
            listOf(
                Buildable(
                    "Oak staircase",
                    27,
                    680.0,
                    mats(OAK to 10, STEEL_BAR to 4),
                    "loc.poh_stairs_3",
                ),
                Buildable(
                    "Teak staircase",
                    48,
                    980.0,
                    mats(TEAK to 10, STEEL_BAR to 4),
                    "loc.poh_stairs_4",
                ),
                Buildable(
                    "Limestone spiral staircase",
                    67,
                    1040.0,
                    mats(TEAK to 10, LIMESTONE to 7),
                    "loc.poh_spiralstairs",
                    BuildSound.STONE,
                ),
                Buildable(
                    "Marble staircase",
                    82,
                    3200.0,
                    mats(MAHOGANY to 5, MARBLE to 5),
                    "loc.poh_stairs_5",
                    BuildSound.STONE,
                ),
                Buildable(
                    "Marble spiral staircase",
                    97,
                    4400.0,
                    mats(TEAK to 10, MARBLE to 7),
                    "loc.poh_spiralstairs_2",
                    BuildSound.STONE,
                ),
            ),
        )

    /** The built staircase locs, and the matching "down" loc placed in the room above. */
    val STAIRS_DOWN: Map<String, String> =
        mapOf(
            "loc.poh_stairs_3" to "loc.poh_stairstop_3",
            "loc.poh_stairs_4" to "loc.poh_stairstop_4",
            "loc.poh_stairs_5" to "loc.poh_stairstop_5",
            "loc.poh_spiralstairs" to "loc.poh_spiralstairs",
            "loc.poh_spiralstairs_2" to "loc.poh_spiralstairs_2",
        )

    const val DUNGEON_ENTRANCE: String = "loc.poh_crude_garden_centrepiece5"

    // ------------------------------------------------------------------------- dungeon

    private val DUNGEON_LIGHTING = dungeonLighting("loc.poh_dungeon_6")

    private fun dungeonLighting(hotspot: String) =
        HotspotGroup(
            "lighting",
            "Lighting space",
            hotspot,
            listOf(
                Buildable("Candles", 72, 243.0, mats(OAK to 4, "obj.lit_candle" to 4), "loc.poh_dungeon_candle"),
                Buildable("Torches", 84, 244.0, mats(OAK to 4, "obj.torch_lit" to 4), "loc.poh_dungeon_torch"),
                Buildable(
                    "Skull torches",
                    94,
                    246.0,
                    mats(OAK to 4, "obj.torch_lit" to 4, "obj.skull" to 4),
                    "loc.poh_dungeon_skulltorch",
                ),
            ),
        )

    private val DUNGEON_DECORATION =
        HotspotGroup(
            "decoration",
            "Decoration space",
            "loc.poh_dungeon_7",
            listOf(
                Buildable("Decorative blood", 72, 4.0, mats("obj.reddye" to 4), "loc.poh_dungeon_walldecor_blood"),
                Buildable(
                    "Decorative pipe",
                    83,
                    120.0,
                    mats(STEEL_BAR to 6),
                    "loc.poh_dungeon_walldecor_pipe",
                    BuildSound.METAL,
                ),
                Buildable(
                    "Hanging skeleton",
                    94,
                    3.0,
                    mats("obj.skull" to 2, "obj.bones" to 6),
                    "loc.poh_dungeon_walldecor_skeleton",
                ),
            ),
        )

    /** A dungeon door, in the pair of door spaces [left] and [right] either side of a passage. */
    private fun dungeonDoor(key: String, left: String, right: String) =
        HotspotGroup(
            key,
            "Door space",
            listOf(left, right),
            listOf(
                Buildable("Oak door", 74, 600.0, mats(OAK to 10), listOf(Dungeon.Door.OAK.left, Dungeon.Door.OAK.right)),
                Buildable(
                    "Steel-plated door",
                    84,
                    800.0,
                    mats(OAK to 10, STEEL_BAR to 10),
                    listOf(Dungeon.Door.STEEL.left, Dungeon.Door.STEEL.right),
                    BuildSound.METAL,
                ),
                Buildable(
                    "Marble door",
                    94,
                    2_000.0,
                    mats(MARBLE to 4),
                    listOf(Dungeon.Door.MARBLE.left, Dungeon.Door.MARBLE.right),
                    BuildSound.STONE,
                ),
            ),
        )

    /** Traps cost only coins; the wiki lists no materials for them. */
    private fun dungeonTrap(key: String, hotspot: String) =
        HotspotGroup(
            key,
            "Trap space",
            hotspot,
            listOf(
                Buildable("Spike trap", 72, 223.0, mats(COINS to 50_000), Dungeon.Trap.SPIKE.built, BuildSound.METAL),
                Buildable("Man trap", 76, 273.0, mats(COINS to 75_000), Dungeon.Trap.MAN.built, BuildSound.METAL),
                Buildable("Tangle vine", 80, 316.0, mats(COINS to 100_000), Dungeon.Trap.VINE.built),
                Buildable("Marble trap", 84, 387.0, mats(COINS to 150_000), Dungeon.Trap.MARBLE.built, BuildSound.STONE),
                Buildable("Teleport trap", 88, 447.0, mats(COINS to 200_000), Dungeon.Trap.TELEPORT.built, BuildSound.STONE),
            ),
        )

    /** Guards cost only coins, from 50,000 for a skeleton to five million for a hellhound. */
    private fun dungeonGuard(key: String, hotspot: String) =
        HotspotGroup(
            key,
            "Guard space",
            hotspot,
            listOf(
                Buildable("Skeleton guard", 70, 223.0, mats(COINS to 50_000), Dungeon.guardStatue("poh_skeleton")),
                Buildable("Guard dog", 74, 273.0, mats(COINS to 75_000), Dungeon.guardStatue("poh_guarddog")),
                Buildable("Hobgoblin guard", 78, 316.0, mats(COINS to 100_000), Dungeon.guardStatue("poh_hobgoblin")),
                Buildable("Baby red dragon", 82, 387.0, mats(COINS to 150_000), Dungeon.guardStatue("poh_babyreddragon")),
                Buildable("Huge spider", 86, 447.0, mats(COINS to 200_000), Dungeon.guardStatue("poh_giantspider")),
                Buildable("Troll guard", 90, 1_000.0, mats(COINS to 1_000_000), Dungeon.guardStatue("poh_troll")),
                Buildable("Hellhound", 94, 2_236.0, mats(COINS to 5_000_000), Dungeon.guardStatue("poh_hellhound")),
            ),
        )

    private val DUNGEON_DOORS =
        listOf(
            dungeonDoor("door_south", "loc.poh_dungeon_5l", "loc.poh_dungeon_5r"),
            dungeonDoor("door_north", "loc.poh_dungeon_4l", "loc.poh_dungeon_4r"),
        )

    /** The corridor and the cross share every hotspot that is built yet. */
    val DUNGEON_ROOM: List<HotspotGroup> =
        listOf(
            DUNGEON_LIGHTING,
            DUNGEON_DECORATION,
            dungeonTrap("trap_north", "loc.poh_dungeon_2"),
            dungeonTrap("trap_south", "loc.poh_dungeon_3"),
            dungeonGuard("guard", "loc.poh_dungeon_1"),
        ) + DUNGEON_DOORS

    /** The stairs room borrows the skill hall's staircase and rug hotspots. */
    val DUNGEON_STAIRS: List<HotspotGroup> =
        listOf(
            staircase("loc.poh_hall1_1_stairs_up"),
            rug("loc.poh_hall1_1_middle", "loc.poh_hall1_1_side", "loc.poh_hall1_1_corner"),
            DUNGEON_LIGHTING,
            DUNGEON_DECORATION,
            dungeonGuard("guard_east", "loc.poh_dungeon_stairs_2"),
            dungeonGuard("guard_west", "loc.poh_dungeon_stairs_3"),
        ) + DUNGEON_DOORS

    /** The pit's three floor hotspots, then its two object-layer ones, as [Oubliette] lists them. */
    private val PIT = Oubliette.PIT_HOTSPOTS + Oubliette.PIT_OBJECT_HOTSPOTS

    private val INVISIBLE_PIT_FLOOR =
        listOf(
            "loc.poh_oubliette_invisible_floor_mid",
            "loc.poh_oubliette_invisible_floor_side",
            "loc.poh_oubliette_invisible_floor_corner",
        )

    private fun cage(label: String, level: Int, xp: Double, materials: List<Material>, cage: Oubliette.Cage) =
        Buildable(label, level, xp, materials, listOf(cage.wall, cage.door), BuildSound.METAL)

    val OUBLIETTE: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "floor",
                "Floor space",
                PIT,
                listOf(
                    Buildable(
                        "Spikes",
                        65,
                        623.0,
                        mats(STEEL_BAR to 20, COINS to 50_000),
                        listOf(
                            "loc.poh_oubliette_spikes_mid",
                            "loc.poh_oubliette_spikes_side",
                            "loc.poh_oubliette_spikes_corner",
                        ) + Oubliette.PIT_OBJECT_HOTSPOTS,
                        BuildSound.METAL,
                    ),
                    Buildable(
                        "Tentacle pool",
                        71,
                        326.0,
                        mats("obj.bucket_water" to 20, COINS to 100_000),
                        listOf(
                            "loc.poh_oubliette_pool_mid",
                            "loc.poh_oubliette_pool_side",
                            "loc.poh_oubliette_pool_corner",
                        ) + Oubliette.PIT_OBJECT_HOTSPOTS,
                    ),
                    Buildable(
                        "Flame pit",
                        77,
                        357.0,
                        mats("obj.tinderbox" to 20, COINS to 125_000),
                        INVISIBLE_PIT_FLOOR + listOf("loc.poh_oubliette_floor_fire", "loc.poh_oubliette_floor_fire"),
                    ),
                    Buildable(
                        "Rocnar",
                        83,
                        387.0,
                        mats(COINS to 150_000),
                        INVISIBLE_PIT_FLOOR + listOf("loc.poh_oubliette_1_type8", "loc.poh_oub_monster1"),
                    ),
                ),
            ),
            HotspotGroup(
                "prison",
                "Prison space",
                listOf("loc.poh_oubliette_2_front", "loc.poh_oubliette_2_door"),
                listOf(
                    cage("Oak cage", 65, 640.0, mats(OAK to 10, STEEL_BAR to 2), Oubliette.Cage.OAK),
                    cage("Oak and steel cage", 70, 800.0, mats(OAK to 10, STEEL_BAR to 10), Oubliette.Cage.OAK_STEEL),
                    cage("Steel cage", 75, 400.0, mats(STEEL_BAR to 20), Oubliette.Cage.STEEL),
                    cage("Spiked cage", 80, 500.0, mats(STEEL_BAR to 25), Oubliette.Cage.SPIKED),
                    cage("Bone cage", 85, 603.0, mats(OAK to 10, "obj.bones" to 10), Oubliette.Cage.BONES),
                ),
            ),
            HotspotGroup(
                "ladder",
                "Ladder space",
                Oubliette.LADDER_HOTSPOT,
                listOf(
                    Buildable("Oak ladder", 68, 300.0, mats(OAK to 5), Oubliette.LADDERS[0]),
                    Buildable("Teak ladder", 78, 450.0, mats(TEAK to 5), Oubliette.LADDERS[1]),
                    Buildable("Mahogany ladder", 88, 700.0, mats(MAHOGANY to 5), Oubliette.LADDERS[2]),
                ),
            ),
            dungeonLighting("loc.poh_oubliette_4"),
            DUNGEON_DECORATION,
            dungeonGuard("guard", "loc.poh_oubliette_3"),
        )

    /** The treasure room's single door space, on its south side. */
    private val TREASURE_DOOR = dungeonDoor("door", "loc.poh_dungeon_4l", "loc.poh_dungeon_4r")

    private fun treasureChest(label: String, level: Int, xp: Double, materials: List<Material>, chest: Treasure.Chest) =
        Buildable(label, level, xp, materials, chest.closed)

    /** Guardians cost only coins; the rune dragon also needs Dragon Slayer II. */
    private fun guardian(label: String, level: Int, xp: Double, coins: Int, name: String, quest: String? = null) =
        Buildable(label, level, xp, mats(COINS to coins), Dungeon.guardStatue(name), quest = quest)

    /**
     * Both of the wiki's room tables give the magic chest 1,500 experience; its own page says 1,000.
     */
    val TREASURE_ROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "treasure",
                "Treasure space",
                "loc.poh_dungeon_treasure_1",
                listOf(
                    treasureChest("Wooden crate", 75, 143.0, mats(PLANK to 5, NAILS to 5), Treasure.Chest.WOODEN_CRATE),
                    treasureChest("Oak chest", 79, 340.0, mats(OAK to 5, STEEL_BAR to 2), Treasure.Chest.OAK),
                    treasureChest("Teak chest", 83, 530.0, mats(TEAK to 5, STEEL_BAR to 4), Treasure.Chest.TEAK),
                    treasureChest("Mahogany chest", 87, 1000.0, mats(MAHOGANY to 5, GOLD_LEAF to 1), Treasure.Chest.MAHOGANY),
                    treasureChest("Magic chest", 91, 1500.0, mats(MAGIC_STONE to 1), Treasure.Chest.MAGIC),
                ),
            ),
            HotspotGroup(
                "monster",
                "Monster space",
                "loc.poh_dungeon_treasure_2",
                listOf(
                    guardian("Demon", 75, 707.0, 500_000, "poh_demon"),
                    guardian("Kalphite soldier", 80, 866.0, 750_000, "poh_kalphite_soldier"),
                    guardian("Tok-Xil", 85, 2_236.0, 5_000_000, "poh_tok_xil"),
                    guardian("Dagannoth", 90, 2_738.0, 7_500_000, "poh_dagganoth"),
                    guardian("Steel dragon", 95, 3_162.0, 10_000_000, "poh_steel_dragon"),
                    guardian("Rune dragon", 99, 5_000.0, 25_000_000, "poh_rune_dragon", "quest_dragonslayer2"),
                ),
            ),
            HotspotGroup(
                "shields",
                "Decoration space",
                "loc.poh_dungeon_treasure_5",
                listOf(
                    Buildable("Round shield", 66, 120.0, mats(OAK to 2), Heraldry.crestDecor("oak")),
                    Buildable("Square shield", 76, 360.0, mats(TEAK to 4), Heraldry.crestDecor("teak")),
                    Buildable("Kite shield", 86, 420.0, mats(MAHOGANY to 3), Heraldry.crestDecor("mahogany")),
                ),
            ),
            DUNGEON_LIGHTING,
            DUNGEON_DECORATION,
            TREASURE_DOOR,
        )

    private val ANCIENT_EXTRAS = mats("obj.poh_ancient_signet" to 1, "obj.pharaohs_sceptre" to 1)
    private val LUNAR_EXTRAS = mats("obj.poh_lunar_signet" to 1, "obj.astralrune" to 10_000)
    private val DARK_EXTRAS = mats("obj.poh_arceuus_signet" to 1, "obj.bloodrune" to 5_000, "obj.soulrune" to 5_000)

    /**
     * The occult altar is built over whichever of the three altars stands there, for the signets and
     * runes of the other two - one cache furniture row per altar it replaces.
     */
    private fun occultAltar(from: Int, materials: List<Material>) =
        Buildable(
            "Occult altar",
            90,
            3445.0,
            materials,
            "loc.poh_altar_occult",
            BuildSound.STONE,
            upgrade = true,
            upgradeFrom = from,
        )

    /** Each jewellery box tier is built over the last; the adventure logs are built outright. */
    val ACHIEVEMENT_GALLERY: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "altar",
                "Altar space",
                "loc.poh_achievement_hotspot_altar",
                listOf(
                    Buildable(
                        "Ancient altar",
                        80,
                        1490.0,
                        mats(LIMESTONE to 10, MAGIC_STONE to 1) + ANCIENT_EXTRAS,
                        "loc.poh_altar_ancient",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Lunar altar",
                        80,
                        1957.0,
                        mats(LIMESTONE to 10, MAGIC_STONE to 1) + LUNAR_EXTRAS,
                        "loc.poh_altar_lunar",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Dark altar",
                        80,
                        3888.0,
                        mats(LIMESTONE to 10, MAGIC_STONE to 1) + DARK_EXTRAS,
                        "loc.poh_altar_dark",
                        BuildSound.STONE,
                    ),
                    occultAltar(0, LUNAR_EXTRAS + DARK_EXTRAS),
                    occultAltar(1, ANCIENT_EXTRAS + DARK_EXTRAS),
                    occultAltar(2, LUNAR_EXTRAS + ANCIENT_EXTRAS),
                ),
            ),
            HotspotGroup(
                "jewellery_box",
                "Jewellery box space",
                "loc.poh_achievement_hotspot_jewellerybox",
                listOf(
                    Buildable(
                        "Basic jewellery box",
                        81,
                        605.0,
                        mats(CLOTH to 1, STEEL_BAR to 1, "obj.necklace_of_minigames_8" to 3, "obj.ring_of_dueling_8" to 3),
                        "loc.poh_jewellery_box_1",
                    ),
                    Buildable(
                        "Fancy jewellery box",
                        86,
                        1350.0,
                        mats(GOLD_LEAF to 1, "obj.jewl_necklace_of_skills_4" to 5, "obj.jewl_bracelet_of_combat_4" to 5),
                        "loc.poh_jewellery_box_2",
                        upgrade = true,
                    ),
                    Buildable(
                        "Ornate jewellery box",
                        91,
                        2680.0,
                        mats(GOLD_LEAF to 2, "obj.amulet_of_glory_4" to 8, "obj.ring_of_wealth_5" to 8),
                        "loc.poh_jewellery_box_3",
                        upgrade = true,
                    ),
                ),
            ),
            HotspotGroup(
                "adventure_log",
                "Adventure log space",
                "loc.poh_achievement_hotspot_log",
                listOf(
                    Buildable("Mahogany adventure log", 83, 504.0, mats(MAHOGANY to 3, "obj.papyrus" to 2, "obj.slayer_gem" to 1), Gallery.ADVENTURE_LOGS[0]),
                    Buildable("Gilded adventure log", 88, 1100.0, mats(MAHOGANY to 3, GOLD_LEAF to 2, "obj.slayer_gem" to 1), Gallery.ADVENTURE_LOGS[1]),
                    Buildable(
                        "Marble adventure log",
                        93,
                        1160.0,
                        mats(MARBLE to 2, LIMESTONE to 4, "obj.slayer_gem" to 1),
                        Gallery.ADVENTURE_LOGS[2],
                        BuildSound.STONE,
                    ),
                ),
            ),
            HotspotGroup(
                "boss_lair",
                "Boss lair space",
                "loc.poh_achievement_hotspot_lair",
                listOf(
                    Buildable(
                        "Boss lair display",
                        87,
                        1483.0,
                        mats(STEEL_BAR to 4, GLASS to 5, MAHOGANY to 10),
                        Gallery.LAIR_BLANK,
                    ),
                ),
            ),
            HotspotGroup(
                "display",
                "Display space",
                "loc.poh_achievement_hotspot_display",
                listOf(
                    Buildable("Mounted emblem", 80, 5300.0, mats(MARBLE to 1, GOLD_LEAF to 1, "obj.bh_2020_emblem_10" to 1), Gallery.EMBLEM),
                    Buildable("Mounted coins", 80, 800.0, mats(MARBLE to 1, GOLD_LEAF to 1, COINS to 100_000_000), Gallery.COINS),
                    Buildable("Cape hanger", 80, 800.0, mats(MARBLE to 1, GOLD_LEAF to 1), Gallery.CAPE_BLANK),
                ),
            ),
            HotspotGroup(
                "quest_list",
                "Quest list space",
                "loc.poh_achievement_hotspot_questlist",
                listOf(Buildable("Quest list", 80, 310.0, mats("obj.papyrus" to 10, GOLD_LEAF to 1), Gallery.QUEST_LIST)),
            ),
        )

    /** One costume room store's options, each [Costumes.Store.closed] loc in build order. */
    private fun costumeStore(
        store: Costumes.Store,
        label: String,
        hotspot: String,
        options: List<Triple<String, Int, Pair<Double, List<Material>>>>,
        upgrades: Boolean = true,
    ) =
        HotspotGroup(
            store.group,
            label,
            hotspot,
            options.mapIndexed { index, (name, level, cost) ->
                Buildable(name, level, cost.first, cost.second, store.closed[index], upgrade = upgrades && index > 0)
            },
        )

    private fun tier(name: String, level: Int, xp: Double, vararg materials: Pair<String, Int>) =
        Triple(name, level, xp to mats(*materials))

    /**
     * The costume room's six stores. Every tier is built over the one before it with Upgrade, except
     * the toy boxes, which are all built from scratch and hold the same things.
     */
    val COSTUME_ROOM: List<HotspotGroup> =
        listOf(
            costumeStore(
                Costumes.Store.TREASURE_CHEST,
                "Treasure chest space",
                "loc.poh_cos_room_tresure_chest_hotspot",
                listOf(
                    tier("Oak treasure chest", 48, 120.0, OAK to 2),
                    tier("Teak treasure chest", 66, 180.0, TEAK to 2),
                    tier("Mahogany treasure chest", 84, 280.0, MAHOGANY to 2),
                ),
            ),
            costumeStore(
                Costumes.Store.ARMOUR_CASE,
                "Armour case space",
                "loc.poh_cos_room_armour_case_hotspot",
                listOf(
                    tier("Oak armour case", 46, 180.0, OAK to 3),
                    tier("Teak armour case", 64, 270.0, TEAK to 3),
                    tier("Mahogany armour case", 82, 420.0, MAHOGANY to 3),
                ),
            ),
            costumeStore(
                Costumes.Store.FANCY_DRESS_BOX,
                "Fancy dress box space",
                "loc.poh_cos_room_fancy_dress_box_hotspot",
                listOf(
                    tier("Oak costume box", 44, 120.0, OAK to 2),
                    tier("Teak costume box", 62, 180.0, TEAK to 2),
                    tier("Mahogany costume box", 80, 280.0, MAHOGANY to 2),
                ),
            ),
            costumeStore(
                Costumes.Store.MAGIC_WARDROBE,
                "Magic wardrobe space",
                "loc.poh_cos_room_magic_wardrobe_hotspot",
                listOf(
                    tier("Oak magic wardrobe", 42, 240.0, OAK to 4),
                    tier("Carved oak magic wardrobe", 51, 360.0, OAK to 6),
                    tier("Teak magic wardrobe", 60, 360.0, TEAK to 4),
                    tier("Carved teak magic wardrobe", 69, 540.0, TEAK to 6),
                    tier("Mahogany magic wardrobe", 78, 560.0, MAHOGANY to 4),
                    tier("Gilded magic wardrobe", 87, 860.0, MAHOGANY to 4, GOLD_LEAF to 1),
                    tier("Marble magic wardrobe", 96, 500.0, MARBLE to 1),
                ),
            ),
            costumeStore(
                Costumes.Store.CAPE_RACK,
                "Cape rack space",
                "loc.poh_cos_room_cape_rack_hotspot",
                listOf(
                    tier("Oak cape rack", 54, 240.0, OAK to 4),
                    tier("Teak cape rack", 63, 360.0, TEAK to 4),
                    tier("Mahogany cape rack", 72, 560.0, MAHOGANY to 4),
                    tier("Gilded cape rack", 81, 860.0, MAHOGANY to 4, GOLD_LEAF to 1),
                    tier("Marble cape rack", 90, 500.0, MARBLE to 1),
                    tier("Magical cape rack", 99, 1000.0, MAGIC_STONE to 1),
                ),
            ),
            costumeStore(
                Costumes.Store.TOY_BOX,
                "Toy box space",
                "loc.poh_cos_room_toy_box_hotspot",
                listOf(
                    tier("Oak toy box", 50, 120.0, OAK to 2),
                    tier("Teak toy box", 68, 180.0, TEAK to 2),
                    tier("Mahogany toy box", 86, 280.0, MAHOGANY to 2),
                ),
                upgrades = false,
            ),
        )

    // --------------------------------------------------------------------- superior garden

    private val THEME_HOTSPOTS =
        listOf("edge", "feature", "innercorner", "outercorner", "path_1", "path_2", "path_3", "pathcorner")
            .map { "loc.poh_superior_garden_hotspot_theme_$it" }

    /** A theme in [THEME_HOTSPOTS] order; the zen and otherworldly themes lay one path on all three paths. */
    private fun theme(name: String, paths: List<String>, corner: String) =
        listOf("edge", "hero", "inner_corner", "outer_corner").map { "loc.poh_theme_${name}_$it" } +
            paths.map { "loc.poh_theme_${name}_$it" } +
            "loc.poh_theme_${name}_$corner"

    private val FENCE_HOTSPOTS =
        listOf("middle", "post", "post_m").map { "loc.poh_superior_garden_hotspot_fence_$it" }

    private fun gardenBench(key: String, row: String) =
        HotspotGroup(
            key,
            "Seating space",
            listOf("left", "right").map { "loc.poh_superior_garden_hotspot_seating_${row}_$it" },
            listOf(
                Buildable("Teak garden bench", 66, 540.0, mats(TEAK to 6), benchLocs("teak")),
                Buildable("Gnome bench", 77, 840.0, mats(MAHOGANY to 6), benchLocs("gnome")),
                Buildable("Marble decorative bench", 88, 3000.0, mats(MARBLE to 6), benchLocs("marble"), BuildSound.STONE),
                Buildable(
                    "Obsidian decorative bench",
                    98,
                    2331.0,
                    mats(MARBLE to 3, "obj.onyx" to 1, "obj.firerune" to 250, "obj.lavarune" to 500),
                    benchLocs("obsidian"),
                    BuildSound.STONE,
                ),
            ),
        )

    private fun benchLocs(kind: String) = listOf("left", "right").map { "loc.poh_garden_bench_${kind}_$it" }

    private const val SPIRIT_SAPLING = "obj.plantpot_spirit_tree_sapling"
    private const val BITTERCAP = "obj.bittercap_mushroom"
    private const val FAIRY_ENCHANTMENT = "obj.poh_fairy_enchantment"

    /**
     * Levels, materials and experience from the cache furniture rows and the wiki. The pools are each
     * built over the last; the spirit tree, obelisk and topiary can be built, but the spirit tree and
     * obelisk networks and topiary carving do not exist on this server yet.
     */
    val SUPERIOR_GARDEN: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "fence",
                "Fence space",
                FENCE_HOTSPOTS,
                listOf(
                    Buildable(
                        "Redwood fence",
                        75,
                        240.0,
                        mats("obj.redwood_logs" to 10, STEEL_BAR to 2),
                        listOf("middle", "post", "post_m").map { "loc.poh_redwood_fence_$it" },
                    ),
                    Buildable("Marble wall", 79, 4000.0, mats(MARBLE to 8), List(3) { "loc.poh_fencing7" }, BuildSound.STONE),
                    Buildable(
                        "Obsidian fence",
                        83,
                        2741.0,
                        mats("obj.tzhaar_staff" to 10, "obj.tzhaar_maul" to 2, "obj.tzhaar_throwingring" to 25),
                        listOf("middle", "post", "post_m").map { "loc.poh_obsidian_fence_$it" },
                        BuildSound.STONE,
                    ),
                ),
            ),
            HotspotGroup(
                "pool",
                "Pool space",
                "loc.poh_superior_garden_hotspot_pool",
                listOf(
                    Buildable(
                        "Restoration pool",
                        65,
                        706.0,
                        mats(LIMESTONE to 5, "obj.bucket_water" to 5, "obj.soulrune" to 1000, "obj.bodyrune" to 1000),
                        "loc.poh_pool_restoration",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Revitalisation pool",
                        70,
                        850.0,
                        mats("obj.4dosestamina" to 10),
                        "loc.poh_pool_revitalisation",
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                    Buildable(
                        "Rejuvenation pool",
                        80,
                        900.0,
                        mats("obj.4doseprayerrestore" to 10),
                        "loc.poh_pool_rejuvenation",
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                    Buildable(
                        "Fancy rejuvenation pool",
                        85,
                        1950.0,
                        mats("obj.4dose2restore" to 10, MARBLE to 2),
                        "loc.poh_pool_recovery",
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                    Buildable(
                        "Ornate rejuvenation pool",
                        90,
                        3107.0,
                        mats("obj.antivenom4" to 10, GOLD_LEAF to 5, "obj.bloodrune" to 1000),
                        "loc.poh_pool_regeneration",
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                ),
            ),
            HotspotGroup(
                "teleport",
                "Teleport space",
                "loc.poh_superior_garden_hotspot_treering",
                listOf(
                    Buildable(
                        "Spirit tree",
                        75,
                        350.0,
                        mats(SPIRIT_SAPLING to 1),
                        "loc.poh_spirit_tree",
                        skill = "stat.farming",
                        skillLevel = 83,
                        skillXp = 350.0,
                        wateringCan = true,
                    ),
                    Buildable(
                        "Obelisk",
                        80,
                        3000.0,
                        mats("obj.wild_cave_obelisk_crystal" to 4, MARBLE to 4),
                        "loc.poh_wilderness_obelisk",
                        BuildSound.STONE,
                    ),
                    Buildable("Fairy ring", 85, 535.0, mats(BITTERCAP to 10, FAIRY_ENCHANTMENT to 1), "loc.poh_fairy_ring"),
                    Buildable(
                        "Spirit tree & fairy ring",
                        95,
                        885.0,
                        mats(SPIRIT_SAPLING to 1, BITTERCAP to 10, FAIRY_ENCHANTMENT to 1),
                        "loc.poh_spirit_ring",
                        skill = "stat.farming",
                        skillLevel = 83,
                        skillXp = 885.0,
                        wateringCan = true,
                    ),
                ),
            ),
            HotspotGroup(
                "theme",
                "Theme space",
                THEME_HOTSPOTS,
                listOf(
                    Buildable(
                        "Zen theme",
                        65,
                        474.0,
                        mats("obj.bucket_sand" to 6, "obj.handsand_pink_dye" to 1, "obj.poh_sapling_tree_2" to 1),
                        theme("zen", List(3) { "path" }, "path_corner"),
                    ),
                    Buildable(
                        "Otherworldly theme",
                        75,
                        316.0,
                        mats(
                            "obj.bucket_supercompost" to 8,
                            "obj.bluedye" to 1,
                            BITTERCAP to 4,
                            "obj.fairy_enchanted_secateurs" to 1,
                        ),
                        theme("zanaris", List(3) { "path" }, "path_corner"),
                    ),
                    Buildable(
                        "Volcanic theme",
                        85,
                        4464.0,
                        mats("obj.enakh_granite_medium" to 2, "obj.onyx" to 6, "obj.firerune" to 1000, "obj.lavarune" to 2000),
                        theme("tzhaar", listOf("path_1", "path_2", "path_3"), "path_4"),
                        BuildSound.STONE,
                    ),
                ),
            ),
            HotspotGroup(
                "topiary",
                "Topiary space",
                "loc.poh_superior_garden_hotspot_topiary",
                listOf(
                    Buildable(
                        "Topiary bush",
                        65,
                        141.0,
                        mats("obj.poh_sapling_hedge4" to 1),
                        "loc.poh_topiary_null",
                        skill = "stat.farming",
                        skillLevel = 1,
                        skillXp = 141.0,
                        wateringCan = true,
                    ),
                ),
            ),
            gardenBench("seating_1", "a"),
            gardenBench("seating_2", "b"),
        )

    // ----------------------------------------------------------------------- league hall

    private fun pedestal(number: Int) =
        HotspotGroup(
            "pedestal_$number",
            "Pedestal space",
            "loc.poh_leaguehall_pedestal_hotspot_$number",
            listOf(
                Buildable(
                    "Trophy pedestal",
                    27,
                    86.0,
                    mats(LIMESTONE to 4, ROPE to 1, "obj.reddye" to 1),
                    Leagues.pedestal(number, "simple"),
                    BuildSound.STONE,
                ),
                Buildable(
                    "Ornate trophy pedestal",
                    64,
                    2106.0,
                    mats(MARBLE to 3, GOLD_LEAF to 1, ROPE to 1, "obj.reddye" to 1),
                    Leagues.pedestal(number, "decorative"),
                    BuildSound.STONE,
                ),
            ),
        )

    private fun leagueRug(tier: Int) =
        listOf("middle", "side", "corner").map { "loc.poh_leaguehall_rug_${it}_$tier" }

    val LEAGUE_HALL: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "statue",
                "Statue space",
                "loc.poh_leaguehall_statue_hotspot",
                listOf(
                    Buildable("League statue", 32, 120.0, mats(LIMESTONE to 6), "loc.poh_leaguehall_statue_simple", BuildSound.STONE),
                    Buildable(
                        "Trailblazer globe",
                        32,
                        120.0,
                        mats(OAK to 2, "obj.trailblazer_statue" to 1),
                        "loc.poh_leaguehall_statue_trailblazer",
                    ),
                    Buildable(
                        "Ornate league statue",
                        68,
                        3600.0,
                        mats(MARBLE to 6, GOLD_LEAF to 1),
                        "loc.poh_leaguehall_statue_decorative",
                        BuildSound.STONE,
                    ),
                ),
            ),
            HotspotGroup(
                "scroll",
                "Accomplishment scroll space",
                "loc.poh_leaguehall_accomplishment_scroll_hotspot",
                listOf(
                    Buildable(
                        "League accomplishments scroll",
                        48,
                        310.0,
                        mats("obj.papyrus" to 10, GOLD_LEAF to 1),
                        Leagues.SCROLL,
                    ),
                ),
            ),
            HotspotGroup(
                "banner_stand",
                "Banner stand space",
                "loc.poh_leaguehall_bannerstand_hotspot",
                listOf(
                    Buildable("Banner stand", 30, 40.0, mats(LIMESTONE to 2), Leagues.bannerStand("simple"), BuildSound.STONE),
                    Buildable(
                        "Ornate banner stand",
                        66,
                        1300.0,
                        mats(MARBLE to 2, GOLD_LEAF to 1),
                        Leagues.bannerStand("decorative"),
                        BuildSound.STONE,
                    ),
                ),
            ),
            HotspotGroup(
                "rug",
                "Rug space",
                listOf("middle", "side", "corner").map { "loc.poh_leaguehall_rug_${it}_hotspot" },
                listOf(
                    Buildable("Rug", 28, 60.0, mats(CLOTH to 4), leagueRug(1)),
                    Buildable("Opulent rug", 65, 360.0, mats(CLOTH to 4, GOLD_LEAF to 1), leagueRug(2)),
                ),
            ),
            pedestal(1),
            pedestal(2),
            pedestal(3),
            HotspotGroup(
                "trophy_case",
                "Trophy case space",
                "loc.poh_leaguehall_trophycase_hotspot",
                listOf(
                    Buildable("Oak trophy case", 36, 360.0, mats(OAK to 6), "loc.poh_leaguehall_trophycase_oak"),
                    Buildable(
                        "Mahogany trophy case",
                        78,
                        1440.0,
                        mats(MAHOGANY to 6, GOLD_LEAF to 2),
                        "loc.poh_leaguehall_trophycase_mahogany",
                    ),
                ),
            ),
            HotspotGroup(
                "outfit_stand",
                "Outfit stand space",
                "loc.poh_leaguehall_outfitstand_hotspot",
                listOf(
                    Buildable("Oak outfit stand", 34, 240.0, mats(OAK to 4), Leagues.outfitStand("oak")),
                    Buildable(
                        "Mahogany outfit stand",
                        74,
                        860.0,
                        mats(MAHOGANY to 4, GOLD_LEAF to 1),
                        Leagues.outfitStand("mahogany"),
                    ),
                ),
            ),
        )

    // ---------------------------------------------------------------------- portal nexus

    val PORTAL_NEXUS: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "nexus",
                "Portal Nexus space",
                "loc.poh_telenexus_1",
                listOf(
                    Buildable("Marble portal nexus", 72, 2000.0, mats(MARBLE to 4), Nexus.Tier.MARBLE.loc, BuildSound.STONE),
                    Buildable(
                        "Gilded portal nexus",
                        82,
                        2600.0,
                        mats(MARBLE to 4, GOLD_LEAF to 2),
                        Nexus.Tier.GILDED.loc,
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                    Buildable(
                        "Crystalline portal nexus",
                        92,
                        2600.0,
                        mats(MAGIC_STONE to 2, GOLD_LEAF to 2),
                        Nexus.Tier.CRYSTALLINE.loc,
                        BuildSound.STONE,
                        upgrade = true,
                    ),
                ),
            ),
            HotspotGroup(
                "amulet_xeric",
                "Amulet space",
                "loc.poh_nexus_4_amulet",
                listOf(
                    Buildable(
                        "Mounted Xeric's talisman",
                        72,
                        500.0,
                        mats(MAHOGANY to 1, GOLD_LEAF to 1, "obj.lizardman_fang" to 5000, "obj.xeric_talisman_empty" to 1),
                        Nexus.Amulet.XERIC.loc,
                    ),
                ),
            ),
            HotspotGroup(
                "amulet_digsite",
                "Amulet space",
                "loc.poh_nexus_5_amulet",
                listOf(
                    Buildable(
                        "Mounted digsite pendant",
                        82,
                        800.0,
                        mats(MAHOGANY to 1, GOLD_LEAF to 1, "obj.poh_curator_medallion" to 1),
                        Nexus.Amulet.DIGSITE.loc,
                    ),
                ),
            ),
            curtains("loc.poh_telenexus_3"),
            rug("loc.poh_telenexus_2_middle", "loc.poh_telenexus_2_side", "loc.poh_telenexus_2_corner"),
        )

    // ----------------------------------------------------------------------- combat room

    val COMBAT_ROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "ring",
                "Combat ring space",
                Combat.RING_HOTSPOTS,
                listOf(
                    Buildable("Boxing ring", 32, 420.0, mats(OAK to 6, CLOTH to 4), Combat.Ring.BOXING.built),
                    Buildable("Fencing ring", 41, 570.0, mats(OAK to 8, CLOTH to 6), Combat.Ring.FENCING.built),
                    Buildable("Combat ring", 51, 630.0, mats(TEAK to 6, CLOTH to 6), Combat.Ring.COMBAT.built),
                    Buildable("Ranging pedestals", 71, 720.0, mats(TEAK to 8), Combat.Ring.PEDESTALS.built),
                    Buildable("Balance beam", 81, 1000.0, mats(TEAK to 10, STEEL_BAR to 5), Combat.Ring.BEAM.built),
                ),
            ),
            HotspotGroup(
                "storage",
                "Storage space",
                "loc.poh_combat_room_4",
                listOf(
                    Buildable("Glove rack", 34, 120.0, mats(OAK to 2), Combat.Rack.GLOVES.loc),
                    Buildable("Weapons rack", 44, 180.0, mats(TEAK to 2), Combat.Rack.WEAPONS.loc),
                    Buildable("Extra weapons rack", 54, 440.0, mats(TEAK to 4, STEEL_BAR to 4), Combat.Rack.EXTRA.loc),
                ),
            ),
            HotspotGroup(
                "decoration",
                "Decoration space",
                "loc.poh_combat_room_5",
                listOf(
                    Buildable(
                        "Greenman carving",
                        1,
                        0.0,
                        mats("obj.greenman_wall_decoration" to 1),
                        "loc.poh_greenman_wall_decoration",
                    ),
                    Buildable("Oak wall decoration", 16, 120.0, mats(OAK to 2), "loc.poh_wall_deco_1"),
                    Buildable("Teak wall decoration", 36, 180.0, mats(TEAK to 2), "loc.poh_wall_deco_2"),
                    Buildable("Gilded decoration", 56, 1020.0, mats(MAHOGANY to 3, GOLD_LEAF to 2), "loc.poh_wall_deco_3"),
                ),
            ),
            HotspotGroup(
                "dummy",
                "Combat dummy space",
                "loc.poh_combat_room_6",
                listOf(
                    Buildable(
                        "Combat dummy",
                        48,
                        660.0,
                        mats(TEAK to 5, CLOTH to 4, "obj.bucket_sand" to 5),
                        Combat.Dummy.PLAIN.loc,
                    ),
                    Buildable(
                        "Undead combat dummy",
                        53,
                        220.0,
                        mats("obj.harmless_black_mask" to 1, "obj.bucket_ectoplasm" to 4),
                        Combat.Dummy.UNDEAD.loc,
                        upgrade = true,
                    ),
                    Buildable(
                        "Ornate undead combat dummy",
                        58,
                        300.0,
                        mats(GOLD_LEAF to 1),
                        Combat.Dummy.ORNATE_UNDEAD.loc,
                        upgrade = true,
                    ),
                ),
            ),
        )

    // ------------------------------------------------------------------------ games room

    private val RUNES = listOf("obj.airrune", "obj.waterrune", "obj.earthrune", "obj.firerune")

    private fun runes(count: Int) = mats(*RUNES.map { it to count }.toTypedArray())

    val GAMES_ROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "attack_stone",
                "Stone space",
                "loc.poh_games_room_5",
                listOf(
                    Buildable("Clay attack stone", 39, 100.0, mats(SOFT_CLAY to 10), "loc.poh_tbt_clay_new", BuildSound.STONE),
                    Buildable("Limestone attack stone", 59, 200.0, mats(LIMESTONE to 10), "loc.poh_tbt_limestone_new", BuildSound.STONE),
                    Buildable("Marble attack stone", 79, 2000.0, mats(MARBLE to 4), "loc.poh_tbt_marble_new", BuildSound.STONE),
                ),
            ),
            HotspotGroup(
                "elemental_balance",
                "Elemental balance space",
                "loc.poh_games_room_6",
                listOf(
                    Buildable("Lesser magical balance", 37, 176.0, runes(500), "loc.poh_elemental_orb_1", BuildSound.STONE),
                    Buildable("Medium magical balance", 57, 252.0, runes(1000), "loc.poh_elemental_orb_2", BuildSound.STONE),
                    Buildable("Greater magical balance", 77, 356.0, runes(2000), "loc.poh_elemental_orb_3", BuildSound.STONE),
                ),
            ),
            HotspotGroup(
                "game",
                "Game space",
                "loc.poh_games_room_2",
                listOf(
                    Buildable("Jester", 39, 360.0, mats(TEAK to 4), "loc.poh_mime_jester"),
                    Buildable("Treasure hunt", 49, 800.0, mats(TEAK to 8, STEEL_BAR to 4), "loc.poh_fairy_house"),
                    Buildable("Hangman game", 59, 1200.0, mats(TEAK to 12, STEEL_BAR to 6), "loc.poh_hangman_chest"),
                ),
            ),
            HotspotGroup(
                "prize_chest",
                "Prize chest space",
                "loc.poh_games_room_4",
                listOf(
                    Buildable("Oak prize chest", 34, 240.0, mats(OAK to 4), "loc.poh_prize_chest_oak_closed"),
                    Buildable("Teak prize chest", 44, 660.0, mats(TEAK to 4, GOLD_LEAF to 1), "loc.poh_prize_chest_teak_closed"),
                    Buildable("Mahogany prize chest", 54, 860.0, mats(MAHOGANY to 4, GOLD_LEAF to 1), "loc.poh_prize_chest_mag_closed"),
                ),
            ),
            HotspotGroup(
                "ranging_game",
                "Ranging game space",
                "loc.poh_games_room_7",
                listOf(
                    Buildable("Hoop and stick", 30, 120.0, mats(OAK to 2), "loc.poh_stick+hoop1"),
                    Buildable("Dartboard", 54, 290.0, mats(TEAK to 3, STEEL_BAR to 1), "loc.poh_dartboard1"),
                    Buildable("Archery target", 81, 600.0, mats(TEAK to 6, STEEL_BAR to 3), "loc.poh_archery_target1"),
                ),
            ),
        )

    // ------------------------------------------------------------------------- menagerie

    private const val PET_HOUSE = "loc.poh_menagerie_pethouse_"

    private val MENAGERIE_SHARED: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "pet_house",
                "Pet house space",
                "loc.poh_menagerie_pethouse_hotspot",
                listOf(
                    Buildable("Oak house", 37, 240.0, mats(OAK to 4), PET_HOUSE + 1),
                    Buildable("Teak house", 48, 360.0, mats(TEAK to 4), PET_HOUSE + 2, upgrade = true),
                    Buildable("Mahogany house", 59, 560.0, mats(MAHOGANY to 4), PET_HOUSE + 3, upgrade = true),
                    Buildable("Consecrated house", 70, 1560.0, mats(MAHOGANY to 4, MAGIC_STONE to 1), PET_HOUSE + 4, upgrade = true),
                    Buildable("Desecrated house", 81, 160.0, mats(MAHOGANY to 1, LIMESTONE to 1), PET_HOUSE + 5, upgrade = true),
                    Buildable(
                        "Nature house",
                        92,
                        158.0,
                        mats(MAHOGANY to 1, "obj.bucket_water" to 2, "obj.bucket_supercompost" to 3),
                        PET_HOUSE + 6,
                        upgrade = true,
                    ),
                ),
            ),
            HotspotGroup(
                "pet_feeder",
                "Pet feeder space",
                "loc.poh_menagerie_petfeeder_hotspot",
                listOf(
                    Buildable("Oak feeder", 37, 182.0, mats(OAK to 3, "obj.bucket_milk" to 1), "loc.poh_menagerie_petfeeder_1"),
                    Buildable("Teak feeder", 48, 272.0, mats(TEAK to 3, "obj.bucket_milk" to 1), "loc.poh_menagerie_petfeeder_2"),
                    Buildable(
                        "Mahogany feeder",
                        59,
                        862.0,
                        mats(MAHOGANY to 4, "obj.bucket_milk" to 1, GOLD_LEAF to 1),
                        "loc.poh_menagerie_petfeeder_3",
                    ),
                ),
            ),
            HotspotGroup(
                "pet_list",
                "Pet list space",
                "loc.poh_menagerie_petlist_hotspot",
                listOf(Buildable("Pet list", 38, 198.0, mats(OAK to 3, CLOTH to 1, "obj.papyrus" to 1), "loc.poh_menagerie_petlist_1")),
            ),
            HotspotGroup(
                "scratching_post",
                "Scratching post space",
                "loc.poh_menagerie_scratchingpost_hotspot",
                listOf(
                    Buildable("Oak scratching post", 39, 124.0, mats(OAK to 2, ROPE to 1), "loc.poh_menagerie_scratchingpost_1"),
                    Buildable(
                        "Teak scratching post",
                        49,
                        204.0,
                        mats(TEAK to 2, ROPE to 1, LIMESTONE to 1),
                        "loc.poh_menagerie_scratchingpost_2",
                    ),
                    Buildable(
                        "Mahogany scratching post",
                        59,
                        304.0,
                        mats(MAHOGANY to 2, ROPE to 1, LIMESTONE to 1),
                        "loc.poh_menagerie_scratchingpost_3",
                    ),
                ),
            ),
            HotspotGroup(
                "arena",
                "Arena space",
                listOf("loc.poh_menagerie_combatring_hotspot", "loc.poh_menagerie_combatring_mat_hotspot"),
                listOf(
                    Buildable("Simple arena", 63, 139.0, mats(OAK to 2, CLOTH to 1, ROPE to 1), arena(1)),
                    Buildable("Advanced arena", 73, 199.0, mats(TEAK to 2, CLOTH to 1, ROPE to 1), arena(2)),
                    Buildable("Glorious arena", 83, 299.0, mats(MAHOGANY to 2, CLOTH to 1, ROPE to 1), arena(3)),
                ),
            ),
        )

    private fun arena(tier: Int) = listOf("loc.poh_menagerie_combatring_$tier", "loc.poh_menagerie_combatring_mat")

    private val HABITAT_PARTS = listOf("ground_middle", "ground_side", "ground_corner", "feature")

    private fun habitat(tier: Int) = HABITAT_PARTS.map { "loc.poh_menagerie_habitat_${it}_$tier" }

    /** Only the outdoor menagerie has a habitat; the wiki lists 37 experience for the grassland one. */
    private val HABITAT =
        HotspotGroup(
            "habitat",
            "Habitat space",
            HABITAT_PARTS.map { "loc.poh_menagerie_habitat_$it" },
            listOf(
                Buildable("Grassland habitat", 37, 37.0, mats("obj.poh_sapling_tree_1" to 1, "obj.bucket_compost" to 2), habitat(1), wateringCan = true),
                Buildable("Forest habitat", 47, 51.0, mats("obj.poh_sapling_tree_2" to 1, "obj.bucket_compost" to 3), habitat(2), wateringCan = true),
                Buildable("Desert habitat", 57, 181.0, mats("obj.poh_sapling_plant_1" to 1, "obj.bucket_sand" to 5), habitat(3), wateringCan = true),
                Buildable(
                    "Polar habitat",
                    67,
                    271.0,
                    mats(OAK to 3, "obj.waterrune" to 2000, "obj.slayer_icy_water" to 5),
                    habitat(4),
                    wateringCan = true,
                ),
                Buildable(
                    "Volcanic habitat",
                    77,
                    46.0,
                    mats("obj.enakh_granite_medium" to 5, "obj.lavarune" to 100),
                    habitat(5),
                    BuildSound.STONE,
                    wateringCan = true,
                ),
            ),
        )

    val MENAGERIE_INDOOR: List<HotspotGroup> = MENAGERIE_SHARED

    val MENAGERIE_OUTDOOR: List<HotspotGroup> = MENAGERIE_SHARED + HABITAT

    // ------------------------------------------------------------------------- gardens

    val GARDEN: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "centrepiece",
                "Centrepiece space",
                "loc.poh_crude_garden_1",
                listOf(
                    Buildable(
                        "Exit portal",
                        1,
                        100.0,
                        mats(IRON_BAR to 10),
                        "loc.poh_exit_portal",
                        BuildSound.METAL,
                    ),
                    Buildable(
                        "Decorative rock",
                        5,
                        100.0,
                        mats(LIMESTONE to 5),
                        "loc.poh_crude_garden_centrepiece2",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Pond",
                        10,
                        100.0,
                        mats(SOFT_CLAY to 10),
                        "loc.poh_crude_garden_centrepiece3",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Imp statue",
                        15,
                        150.0,
                        mats(LIMESTONE to 5, SOFT_CLAY to 5),
                        "loc.poh_crude_garden_centrepiece4",
                        BuildSound.STONE,
                    ),
                    Buildable(
                        "Dungeon entrance",
                        70,
                        500.0,
                        mats(MARBLE to 1),
                        DUNGEON_ENTRANCE,
                        BuildSound.STONE,
                    ),
                ),
            ),
            trees("big_tree", "Big tree space", "loc.poh_crude_garden_2", "big_tree", "_4"),
            trees("tree", "Tree space", "loc.poh_crude_garden_3", "small_tree", "_5"),
            plants("big_plant_1", "Big plant space", "loc.poh_crude_garden_4", "plantbig1", "Fern", "Bush", "Tall plant"),
            plants("big_plant_2", "Big plant space", "loc.poh_crude_garden_5", "plantbig2", "Short plant", "Large-leaf plant", "Huge plant"),
            plants("small_plant_1", "Small plant space", "loc.poh_crude_garden_6", "plantbsmall1", "Plant", "Small fern", "Fern"),
            plants("small_plant_2", "Small plant space", "loc.poh_crude_garden_7", "plantbsmall2", "Dock leaf", "Thistle", "Reeds"),
        )

    private fun trees(key: String, label: String, hotspot: String, prefix: String, suffix: String) =
        HotspotGroup(
            key,
            label,
            hotspot,
            listOf(
                tree("Dead tree", 5, 31.0, 1, prefix, suffix),
                tree("Tree", 10, 44.0, 2, prefix, suffix),
                tree("Oak tree", 15, 70.0, 3, prefix, suffix),
                tree("Willow tree", 30, 100.0, 4, prefix, suffix),
                tree("Maple tree", 45, 122.0, 5, prefix, suffix),
                tree("Yew tree", 60, 141.0, 6, prefix, suffix),
                tree("Magic tree", 75, 223.0, 7, prefix, suffix),
            ),
        )

    private fun tree(label: String, level: Int, xp: Double, index: Int, prefix: String, suffix: String) =
        Buildable(
            label,
            level,
            xp,
            mats("obj.poh_sapling_tree_$index" to 1),
            "loc.poh_$prefix$index$suffix",
            wateringCan = true,
        )

    private fun plants(
        key: String,
        label: String,
        hotspot: String,
        prefix: String,
        small: String,
        medium: String,
        large: String,
    ) =
        HotspotGroup(
            key,
            label,
            hotspot,
            listOf(
                Buildable(small, 1, 31.0, mats("obj.poh_sapling_plant_1" to 1), "loc.poh_${prefix}a", wateringCan = true),
                Buildable(medium, 6, 70.0, mats("obj.poh_sapling_plant_2" to 1), "loc.poh_${prefix}b", wateringCan = true),
                Buildable(large, 12, 100.0, mats("obj.poh_sapling_plant_3" to 1), "loc.poh_${prefix}c", wateringCan = true),
            ),
        )

    // ------------------------------------------------------------------- formal garden

    /** A planted piece: it trains Farming as much as Construction, as the wiki has it. */
    private fun planted(label: String, level: Int, xp: Double, sapling: String, built: String) =
        Buildable(label, level, xp, mats(sapling to 1), built, skill = "stat.farming", skillXp = xp, wateringCan = true)

    private fun flowers(key: String, label: String, hotspot: String, row: Char, size: String, names: List<String>) =
        HotspotGroup(
            key,
            label,
            hotspot,
            names.mapIndexed { index, name ->
                planted(
                    name,
                    FLOWER_LEVELS[index],
                    FLOWER_XP[index],
                    "obj.poh_sapling_flower$row${index + 1}",
                    "loc.poh_flower$row${index + 1}_$size",
                )
            },
        )

    private val FLOWER_LEVELS = listOf(66, 71, 76)
    private val FLOWER_XP = listOf(70.0, 100.0, 122.0)
    private val SUNFLOWERS = listOf("Sunflower", "Marigolds", "Roses")
    private val ROSEMARY = listOf("Rosemary", "Daffodils", "Bluebells")

    private val HEDGE_PARTS = listOf("cor" to "corner", "mid" to "middle", "end" to "end")

    private fun hedge(label: String, level: Int, xp: Double, tier: Int) =
        Buildable(
            label,
            level,
            xp,
            mats("obj.poh_sapling_hedge$tier" to 1),
            HEDGE_PARTS.map { (_, part) -> "loc.poh_hedge$part$tier" },
            skill = "stat.farming",
            skillXp = xp,
            wateringCan = true,
        )

    val FORMAL_GARDEN: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "centrepiece",
                "Centrepiece space",
                "loc.poh_posh_garden_1",
                listOf(
                    Buildable("Exit portal", 1, 100.0, mats(IRON_BAR to 10), "loc.poh_exit_portal", BuildSound.METAL),
                    Buildable("Gazebo", 65, 1200.0, mats(MAHOGANY to 8, STEEL_BAR to 4), "loc.poh_posh_garden_centrepiece2"),
                    Buildable("Dungeon entrance", 70, 500.0, mats(MARBLE to 1), DUNGEON_ENTRANCE, BuildSound.STONE),
                    Buildable("Small fountain", 71, 500.0, mats(MARBLE to 1), "loc.poh_posh_garden_centrepiece3", BuildSound.STONE),
                    Buildable("Large fountain", 75, 1000.0, mats(MARBLE to 2), "loc.poh_posh_garden_centrepiece4", BuildSound.STONE),
                    Buildable("Posh fountain", 81, 1500.0, mats(MARBLE to 3), "loc.poh_posh_garden_centrepiece5", BuildSound.STONE),
                ),
            ),
            flowers("big_plant_1", "Big plant", "loc.poh_posh_garden_2", 'a', "big", SUNFLOWERS),
            flowers("big_plant_2", "Big plant 2", "loc.poh_posh_garden_3", 'b', "big", ROSEMARY),
            flowers("small_plant_1", "Small plant", "loc.poh_posh_garden_6", 'a', "small", SUNFLOWERS),
            flowers("small_plant_2", "Small plant 2", "loc.poh_posh_garden_7", 'b', "small", ROSEMARY),
            HotspotGroup(
                "fencing",
                "Fencing",
                "loc.poh_posh_garden_4",
                listOf(
                    Buildable("Boundary stones", 55, 100.0, mats(SOFT_CLAY to 10), "loc.poh_fencing1", BuildSound.STONE),
                    Buildable("Wooden fence", 59, 280.0, mats(PLANK to 10), "loc.poh_fencing2"),
                    Buildable("Stone wall", 63, 200.0, mats(LIMESTONE to 10), "loc.poh_fencing3", BuildSound.STONE),
                    Buildable("Iron railings", 67, 220.0, mats(IRON_BAR to 10, LIMESTONE to 6), "loc.poh_fencing4", BuildSound.METAL),
                    Buildable("Picket fence", 71, 640.0, mats(OAK to 10, STEEL_BAR to 2), "loc.poh_fencing5"),
                    Buildable("Garden fence", 75, 940.0, mats(TEAK to 10, STEEL_BAR to 2), "loc.poh_fencing6"),
                    Buildable("Marble wall", 79, 4000.0, mats(MARBLE to 8), "loc.poh_fencing7", BuildSound.STONE),
                ),
            ),
            HotspotGroup(
                "hedging",
                "Hedging",
                HEDGE_PARTS.map { (hotspot, _) -> "loc.poh_posh_garden_5$hotspot" },
                listOf(
                    hedge("Thorny hedge", 56, 70.0, 1),
                    hedge("Nice hedge", 60, 100.0, 2),
                    hedge("Small box hedge", 64, 122.0, 3),
                    hedge("Topiary hedge", 68, 141.0, 4),
                    hedge("Fancy hedge", 72, 158.0, 5),
                    hedge("Tall fancy hedge", 76, 223.0, 6),
                    hedge("Tall box hedge", 80, 316.0, 7),
                ),
            ),
        )

    // ------------------------------------------------------------------------- parlour

    val PARLOUR: List<HotspotGroup> =
        listOf(
            chair("chair_1", "loc.poh_parlour_1"),
            chair("chair_2", "loc.poh_parlour_2"),
            chair("chair_3", "loc.poh_parlour_3"),
            rug("loc.poh_parlour_4_middle", "loc.poh_parlour_4_side", "loc.poh_parlour_4_corner"),
            bookcase("bookcase", "loc.poh_parlour_5"),
            fireplace("loc.poh_parlour_6"),
            curtains("loc.poh_parlour_7"),
        )

    private fun chair(key: String, loc: String) =
        HotspotGroup(
            key,
            "Chair space",
            loc,
            listOf(
                Buildable(
                    "Crude wooden chair",
                    1,
                    58.0,
                    mats(PLANK to 2, NAILS to 2),
                    "loc.poh_chair1",
                ),
                Buildable("Wooden chair", 8, 87.0, mats(PLANK to 3, NAILS to 3), "loc.poh_chair2"),
                Buildable("Rocking chair", 14, 87.0, mats(PLANK to 3, NAILS to 3), "loc.poh_chair3"),
                Buildable("Oak chair", 19, 120.0, mats(OAK to 2), "loc.poh_chair4"),
                Buildable("Oak armchair", 26, 180.0, mats(OAK to 3), "loc.poh_chair5"),
                Buildable("Teak armchair", 35, 180.0, mats(TEAK to 2), "loc.poh_chair6"),
                Buildable("Mahogany armchair", 50, 280.0, mats(MAHOGANY to 2), "loc.poh_chair7"),
            ),
        )

    // ------------------------------------------------------------------------- kitchen

    val KITCHEN: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "stove",
                "Stove space",
                "loc.poh_kitchen_1",
                listOf(
                    Buildable("Firepit", 5, 40.0, mats(STEEL_BAR to 1, SOFT_CLAY to 2), "loc.poh_stove_1", BuildSound.METAL),
                    Buildable("Firepit with hook", 11, 60.0, mats(STEEL_BAR to 2, SOFT_CLAY to 2), "loc.poh_stove_2", BuildSound.METAL),
                    Buildable("Firepit with pot", 17, 80.0, mats(STEEL_BAR to 3, SOFT_CLAY to 2), "loc.poh_stove_3", BuildSound.METAL),
                    Buildable("Small oven", 24, 80.0, mats(STEEL_BAR to 4), "loc.poh_stove_4", BuildSound.METAL),
                    Buildable("Large oven", 29, 100.0, mats(STEEL_BAR to 5), "loc.poh_stove_5", BuildSound.METAL),
                    Buildable("Steel range", 34, 120.0, mats(STEEL_BAR to 6), "loc.poh_stove_6", BuildSound.METAL),
                    Buildable("Fancy range", 42, 160.0, mats(STEEL_BAR to 8), "loc.poh_stove_7", BuildSound.METAL),
                ),
            ),
            HotspotGroup(
                "shelves",
                "Shelf space",
                listOf("loc.poh_kitchen_2", "loc.poh_kitchen_2_crockery"),
                listOf(
                    shelves("Wooden shelves 1", 6, 87.0, mats(PLANK to 3, NAILS to 3), 1),
                    shelves("Wooden shelves 2", 12, 147.0, mats(PLANK to 3, NAILS to 3, SOFT_CLAY to 6), 2),
                    shelves("Wooden shelves 3", 23, 147.0, mats(PLANK to 3, NAILS to 3, SOFT_CLAY to 6), 3),
                    shelves("Oak shelves 1", 34, 240.0, mats(OAK to 3, SOFT_CLAY to 6), 4),
                    shelves("Oak shelves 2", 45, 240.0, mats(OAK to 3, SOFT_CLAY to 6), 5),
                    shelves("Teak shelves 1", 56, 330.0, mats(TEAK to 3, SOFT_CLAY to 6), 6),
                    shelves("Teak shelves 2", 67, 930.0, mats(TEAK to 3, SOFT_CLAY to 6, GOLD_LEAF to 2), 7),
                ),
            ),
            HotspotGroup(
                "barrel",
                "Barrel space",
                "loc.poh_kitchen_3",
                listOf(
                    Buildable("Beer barrel", 7, 87.0, mats(PLANK to 3, NAILS to 3), "loc.poh_barrel_1"),
                    Buildable("Cider barrel", 12, 91.0, mats(PLANK to 3, NAILS to 3, "obj.cider" to 8), "loc.poh_barrel_2"),
                    Buildable("Asgarnian ale", 18, 184.0, mats(OAK to 3, "obj.asgarnian_ale" to 8), "loc.poh_barrel_3"),
                    Buildable("Greenman's ale", 26, 184.0, mats(OAK to 3, "obj.greenmans_ale" to 8), "loc.poh_barrel_4"),
                ),
            ),
            HotspotGroup(
                "cat_basket",
                "Cat basket space",
                "loc.poh_kitchen_4",
                listOf(
                    Buildable("Cat blanket", 5, 15.0, mats(CLOTH to 1), "loc.poh_pet_1"),
                    Buildable("Cat basket", 19, 58.0, mats(PLANK to 2, NAILS to 2), "loc.poh_pet_2"),
                    Buildable("Cushioned basket", 33, 58.0, mats(PLANK to 2, NAILS to 2, "obj.wool" to 2), "loc.poh_pet_3"),
                ),
            ),
            HotspotGroup(
                "larder",
                "Larder space",
                "loc.poh_kitchen_5",
                listOf(
                    Buildable("Wooden larder", 9, 228.0, mats(PLANK to 8, NAILS to 8), "loc.poh_larder_1"),
                    Buildable("Oak larder", 33, 480.0, mats(OAK to 8), "loc.poh_larder_2"),
                    Buildable("Teak larder", 43, 750.0, mats(TEAK to 8, CLOTH to 2), "loc.poh_larder_3"),
                ),
            ),
            HotspotGroup(
                "sink",
                "Sink space",
                "loc.poh_kitchen_6",
                listOf(
                    Buildable("Pump and drain", 7, 100.0, mats(STEEL_BAR to 5), "loc.poh_sink_1", BuildSound.METAL),
                    Buildable("Pump and tub", 27, 200.0, mats(STEEL_BAR to 10), "loc.poh_sink_2", BuildSound.METAL),
                    Buildable("Sink", 47, 300.0, mats(STEEL_BAR to 15), "loc.poh_sink_3", BuildSound.METAL),
                ),
            ),
            HotspotGroup(
                "table",
                "Table space",
                "loc.poh_kitchen_7",
                listOf(
                    Buildable("Kitchen table", 12, 87.0, mats(PLANK to 3, NAILS to 3), "loc.poh_kitchentable_1"),
                    Buildable("Oak kitchen table", 32, 180.0, mats(OAK to 3), "loc.poh_kitchentable_2"),
                    Buildable("Teak kitchen table", 52, 270.0, mats(TEAK to 3), "loc.poh_kitchentable_3"),
                ),
            ),
        )

    private fun shelves(label: String, level: Int, xp: Double, materials: List<Material>, index: Int) =
        Buildable(
            label,
            level,
            xp,
            materials,
            listOf("loc.poh_kitchen_shelves_$index", "loc.poh_kitchen_crockery_$index"),
        )

    // --------------------------------------------------------------------- dining room

    val DINING_ROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "table",
                "Table space",
                "loc.poh_dining_room_1",
                listOf(
                    Buildable("Wood dining table", 10, 115.0, mats(PLANK to 4, NAILS to 4), "loc.poh_diningtable_1"),
                    Buildable("Oak dining table", 22, 240.0, mats(OAK to 4), "loc.poh_diningtable_2"),
                    Buildable("Carved oak table", 31, 360.0, mats(OAK to 6), "loc.poh_diningtable_3"),
                    Buildable("Teak table", 38, 360.0, mats(TEAK to 4), "loc.poh_diningtable_4"),
                    Buildable("Carved teak table", 45, 600.0, mats(TEAK to 6, CLOTH to 4), "loc.poh_diningtable_5"),
                    Buildable("Mahogany table", 52, 840.0, mats(MAHOGANY to 6), "loc.poh_diningtable_6"),
                ),
            ),
            bench("seating_1", "loc.poh_dining_room_2"),
            bench("seating_2", "loc.poh_dining_room_3"),
            fireplace("loc.poh_dining_room_4"),
            curtains("loc.poh_dining_room_5"),
            HotspotGroup(
                "decoration",
                "Decoration space",
                "loc.poh_dining_room_6",
                listOf(
                    Buildable("Oak wall decoration", 16, 120.0, mats(OAK to 2), "loc.poh_wall_deco_1"),
                    Buildable("Teak wall decoration", 36, 180.0, mats(TEAK to 2), "loc.poh_wall_deco_2"),
                    Buildable("Gilded decoration", 56, 1020.0, mats(MAHOGANY to 3, GOLD_LEAF to 2), "loc.poh_wall_deco_3"),
                ),
            ),
            HotspotGroup(
                "bell_pull",
                "Bell pull space",
                "loc.poh_dining_room_7",
                listOf(
                    Buildable("Rope bell-pull", 26, 64.0, mats(OAK to 1, ROPE to 1), "loc.poh_bellpull_1"),
                    Buildable("Bell-pull", 37, 120.0, mats(TEAK to 1, CLOTH to 2), "loc.poh_bellpull_2"),
                    Buildable("Posh bell-pull", 60, 420.0, mats(TEAK to 1, CLOTH to 2, GOLD_LEAF to 1), "loc.poh_bellpull_3"),
                ),
            ),
        )

    private fun bench(key: String, loc: String) =
        HotspotGroup(
            key,
            "Seating space",
            loc,
            listOf(
                Buildable("Wooden bench", 10, 115.0, mats(PLANK to 4, NAILS to 4), "loc.poh_diningchairs_1"),
                Buildable("Oak bench", 22, 240.0, mats(OAK to 4), "loc.poh_diningchairs_2"),
                Buildable("Carved oak bench", 31, 240.0, mats(OAK to 4), "loc.poh_diningchairs_3"),
                Buildable("Teak dining bench", 38, 360.0, mats(TEAK to 4), "loc.poh_diningchairs_4"),
                Buildable("Carved teak bench", 44, 360.0, mats(TEAK to 4), "loc.poh_diningchairs_5"),
                Buildable("Mahogany bench", 52, 560.0, mats(MAHOGANY to 4), "loc.poh_diningchairs_6"),
                Buildable("Gilded bench", 61, 1760.0, mats(MAHOGANY to 4, GOLD_LEAF to 4), "loc.poh_diningchairs_7"),
            ),
        )

    // ------------------------------------------------------------------------ workshop

    private val TOOL_SPACES =
        listOf(
            "loc.poh_workshop_3a",
            "loc.poh_workshop_3b",
            "loc.poh_workshop_3c",
            "loc.poh_workshop_3d",
            "loc.poh_workshop_3e",
        )

    /**
     * Each tool store tier fills one more of the five tool spaces, keeping the ones before it: the
     * first is built, every later one is an upgrade of the one before.
     */
    private fun toolStores(): List<Buildable> {
        val levels = listOf(15, 25, 35, 44, 55)
        return levels.mapIndexed { index, level ->
            val built = TOOL_SPACES.mapIndexed { slot, space -> if (slot <= index) "loc.poh_tools${slot + 1}" else space }
            Buildable("Tool store ${index + 1}", level, 120.0, mats(OAK to 2), built, upgrade = index > 0)
        }
    }

    val WORKSHOP: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "workbench",
                "Workbench space",
                "loc.poh_workshop_1",
                listOf(
                    Buildable("Wooden workbench", 17, 143.0, mats(PLANK to 5, NAILS to 5), "loc.poh_workbench_1"),
                    Buildable("Oak workbench", 32, 300.0, mats(OAK to 5), "loc.poh_workbench_2"),
                    Buildable("Steel framed workbench", 46, 440.0, mats(OAK to 6, STEEL_BAR to 4), "loc.poh_workbench_3"),
                    Buildable("Bench with vice", 62, 140.0, mats(OAK to 2, STEEL_BAR to 1), "loc.poh_workbench_4", upgrade = true),
                    Buildable("Bench with lathe", 77, 140.0, mats(OAK to 2, STEEL_BAR to 1), "loc.poh_workbench_5", upgrade = true),
                ),
            ),
            HotspotGroup(
                "clockmaking",
                "Clockmaking space",
                "loc.poh_workshop_2",
                listOf(
                    Buildable("Crafting table 1", 16, 240.0, mats(OAK to 4), "loc.poh_clockmaking_1"),
                    Buildable("Crafting table 2", 25, 1.0, mats(GLASS to 1), "loc.poh_clockmaking_2", upgrade = true),
                    Buildable("Crafting table 3", 34, 2.0, mats(GLASS to 2), "loc.poh_clockmaking_3", upgrade = true),
                    Buildable("Crafting table 4", 42, 120.0, mats(OAK to 2), "loc.poh_clockmaking_4", upgrade = true),
                ),
            ),
            HotspotGroup("tools", "Tool space", TOOL_SPACES, toolStores()),
            HotspotGroup(
                "repair",
                "Repair space",
                "loc.poh_workshop_4",
                listOf(
                    Buildable("Repair bench", 15, 120.0, mats(OAK to 2), "loc.poh_repair_1"),
                    Buildable("Whetstone", 35, 260.0, mats(OAK to 4, LIMESTONE to 1), "loc.poh_repair_2"),
                    Buildable("Armour stand", 55, 500.0, mats(OAK to 8, LIMESTONE to 1), "loc.poh_repair_3"),
                ),
            ),
            HotspotGroup(
                "heraldry",
                "Heraldry space",
                "loc.poh_workshop_5",
                listOf(
                    Buildable("Pluming stand", 16, 120.0, mats(OAK to 2), "loc.poh_repair_4"),
                    Buildable("Shield easel", 41, 240.0, mats(OAK to 4), "loc.poh_repair_5"),
                    Buildable("Banner easel", 66, 510.0, mats(OAK to 8, CLOTH to 2), "loc.poh_repair_6"),
                ),
            ),
        )

    // ------------------------------------------------------------------------- bedroom

    val BEDROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "bed",
                "Bed space",
                "loc.poh_bedroom_1_doublebed",
                listOf(
                    Buildable("Wooden bed", 20, 117.0, mats(PLANK to 3, NAILS to 3, CLOTH to 2), "loc.poh_bed_1"),
                    Buildable("Oak bed", 30, 210.0, mats(OAK to 3, CLOTH to 2), "loc.poh_bed_2"),
                    Buildable("Large oak bed", 34, 330.0, mats(OAK to 5, CLOTH to 2), "loc.poh_bed_3"),
                    Buildable("Teak bed", 40, 300.0, mats(TEAK to 3, CLOTH to 2), "loc.poh_bed_4"),
                    Buildable("Large teak bed", 45, 480.0, mats(TEAK to 5, CLOTH to 2), "loc.poh_bed_5"),
                    Buildable("4-poster bed", 53, 450.0, mats(MAHOGANY to 3, CLOTH to 2), "loc.poh_bed_6"),
                    Buildable("Gilded 4-poster", 60, 1330.0, mats(MAHOGANY to 5, CLOTH to 2, GOLD_LEAF to 2), "loc.poh_bed_7"),
                ),
            ),
            HotspotGroup(
                "wardrobe",
                "Wardrobe space",
                "loc.poh_bedroom_2",
                listOf(
                    Buildable("Shoe box", 20, 58.0, mats(PLANK to 2, NAILS to 2), "loc.poh_wardrobe_1"),
                    Buildable("Oak drawers", 27, 120.0, mats(OAK to 2), "loc.poh_wardrobe_2"),
                    Buildable("Oak wardrobe", 39, 180.0, mats(OAK to 3), "loc.poh_wardrobe_3"),
                    Buildable("Teak drawers", 51, 180.0, mats(TEAK to 2), "loc.poh_wardrobe_4"),
                    Buildable("Teak wardrobe", 63, 270.0, mats(TEAK to 3), "loc.poh_wardrobe_5"),
                    Buildable("Mahogany wardrobe", 75, 420.0, mats(MAHOGANY to 3), "loc.poh_wardrobe_6"),
                    Buildable("Gilded wardrobe", 87, 720.0, mats(MAHOGANY to 3, GOLD_LEAF to 1), "loc.poh_wardrobe_7"),
                ),
            ),
            HotspotGroup(
                "dresser",
                "Dresser space",
                "loc.poh_bedroom_3",
                listOf(
                    Buildable("Shaving stand", 21, 30.0, mats(PLANK to 1, NAILS to 1, GLASS to 1), "loc.poh_mirror_1"),
                    Buildable("Oak shaving stand", 29, 61.0, mats(OAK to 1, GLASS to 1), "loc.poh_mirror_2"),
                    Buildable("Oak dresser", 37, 121.0, mats(OAK to 2, GLASS to 1), "loc.poh_mirror_3"),
                    Buildable("Teak dresser", 46, 181.0, mats(TEAK to 2, GLASS to 1), "loc.poh_mirror_4"),
                    Buildable("Fancy teak dresser", 56, 182.0, mats(TEAK to 2, GLASS to 2), "loc.poh_mirror_5"),
                    Buildable("Mahogany dresser", 64, 281.0, mats(MAHOGANY to 2, GLASS to 1), "loc.poh_mirror_6"),
                    Buildable("Gilded dresser", 74, 582.0, mats(MAHOGANY to 2, GLASS to 2, GOLD_LEAF to 1), "loc.poh_mirror_7"),
                ),
            ),
            curtains("loc.poh_bedroom_4"),
            rug("loc.poh_bedroom_5_middle", "loc.poh_bedroom_5_side", "loc.poh_bedroom_5_corner"),
            fireplace("loc.poh_bedroom_6"),
            HotspotGroup(
                "clock",
                "Corner space",
                "loc.poh_bedroom_7",
                listOf(
                    Buildable("Oak clock", 25, 142.0, mats(OAK to 2, CLOCKWORK to 1), "loc.poh_clock_1"),
                    Buildable("Teak clock", 55, 202.0, mats(TEAK to 2, CLOCKWORK to 1), "loc.poh_clock_2"),
                    Buildable("Gilded clock", 85, 602.0, mats(MAHOGANY to 2, CLOCKWORK to 1, GOLD_LEAF to 1), "loc.poh_clock_3"),
                ),
            ),
        )

    // -------------------------------------------------------------------------- halls

    val SKILL_HALL: List<HotspotGroup> =
        listOf(
            staircase("loc.poh_hall1_1_stairs_up"),
            rug("loc.poh_hall1_1_middle", "loc.poh_hall1_1_side", "loc.poh_hall1_1_corner"),
            HotspotGroup(
                "head_trophy",
                "Head trophy space",
                "loc.poh_hall1_2",
                listOf(
                    Buildable("Teak display", 38, 180.0, mats(TEAK to 2), "loc.poh_trophy_head_blank_teak"),
                    Buildable("Mahogany display", 58, 280.0, mats(MAHOGANY to 2), "loc.poh_trophy_head_blank_mahogany", upgradeMaterials = mats(MAHOGANY to 2)),
                    Buildable("Gilded display", 78, 600.0, mats(MAHOGANY to 2, GOLD_LEAF to 2), "loc.poh_trophy_head_blank_gilded", upgradeMaterials = mats(GOLD_LEAF to 2)),
                ),
            ),
            HotspotGroup(
                "fishing_trophy",
                "Fishing trophy space",
                "loc.poh_hall1_4",
                listOf(
                    Buildable("Oak display", 36, 120.0, mats(OAK to 2), "loc.poh_trophy_fish_blank_oak"),
                    Buildable("Teak display", 56, 180.0, mats(TEAK to 2), "loc.poh_trophy_fish_blank_teak", upgradeMaterials = mats(TEAK to 2)),
                    Buildable("Mahogany display", 76, 280.0, mats(MAHOGANY to 2), "loc.poh_trophy_fish_blank_mahogany", upgradeMaterials = mats(MAHOGANY to 2)),
                ),
            ),
            HotspotGroup(
                "armour",
                "Armour space",
                "loc.poh_hall1_5",
                listOf(
                    armour("Mithril armour", 135.0, "mithril", 68),
                    armour("Adamantite armour", 150.0, "adamant", 88),
                    armour("Runite armour", 165.0, "rune", 99),
                ),
            ),
            HotspotGroup(
                "cw_armour",
                "Armour space",
                "loc.poh_hall1_6",
                listOf(
                    castleWarsArmour("CW armour 1", 135.0, "", "red"),
                    castleWarsArmour("CW armour 2", 150.0, "_2", "white"),
                    castleWarsArmour("CW armour 3", 165.0, "_3", "gold"),
                ),
            ),
            HotspotGroup(
                "rune_case",
                "Rune case space",
                "loc.poh_hall1_7",
                listOf(
                    runeCase(1, 190.0, 14, "airrune", "waterrune", "earthrune", "firerune"),
                    runeCase(2, 212.0, 44, "bodyrune", "cosmicrune", "chaosrune", "naturerune"),
                    runeCase(3, 247.0, 90, "lawrune", "bloodrune", "soulrune", "deathrune"),
                ),
            ),
        )

    /** A suit on an armour stand: it needs Smithing as well, and comes back off when removed. */
    private fun armour(label: String, xp: Double, metal: String, smithing: Int): Buildable {
        val suit = mats("obj.${metal}_full_helm" to 1, "obj.${metal}_platebody" to 1, "obj.${metal}_plateskirt" to 1)
        return Buildable(
            label,
            28,
            xp,
            mats(OAK to 2) + suit,
            "loc.poh_armour_${metal}_5",
            BuildSound.METAL,
            skill = "stat.smithing",
            skillLevel = smithing,
            skillXp = 25.0,
            refund = suit,
        )
    }

    private fun castleWarsArmour(label: String, xp: Double, suffix: String, colour: String): Buildable {
        val suit =
            mats(
                "obj.castlewars_med_helm$suffix" to 1,
                "obj.castlewars_armour_body$suffix" to 1,
                "obj.castlewars_shield$suffix" to 1,
            )
        return Buildable(label, 28, xp, mats(OAK to 2) + suit, "loc.poh_armour_castlewars_${colour}_6", BuildSound.METAL, refund = suit)
    }

    private fun runeCase(tier: Int, xp: Double, runecraft: Int, vararg runes: String) =
        Buildable(
            "Rune case $tier",
            41,
            xp,
            mats(TEAK to 2, GLASS to 2) + runes.map { Material("obj.$it", 1) },
            "loc.poh_display_case_rune${tier}_6",
            skill = "stat.runecrafting",
            skillLevel = runecraft,
            skillXp = 25.0,
        )

    val QUEST_HALL: List<HotspotGroup> =
        listOf(
            staircase("loc.poh_hall2_1_stairs_up"),
            rug("loc.poh_hall2_1_middle", "loc.poh_hall2_1_side", "loc.poh_hall2_1_corner"),
            bookcase("bookcase", "loc.poh_hall2_7"),
            HotspotGroup(
                "portrait",
                "Portrait space",
                "loc.poh_hall2_2",
                listOf(
                    Buildable("King Arthur", 35, 211.0, mats(TEAK to 2, PAINTING + "kingarthur" to 1), "loc.poh_portrait_kingarthur_1"),
                    Buildable("Elena", 35, 211.0, mats(TEAK to 2, PAINTING + "elena" to 1), "loc.poh_portrait_elena_1"),
                    Buildable("Giant Dwarf", 35, 211.0, mats(TEAK to 2, PAINTING + "giantdwarf" to 1), "loc.poh_portrait_giantdwarf_1"),
                    Buildable("Miscellanians", 55, 311.0, mats(MAHOGANY to 2, PAINTING + "prince+princess" to 1), "loc.poh_portrait_prince+princess_1"),
                ),
            ),
            HotspotGroup(
                "landscape",
                "Landscape space",
                "loc.poh_hall2_3",
                listOf(
                    Buildable("Lumbridge", 44, 314.0, mats(TEAK to 3, PAINTING + "lumbridge" to 1), "loc.poh_landscape_lumbridge_1"),
                    Buildable("The Desert", 44, 314.0, mats(TEAK to 3, PAINTING + "desert" to 1), "loc.poh_landscape_desert_1"),
                    Buildable("Morytania", 44, 314.0, mats(TEAK to 3, PAINTING + "morytania" to 1), "loc.poh_landscape_morytania_1"),
                    Buildable("Karamja", 65, 464.0, mats(MAHOGANY to 3, PAINTING + "karamja" to 1), "loc.poh_landscape_karamja_1"),
                    Buildable("Isafdar", 65, 464.0, mats(MAHOGANY to 3, PAINTING + "istafar" to 1), "loc.poh_landscape_istafar_1"),
                ),
            ),
            HotspotGroup(
                "trophy",
                "Guild trophy space",
                "loc.poh_hall2_4",
                listOf(
                    Buildable("Anti-dragon shield", 47, 280.0, mats(TEAK to 3, "obj.antidragonbreathshield" to 1), "loc.poh_trophy_antidragonbreath_4", quest = "quest_dragonslayer1"),
                    Buildable("Amulet of glory", 47, 290.0, mats(TEAK to 3, "obj.amulet_of_glory" to 1), "loc.poh_trophy_amuletofglory_4"),
                    Buildable("Cape of Legends", 47, 300.0, mats(TEAK to 3, "obj.cape_of_legends" to 1), "loc.poh_trophy_legendscape_4", quest = "quest_legends"),
                    Buildable("Mythical cape", 47, 370.0, mats(TEAK to 3, "obj.mythical_cape" to 1), "loc.poh_trophy_mythical_cape", quest = "quest_dragonslayer2"),
                ),
            ),
            HotspotGroup(
                "sword",
                "Sword space",
                "loc.poh_hall2_5",
                listOf(
                    Buildable("Silverlight", 42, 187.0, mats(TEAK to 2, "obj.silverlight" to 1), "loc.poh_trophy_silverlight_5", quest = "quest_demonslayer"),
                    Buildable("Excalibur", 42, 194.0, mats(TEAK to 2, "obj.excalibur" to 1), "loc.poh_trophy_excalibur_5", quest = "quest_merlinscrystal"),
                    Buildable("Darklight", 42, 202.0, mats(TEAK to 2, "obj.darklight" to 1), "loc.poh_trophy_darklight_5", quest = "quest_shadowofthestorm"),
                ),
            ),
            HotspotGroup(
                "map",
                "Map space",
                "loc.poh_hall2_6",
                listOf(
                    Buildable("Small map", 38, 211.0, mats(TEAK to 2, PAINTING + "small_map" to 1), "loc.poh_wall_map_freearea"),
                    Buildable("Medium map", 58, 451.0, mats(MAHOGANY to 3, PAINTING + "medium_map" to 1), "loc.poh_wall_map_world"),
                    Buildable("Large map", 78, 591.0, mats(MAHOGANY to 4, PAINTING + "large_map" to 1), "loc.poh_wall_map_world+underground"),
                ),
            ),
        )

    // -------------------------------------------------------------------------- chapel

    val CHAPEL: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "altar",
                "Altar space",
                "loc.poh_chapel_2",
                listOf(
                    Buildable("Oak altar", 45, 240.0, mats(OAK to 4), "loc.poh_altar_saradomin_1"),
                    Buildable("Teak altar", 50, 360.0, mats(TEAK to 4), "loc.poh_altar_saradomin_2"),
                    Buildable("Cloth altar", 56, 390.0, mats(TEAK to 4, CLOTH to 2), "loc.poh_altar_saradomin_3"),
                    Buildable("Mahogany altar", 60, 590.0, mats(MAHOGANY to 4, CLOTH to 2), "loc.poh_altar_saradomin_4"),
                    Buildable("Limestone altar", 64, 910.0, mats(MAHOGANY to 6, CLOTH to 2, LIMESTONE to 2), "loc.poh_altar_saradomin_5"),
                    Buildable("Marble altar", 70, 1030.0, mats(MARBLE to 2, CLOTH to 2), "loc.poh_altar_saradomin_6", BuildSound.STONE),
                    Buildable("Gilded altar", 75, 2230.0, mats(MARBLE to 2, CLOTH to 2, GOLD_LEAF to 4), "loc.poh_altar_saradomin_7", BuildSound.STONE),
                ),
            ),
            HotspotGroup(
                "icon",
                "Icon space",
                "loc.poh_chapel_1",
                listOf(
                    Buildable("Symbol of Saradomin", 48, 120.0, mats(OAK to 2), "loc.poh_icon_1"),
                    Buildable("Symbol of Zamorak", 48, 120.0, mats(OAK to 2), "loc.poh_icon_2"),
                    Buildable("Symbol of Guthix", 48, 120.0, mats(OAK to 2), "loc.poh_icon_3"),
                    Buildable("Icon of Saradomin", 59, 960.0, mats(TEAK to 4, GOLD_LEAF to 2), "loc.poh_icon_4"),
                    Buildable("Icon of Zamorak", 59, 960.0, mats(TEAK to 4, GOLD_LEAF to 2), "loc.poh_icon_5"),
                    Buildable("Icon of Guthix", 59, 960.0, mats(TEAK to 4, GOLD_LEAF to 2), "loc.poh_icon_6"),
                    Buildable("Icon of Bob", 71, 1160.0, mats(MAHOGANY to 4, GOLD_LEAF to 2), "loc.poh_icon_7"),
                ),
            ),
            HotspotGroup(
                "lamp",
                "Lamp space",
                "loc.poh_chapel_3",
                listOf(
                    Buildable("Steel torches", 45, 40.0, mats(STEEL_BAR to 2), "loc.poh_torch_1", BuildSound.METAL),
                    Buildable("Wooden torches", 49, 58.0, mats(PLANK to 2, NAILS to 2), "loc.poh_torch_2"),
                    Buildable("Steel candlesticks", 53, 124.0, mats(STEEL_BAR to 6, CANDLE to 6), "loc.poh_torch_3", BuildSound.METAL),
                    Buildable("Gold candlesticks", 57, 46.0, mats(GOLD_BAR to 6, CANDLE to 6), "loc.poh_torch_4", BuildSound.METAL),
                    Buildable("Oak incense burners", 61, 280.0, mats(OAK to 4, STEEL_BAR to 2), "loc.poh_torch_5"),
                    Buildable("Mahogany incense burners", 65, 600.0, mats(MAHOGANY to 4, STEEL_BAR to 2), "loc.poh_torch_6"),
                ),
            ),
            HotspotGroup(
                "musical",
                "Musical space",
                "loc.poh_chapel_7",
                listOf(
                    Buildable("Windchimes", 49, 323.0, mats(OAK to 4, NAILS to 4, STEEL_BAR to 4), "loc.poh_musical_thing_1"),
                    Buildable("Bells", 58, 480.0, mats(TEAK to 4, STEEL_BAR to 6), "loc.poh_musical_thing_2"),
                    Buildable("Organ", 69, 680.0, mats(MAHOGANY to 4, STEEL_BAR to 6), "loc.poh_musical_thing_3"),
                ),
            ),
            rug("loc.poh_chapel_5_middle", "loc.poh_chapel_5_side", "loc.poh_chapel_5_corner"),
        )

    // --------------------------------------------------------------------------- study

    val STUDY: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "lectern",
                "Lectern space",
                "loc.poh_study_1",
                listOf(
                    Buildable("Oak lectern", 40, 60.0, mats(OAK to 1), "loc.poh_lectern_1"),
                    Buildable("Eagle lectern", 47, 120.0, mats(OAK to 2), "loc.poh_lectern_2"),
                    Buildable("Demon lectern", 47, 120.0, mats(OAK to 2), "loc.poh_lectern_3"),
                    Buildable("Teak eagle lectern", 57, 180.0, mats(TEAK to 2), "loc.poh_lectern_4"),
                    Buildable("Teak demon lectern", 57, 180.0, mats(TEAK to 2), "loc.poh_lectern_5"),
                    Buildable("Mahogany eagle lectern", 67, 580.0, mats(MAHOGANY to 2, GOLD_LEAF to 1), "loc.poh_lectern_6"),
                    Buildable("Mahogany demon lectern", 67, 580.0, mats(MAHOGANY to 2, GOLD_LEAF to 1), "loc.poh_lectern_7"),
                ),
            ),
            HotspotGroup(
                "globe",
                "Globe space",
                "loc.poh_study_2",
                listOf(
                    Buildable("Globe", 41, 180.0, mats(OAK to 3), "loc.poh_globe_1"),
                    Buildable("Ornamental globe", 50, 270.0, mats(TEAK to 3), "loc.poh_globe_2"),
                    Buildable("Lunar globe", 59, 570.0, mats(TEAK to 3, GOLD_LEAF to 1), "loc.poh_globe_3"),
                    Buildable("Celestial globe", 68, 570.0, mats(TEAK to 3, GOLD_LEAF to 1), "loc.poh_globe_4"),
                    Buildable("Armillary sphere", 77, 960.0, mats(MAHOGANY to 2, GOLD_LEAF to 2, STEEL_BAR to 4), "loc.poh_globe_5"),
                    Buildable("Small orrery", 86, 1320.0, mats(MAHOGANY to 3, GOLD_LEAF to 3), "loc.poh_globe_6"),
                    Buildable("Large orrery", 95, 1420.0, mats(MAHOGANY to 3, GOLD_LEAF to 5), "loc.poh_globe_7"),
                ),
            ),
            HotspotGroup(
                "crystal_ball",
                "Crystal ball space",
                "loc.poh_study_4",
                listOf(
                    Buildable("Crystal ball", 42, 280.0, mats(TEAK to 3, ORB to 1), "loc.poh_crystalball_1"),
                    Buildable("Elemental sphere", 54, 580.0, mats(TEAK to 3, ORB to 1, GOLD_LEAF to 1), "loc.poh_crystalball_2"),
                    Buildable("Crystal of power", 66, 890.0, mats(MAHOGANY to 2, ORB to 1, GOLD_LEAF to 2), "loc.poh_crystalball_3"),
                ),
            ),
            HotspotGroup(
                "wall_chart",
                "Wall chart space",
                "loc.poh_study_5",
                listOf(
                    Buildable("Alchemical chart", 43, 30.0, mats(CLOTH to 2), "loc.poh_wallchart_1"),
                    Buildable("Astronomical chart", 63, 45.0, mats(CLOTH to 3), "loc.poh_wallchart_2"),
                    Buildable("Infernal chart", 83, 60.0, mats(CLOTH to 4), "loc.poh_wallchart_3"),
                ),
            ),
            HotspotGroup(
                "telescope",
                "Telescope space",
                "loc.poh_study_6",
                listOf(
                    Buildable("Oak telescope", 44, 121.0, mats(OAK to 2, GLASS to 1), "loc.poh_telescope_1"),
                    Buildable("Teak telescope", 64, 181.0, mats(TEAK to 2, GLASS to 1), "loc.poh_telescope_2"),
                    Buildable("Mahogany telescope", 84, 580.0, mats(MAHOGANY to 2, GLASS to 1), "loc.poh_telescope_3"),
                ),
            ),
            bookcase("bookcase", "loc.poh_study_7"),
        )

    // ------------------------------------------------------------------ portal chamber

    val PORTAL_CHAMBER: List<HotspotGroup> =
        listOf(
            portalFrame("portal_1", "loc.poh_teleroom_1"),
            portalFrame("portal_2", "loc.poh_teleroom_2"),
            portalFrame("portal_3", "loc.poh_teleroom_3"),
            HotspotGroup(
                "centrepiece",
                "Centrepiece space",
                "loc.poh_teleroom_7",
                listOf(
                    Buildable("Teleport focus", 50, 40.0, mats(LIMESTONE to 2), "loc.poh_teleport_centrepiece", BuildSound.STONE),
                    Buildable("Greater teleport focus", 65, 500.0, mats(MARBLE to 1), "loc.poh_teleport_centrepiece_grand", BuildSound.STONE),
                    Buildable("Scrying pool", 80, 2000.0, mats(MARBLE to 4), "loc.poh_scrying_pool", BuildSound.STONE),
                ),
            ),
        )

    private fun portalFrame(key: String, loc: String) =
        HotspotGroup(
            key,
            "Portal space",
            loc,
            listOf(
                Buildable("Teak portal", 50, 270.0, mats(TEAK to 3), "loc.poh_portal_teak_empty"),
                Buildable("Mahogany portal", 65, 420.0, mats(MAHOGANY to 3), "loc.poh_portal_mag_empty"),
                Buildable("Marble portal", 80, 1500.0, mats(MARBLE to 3), "loc.poh_portal_marble_empty", BuildSound.STONE),
            ),
        )

    // --------------------------------------------------------------------- throne room

    /** The style names the throne room's floor hotspot is authored under, one per house style block. */
    private val THRONE_FLOOR_STYLES =
        listOf("rimmington", "lumbridge", "pollnivneach", "rellekka", "brimhaven", "yanille", "deathly")

    /** Every throne room floor is laid as the style's floor decoration; its cage only comes down with the lever. */
    private val THRONE_MAT = THRONE_FLOOR_STYLES.map { "loc.poh_floordecor_$it" }

    val THRONE_ROOM: List<HotspotGroup> =
        listOf(
            HotspotGroup(
                "throne",
                "Throne space",
                "loc.poh_throne_room_1",
                listOf(
                    Buildable("Oak throne", 60, 800.0, mats(OAK to 5, MARBLE to 1), "loc.poh_throne_1"),
                    Buildable("Teak throne", 67, 1450.0, mats(TEAK to 5, MARBLE to 2), "loc.poh_throne_2"),
                    Buildable("Mahogany throne", 74, 2200.0, mats(MAHOGANY to 5, MARBLE to 3), "loc.poh_throne_3"),
                    Buildable("Gilded throne", 81, 2600.0, mats(MAHOGANY to 5, MARBLE to 2, GOLD_LEAF to 3), "loc.poh_throne_4"),
                    Buildable(
                        "Skeleton throne",
                        88,
                        7003.0,
                        mats(MAGIC_STONE to 5, MARBLE to 4, "obj.bones" to 5, "obj.skull" to 2),
                        "loc.poh_throne_5",
                        BuildSound.STONE,
                    ),
                    Buildable("Crystal throne", 95, 15000.0, mats(MAGIC_STONE to 15), "loc.poh_throne_6", BuildSound.STONE),
                    Buildable("Demonic throne", 99, 25000.0, mats(MAGIC_STONE to 25), "loc.poh_throne_7", BuildSound.STONE),
                ),
            ),
            HotspotGroup(
                "lever",
                "Lever space",
                "loc.poh_throne_room_4",
                listOf(
                    Buildable("Oak lever", 68, 300.0, mats(OAK to 5), "loc.poh_lever_oak_4"),
                    Buildable("Teak lever", 78, 450.0, mats(TEAK to 5), "loc.poh_lever_teak_4"),
                    Buildable("Mahogany lever", 88, 700.0, mats(MAHOGANY to 5), "loc.poh_lever_mag_4"),
                ),
            ),
            throneBench("seating_1", "loc.poh_throne_room_5"),
            throneBench("seating_2", "loc.poh_throne_room_6"),
            HotspotGroup(
                "decoration",
                "Decoration space",
                "loc.poh_throne_room_3_q",
                listOf(
                    Buildable("Gilded decoration", 56, 1020.0, mats(MAHOGANY to 3, GOLD_LEAF to 2), "loc.poh_wall_deco_3"),
                    Buildable("Round shield", 66, 120.0, mats(OAK to 2), Heraldry.crestDecor("oak")),
                    Buildable("Square shield", 76, 360.0, mats(TEAK to 4), Heraldry.crestDecor("teak")),
                    Buildable("Kite shield", 86, 420.0, mats(MAHOGANY to 3), Heraldry.crestDecor("mahogany")),
                ),
            ),
            HotspotGroup(
                "floor",
                "Floor space",
                THRONE_FLOOR_STYLES.map { "loc.poh_throne_room_3_$it" },
                listOf(
                    Buildable("Floor decoration", 61, 700.0, mats(MAHOGANY to 5), THRONE_MAT),
                    Buildable("Steel cage", 68, 1100.0, mats(MAHOGANY to 5, STEEL_BAR to 20), THRONE_MAT, BuildSound.METAL),
                    Buildable("Trapdoor", 74, 770.0, mats(MAHOGANY to 5, CLOCKWORK to 10), THRONE_MAT),
                    Buildable("Lesser magic cage", 82, 2700.0, mats(MAHOGANY to 5, MAGIC_STONE to 2), THRONE_MAT),
                    Buildable("Greater magic cage", 89, 4700.0, mats(MAHOGANY to 5, MAGIC_STONE to 4), THRONE_MAT),
                ),
            ),
            HotspotGroup(
                "trapdoor",
                "Trapdoor space",
                Oubliette.TRAPDOOR_HOTSPOT,
                listOf(
                    Buildable("Oak trapdoor", 68, 300.0, mats(OAK to 5), Oubliette.TRAPDOORS[0]),
                    Buildable("Teak trapdoor", 78, 450.0, mats(TEAK to 5), Oubliette.TRAPDOORS[1]),
                    Buildable("Mahogany trapdoor", 88, 700.0, mats(MAHOGANY to 5), Oubliette.TRAPDOORS[2]),
                ),
            ),
        )

    private fun throneBench(key: String, loc: String) =
        HotspotGroup(
            key,
            "Seating space",
            loc,
            listOf(
                Buildable("Carved teak bench", 44, 360.0, mats(TEAK to 4), "loc.poh_throneroom_bench_1"),
                Buildable("Mahogany bench", 52, 560.0, mats(MAHOGANY to 4), "loc.poh_throneroom_bench_2"),
                Buildable("Gilded bench", 61, 1760.0, mats(MAHOGANY to 4, GOLD_LEAF to 4), "loc.poh_throneroom_bench_3"),
            ),
        )
}
