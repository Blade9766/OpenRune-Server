package org.rsmod.content.skills.construction.data

/**
 * The achievement gallery's boss lair display and cape hanger.
 *
 * A boss's lair can be shown once its jar has been put in the display; the jars put in are a bit
 * each in [LAIR_VARP], and the lair on show is kept above [SHOWN_SHIFT] as its index plus one. The
 * cape hanger keeps the hung cape in [CAPE_VARP] as its index in [Cape] plus one. The bosses and
 * jars are the wiki's boss lair display list.
 */
object Gallery {
    enum class Lair(val label: String, val jar: String, private val key: String) {
        KRAKEN("Kraken", "obj.jar_of_dirt", "kraken"),
        KALPHITE_QUEEN("Kalphite Queen", "obj.jar_of_sand", "kq"),
        ZULRAH("Zulrah", "obj.jar_of_swamp", "zulrah"),
        CERBERUS("Cerberus", "obj.jar_of_souls", "cerberus"),
        ABYSSAL_SIRE("Abyssal Sire", "obj.jar_of_miasma", "sire"),
        SKOTIZO("Skotizo", "obj.jar_of_darkness", "skotizo"),
        GROTESQUE_GUARDIANS("Grotesque Guardians", "obj.jar_of_stone", "gargoyle"),
        VORKATH("Vorkath", "obj.jar_of_decay", "vorkath"),
        HYDRA("Alchemical Hydra", "obj.jar_of_chemicals", "hydra"),
        SARACHNIS("Sarachnis", "obj.jar_of_eyes", "sarachnis"),
        NIGHTMARE("The Nightmare", "obj.jar_of_dreams", "nightmare"),
        CORPOREAL_BEAST("Corporeal Beast", "obj.jar_of_spirits", "corp"),
        SMOKE_DEVIL("Thermonuclear Smoke Devil", "obj.jar_of_smoke", "thermy"),
        ARAXXOR("Araxxor", "obj.jar_of_venom", "araxyte"),
        GRYPHON("Shellbane Gryphon", "obj.jar_of_feathers", "gryphon"),
        MAD_ANGEL("Mad Angel", "obj.jar_of_light", "angel");

        val loc: String
            get() = "loc.poh_display_$key"
    }

    const val LAIR_BLANK: String = "loc.poh_display_blank"
    const val LAIR_VARP: String = "varp.poh_gallery_lair"
    const val SHOWN_SHIFT: Int = 16
    private const val SHOWN_MASK = 0x1F

    fun hasJar(varp: Int, lair: Lair): Boolean = varp and (1 shl lair.ordinal) != 0

    fun withJar(varp: Int, lair: Lair, held: Boolean): Int =
        if (held) varp or (1 shl lair.ordinal) else varp and (1 shl lair.ordinal).inv()

    fun shownLair(varp: Int): Lair? = Lair.entries.getOrNull(((varp shr SHOWN_SHIFT) and SHOWN_MASK) - 1)

    fun withShown(varp: Int, lair: Lair?): Int =
        (varp and (SHOWN_MASK shl SHOWN_SHIFT).inv()) or (((lair?.ordinal ?: -1) + 1) shl SHOWN_SHIFT)

