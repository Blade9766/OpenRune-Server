package org.rsmod.content.skills.construction.data

/**
 * The games room's games. Prize limits, experience and the dartboard and archery target's scoring
 * chances come from the Old School wiki; the hangman words are the list Mod Ash gave.
 */
object Games {
    /** A prize chest, by its closed and opened locs, and the most coins the owner can put in it. */
    enum class PrizeChest(val closed: String, val open: String, val limit: Int) {
        OAK("loc.poh_prize_chest_oak_closed", "loc.poh_prize_chest_oak_open", 20_000),
        TEAK("loc.poh_prize_chest_teak_closed", "loc.poh_prize_chest_teak_open", 50_000),
        MAHOGANY("loc.poh_prize_chest_mag_closed", "loc.poh_prize_chest_mag_open", 100_000),
    }

    /**
     * A tier of a ranging game's target: hitting it scores [points], and reaching it takes a
     * Ranged roll between [low] and [high] out of 256 after every tier before it.
     */
    class ScoreTier(val points: Int, val low: Int, val high: Int)

    /** A ranging game, by its loc, the tiers a shot can reach and the experience for scoring. */
    enum class RangingGame(val loc: String, val tiers: List<ScoreTier>, val xp: Double, val label: String) {
        HOOP("loc.poh_stick+hoop1", listOf(ScoreTier(1, 75, 240)), 2.5, "hoop"),
        DARTBOARD(
            "loc.poh_dartboard1",
            listOf(ScoreTier(1, 75, 240), ScoreTier(2, 50, 240), ScoreTier(3, 15, 160)),
            7.5,
            "dart",
        ),
        ARCHERY(
            "loc.poh_archery_target1",
            listOf(
                ScoreTier(1, 150, 240),
                ScoreTier(2, 75, 240),
                ScoreTier(3, 50, 240),
                ScoreTier(5, 15, 160),
                ScoreTier(10, 8, 120),
            ),
            10.0,
            "arrow",
        ),
    }

    /** Ten shots each, as the wiki has it for all three games. */
    const val SHOTS: Int = 10

    /** The scoreboard has four rows; anyone past them still plays, off the board. */
    const val SCOREBOARD_ROWS: Int = 4

    /** An attack stone, by its built loc, its npc's name and how much damage breaks it. */
    enum class AttackStone(val loc: String, val npc: String, val hitpoints: Int) {
        CLAY("loc.poh_tbt_clay_new", "poh_tbt_clay", 100),
        LIMESTONE("loc.poh_tbt_limestone_new", "poh_tbt_limestone", 200),
        MARBLE("loc.poh_tbt_marble_new", "poh_tbt_marble", 300);

        /**
         * The stone's npc for how far it has been broken: [CRACK_STAGES] stages counted in base
         * four across the npc name's three digits, then its rubble.
         */
        fun npcAt(damage: Int): String =
            if (damage >= hitpoints) destroyed else stages[damage * CRACK_STAGES / hitpoints]

        /** The stone's npc at every crack stage, from whole to nearly broken. */
        val stages: List<String>
            get() = (0 until CRACK_STAGES).map { "npc.${npc}_" + Integer.toString(it, 4).padStart(3, '0') }

        val destroyed: String
            get() = "npc.${npc}_destroyed"

        companion object {
            const val CRACK_STAGES: Int = 64
        }
    }

    /**
     * Hitting a stone trains the attack style's stat for this share of the four experience a point
     * of damage normally gives.
     */
    const val STONE_XP_PER_DAMAGE: Double = 4 * 0.025

    /** The elements a balance weighs, in the pairs that cancel out, and the colours they show as. */
    enum class Element(val colour: String) {
        AIR("cyan"),
        EARTH("green"),
        WATER("blue"),
        FIRE("red"),
    }

    /** A combat spell's element and how far it tips a balance: strike 1 up to surge 5. */
    class BalanceSpell(val component: String, val element: Element, val weight: Int)

    val BALANCE_SPELLS: List<BalanceSpell> =
        listOf("strike", "bolt", "blast", "wave", "surge").withIndex().flatMap { (tier, size) ->
            listOf(
                BalanceSpell("component.magic_spellbook:wind_$size", Element.AIR, tier + 1),
                BalanceSpell("component.magic_spellbook:earth_$size", Element.EARTH, tier + 1),
                BalanceSpell("component.magic_spellbook:water_$size", Element.WATER, tier + 1),
                BalanceSpell("component.magic_spellbook:fire_$size", Element.FIRE, tier + 1),
            )
        }

    /** The balance npcs' brightest shade; a pair further out than this still shows it. */
    const val BALANCE_SHADES: Int = 6

    /** A magical balance, by its orb loc and its npc; a bigger balance starts further out. */
    enum class Balance(val loc: String, val npc: String, val start: Int) {
        LESSER("loc.poh_elemental_orb_1", "poh_elemental_balance_1", 3),
        MEDIUM("loc.poh_elemental_orb_2", "poh_elemental_balance_2", 5),
        GREATER("loc.poh_elemental_orb_3", "poh_elemental_balance_3", 7);

        /**
         * The npc a balance shows as, given how far air outweighs earth and water outweighs fire:
         * the heavier pair's colour at its shade, or the plain white balance when both are even.
         */
        /** Every npc the balance can show as: white, then each element at each shade. */
        val forms: List<String>
            get() = listOf("npc.$npc") + Element.entries.flatMap { e -> (1..BALANCE_SHADES).map { "npc.${npc}_${e.colour}$it" } }

