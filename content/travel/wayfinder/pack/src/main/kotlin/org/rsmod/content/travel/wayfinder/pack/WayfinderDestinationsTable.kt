package org.rsmod.content.travel.wayfinder.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import dev.openrune.pack.columnCoord
import org.rsmod.map.CoordGrid

/**
 * The wayfinder teleport network. One row per destination; the mages, the teleport interface and
 * bots all read this table, so a new destination is a new row (plus its `dbrow.` gameval).
 *
 * - `slot` is the destination's permanent number (1-127). Favourites and recent destinations are
 *   saved by it, so never reuse or renumber one; disable a row with `enabled = false` instead.
 * - `category`: 0 towns, 1 skilling, 2 dungeons, 3 slayer, 4 bosses, 5 minigames, 6 wilderness.
 * - `danger`: 0 safe, 1 low, 2 high, 3 deadly. Any tile inside the Wilderness also asks the
 *   player to confirm, whatever this says.
 * - `quest` (a quest key) is checked before travel, under the server's quest requirement mode.
 *   Skill levels are not: arrival tiles sit outside entrances, so a guild door, course obstacle or
 *   dungeon gate still applies its own level check.
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

    private const val PRIEST_IN_PERIL = "quest_priestinperil"

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

            fun destination(
                key: String,
                slot: Int,
                name: String,
                category: Int,
                coords: CoordGrid,
                description: String,
                danger: Int = SAFE,
                warning: String? = null,
                quest: String? = null,
                bots: Boolean = danger == SAFE && category != WILDERNESS,
                keywords: String? = null,
            ) {
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
                }
            }

            fun tile(x: Int, z: Int) = CoordGrid(x, z, 0)

            val wild = "You will arrive in the Wilderness. Other players can attack you, and you lose your items if you die."
            val deep = "Deep Wilderness. Player killers roam here and anything you carry can be lost on death."

            destination("lumbridge", 1, "Lumbridge", TOWNS, tile(3222, 3218), "The castle town on the River Lum, where every adventure begins.", keywords = "home,castle,duke")
            destination("varrock", 2, "Varrock", TOWNS, tile(3213, 3428), "The bustling capital of Misthalin, home to King Roald.", keywords = "capital,square")
            destination("grand_exchange", 3, "Grand Exchange", TOWNS, tile(3164, 3486), "Trade with all of Gielinor at the great marketplace.", keywords = "ge,trade,market")
            destination("falador", 4, "Falador", TOWNS, tile(2965, 3380), "The white-walled city of the White Knights.", keywords = "white knights,park")
            destination("edgeville", 5, "Edgeville", TOWNS, tile(3087, 3496), "A quiet village on the edge of the Wilderness.", keywords = "edge,bank")
            destination("draynor_village", 6, "Draynor Village", TOWNS, tile(3093, 3244), "Market stalls, willows and a manor best avoided at night.", keywords = "draynor")
            destination("al_kharid", 7, "Al Kharid", TOWNS, tile(3293, 3174), "A desert trading town of silk, kebabs and the Emir's palace.", keywords = "alkharid,desert,palace")
            destination("port_sarim", 8, "Port Sarim", TOWNS, tile(3022, 3217), "Asgarnia's harbour, where ships sail for distant shores.", keywords = "port,docks,boat")
            destination("rimmington", 9, "Rimmington", TOWNS, tile(2957, 3214), "A sleepy hamlet with a witch, a mine and a makeover mage.")
            destination("barbarian_village", 10, "Barbarian Village", TOWNS, tile(3081, 3421), "Rowdy barbarians, a river full of trout and a deep stronghold.", keywords = "barbarians")
            destination("burthorpe", 11, "Burthorpe", TOWNS, tile(2899, 3544), "The mountain town of warriors and the Imperial Guard.")
            destination("taverley", 12, "Taverley", TOWNS, tile(2894, 3443), "The druids' village in the shadow of White Wolf Mountain.", keywords = "druids")
            destination("catherby", 13, "Catherby", TOWNS, tile(2809, 3435), "A seaside village of fishermen and herb growers.")
            destination("seers_village", 14, "Seers' Village", TOWNS, tile(2757, 3477), "Mystic seers, with the towers of Camelot close by.", keywords = "seers,camelot")
            destination("east_ardougne", 15, "Ardougne", TOWNS, tile(2662, 3305), "King Lathas's city and its famous market square.", keywords = "ardy,east ardougne")
            destination("yanille", 16, "Yanille", TOWNS, tile(2605, 3093), "A walled town, home to the Wizards' Guild.")
            destination("canifis", 17, "Canifis", TOWNS, tile(3494, 3489), "A gloomy village of werewolves deep in Morytania.", danger = LOW, warning = "Morytania's ghouls and werewolves are not friendly to strangers.", quest = PRIEST_IN_PERIL, keywords = "morytania,werewolf")
            destination("port_phasmatys", 18, "Port Phasmatys", TOWNS, tile(3676, 3479), "A haunted port where the dead still walk the docks.", quest = PRIEST_IN_PERIL, keywords = "ectofuntus,ghosts,morytania")
            destination("rellekka", 19, "Rellekka", TOWNS, tile(2660, 3657), "The Fremennik longhall town on the frozen northern coast.", keywords = "fremennik")
            destination("brimhaven", 20, "Brimhaven", TOWNS, tile(2802, 3178), "A pirate port on the jungle isle of Karamja.", keywords = "karamja,pirates")
            destination("shilo_village", 21, "Shilo Village", TOWNS, tile(2852, 2954), "A gem-rich village hidden in Karamja's southern jungle.", quest = "quest_shilovillage", keywords = "karamja,gems")
            destination("pollnivneach", 22, "Pollnivneach", TOWNS, tile(3357, 2964), "A lawless desert town between Al Kharid and Nardah.", keywords = "desert")
            destination("great_kourend", 23, "Great Kourend", TOWNS, tile(1608, 3670), "The heart of the Kingdom of Great Kourend.", keywords = "kourend,zeah,hosidius")
            destination("prifddinas", 24, "Prifddinas", TOWNS, tile(3243, 6053), "The crystal city of the elves, restored at last.", quest = "quest_songoftheelves", keywords = "prif,elves,crystal")

            destination("varrock_woods", 30, "Varrock Woods", SKILLING, tile(3172, 3419), "Trees and oaks south-west of Varrock, close to the bank.", keywords = "woodcutting,oak,trees")
            destination("draynor_willows", 31, "Draynor Willows", SKILLING, tile(3089, 3236), "Willow trees lining the Draynor shore by the bank.", keywords = "woodcutting,willow")
            destination("seers_maples", 32, "Seers' Maples", SKILLING, tile(2727, 3500), "Maple trees just north of the Seers' Village bank.", keywords = "woodcutting,maple")
            destination("woodcutting_guild", 33, "Woodcutting Guild", SKILLING, CoordGrid(1662, 3505, 0), "Redwoods, yews and magic trees for the finest foresters.", keywords = "woodcutting,redwood,magic")
            destination("barbarian_fishing", 34, "Barbarian Village River", SKILLING, tile(3104, 3432), "Fly-fish for trout and salmon on the river bank.", keywords = "fishing,trout,salmon,fly")
            destination("catherby_shore", 35, "Catherby Shore", SKILLING, tile(2836, 3432), "Lobsters, swordfish and sharks off Catherby's beach.", keywords = "fishing,lobster,shark")
            destination("karamja_docks", 36, "Karamja Docks", SKILLING, tile(2918, 3176), "Lobster and tuna spots on Karamja's northern docks.", keywords = "fishing,musa point,tuna")
            destination("fishing_guild", 37, "Fishing Guild", SKILLING, tile(2611, 3390), "Elite fishing waters for those of true skill.", keywords = "fishing,guild")
            destination("varrock_east_mine", 38, "Varrock East Mine", SKILLING, tile(3290, 3379), "Copper, tin and iron south-east of Varrock.", keywords = "mining,copper,tin,iron")
            destination("varrock_west_mine", 39, "Varrock West Mine", SKILLING, tile(3180, 3371), "Clay, tin, iron and silver south-west of Varrock.", keywords = "mining,clay,silver")
            destination("dwarven_mine", 40, "Dwarven Mine", SKILLING, tile(3061, 3377), "Stairs down to the dwarves' sprawling mine below Falador.", keywords = "mining,dwarves,coal")
            destination("mining_guild", 41, "Mining Guild", SKILLING, tile(3051, 9763), "Rich seams of coal and mithril for seasoned miners.", keywords = "mining,guild,mithril")
            destination("motherlode_mine", 42, "Motherlode Mine", SKILLING, tile(3060, 9766), "Pay-dirt veins and a creaky water wheel beneath Falador.", keywords = "mining,paydirt,mlm")
            destination("varrock_smithy", 43, "Varrock West Smithy", SKILLING, tile(3182, 3429), "Anvils and a bank side by side in west Varrock.", keywords = "smithing,anvil")
            destination("crafting_guild", 44, "Crafting Guild", SKILLING, tile(2933, 3295), "Spinning wheels, pottery and gem cutting under one roof.", keywords = "crafting,guild,gems")
            destination("cooks_guild", 45, "Cooks' Guild", SKILLING, tile(3143, 3442), "Ranges and recipes for chefs in proper attire.", keywords = "cooking,guild,range")
            destination("falador_farm", 46, "Falador Farm Patches", SKILLING, tile(3056, 3307), "Allotments, flowers and herbs south of Falador.", keywords = "farming,allotment,herb")
            destination("catherby_farm", 47, "Catherby Farm Patches", SKILLING, tile(2811, 3463), "Fertile patches tended beside the Catherby coast.", keywords = "farming,allotment,herb")
            destination("ardougne_farm", 48, "Ardougne Farm Patches", SKILLING, tile(2668, 3374), "Allotments north of Ardougne, by the Kandarin road.", keywords = "farming,allotment,herb")
            destination("farming_guild", 49, "Farming Guild", SKILLING, CoordGrid(1249, 3719, 0), "The great guild of growers in the Kebos Lowlands.", keywords = "farming,guild")
            destination("gnome_course", 50, "Gnome Agility Course", SKILLING, tile(2474, 3436), "The gnomes' training course of logs, nets and ropes.", keywords = "agility,gnome stronghold")
            destination("draynor_rooftop", 51, "Draynor Rooftop Course", SKILLING, tile(3103, 3279), "Leap across Draynor's rooftops, ideal for beginners.", keywords = "agility,rooftop")
            destination("alkharid_rooftop", 52, "Al Kharid Rooftop Course", SKILLING, tile(3273, 3195), "Tightropes and zip lines above the desert town.", keywords = "agility,rooftop")
            destination("varrock_rooftop", 53, "Varrock Rooftop Course", SKILLING, tile(3221, 3414), "Gaps and ledges across the capital's rooftops.", keywords = "agility,rooftop")
            destination("canifis_rooftop", 54, "Canifis Rooftop Course", SKILLING, tile(3506, 3488), "Hop between the leaning roofs of Canifis.", quest = PRIEST_IN_PERIL, keywords = "agility,rooftop,morytania")
            destination("falador_rooftop", 55, "Falador Rooftop Course", SKILLING, tile(3036, 3341), "High walls and tightropes above the white city.", keywords = "agility,rooftop")
            destination("seers_rooftop", 56, "Seers' Village Rooftop", SKILLING, tile(2729, 3489), "A favourite rooftop run over the Seers' bank.", keywords = "agility,rooftop")
            destination("pollnivneach_rooftop", 57, "Pollnivneach Rooftop", SKILLING, tile(3351, 2961), "Market stalls and monkey bars in the desert heat.", keywords = "agility,rooftop")
            destination("rellekka_rooftop", 58, "Rellekka Rooftop", SKILLING, tile(2625, 3677), "Daring leaps across the Fremennik rooftops.", keywords = "agility,rooftop")
            destination("ardougne_rooftop", 59, "Ardougne Rooftop", SKILLING, tile(2673, 3298), "The most perilous rooftop run in all of Kandarin.", keywords = "agility,rooftop")
            destination("piscatoris_hunter", 60, "Piscatoris Hunter Area", SKILLING, tile(2319, 3618), "Kebbits, falconry and birds in the Piscatoris woods.", keywords = "hunter,falconry,kebbit")
            destination("feldip_hunter", 61, "Feldip Hunter Area", SKILLING, tile(2570, 2955), "Jungle hunting grounds for chinchompas and weasels.", keywords = "hunter,chinchompa")
            destination("polar_hunter", 62, "Polar Hunter Area", SKILLING, tile(2743, 3718), "Snowy plains east of Rellekka, home to polar kebbits.", keywords = "hunter,kebbit,snow")
            destination("fossil_island", 63, "Fossil Island", SKILLING, tile(3762, 3867), "A wild isle of fossils, birdhouses and the herbiboar.", quest = "quest_bonevoyage", keywords = "hunter,birdhouse,herbiboar")
            destination("air_altar", 64, "Air Altar", SKILLING, tile(2983, 3288), "Mysterious ruins south of Falador, attuned to air.", keywords = "runecrafting,runes")
            destination("mind_altar", 65, "Mind Altar", SKILLING, tile(2980, 3511), "Ruins north of Falador humming with mental energy.", keywords = "runecrafting,runes")
            destination("water_altar", 66, "Water Altar", SKILLING, tile(3182, 3162), "Ruins hidden in the Lumbridge Swamp.", keywords = "runecrafting,runes")
            destination("earth_altar", 67, "Earth Altar", SKILLING, tile(3302, 3477), "Ruins in the woods north-east of Varrock.", keywords = "runecrafting,runes")
            destination("fire_altar", 68, "Fire Altar", SKILLING, tile(3310, 3252), "Scorched ruins north of the Emir's Arena.", keywords = "runecrafting,runes")
            destination("body_altar", 69, "Body Altar", SKILLING, tile(3050, 3442), "Ruins south of the Edgeville Monastery.", keywords = "runecrafting,runes")
            destination("nature_altar", 70, "Nature Altar", SKILLING, tile(2865, 3022), "Jungle ruins north-east of Shilo Village.", keywords = "runecrafting,runes")
            destination("ardougne_market", 71, "Ardougne Market", SKILLING, tile(2662, 3305), "Cake, silk and gem stalls ripe for nimble fingers.", keywords = "thieving,stalls")

            destination("edgeville_dungeon", 80, "Edgeville Dungeon", DUNGEONS, tile(3094, 3470), "A trapdoor down to hill giants, Vannaka and the depths below.", keywords = "vannaka,hill giants")
            destination("varrock_sewers", 81, "Varrock Sewers", DUNGEONS, tile(3237, 3456), "A manhole into the rat-infested sewers beneath Varrock.", keywords = "sewer,moss giants")
            destination("taverley_dungeon", 82, "Taverley Dungeon", DUNGEONS, tile(2885, 3395), "A ladder down to dragons, demons and giants.", danger = LOW, warning = "The dungeon below is full of aggressive monsters.", keywords = "blue dragons,black demons")
            destination("asgarnian_ice_dungeon", 83, "Asgarnian Ice Dungeon", DUNGEONS, tile(3007, 3152), "A frozen cavern of ice giants south of Port Sarim.", keywords = "ice giants,ice warriors")
            destination("lumbridge_swamp_caves", 84, "Lumbridge Swamp Caves", DUNGEONS, tile(3168, 3172), "Dark caves of wall beasts beneath the swamp. Bring a light.", keywords = "caves,swamp")
            destination("stronghold_of_security", 85, "Stronghold of Security", DUNGEONS, tile(3081, 3421), "Prove your account security and claim the boots of stone.", keywords = "stronghold,security")

            destination("stronghold_slayer_cave", 90, "Stronghold Slayer Cave", SLAYER, tile(2433, 3423), "Nieve's cave of slayer beasts beneath the Gnome Stronghold.", keywords = "nieve,steve,slayer")
            destination("slayer_tower", 91, "Slayer Tower", SLAYER, tile(3422, 3537), "A towering fortress of gargoyles, nechryael and worse.", quest = PRIEST_IN_PERIL, keywords = "gargoyles,morytania")
            destination("turael", 92, "Turael (Burthorpe)", SLAYER, tile(2930, 3532), "Seek Turael in Burthorpe for easy slayer assignments.", keywords = "slayer master")
            destination("spria", 93, "Spria (Draynor)", SLAYER, tile(3091, 3264), "Spria hands out slayer tasks for new hunters.", keywords = "slayer master")
            destination("krystilia", 94, "Krystilia (Edgeville)", SLAYER, tile(3107, 3510), "Krystilia sets slayer tasks in the Wilderness.", keywords = "slayer master,wilderness")
            destination("konar", 95, "Konar (Mount Karuulm)", SLAYER, CoordGrid(1311, 3808, 0), "Konar quo Maten assigns tasks with a location twist.", keywords = "slayer master,karuulm")
            destination("duradel", 96, "Duradel (Shilo Village)", SLAYER, tile(2852, 2954), "Duradel sets the toughest slayer tasks in all of Gielinor.", quest = "quest_shilovillage", keywords = "slayer master,shilo")

            destination("scurrius", 100, "Scurrius", BOSSES, tile(3237, 3456), "The rat king lurks in the sewers beneath Varrock.", danger = LOW, warning = "Arrives at the sewer manhole; Scurrius waits below.", keywords = "rat king,sewers")
            destination("barrows", 101, "Barrows", BOSSES, tile(3565, 3306), "Dig into the crypts of the six Barrows brothers.", danger = LOW, warning = "The brothers fight back. Bring supplies.", quest = PRIEST_IN_PERIL, keywords = "brothers,crypt,morytania")
            destination("gemstone_crab", 102, "Gemstone Crab", BOSSES, CoordGrid(1277, 3169, 0), "A glittering giant crab on the Varlamore coast.", keywords = "varlamore,crab")
            destination("king_black_dragon", 103, "King Black Dragon", BOSSES, tile(3028, 3842), "The Lava Maze ladder leads down to the King Black Dragon.", danger = DEADLY, warning = deep, keywords = "kbd,lava maze,dragon")
            destination("artio", 104, "Artio", BOSSES, tile(3115, 3675), "The great bear's den in the northern Wilderness.", danger = DEADLY, warning = deep, keywords = "callisto,bear")
            destination("callisto", 105, "Callisto", BOSSES, tile(3259, 3662), "Callisto prowls the depths of the Wilderness Slayer Cave.", danger = HIGH, warning = wild, keywords = "bear,slayer cave")
            destination("spindel", 106, "Spindel", BOSSES, tile(3182, 3745), "A web-choked lair where the spider Spindel waits.", danger = DEADLY, warning = deep, keywords = "venenatis,spider")
            destination("venenatis", 107, "Venenatis", BOSSES, tile(3294, 3743), "The north cave mouth leads to Venenatis' nest.", danger = DEADLY, warning = deep, keywords = "spider,slayer cave")
            destination("abyssal_sire", 108, "Abyssal Sire", BOSSES, tile(3106, 3556), "Through the Abyss to the Sire's nexus.", danger = HIGH, warning = wild, quest = "miniquest_entertheabyss", keywords = "abyss,sire")

            destination("castle_wars", 110, "Castle Wars", MINIGAMES, tile(2441, 3090), "Saradomin versus Zamorak: capture the enemy standard!", keywords = "cw,minigame")
            destination("emirs_arena", 111, "Emir's Arena", MINIGAMES, tile(3316, 3235), "Duel other adventurers in the Emir's arena.", keywords = "duel,pvp arena")
            destination("gauntlet", 112, "The Gauntlet", MINIGAMES, tile(3228, 6113), "Enter the Gauntlet and face the Crystalline Hunllef.", quest = "quest_songoftheelves", keywords = "hunllef,prifddinas")
            destination("champions_guild", 113, "Champions' Guild", MINIGAMES, tile(3190, 3367), "A hall open only to adventurers of renown.", keywords = "guild,quest points")
            destination("ranging_guild", 114, "Ranging Guild", MINIGAMES, tile(2656, 3441), "Archery targets and fine bows for keen-eyed rangers.", keywords = "guild,archery")
            destination("warriors_guild", 115, "Warriors' Guild", MINIGAMES, tile(2880, 3546), "Prove your strength to join the mightiest warriors.", keywords = "guild,burthorpe,defenders")

            destination("wilderness_ditch", 120, "Wilderness Ditch", WILDERNESS, tile(3087, 3523), "Just over the ditch north of Edgeville. Beware!", danger = HIGH, warning = wild, keywords = "wildy,edgeville")
            destination("ferox_enclave", 121, "Ferox Enclave", WILDERNESS, tile(3151, 3635), "A walled safe haven in the heart of the Wilderness.", danger = HIGH, warning = "The enclave is safe, but the Wilderness lies all around its walls.", keywords = "wildy,safe,bank")
            destination("chaos_temple", 122, "Chaos Temple", WILDERNESS, tile(3236, 3635), "A dark temple to Zamorak in the eastern Wilderness.", danger = HIGH, warning = wild, keywords = "wildy,zamorak")
            destination("bandit_camp", 123, "Bandit Camp", WILDERNESS, tile(3038, 3651), "A lawless camp of bandits and black-market traders.", danger = HIGH, warning = wild, keywords = "wildy,bandits")
            destination("graveyard_of_shadows", 124, "Graveyard of Shadows", WILDERNESS, tile(3227, 3667), "A haunted graveyard beside a Wilderness obelisk.", danger = HIGH, warning = wild, keywords = "wildy,obelisk")
            destination("chaos_altar_rc", 125, "Chaos Altar", WILDERNESS, tile(3060, 3585), "Ruins in the Wilderness where chaos runes are bound.", danger = HIGH, warning = wild, keywords = "wildy,runecrafting")
            destination("mage_of_zamorak", 126, "Mage of Zamorak", WILDERNESS, tile(3106, 3556), "Seek the Mage of Zamorak to enter the Abyss.", danger = HIGH, warning = wild, quest = "miniquest_entertheabyss", keywords = "wildy,abyss,runecrafting")
            destination("lava_dragon_isle", 127, "Lava Dragon Isle", WILDERNESS, tile(3200, 3807), "A volcanic isle where lava dragons bask.", danger = DEADLY, warning = deep, keywords = "wildy,lava dragons")
            destination("wilderness_course", 26, "Wilderness Agility Course", WILDERNESS, tile(2998, 3916), "The deadliest agility course, deep in the Wilderness.", danger = DEADLY, warning = deep, keywords = "wildy,agility")
            destination("deserted_keep", 27, "Deserted Keep", WILDERNESS, tile(3154, 3923), "The ruined keep where the Edgeville lever leads.", danger = DEADLY, warning = deep, keywords = "wildy,lever")
            destination("mage_arena", 28, "Mage Arena", WILDERNESS, tile(3093, 3955), "Kolodion's arena, where battle mages prove their worth.", danger = DEADLY, warning = deep, keywords = "wildy,kolodion,god capes")
            destination("rogues_castle", 29, "Rogues' Castle", WILDERNESS, tile(3307, 3916), "An obelisk near the rogues' fortress of chests.", danger = DEADLY, warning = deep, keywords = "wildy,obelisk,chests")
        }
}
