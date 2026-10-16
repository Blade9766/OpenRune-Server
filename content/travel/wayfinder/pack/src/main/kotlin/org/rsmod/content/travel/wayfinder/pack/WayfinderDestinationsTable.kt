package org.rsmod.content.travel.wayfinder.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

/**
 * The wayfinder teleport network. One row per destination, listed in display order; the mages,
 * the teleport interface and bots all read this table, so a new destination is a new row (plus
 * its `dbrow.` gameval and, optionally, a preview sprite).
 *
 * - `key` is the stable id favourites and recent journeys are saved by; never rename one.
 *   `slot` is assigned from table order and only identifies a row while the window is open.
 * - `category`: 0 towns, 1 skilling, 2 dungeons, 3 slayer, 4 bosses, 5 minigames, 6 wilderness.
 * - `icon` is the row's item model. `preview` is the details picture: sprite group
 *   `PREVIEW_BASE + slot`, packed from `pack/sprites/wayfinder_<key>.png` (see sprites/manifest.toml)
 *   and left empty when no picture exists, so the window shows the icon model instead.
 * - `danger`: 0 safe, 1 low, 2 high, 3 deadly. Any tile inside the Wilderness also asks the
 *   player to confirm, whatever this says.
 * - `quest` (a quest key) is checked before travel, under the server's quest requirement mode.
 *   Skill levels are not: arrival tiles sit outside entrances, so a guild door, course obstacle or
 *   dungeon gate still applies its own level check.
 * - `cost` in coins; 0 is free (every destination is free today).
 * - `bot_allowed`: whether in-game bots may use the destination; off for anything dangerous.
 */
object WayfinderDestinationsTable {
    const val KEY = 0
    const val SLOT = 1
    const val NAME = 2
    const val CATEGORY = 3
    const val DESCRIPTION = 4
    const val COORDS = 5
    const val DANGER = 6
    const val WARNING = 7
    const val QUEST = 8
    const val BOT_ALLOWED = 9
    const val KEYWORDS = 10
    const val ENABLED = 11
    const val ARRIVAL = 12
    const val TAGS = 13
    const val ICON = 14
    const val PREVIEW = 15
    const val COST = 16

    private const val TOWNS = 0
    private const val SKILLING = 1
    private const val DUNGEONS = 2
    private const val SLAYER = 3
    private const val BOSSES = 4
    private const val MINIGAMES = 5
    private const val WILDERNESS = 6

    private const val SAFE = 0
    private const val LOW = 1
    private const val HIGH = 2
    private const val DEADLY = 3

    const val PREVIEW_BASE = 9100

    private const val PRIEST_IN_PERIL = "quest_priestinperil"
    private const val WILD = "You will arrive in the Wilderness. Other players can attack you, and you lose your items if you die."
    private const val DEEP = "Deep Wilderness. Player killers roam here and anything you carry can be lost on death."

