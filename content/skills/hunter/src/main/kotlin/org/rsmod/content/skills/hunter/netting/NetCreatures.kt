package org.rsmod.content.skills.hunter.netting

enum class ButterflyEffect {
    Attack,
    Defence,
    Strength,
    Hitpoints,
    Restore,
    Prayer,
}

/**
 * [low]/[high] are the net catch-rate endpoints out of 256; [bonusLow]/[bonusHigh] apply when
 * catching barehanded or with a magic butterfly net. Only the black warlock and moonlight moth rates
 * are published; the rest are interpolated between them by level.
 */
enum class Butterfly(
    val npc: String,
    val displayName: String,
    val level: Int,
    val xp: Double,
    val jar: String,
    val low: Int,
    val high: Int,
    val bonusLow: Int,
    val bonusHigh: Int,
    val effect: ButterflyEffect,
) {
    RubyHarvest(
        npc = "npc.butterfly_ruby",
        displayName = "ruby harvest",
        level = 15,
        xp = 24.0,
        jar = "obj.butterfly_jar_ruby",
        low = 40,
        high = 316,
        bonusLow = 60,
        bonusHigh = 336,
        effect = ButterflyEffect.Attack,
    ),
    SapphireGlacialis(
        npc = "npc.butterfly_glacialis",
        displayName = "sapphire glacialis",
        level = 25,
        xp = 34.0,
        jar = "obj.butterfly_jar_glacialis",
        low = 33,
        high = 309,
        bonusLow = 53,
        bonusHigh = 329,
        effect = ButterflyEffect.Defence,
    ),
    SnowyKnight(
        npc = "npc.butterfly_snowy",
        displayName = "snowy knight",
        level = 35,
        xp = 44.0,
        jar = "obj.butterfly_jar_snowy",
        low = 27,
        high = 303,
        bonusLow = 47,
        bonusHigh = 323,
        effect = ButterflyEffect.Hitpoints,
    ),
    BlackWarlock(
        npc = "npc.butterfly_warlock",
        displayName = "black warlock",
        level = 45,
        xp = 54.0,
        jar = "obj.butterfly_jar_warlock",
        low = 20,
        high = 296,
        bonusLow = 40,
        bonusHigh = 316,
        effect = ButterflyEffect.Strength,
    ),
    SunlightMoth(
        npc = "npc.moth_sunlight",
        displayName = "sunlight moth",
        level = 65,
        xp = 74.0,
        jar = "obj.butterfly_jar_sunmoth",
        low = 7,
        high = 283,
        bonusLow = 27,
        bonusHigh = 293,
        effect = ButterflyEffect.Restore,
    ),
    MoonlightMoth(
        npc = "npc.moth_moonlight",
        displayName = "moonlight moth",
        level = 75,
        xp = 84.0,
        jar = "obj.butterfly_jar_moonmoth",
        low = 0,
        high = 276,
        bonusLow = 20,
        bonusHigh = 286,
        effect = ButterflyEffect.Prayer,
    ),
    ;

    companion object {
        val byJar: Map<String, Butterfly> = entries.associateBy { it.jar }
    }
}

enum class Impling(
    val npcs: List<String>,
    val mazeNpcs: List<String>,
    val displayName: String,
    val level: Int,
    val puroXp: Double,
    val worldXp: Double,
    val jar: String,
) {
    Baby(listOf("npc.ii_impling_type_1"), listOf("npc.ii_impling_type_1_maze"), "baby", 17, 18.0, 20.0, "obj.ii_captured_impling_1"),
    Young(listOf("npc.ii_impling_type_2"), listOf("npc.ii_impling_type_2_maze"), "young", 22, 20.0, 22.0, "obj.ii_captured_impling_2"),
    Gourmet(listOf("npc.ii_impling_type_3"), listOf("npc.ii_impling_type_3_maze"), "gourmet", 28, 22.0, 24.0, "obj.ii_captured_impling_3"),
    Earth(listOf("npc.ii_impling_type_4"), listOf("npc.ii_impling_type_4_maze"), "earth", 36, 25.0, 27.0, "obj.ii_captured_impling_4"),
    Essence(listOf("npc.ii_impling_type_5"), listOf("npc.ii_impling_type_5_maze"), "essence", 42, 27.0, 29.0, "obj.ii_captured_impling_5"),
    Eclectic(listOf("npc.ii_impling_type_6"), listOf("npc.ii_impling_type_6_maze"), "eclectic", 50, 30.0, 32.0, "obj.ii_captured_impling_6"),
    Nature(listOf("npc.ii_impling_type_7"), listOf("npc.ii_impling_type_7_maze"), "nature", 58, 34.0, 36.0, "obj.ii_captured_impling_7"),
    Magpie(listOf("npc.ii_impling_type_8"), listOf("npc.ii_impling_type_8_maze"), "magpie", 65, 44.0, 216.0, "obj.ii_captured_impling_8"),
    Ninja(listOf("npc.ii_impling_type_9"), listOf("npc.ii_impling_type_9_maze"), "ninja", 74, 50.0, 240.0, "obj.ii_captured_impling_9"),
    Crystal(CRYSTAL_NPCS, emptyList(), "crystal", 80, 280.0, 280.0, "obj.ii_captured_impling_12"),
    Dragon(listOf("npc.ii_impling_type_10"), listOf("npc.ii_impling_type_10_maze"), "dragon", 83, 65.0, 300.0, "obj.ii_captured_impling_10"),
    Lucky(listOf("npc.ii_impling_type_11"), listOf("npc.ii_impling_type_11_maze"), "lucky", 89, 80.0, 380.0, "obj.ii_captured_impling_11"),
    ;

    companion object {
        val byJar: Map<String, Impling> = entries.associateBy { it.jar }
    }
}

private val CRYSTAL_NPCS =
    listOf(
        "johnny", "junior", "andy", "joey", "trouble", "hingy", "zolty", "neil", "yanny", "matty",
        "stace", "ian", "jamie", "damo", "xander", "steveo", "stewie",
    ).map { "npc.ii_impling_type_12_$it" }
