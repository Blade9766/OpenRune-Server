package org.rsmod.content.skills.farming

/** Shared identifiers for the farming plugin. */
object Farming {
    const val STAT = "stat.farming"

    const val RAKE = "obj.rake"
    const val DIBBER = "obj.dibber"
    const val SPADE = "obj.spade"
    const val SECATEURS = "obj.secateurs"
    const val MAGIC_SECATEURS = "obj.fairy_enchanted_secateurs"
    const val PLANT_CURE = "obj.plant_cure"
    const val WEEDS = "obj.weeds"
    const val EMPTY_BUCKET = "obj.bucket_empty"

    const val RAKE_ANIM = "seq.farming_raking"
    const val DIBBING_ANIM = "seq.farming_seed_dibbing"
    const val WATERING_ANIM = "seq.farming_watering"
    const val CURE_ANIM = "seq.farming_plant_cure"
    const val POUR_ANIM = "seq.farming_pour_water"
    const val DIG_ANIM = "seq.human_dig"
    const val PICK_LOW_ANIM = "seq.picking_low"
    const val PICK_MID_ANIM = "seq.picking_mid"

    const val RAKE_SOUND = "synth.farming_raking"
    const val DIBBING_SOUND = "synth.farming_dibbing"
    const val WATERING_SOUND = "synth.farming_watering"
    const val CURE_SOUND = "synth.farming_plantcure"
    const val COMPOST_SOUND = "synth.farming_compost"
    const val PICK_SOUND = "synth.farming_pick"

    /** Ticks a single rake, dib, pour or pick takes before it resolves. */
    const val ACTION_CYCLE = 3

    const val WEED_XP = 4.0

    /** Watering cans, fullest first, so a partly used can is emptied before a fresh one. */
    val WATERING_CANS: List<String> =
        listOf(
            "obj.watering_can_8",
            "obj.watering_can_7",
            "obj.watering_can_6",
            "obj.watering_can_5",
            "obj.watering_can_4",
            "obj.watering_can_3",
            "obj.watering_can_2",
            "obj.watering_can_1",
        )

    const val EMPTY_WATERING_CAN = "obj.watering_can_0"

    fun drainedCan(can: String): String {
        val index = WATERING_CANS.indexOf(can)
        return if (index == WATERING_CANS.lastIndex) EMPTY_WATERING_CAN else WATERING_CANS[index + 1]
    }
}