    /** A cape that can be hung, by its obj and the mounted loc showing it. */
    enum class Cape(val obj: String, val loc: String) {
        ATTACK("obj.skillcape_attack", "loc.poh_mounted_attack_cape"),
        ATTACK_T("obj.skillcape_attack_trimmed", "loc.poh_mounted_attack_cape_trim"),
        STRENGTH("obj.skillcape_strength", "loc.poh_mounted_strength_cape"),
        STRENGTH_T("obj.skillcape_strength_trimmed", "loc.poh_mounted_strength_cape_trim"),
        DEFENCE("obj.skillcape_defence", "loc.poh_mounted_defence_cape"),
        DEFENCE_T("obj.skillcape_defence_trimmed", "loc.poh_mounted_defence_cape_trim"),
        RANGED("obj.skillcape_ranging", "loc.poh_mounted_ranged_cape"),
        RANGED_T("obj.skillcape_ranging_trimmed", "loc.poh_mounted_ranged_cape_trim"),
        PRAYER("obj.skillcape_prayer", "loc.poh_mounted_prayer_cape"),
        PRAYER_T("obj.skillcape_prayer_trimmed", "loc.poh_mounted_prayer_cape_trim"),
        MAGIC("obj.skillcape_magic", "loc.poh_mounted_magic_cape"),
        MAGIC_T("obj.skillcape_magic_trimmed", "loc.poh_mounted_magic_cape_trim"),
        RUNECRAFTING("obj.skillcape_runecrafting", "loc.poh_mounted_runecrafting_cape"),
        RUNECRAFTING_T("obj.skillcape_runecrafting_trimmed", "loc.poh_mounted_runecrafting_cape_trim"),
        CONSTRUCTION("obj.skillcape_construction", "loc.poh_mounted_construction_cape"),
        CONSTRUCTION_T("obj.skillcape_construction_trimmed", "loc.poh_mounted_construction_cape_trim"),
        HITPOINTS("obj.skillcape_hitpoints", "loc.poh_mounted_hitpoints_cape"),
        HITPOINTS_T("obj.skillcape_hitpoints_trimmed", "loc.poh_mounted_hitpoints_cape_trim"),
        AGILITY("obj.skillcape_agility", "loc.poh_mounted_agility_cape"),
        AGILITY_T("obj.skillcape_agility_trimmed", "loc.poh_mounted_agility_cape_trim"),
        HERBLORE("obj.skillcape_herblore", "loc.poh_mounted_herblore_cape"),
        HERBLORE_T("obj.skillcape_herblore_trimmed", "loc.poh_mounted_herblore_cape_trim"),
        THIEVING("obj.skillcape_thieving", "loc.poh_mounted_thieving_cape"),
        THIEVING_T("obj.skillcape_thieving_trimmed", "loc.poh_mounted_thieving_cape_trim"),
        CRAFTING("obj.skillcape_crafting", "loc.poh_mounted_crafting_cape"),
        CRAFTING_T("obj.skillcape_crafting_trimmed", "loc.poh_mounted_crafting_cape_trim"),
        FLETCHING("obj.skillcape_fletching", "loc.poh_mounted_fletching_cape"),
        FLETCHING_T("obj.skillcape_fletching_trimmed", "loc.poh_mounted_fletching_cape_trim"),
        SLAYER("obj.skillcape_slayer", "loc.poh_mounted_slayer_cape"),
        SLAYER_T("obj.skillcape_slayer_trimmed", "loc.poh_mounted_slayer_cape_trim"),
        HUNTER("obj.skillcape_hunting", "loc.poh_mounted_hunting_cape"),
        HUNTER_T("obj.skillcape_hunting_trimmed", "loc.poh_mounted_hunting_cape_trim"),
        MINING("obj.skillcape_mining", "loc.poh_mounted_mining_cape"),
        MINING_T("obj.skillcape_mining_trimmed", "loc.poh_mounted_mining_cape_trim"),
        SMITHING("obj.skillcape_smithing", "loc.poh_mounted_smithing_cape"),
        SMITHING_T("obj.skillcape_smithing_trimmed", "loc.poh_mounted_smithing_cape_trim"),
        FISHING("obj.skillcape_fishing", "loc.poh_mounted_fishing_cape"),
        FISHING_T("obj.skillcape_fishing_trimmed", "loc.poh_mounted_fishing_cape_trim"),
        COOKING("obj.skillcape_cooking", "loc.poh_mounted_cooking_cape"),
        COOKING_T("obj.skillcape_cooking_trimmed", "loc.poh_mounted_cooking_cape_trim"),
        FIREMAKING("obj.skillcape_firemaking", "loc.poh_mounted_firemaking_cape"),
        FIREMAKING_T("obj.skillcape_firemaking_trimmed", "loc.poh_mounted_firemaking_cape_trim"),
        WOODCUTTING("obj.skillcape_woodcutting", "loc.poh_mounted_woodcutting_cape"),
        WOODCUTTING_T("obj.skillcape_woodcutting_trimmed", "loc.poh_mounted_woodcutting_cape_trim"),
        FARMING("obj.skillcape_farming", "loc.poh_mounted_farming_cape"),
        FARMING_T("obj.skillcape_farming_trimmed", "loc.poh_mounted_farming_cape_trim"),
        QUEST("obj.skillcape_qp", "loc.poh_mounted_quest_cape"),
        QUEST_T("obj.skillcape_qp_trimmed", "loc.poh_mounted_quest_cape_trim"),
        DIARY("obj.skillcape_ad", "loc.poh_mounted_diary_cape"),
        DIARY_T("obj.skillcape_ad_trimmed", "loc.poh_mounted_diary_cape_trim"),
        MUSIC("obj.music_cape", "loc.poh_mounted_music_cape"),
        MUSIC_T("obj.music_cape_trimmed", "loc.poh_mounted_music_cape_trim"),
        FIRE("obj.tzhaar_cape_fire", "loc.poh_mounted_fire_cape"),
        CHAMPION("obj.champion_cape", "loc.poh_mounted_championscape"),
        MYTHICAL("obj.mythical_cape", "loc.poh_mounted_mythicalcape"),
        MAX("obj.skillcape_max", "loc.poh_mounted_max_cape"),
        MAX_FIRE("obj.skillcape_max_firecape", "loc.poh_mounted_max_cape_fire"),
        MAX_SARADOMIN("obj.skillcape_max_saradomin", "loc.poh_mounted_max_cape_saradomin"),
        MAX_ZAMORAK("obj.skillcape_max_zamorak", "loc.poh_mounted_max_cape_zamorak"),
        MAX_GUTHIX("obj.skillcape_max_guthix", "loc.poh_mounted_max_cape_guthix"),
        MAX_AVA("obj.skillcape_max_anma", "loc.poh_mounted_max_cape_ava"),
        MAX_ARDOUGNE("obj.skillcape_max_ardy", "loc.poh_mounted_max_cape_ardy"),
        MAX_ASSEMBLER("obj.skillcape_max_assembler", "loc.poh_mounted_max_cape_assembler"),
        MAX_MASORI("obj.skillcape_max_assembler_masori", "loc.poh_mounted_max_cape_assembler_masori"),
        MAX_DIZANAS("obj.skillcape_max_dizanas", "loc.poh_mounted_max_cape_dizanas"),
        MAX_INFERNAL("obj.skillcape_max_infernalcape", "loc.poh_mounted_max_cape_infernalcape"),
        MAX_MYTHICAL("obj.skillcape_max_mythical", "loc.poh_mounted_max_cape_mythical");

