package org.rsmod.content.skills.farming.state

import org.rsmod.content.skills.farming.data.Crop
import org.rsmod.content.skills.farming.data.Crops
import org.rsmod.content.skills.farming.data.PatchKind

enum class Compost(val obj: String, val label: String, val extraLives: Int, val diseaseDivisor: Int) {
    NONE("", "", 0, 1),
    NORMAL("obj.bucket_compost", "compost", 1, 2),
    SUPER("obj.bucket_supercompost", "supercompost", 2, 5),
    ULTRA("obj.bucket_ultracompost", "ultracompost", 3, 10);

    companion object {
        fun forObj(obj: String): Compost? = entries.firstOrNull { it.obj == obj }
    }
}

/**
 * What a single patch is currently holding, for one player.
 *
 * [lastTick] is an absolute epoch minute rather than a countdown so that crops keep growing while
 * the player is logged out; the growth loop replays however many cycles elapsed the next time the
 * patch is looked at.
 */
class PatchState(
    var cropKey: String? = null,
    var stage: Int = 0,
    var weeds: Int = PatchKind.WEEDS_HEAVY,
    var lastTick: Int = 0,
    var diseased: Boolean = false,
    var dead: Boolean = false,
    var watered: Boolean = false,
    var compost: Compost = Compost.NONE,
    var protectedByFarmer: Boolean = false,
    var lives: Int = 0,
) {
    val crop: Crop?
        get() = cropKey?.let(Crops::forKey)

    val isEmpty: Boolean
        get() = cropKey == null

    fun clearToWeeded() {
        cropKey = null
        stage = 0
        weeds = PatchKind.WEEDED
        diseased = false
        dead = false
        watered = false
        compost = Compost.NONE
        protectedByFarmer = false
        lives = 0
    }

    /** The value the patch's transmit varbit must take for the client to draw this state. */
    fun varbitValue(): Int {
        val crop = crop ?: return weeds
        return when {
            dead -> crop.deadState(stage.coerceIn(1, crop.cycles - 1))
            diseased -> crop.diseasedState(stage.coerceIn(1, crop.cycles - 1))
            else -> crop.growthState(stage, watered)
        }
    }

    fun encode(): String =
        listOf(
            cropKey ?: "",
            stage,
            weeds,
            lastTick,
            compost.ordinal,
            lives,
            flags(),
        ).joinToString(":")

    private fun flags(): Int =
        (if (diseased) 1 else 0) or
            (if (dead) 2 else 0) or
            (if (watered) 4 else 0) or
            (if (protectedByFarmer) 8 else 0)

    companion object {
        fun decode(encoded: String): PatchState? {
            val parts = encoded.split(':')
            if (parts.size != 7) {
                return null
            }
            val stage = parts[1].toIntOrNull() ?: return null
            val weeds = parts[2].toIntOrNull() ?: return null
            val lastTick = parts[3].toIntOrNull() ?: return null
            val compost = parts[4].toIntOrNull() ?: return null
            val lives = parts[5].toIntOrNull() ?: return null
            val flags = parts[6].toIntOrNull() ?: return null
            val cropKey = parts[0].takeIf(String::isNotEmpty)
            if (cropKey != null && Crops.forKey(cropKey) == null) {
                return null
            }
            return PatchState(
                cropKey = cropKey,
                stage = stage,
                weeds = weeds,
                lastTick = lastTick,
                diseased = flags and 1 != 0,
                dead = flags and 2 != 0,
                watered = flags and 4 != 0,
                compost = Compost.entries.getOrElse(compost) { Compost.NONE },
                protectedByFarmer = flags and 8 != 0,
                lives = lives,
            )
        }
    }
}