    fun destinations() =
        dbTable("dbtable.wayfinder_destinations", serverOnly = true) {
            column("key", KEY, VarType.STRING)
            column("slot", SLOT, VarType.INT)
            column("name", NAME, VarType.STRING)
            column("category", CATEGORY, VarType.INT)
            column("description", DESCRIPTION, VarType.STRING)
            column("coords", COORDS, VarType.COORDGRID)
            column("danger", DANGER, VarType.INT)
            column("warning", WARNING, VarType.STRING)
            column("quest", QUEST, VarType.STRING)
            column("bot_allowed", BOT_ALLOWED, VarType.BOOLEAN)
            column("keywords", KEYWORDS, VarType.STRING)
            column("enabled", ENABLED, VarType.BOOLEAN)
            column("arrival", ARRIVAL, VarType.STRING)
            column("tags", TAGS, VarType.STRING)
            column("icon", ICON, VarType.OBJ)
            column("preview", PREVIEW, VarType.INT)
            column("cost", COST, VarType.INT)

            var nextSlot = 1

            fun destination(
                key: String,
                name: String,
                category: Int,
                coords: CoordGrid,
                description: String,
                arrival: String,
                tags: String,
                icon: String,
                danger: Int = SAFE,
                warning: String? = null,
                quest: String? = null,
                bots: Boolean = danger == SAFE && category != WILDERNESS,
                keywords: String? = null,
            ) {
                val slot = nextSlot++
                row("dbrow.wayfinder_$key") {
                    column(KEY, key)
                    column(SLOT, slot)
                    column(NAME, name)
                    column(CATEGORY, category)
                    column(DESCRIPTION, description)
                    columnCoord(COORDS, coords)
                    column(DANGER, danger)
                    warning?.let { column(WARNING, it) }
                    quest?.let { column(QUEST, it) }
                    column(BOT_ALLOWED, bots)
                    keywords?.let { column(KEYWORDS, it) }
                    column(ENABLED, true)
                    column(ARRIVAL, arrival)
                    column(TAGS, tags)
                    columnRSCM(ICON, icon)
                    if (hasPreview(key)) {
                        column(PREVIEW, PREVIEW_BASE + slot)
                    }
                    column(COST, 0)
                }
            }

            fun tile(x: Int, z: Int) = CoordGrid(x, z, 0)

            destination("lumbridge", "Lumbridge", TOWNS, tile(3222, 3218), "The castle town where every adventure begins.", "Castle courtyard", "Cooking,Firemaking,Quests", "obj.poh_tablet_lumbridgeteleport", keywords = "home,castle,duke")
            destination("varrock", "Varrock", TOWNS, tile(3213, 3428), "The bustling capital of Misthalin.", "Town square", "Smithing,Shops,Quests", "obj.poh_tablet_varrockteleport", keywords = "capital,square")
            destination("falador", "Falador", TOWNS, tile(2965, 3380), "The white-walled city of the White Knights.", "Falador park", "Mining,Smithing,Farming", "obj.poh_tablet_faladorteleport", keywords = "white knights,park")
            destination("edgeville", "Edgeville", TOWNS, tile(3087, 3496), "A quiet village on the Wilderness border.", "Village bank", "Banking,Slayer,Wilderness", "obj.amulet_of_glory", keywords = "edge,bank")
            destination("draynor_village", "Draynor Village", TOWNS, tile(3093, 3244), "Market stalls, willows and a spooky manor.", "Market square", "Woodcutting,Fishing,Thieving", "obj.garlic", keywords = "draynor")
            destination("al_kharid", "Al Kharid", TOWNS, tile(3293, 3174), "A desert town of silk and kebabs.", "Palace gates", "Mining,Crafting,Shops", "obj.kebab", keywords = "alkharid,desert,palace")
            destination("catherby", "Catherby", TOWNS, tile(2809, 3435), "A peaceful coastal village.", "Village bank", "Fishing,Cooking,Farming", "obj.raw_lobster")
            destination("east_ardougne", "Ardougne", TOWNS, tile(2662, 3305), "King Lathas's city and its busy market.", "Market square", "Thieving,Agility,Farming", "obj.poh_tablet_ardougneteleport", keywords = "ardy,east ardougne")
            destination("grand_exchange", "Grand Exchange", TOWNS, tile(3164, 3486), "Trade with all of Gielinor.", "Exchange plaza", "Trading,Banking", "obj.coins", keywords = "ge,trade,market")
            destination("port_sarim", "Port Sarim", TOWNS, tile(3022, 3217), "Asgarnia's harbour town.", "Docks", "Fishing,Shops,Boats", "obj.rope", keywords = "port,docks,boat")
            destination("rimmington", "Rimmington", TOWNS, tile(2957, 3214), "A sleepy hamlet with a witch and a mine.", "Village centre", "Mining,Crafting", "obj.eye_of_newt")
            destination("barbarian_village", "Barbarian Village", TOWNS, tile(3081, 3421), "Rowdy barbarians and a river of trout.", "Village centre", "Fishing,Combat", "obj.beer", keywords = "barbarians")
            destination("burthorpe", "Burthorpe", TOWNS, tile(2899, 3544), "The mountain town of the Imperial Guard.", "Town square", "Slayer,Combat", "obj.steel_platebody")
            destination("taverley", "Taverley", TOWNS, tile(2894, 3443), "The druids' village by White Wolf Mountain.", "Village centre", "Herblore,Shops", "obj.druid_pouch", keywords = "druids")
            destination("seers_village", "Seers' Village", TOWNS, tile(2757, 3477), "Mystic seers, with Camelot close by.", "Village bank", "Woodcutting,Agility", "obj.poh_tablet_camelotteleport", keywords = "seers,camelot")
            destination("yanille", "Yanille", TOWNS, tile(2605, 3093), "A walled town of wizards.", "Town centre", "Magic,Agility", "obj.poh_tablet_watchtowerteleport")
            destination("canifis", "Canifis", TOWNS, tile(3494, 3489), "A gloomy village of werewolves.", "Village centre", "Agility,Slayer", "obj.amulet_of_ghostspeak", danger = LOW, warning = "Morytania's ghouls and werewolves are not friendly to strangers.", quest = PRIEST_IN_PERIL, keywords = "morytania,werewolf")
            destination("port_phasmatys", "Port Phasmatys", TOWNS, tile(3676, 3479), "A haunted port of ghostly sailors.", "Town gates", "Prayer,Quests", "obj.amulet_of_ghostspeak", quest = PRIEST_IN_PERIL, keywords = "ectofuntus,ghosts,morytania")
            destination("rellekka", "Rellekka", TOWNS, tile(2660, 3657), "The Fremennik town on the frozen coast.", "Longhall square", "Fishing,Agility", "obj.raw_shark", keywords = "fremennik")
            destination("brimhaven", "Brimhaven", TOWNS, tile(2802, 3178), "A pirate port on the isle of Karamja.", "Harbour", "Agility,Cooking", "obj.banana", keywords = "karamja,pirates")
            destination("shilo_village", "Shilo Village", TOWNS, tile(2852, 2954), "A gem-rich village in the jungle.", "Village centre", "Mining,Slayer", "obj.uncut_sapphire", quest = "quest_shilovillage", keywords = "karamja,gems")
            destination("pollnivneach", "Pollnivneach", TOWNS, tile(3357, 2964), "A lawless town in the desert.", "Town well", "Agility,Thieving", "obj.knife", keywords = "desert")
            destination("great_kourend", "Great Kourend", TOWNS, CoordGrid(1608, 3670, 0), "The heart of the Kingdom of Kourend.", "Kourend Castle", "Shops,Quests", "obj.poh_tablet_kourendteleport", keywords = "kourend,zeah,hosidius")
            destination("prifddinas", "Prifddinas", TOWNS, tile(3243, 6053), "The crystal city of the elves.", "City centre", "Skilling,Gauntlet", "obj.crystal_key", quest = "quest_songoftheelves", keywords = "prif,elves,crystal")

            destination("varrock_woods", "Varrock Woods", SKILLING, tile(3172, 3419), "Trees and oaks beside Varrock.", "Woodland path", "Woodcutting", "obj.oak_logs", keywords = "oak,trees")
            destination("draynor_willows", "Draynor Willows", SKILLING, tile(3089, 3236), "Willows lining the Draynor shore.", "Shoreline", "Woodcutting,Fishing", "obj.willow_logs", keywords = "willow")
            destination("seers_maples", "Seers' Maples", SKILLING, tile(2727, 3500), "Maple trees by the Seers' bank.", "Maple grove", "Woodcutting", "obj.maple_logs", keywords = "maple")
            destination("woodcutting_guild", "Woodcutting Guild", SKILLING, CoordGrid(1662, 3505, 0), "Redwoods and magic trees for experts.", "Guild gate", "Woodcutting", "obj.yew_logs", keywords = "redwood,magic")
            destination("barbarian_fishing", "Barbarian River", SKILLING, tile(3104, 3432), "Fly-fish for trout and salmon.", "River bank", "Fishing", "obj.raw_trout", keywords = "trout,salmon,fly")
            destination("catherby_shore", "Catherby Shore", SKILLING, tile(2836, 3432), "Lobsters, swordfish and sharks.", "Beach", "Fishing,Cooking", "obj.lobster_pot", keywords = "lobster,shark")
            destination("karamja_docks", "Karamja Docks", SKILLING, tile(2918, 3176), "Lobster and tuna by Musa Point.", "Docks", "Fishing", "obj.harpoon", keywords = "musa point,tuna")
            destination("fishing_guild", "Fishing Guild", SKILLING, tile(2611, 3390), "Elite waters for skilled anglers.", "Guild gate", "Fishing", "obj.raw_swordfish")
            destination("varrock_east_mine", "Varrock East Mine", SKILLING, tile(3290, 3379), "Copper, tin and iron rocks.", "Mine entrance", "Mining", "obj.iron_ore", keywords = "copper,tin,iron")
            destination("varrock_west_mine", "Varrock West Mine", SKILLING, tile(3180, 3371), "Clay, tin, iron and silver.", "Mine entrance", "Mining", "obj.bronze_pickaxe", keywords = "clay,silver")
            destination("dwarven_mine", "Dwarven Mine", SKILLING, tile(3061, 3377), "Stairs down to the dwarves' mine.", "Stair house", "Mining,Smithing", "obj.coal", keywords = "dwarves,coal")
            destination("mining_guild", "Mining Guild", SKILLING, tile(3051, 9763), "Coal and mithril for seasoned miners.", "Guild door", "Mining", "obj.mithril_ore")
            destination("motherlode_mine", "Motherlode Mine", SKILLING, tile(3060, 9766), "Pay-dirt veins and a water wheel.", "Mine entrance", "Mining", "obj.rune_pickaxe", keywords = "paydirt,mlm")
            destination("varrock_smithy", "Varrock Smithy", SKILLING, tile(3182, 3429), "Anvils beside the west bank.", "West bank", "Smithing", "obj.hammer", keywords = "anvil")
            destination("crafting_guild", "Crafting Guild", SKILLING, tile(2933, 3295), "Pottery, spinning and gem cutting.", "Guild door", "Crafting", "obj.chisel", keywords = "gems,pottery")
            destination("cooks_guild", "Cooks' Guild", SKILLING, tile(3143, 3442), "Ranges for chefs in proper attire.", "Guild door", "Cooking", "obj.chefs_hat", keywords = "range")
            destination("falador_farm", "Falador Farm", SKILLING, tile(3056, 3307), "Allotments and herbs by Falador.", "Farm patches", "Farming", "obj.rake", keywords = "allotment,herb")
            destination("catherby_farm", "Catherby Farm", SKILLING, tile(2811, 3463), "Fertile patches by the coast.", "Farm patches", "Farming", "obj.spade", keywords = "allotment,herb")
            destination("ardougne_farm", "Ardougne Farm", SKILLING, tile(2668, 3374), "Allotments north of Ardougne.", "Farm patches", "Farming", "obj.rake", keywords = "allotment,herb")
            destination("farming_guild", "Farming Guild", SKILLING, CoordGrid(1249, 3719, 0), "The great guild of growers.", "Guild door", "Farming", "obj.spade")
            destination("gnome_course", "Gnome Agility Course", SKILLING, tile(2474, 3436), "Logs, nets and ropes for beginners.", "Course start", "Agility", "obj.graceful_hood", keywords = "gnome stronghold")
            destination("draynor_rooftop", "Draynor Rooftops", SKILLING, tile(3103, 3279), "The first rooftop course.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("alkharid_rooftop", "Al Kharid Rooftops", SKILLING, tile(3273, 3195), "Tightropes above the desert town.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("varrock_rooftop", "Varrock Rooftops", SKILLING, tile(3221, 3414), "Gaps and ledges in the capital.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("canifis_rooftop", "Canifis Rooftops", SKILLING, tile(3506, 3488), "Leaning roofs over Canifis.", "Course start", "Agility", "obj.graceful_hood", quest = PRIEST_IN_PERIL, keywords = "rooftop,morytania")
            destination("falador_rooftop", "Falador Rooftops", SKILLING, tile(3036, 3341), "High walls above the white city.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("seers_rooftop", "Seers' Rooftops", SKILLING, tile(2729, 3489), "A favourite run over the bank.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("pollnivneach_rooftop", "Pollnivneach Rooftops", SKILLING, tile(3351, 2961), "Monkey bars in the desert heat.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("rellekka_rooftop", "Rellekka Rooftops", SKILLING, tile(2625, 3677), "Daring leaps over Fremennik roofs.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("ardougne_rooftop", "Ardougne Rooftops", SKILLING, tile(2673, 3298), "The most perilous rooftop run.", "Course start", "Agility", "obj.graceful_hood", keywords = "rooftop")
            destination("piscatoris_hunter", "Piscatoris Hunting", SKILLING, tile(2319, 3618), "Kebbits and falconry in the woods.", "Fairy ring", "Hunter", "obj.chinchompa_captured", keywords = "falconry,kebbit")
            destination("feldip_hunter", "Feldip Hunting", SKILLING, tile(2570, 2955), "Jungle grounds for chinchompas.", "Fairy ring", "Hunter", "obj.chinchompa_big_captured", keywords = "chinchompa")
            destination("polar_hunter", "Polar Hunting", SKILLING, tile(2743, 3718), "Snowy plains of polar kebbits.", "Fairy ring", "Hunter", "obj.chinchompa_black", keywords = "kebbit,snow")
            destination("fossil_island", "Fossil Island", SKILLING, tile(3762, 3867), "Fossils, birdhouses and herbiboar.", "Digsite pendant spot", "Hunter,Mining", "obj.fossil_calcite", quest = "quest_bonevoyage", keywords = "birdhouse,herbiboar")
            destination("air_altar", "Air Altar", SKILLING, tile(2983, 3288), "Ruins south of Falador.", "Ruins", "Runecraft", "obj.air_talisman", keywords = "runecrafting,runes")
            destination("mind_altar", "Mind Altar", SKILLING, tile(2980, 3511), "Ruins north of Falador.", "Ruins", "Runecraft", "obj.mind_talisman", keywords = "runecrafting,runes")
            destination("water_altar", "Water Altar", SKILLING, tile(3182, 3162), "Ruins in the Lumbridge Swamp.", "Ruins", "Runecraft", "obj.water_talisman", keywords = "runecrafting,runes")
            destination("earth_altar", "Earth Altar", SKILLING, tile(3302, 3477), "Ruins north-east of Varrock.", "Ruins", "Runecraft", "obj.earth_talisman", keywords = "runecrafting,runes")
            destination("fire_altar", "Fire Altar", SKILLING, tile(3310, 3252), "Scorched ruins by the duel arena.", "Ruins", "Runecraft", "obj.fire_talisman", keywords = "runecrafting,runes")
            destination("body_altar", "Body Altar", SKILLING, tile(3050, 3442), "Ruins south of the monastery.", "Ruins", "Runecraft", "obj.body_talisman", keywords = "runecrafting,runes")
            destination("nature_altar", "Nature Altar", SKILLING, tile(2865, 3022), "Jungle ruins near Shilo Village.", "Ruins", "Runecraft", "obj.nature_talisman", keywords = "runecrafting,runes")
            destination("ardougne_market", "Ardougne Market", SKILLING, tile(2662, 3305), "Stalls ripe for nimble fingers.", "Market square", "Thieving", "obj.cake", keywords = "stalls")

            destination("edgeville_dungeon", "Edgeville Dungeon", DUNGEONS, tile(3094, 3470), "Hill giants, Vannaka and more.", "Trapdoor", "Combat,Slayer", "obj.big_bones", keywords = "vannaka,hill giants")
            destination("varrock_sewers", "Varrock Sewers", DUNGEONS, tile(3237, 3456), "Rats and moss giants below Varrock.", "Manhole", "Combat", "obj.bones", keywords = "sewer,moss giants")
            destination("taverley_dungeon", "Taverley Dungeon", DUNGEONS, tile(2885, 3395), "Dragons, demons and giants.", "Dungeon ladder", "Combat,Slayer", "obj.dragon_bones", danger = LOW, warning = "The dungeon below is full of aggressive monsters.", keywords = "blue dragons,black demons")
            destination("asgarnian_ice_dungeon", "Asgarnian Ice Dungeon", DUNGEONS, tile(3007, 3152), "A frozen cavern of ice giants.", "Trapdoor", "Combat", "obj.big_bones", keywords = "ice giants,ice warriors")
            destination("lumbridge_swamp_caves", "Lumbridge Swamp Caves", DUNGEONS, tile(3168, 3172), "Dark caves of wall beasts.", "Cave entrance", "Slayer", "obj.torch_lit", keywords = "caves,swamp")
            destination("stronghold_of_security", "Stronghold of Security", DUNGEONS, tile(3081, 3421), "Prove your security, win boots.", "Stronghold entrance", "Rewards", "obj.skull", keywords = "stronghold,security")

            destination("stronghold_slayer_cave", "Stronghold Slayer Cave", SLAYER, tile(2433, 3423), "Nieve's cave of slayer beasts.", "Cave entrance", "Slayer", "obj.slayer_helm", keywords = "nieve,steve")
            destination("slayer_tower", "Slayer Tower", SLAYER, tile(3422, 3537), "Gargoyles, nechryael and worse.", "Tower doors", "Slayer", "obj.slayer_nosepeg", quest = PRIEST_IN_PERIL, keywords = "gargoyles,morytania")
            destination("turael", "Turael", SLAYER, tile(2930, 3532), "Easy slayer tasks in Burthorpe.", "Turael's house", "Slayer", "obj.slayer_gem", keywords = "slayer master,burthorpe")
            destination("spria", "Spria", SLAYER, tile(3091, 3264), "Slayer tasks for new hunters.", "Draynor", "Slayer", "obj.slayer_gem", keywords = "slayer master,draynor")
            destination("krystilia", "Krystilia", SLAYER, tile(3107, 3510), "Wilderness slayer tasks.", "Edgeville jail", "Slayer", "obj.slayer_gem", keywords = "slayer master,edgeville")
            destination("konar", "Konar", SLAYER, CoordGrid(1311, 3808, 0), "Tasks with a location twist.", "Mount Karuulm", "Slayer", "obj.slayer_gem", keywords = "slayer master,karuulm")
            destination("duradel", "Duradel", SLAYER, tile(2852, 2954), "The toughest slayer tasks.", "Shilo Village", "Slayer", "obj.slayer_gem", quest = "quest_shilovillage", keywords = "slayer master,shilo")

            destination("scurrius", "Scurrius", BOSSES, tile(3237, 3456), "The rat king of the sewers.", "Sewer manhole", "Boss", "obj.bones", danger = LOW, warning = "You arrive at the sewer manhole; Scurrius waits below.", keywords = "rat king,sewers")
            destination("barrows", "Barrows", BOSSES, tile(3565, 3306), "Crypts of the six brothers.", "Burial mounds", "Boss,Rewards", "obj.spade", danger = LOW, warning = "The brothers fight back. Bring supplies.", quest = PRIEST_IN_PERIL, keywords = "brothers,crypt,morytania")
            destination("gemstone_crab", "Gemstone Crab", BOSSES, CoordGrid(1277, 3169, 0), "A glittering crab in Varlamore.", "Crab beach", "Boss", "obj.uncut_sapphire", keywords = "varlamore,crab")
            destination("king_black_dragon", "King Black Dragon", BOSSES, tile(3028, 3842), "Down the Lava Maze ladder.", "Lava Maze", "Boss", "obj.dragon_bones", danger = DEADLY, warning = DEEP, keywords = "kbd,lava maze,dragon")
            destination("artio", "Artio", BOSSES, tile(3115, 3675), "The great bear's northern den.", "Den entrance", "Boss", "obj.big_bones", danger = DEADLY, warning = DEEP, keywords = "callisto,bear")
            destination("callisto", "Callisto", BOSSES, tile(3259, 3662), "The bear of the Slayer Cave.", "Cave mouth", "Boss", "obj.big_bones", danger = HIGH, warning = WILD, keywords = "bear,slayer cave")
            destination("spindel", "Spindel", BOSSES, tile(3182, 3745), "A web-choked spider lair.", "Lair entrance", "Boss", "obj.big_bones", danger = DEADLY, warning = DEEP, keywords = "venenatis,spider")
            destination("venenatis", "Venenatis", BOSSES, tile(3294, 3743), "The spider queen's nest.", "Cave mouth", "Boss", "obj.big_bones", danger = DEADLY, warning = DEEP, keywords = "spider,slayer cave")
            destination("abyssal_sire", "Abyssal Sire", BOSSES, tile(3106, 3556), "Through the Abyss to the Sire.", "Mage of Zamorak", "Boss,Slayer", "obj.abyssal_whip", danger = HIGH, warning = WILD, quest = "miniquest_entertheabyss", keywords = "abyss,sire")

            destination("castle_wars", "Castle Wars", MINIGAMES, tile(2441, 3090), "Saradomin versus Zamorak.", "Lobby", "Minigame,Combat", "obj.castlewars_hood_saradomin_prize", keywords = "cw")
            destination("emirs_arena", "Emir's Arena", MINIGAMES, tile(3316, 3235), "Duel other adventurers.", "Arena gates", "Minigame,PvP", "obj.ring_of_dueling_8", keywords = "duel,pvp arena")
            destination("gauntlet", "The Gauntlet", MINIGAMES, tile(3228, 6113), "Face the Crystalline Hunllef.", "Gauntlet lobby", "Minigame,Boss", "obj.crystal_key", quest = "quest_songoftheelves", keywords = "hunllef,prifddinas")
            destination("champions_guild", "Champions' Guild", MINIGAMES, tile(3190, 3367), "A hall for adventurers of renown.", "Guild door", "Guild,Quests", "obj.dragon_scimitar", keywords = "guild,quest points")
            destination("ranging_guild", "Ranging Guild", MINIGAMES, tile(2656, 3441), "Archery targets and fine bows.", "Guild gate", "Guild,Ranged", "obj.crystal_bow", keywords = "guild,archery")
            destination("warriors_guild", "Warriors' Guild", MINIGAMES, tile(2880, 3546), "Home of the mightiest warriors.", "Guild door", "Guild,Combat", "obj.dragon_scimitar", keywords = "guild,burthorpe,defenders")

            destination("wilderness_ditch", "Wilderness Ditch", WILDERNESS, tile(3087, 3523), "Just over the ditch. Beware!", "North of Edgeville", "PvP,Danger", "obj.skull", danger = HIGH, warning = WILD, keywords = "wildy,edgeville")
            destination("ferox_enclave", "Ferox Enclave", WILDERNESS, tile(3151, 3635), "A walled safe haven.", "Enclave gates", "Banking,Safe zone", "obj.ring_of_dueling_8", danger = HIGH, warning = "The enclave is safe, but the Wilderness lies all around its walls.", keywords = "wildy,safe,bank")
            destination("chaos_temple", "Chaos Temple", WILDERNESS, tile(3236, 3635), "A dark temple to Zamorak.", "Temple", "Prayer,PvP", "obj.bones", danger = HIGH, warning = WILD, keywords = "wildy,zamorak")
            destination("bandit_camp", "Bandit Camp", WILDERNESS, tile(3038, 3651), "Bandits and black-market traders.", "Camp", "Shops,PvP", "obj.knife", danger = HIGH, warning = WILD, keywords = "wildy,bandits")
            destination("graveyard_of_shadows", "Graveyard of Shadows", WILDERNESS, tile(3227, 3667), "A haunted graveyard.", "Obelisk", "Combat,PvP", "obj.bones", danger = HIGH, warning = WILD, keywords = "wildy,obelisk")
            destination("chaos_altar_rc", "Chaos Altar", WILDERNESS, tile(3060, 3585), "Ruins for chaos runes.", "Ruins", "Runecraft,PvP", "obj.chaos_talisman", danger = HIGH, warning = WILD, keywords = "wildy,runecrafting")
            destination("mage_of_zamorak", "Mage of Zamorak", WILDERNESS, tile(3106, 3556), "The way into the Abyss.", "Mage's spot", "Runecraft,PvP", "obj.abyssal_whip", danger = HIGH, warning = WILD, quest = "miniquest_entertheabyss", keywords = "wildy,abyss,runecrafting")
            destination("lava_dragon_isle", "Lava Dragon Isle", WILDERNESS, tile(3200, 3807), "A volcanic isle of lava dragons.", "Isle shore", "Combat,PvP", "obj.dragon_bones", danger = DEADLY, warning = DEEP, keywords = "wildy,lava dragons")
            destination("wilderness_course", "Wilderness Agility Course", WILDERNESS, tile(2998, 3916), "The deadliest agility course.", "Course gate", "Agility,PvP", "obj.graceful_hood", danger = DEADLY, warning = DEEP, keywords = "wildy,agility")
            destination("deserted_keep", "Deserted Keep", WILDERNESS, tile(3154, 3923), "Where the Edgeville lever leads.", "Lever room", "PvP", "obj.skull", danger = DEADLY, warning = DEEP, keywords = "wildy,lever")
            destination("mage_arena", "Mage Arena", WILDERNESS, tile(3093, 3955), "Kolodion's arena of battle mages.", "Arena entrance", "Magic,PvP", "obj.lawrune", danger = DEADLY, warning = DEEP, keywords = "wildy,kolodion,god capes")
            destination("rogues_castle", "Rogues' Castle", WILDERNESS, tile(3307, 3916), "Chests and rogues of the north.", "Obelisk", "Thieving,PvP", "obj.knife", danger = DEADLY, warning = DEEP, keywords = "wildy,obelisk,chests")
        }
}

private fun hasPreview(key: String): Boolean =
    WayfinderDestinationsTable::class.java.classLoader.getResource("pack/sprites/wayfinder_$key.png") != null