        companion object {
            fun ofLoc(loc: String): Cape? = entries.firstOrNull { it.loc == loc }
        }
    }

    const val CAPE_BLANK: String = "loc.poh_mounted_capestand_blank"
    const val CAPE_VARP: String = "varp.poh_gallery_cape"

    /** What an owner's [built] lair or cape hanger shows, or null when it is neither. */
    fun shown(built: String, lairVarp: Int, capeVarp: Int): String? =
        when (built) {
            LAIR_BLANK -> shownLair(lairVarp)?.loc ?: built
            CAPE_BLANK -> Cape.entries.getOrNull(capeVarp - 1)?.loc ?: built
            else -> null
        }

    /** Every form the lair and the cape hanger can take besides empty. */
    val SHOWN_FORMS: List<String>
        get() = Lair.entries.map { it.loc } + Cape.entries.map { it.loc }

    /** The empty display a shown form stands in for. */
    fun baseOf(loc: String): String? =
        when {
            Lair.entries.any { it.loc == loc } -> LAIR_BLANK
            Cape.ofLoc(loc) != null -> CAPE_BLANK
            else -> null
        }

    val ADVENTURE_LOGS: List<String> = (1..3).map { "loc.poh_adventure_log_$it" }
    const val QUEST_LIST: String = "loc.poh_quest_list"
    const val EMBLEM: String = "loc.poh_mounted_emblem"
    const val COINS: String = "loc.poh_mounted_coins"
}
