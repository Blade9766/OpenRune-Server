package org.rsmod.content.skills.hunter.rumours

class SackLoot(val obj: String, val min: Int, val max: Int = min)

/**
 * Hunters' loot sack tiers. Each open rolls [rolls] times on an equal-weight table of [main]
 * entries plus one herb slot (when [herbs]) and one log slot; herb and log tiers cascade on the
 * Herblore and Woodcutting levels. Tertiaries roll once per sack.
 */
enum class LootSack(
    val obj: String,
    val rolls: Int,
    val main: List<SackLoot>,
    val herbs: Boolean,
    val tertiaries: Boolean,
    val perfectedBlueprint: Boolean,
) {
    Basic(
        "obj.hg_lootsack_t0",
        rolls = 5,
        main =
            listOf(
                SackLoot(FEED, 1),
                SackLoot(COINS, 750, 1250),
                SackLoot(SPEAR_TIPS, 15, 30),
                SackLoot(BONE_SHARDS, 100, 200),
                SackLoot(KYATT, 2),
                SackLoot(PYRE_FOX, 3),
            ),
        herbs = false,
        tertiaries = false,
        perfectedBlueprint = false,
    ),
    Adept(
        "obj.hg_lootsack_t1",
        rolls = 7,
        main =
            listOf(
                SackLoot(FEED, 1),
                SackLoot(NEST, 1),
                SackLoot(NEST, 2, 4),
                SackLoot(SUN_KISSED, 2),
                SackLoot(BONE_SHARDS, 100, 200),
                SackLoot(SPEAR_TIPS, 15, 30),
                SackLoot(KYATT, 2),
                SackLoot(PYRE_FOX, 3),
                SackLoot(SUN_ANTELOPE, 2),
                SackLoot(COINS, 750, 1250),
            ),
        herbs = true,
        tertiaries = true,
        perfectedBlueprint = false,
    ),
    Expert("obj.hg_lootsack_t2", rolls = 9, main = HIGH_TABLE, herbs = true, tertiaries = true, perfectedBlueprint = true),
    Master("obj.hg_lootsack_t3", rolls = 11, main = HIGH_TABLE, herbs = true, tertiaries = true, perfectedBlueprint = true),
}

class CascadeTier(val obj: String, val low: Int, val high: Int, val req: Int = 1)

val SACK_HERBS =
    listOf(
        CascadeTier("obj.cert_unidentified_lantadyme", -30, 60, 34),
        CascadeTier("obj.cert_unidentified_cadantine", -10, 70, 13),
        CascadeTier("obj.cert_unidentified_kwuarm", 10, 85),
        CascadeTier("obj.cert_unidentified_avantoe", 20, 100),
        CascadeTier("obj.cert_unidentified_irit", 30, 115),
        CascadeTier("obj.cert_unidentified_ranarr", 10, 170),
        CascadeTier("obj.cert_unidentified_tarromin", 70, -20),
        CascadeTier("obj.cert_unidentified_harralander", 256, 256),
    )

val SACK_LOGS =
    listOf(
        CascadeTier("obj.cert_magic_logs", -60, 60, 50),
        CascadeTier("obj.cert_yew_logs", -50, 90, 36),
        CascadeTier("obj.cert_mahogany_logs", -40, 130, 24),
        CascadeTier("obj.cert_maple_logs", 0, 160),
        CascadeTier("obj.cert_teak_logs", 256, 256),
    )

private const val FEED = "obj.hg_seedsack"
private const val COINS = "obj.coins"
private const val SPEAR_TIPS = "obj.hg_speartip"
private const val BONE_SHARDS = "obj.blessed_bone_shard"
private const val KYATT = "obj.cert_hunting_kyatt_meat"
private const val PYRE_FOX = "obj.cert_hunting_fennecfox_meat"
private const val SUN_ANTELOPE = "obj.cert_hunting_antelopesun_meat"
private const val MOON_ANTELOPE = "obj.cert_hunting_antelopemoon_meat"
private const val NEST = "obj.cert_bird_nest_empty"
private const val SUN_KISSED = "obj.cert_sun_kissed_bone"

private val HIGH_TABLE =
    listOf(
        SackLoot(FEED, 1),
        SackLoot(NEST, 1),
        SackLoot(NEST, 2, 4),
        SackLoot(SUN_KISSED, 2),
        SackLoot(SUN_KISSED, 3),
        SackLoot(BONE_SHARDS, 100, 200),
        SackLoot(SPEAR_TIPS, 15, 30),
        SackLoot(KYATT, 2),
        SackLoot(PYRE_FOX, 3),
        SackLoot(SUN_ANTELOPE, 2),
        SackLoot(MOON_ANTELOPE, 2),
        SackLoot(COINS, 750, 1250),
        SackLoot(COINS, 2500, 3500),
    )