        fun npcFor(airEarth: Int, waterFire: Int): String {
            if (airEarth == 0 && waterFire == 0) {
                return "npc.$npc"
            }
            val (lean, element) =
                if (kotlin.math.abs(airEarth) >= kotlin.math.abs(waterFire)) {
                    airEarth to if (airEarth > 0) Element.AIR else Element.EARTH
                } else {
                    waterFire to if (waterFire > 0) Element.WATER else Element.FIRE
                }
            val shade = kotlin.math.abs(lean).coerceAtMost(BALANCE_SHADES)
            return "npc.${npc}_${element.colour}$shade"
        }
    }

    const val JESTER: String = "loc.poh_mime_jester"
    const val JESTER_PLAYING: String = "loc.poh_mime_jester2"
    const val TREASURE_HUNT: String = "loc.poh_fairy_house"
    const val TREASURE_HUNT_OPEN: String = "loc.poh_fairy_house_open"
    const val HANGMAN: String = "loc.poh_hangman_chest"

    /** The emotes Jacky Jester performs, by name and every seq the emotes tab plays for it. */
    val JESTER_EMOTES: List<Pair<String, List<String>>> =
        listOf(
            "Yes" to "emote_yes",
            "No" to "emote_no",
            "Bow" to "emote_bow",
            "Angry" to "emote_angry",
            "Think" to "emote_think",
            "Wave" to "emote_wave",
            "Shrug" to "emote_shrug",
            "Cheer" to "emote_cheer",
            "Beckon" to "emote_beckon",
            "Laugh" to "emote_laugh",
            "Jump for Joy" to "emote_jump_with_joy",
            "Yawn" to "emote_yawn",
            "Dance" to "emote_dance",
            "Jig" to "emote_dance_scottish",
            "Spin" to "emote_dance_spin",
            "Headbang" to "emote_dance_headbang",
            "Cry" to "emote_cry",
            "Blow Kiss" to "emote_blow_kiss",
            "Panic" to "emote_panic",
            "Raspberry" to "emote_ya_boo_sucks",
            "Clap" to "emote_clap",
            "Salute" to "emote_fremmenik_salute",
        ).map { (name, seq) -> name to listOf("seq.$seq", "seq.${seq}_loop") }

    /** The first to copy this many of the jester's emotes wins. */
    const val JESTER_ROUNDS: Int = 10

    /** The hangman's armour npcs: each wrong guess adds a piece, and the tenth ends the game. */
    val HANGMAN_STAGES: List<String> = (1..10).map { "npc.poh_hangman_armour$it" }

    /** A word guess may name at most this many of the letters still missing. */
    const val HANGMAN_GUESS_LETTERS: Int = 5

    val HANGMAN_WORDS: List<String> =
        listOf(
            "ABYSSAL", "ADAMANTITE", "ALKHARID", "ARDOUGNE", "ASGARNIA", "AVANTOE", "BANSHEE", "BARROWS",
            "BASILISK", "BLOODVELD", "BOBTHECAT", "BRIMHAVEN", "BURTHORPE", "CADANTINE", "CAMELOT", "CANIFIS",
            "CATHERBY", "CHAOSDRUID", "CHAOSDWARF", "CHOMPYBIRD", "COCKATRICE", "CRANDOR", "DAGANNOTH",
            "DORGESHUUN", "DRAGON", "DRAYNOR", "DUSTDEVIL", "DWARFWEED", "EDGEVILLE", "ENTRANA", "FALADOR",
            "FELDIP", "FIREGIANT", "FREMENNIK", "GARGOYLE", "GOBLIN", "GRANDTREE", "GUAMLEAF", "GUTANOTH",
            "GUTHIX", "HELLHOUND", "HIGHWAYMAN", "HILLGIANT", "HOBGOBLIN", "ICEGIANT", "ICEQUEEN", "ICETROLL",
            "ICEWARRIOR", "ICEWOLF", "IRITLEAF", "ISAFDAR", "JOGRE", "KALPHITE", "KANDARIN", "KARAMJA",
            "KELDAGRIM", "KHAZARD", "KWUARM", "LANTADYME", "LLETYA", "LUMBRIDGE", "MARRENTILL", "MENAPHOS",
            "MISTHALIN", "MITHRIL", "MORTTON", "MORYTANIA", "MOSSGIANT", "NECHRYAEL", "NIGHTSHADE", "PALADIN",
            "PHASMATYS", "PORTSARIM", "PRIFDDINAS", "PYREFIEND", "RANARRWEED", "RELLEKKA", "RIMMINGTON",
            "RUNESCAPE", "RUNITE", "SARADOMIN", "SKELETON", "SNAPDRAGON", "SNAPEGRASS", "SOPHANEM", "SOULLESS",
            "SPIRITTREE", "TARROMIN", "TAVERLEY", "TERRORBIRD", "TIRANNWN", "TOADFLAX", "TORSTOL", "UGTHANKI",
            "UNICORN", "VARROCK", "YANILLE", "ZAMORAK",
        )

    const val TREASURE_FAIRY: String = "npc.poh_treasure_hunt_fairy"
    const val TREASURE_STONE: String = "obj.poh_treasure_hunt_stone"

    /** Close enough to the fairy to have found her. */
    const val FAIRY_FOUND_RANGE: Int = 1

    const val PRIZE_KEY: String = "obj.poh_prize_key"
    const val PRIZE_VARP: String = "varp.poh_prize_chest"
}
