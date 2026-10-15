package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.rsmod.api.table.FoodRow
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_ARMED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_AWAKE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_MORE
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_FED_WELL
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_GOT_CHEST
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_NEEDS_ARMOUR
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SITTING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_SPIRIT_LIFTED
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STANDING
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Player

/**
 * Nursing Cyrisus back to health. Food raises his health and talking to him raises his spirit,
 * each up to the next marker on the Guidance System bars (health 20, 40, 70 and 100; spirit 32,
 * 72 and 100). He takes turns between the two: once one bar reaches its marker he needs the other
 * before he will take more. Twenty pieces of food take him from 0 to 100% health.
 *
 * He won't eat the same kind of food as either of his last two meals, and forgets what he ate
 * when the player logs out.
 */
object CyrisusRecovery {
    const val FOOD_STEP = 5
    const val SPIRIT_STEP = 8
    const val FULL = 100

    /** Spirit at which he sits up; the bar has no marker of its own here. */
    const val SIT_UP_SPIRIT = 52

    private const val DRINK_LEFTOVER = "obj.beer_glass"
    private const val KEG = "obj.keg_of_beer"
    private const val TEA_LEFTOVER = "obj.cup_empty"
    const val SWEETS = "obj.trail_sweets"
    private const val WINE = "obj.jug_wine"

    /** Guthix rest is a potion, not food, but he will drink it a dose at a time. */
    val GUTHIX_REST =
        listOf(
            "obj.cup_guthix_rest_4",
            "obj.cup_guthix_rest_3",
            "obj.cup_guthix_rest_2",
            "obj.cup_guthix_rest_1",
            TEA_LEFTOVER,
        )

    private const val GUTHIX_REST_KIND = 1

    sealed class Feeding {
        data object NotFood : Feeding()

        data object Sweets : Feeding()

        data object NotHungry : Feeding()

        data object Recent : Feeding()

        /** He eats [obj]; [leftover] replaces it in the player's inventory, or nothing. */
        data class Eats(val obj: String, val kind: Int, val leftover: String?) : Feeding()
    }

    fun healthCap(stage: Int): Int =
        when (stage) {
            STAGE_STARTED -> 20
            STAGE_SPIRIT_LIFTED -> 40
            STAGE_SITTING -> 70
            STAGE_ARMED -> FULL
            else -> 0
        }

    fun spiritCap(stage: Int): Int =
        when (stage) {
            STAGE_AWAKE -> 32
            STAGE_FED_MORE -> SIT_UP_SPIRIT
            STAGE_FED_WELL, STAGE_STANDING, STAGE_NEEDS_ARMOUR, STAGE_GOT_CHEST -> 72
            STAGE_ARMED -> FULL
            else -> 0
        }

    fun wantsFood(player: Player, stage: Int): Boolean = player.dreamHealth < healthCap(stage)

    fun wantsCompany(player: Player, stage: Int): Boolean = player.dreamSpirit < spiritCap(stage)

    /** Works out what happens when [obj] is used on Cyrisus, without changing anything. */
    fun feeding(player: Player, stage: Int, obj: String): Feeding {
        if (obj == SWEETS) {
            return Feeding.Sweets
        }
        val (kind, leftover) = foodKind(obj) ?: return Feeding.NotFood
        if (!wantsFood(player, stage)) {
            return Feeding.NotHungry
        }
        if (kind == player.dreamRecentFoodA || kind == player.dreamRecentFoodB) {
            return Feeding.Recent
        }
        return Feeding.Eats(obj, kind, leftover)
    }

    /** Records an accepted meal; returns his new health. */
    fun eat(player: Player, stage: Int, meal: Feeding.Eats): Int {
        player.dreamRecentFoodB = player.dreamRecentFoodA
        player.dreamRecentFoodA = meal.kind
        player.dreamHealth = (player.dreamHealth + FOOD_STEP).coerceAtMost(healthCap(stage))
        return player.dreamHealth
    }

    /** Raises his spirit after a positive answer; returns his new spirit. */
    fun cheer(player: Player, stage: Int): Int {
        player.dreamSpirit = (player.dreamSpirit + SPIRIT_STEP).coerceAtMost(spiritCap(stage))
        return player.dreamSpirit
    }

    fun forgetMeals(player: Player) {
        player.dreamRecentFoodA = 0
        player.dreamRecentFoodB = 0
    }

    /** The stage his health or spirit has just carried him to, or null if he is not there yet. */
    fun nextStage(player: Player, stage: Int): Int? {
        val health = player.dreamHealth
        val spirit = player.dreamSpirit
        return when (stage) {
            STAGE_STARTED -> STAGE_AWAKE.takeIf { health >= healthCap(stage) }
            STAGE_AWAKE -> STAGE_SPIRIT_LIFTED.takeIf { spirit >= spiritCap(stage) }
            STAGE_SPIRIT_LIFTED -> STAGE_FED_MORE.takeIf { health >= healthCap(stage) }
            STAGE_FED_MORE -> STAGE_SITTING.takeIf { spirit >= spiritCap(stage) }
            STAGE_SITTING -> STAGE_FED_WELL.takeIf { health >= healthCap(stage) }
            STAGE_FED_WELL -> STAGE_STANDING.takeIf { spirit >= spiritCap(stage) }
            else -> null
        }
    }

    fun fullyRestored(player: Player): Boolean =
        player.dreamHealth >= FULL && player.dreamSpirit >= FULL && player.dreamArmament >= FULL

    /**
     * The kind of food [obj] is (its `dbtable.food` row, or Guthix rest) and what is left once he
     * has had it; null for anything he won't take. Of the drinks only wine and Guthix rest count,
     * and empty dishes and glasses listed beside a food are not food.
     */
    fun foodKind(obj: String): Pair<Int, String?>? {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return null
        if (!type.isConsumable()) {
            return null
        }
        val dose = GUTHIX_REST.indexOf(obj)
        if (dose in 0 until GUTHIX_REST.lastIndex) {
            return GUTHIX_REST_KIND to GUTHIX_REST[dose + 1]
        }
        val row = foodRows[obj] ?: return null
        val items = row.items.map { it.internalName }
        if (obj != WINE && items.any { it == DRINK_LEFTOVER || it == KEG || it == TEA_LEFTOVER }) {
            return null
        }
        val leftover = items.getOrNull(items.indexOf(obj) + 1)
        return (row.rowId and KIND_MASK) to leftover
    }

    private const val KIND_MASK = 0xFFFF

    private fun ItemServerType.isConsumable(): Boolean =
        interfaceOptions.any { it == "Eat" || it == "Drink" }

    private val foodRows: Map<String, FoodRow> by lazy {
        buildMap {
            for (row in FoodRow.all()) {
                for (item in row.items) {
                    putIfAbsent(item.internalName, row)
                }
            }
        }
    }
}
