package org.rsmod.content.skills.construction.data

/**
 * The hotspots each room offers and what can be built on them.
 *
 * Hotspot loc names and the locs they turn into come from the cache; the levels, materials and
 * experience come from the Old School wiki. A hotspot whose data could not be sourced - the trophy
 * and quest-item spaces, the chapel statues, the workshop tool stores - is simply left out, and the
 * build script hides it rather than offering something invented.
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
    private const val GOLD_LEAF = "obj.gold_leaf"
    private const val STEEL_BAR = "obj.steel_bar"
    private const val GOLD_BAR = "obj.gold_bar"
    private const val IRON_BAR = "obj.iron_bar"
    private const val GLASS = "obj.molten_glass"
    private const val ORB = "obj.stafforb"
    private const val CLOCKWORK = "obj.poh_clockwork_mechanism"
    private const val ROPE = "obj.rope"
    private const val CANDLE = "obj.unlit_candle"

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
                ),
            ),
            trees("big_tree", "Big tree space", "loc.poh_crude_garden_2", "big_tree", "_4"),
            trees("tree", "Tree space", "loc.poh_crude_garden_3", "small_tree", "_5"),
            plants("big_plant_1", "Big plant space", "loc.poh_crude_garden_4", "plantbig1"),
            plants("big_plant_2", "Big plant space", "loc.poh_crude_garden_5", "plantbig2"),
            plants("small_plant_1", "Small plant space", "loc.poh_crude_garden_6", "plantbsmall1"),
            plants("small_plant_2", "Small plant space", "loc.poh_crude_garden_7", "plantbsmall2"),
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
        )

    private fun plants(key: String, label: String, hotspot: String, prefix: String) =
        HotspotGroup(
            key,
            label,
            hotspot,
            listOf(
                Buildable("Plant", 1, 31.0, mats("obj.poh_sapling_plant_1" to 1), "loc.poh_${prefix}a"),
                Buildable("Bush", 6, 70.0, mats("obj.poh_sapling_plant_2" to 1), "loc.poh_${prefix}b"),
                Buildable("Tall plant", 12, 100.0, mats("obj.poh_sapling_plant_3" to 1), "loc.poh_${prefix}c"),
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
                ),
            ),
            HotspotGroup(
                "clockmaking",
                "Clockmaking space",
                "loc.poh_workshop_2",
                listOf(
                    Buildable("Crafting table 1", 16, 240.0, mats(OAK to 4), "loc.poh_clockmaking_1"),
                ),
            ),
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
        )

    val QUEST_HALL: List<HotspotGroup> =
        listOf(
            staircase("loc.poh_hall2_1_stairs_up"),
            rug("loc.poh_hall2_1_middle", "loc.poh_hall2_1_side", "loc.poh_hall2_1_corner"),
            bookcase("bookcase", "loc.poh_hall2_7"),
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
}
