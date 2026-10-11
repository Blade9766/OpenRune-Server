package org.rsmod.content.events.halloween.pack

import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType

/**
 * The Great cauldron's gift ladder (2023 "Trick-or-treating Reloaded" rewards). Row `n` is tracked
 * by the cache varbit `hw23_reward_received_n`.
 */
object HalloweenRewardsTable {
    const val INDEX = 0
    const val TREATS = 1
    const val KIND = 2
    const val OBJ = 3
    const val COUNT = 4
    const val HOLIDAY_INDEX = 5
    const val STYLE_NAME = 6

    const val KIND_ITEM = 0
    const val KIND_STYLE = 1
    const val KIND_EMOTE = 2

    fun rewards() =
        dbTable("dbtable.halloween_treat_rewards", serverOnly = true) {
            column("index", INDEX, VarType.INT)
            column("treats", TREATS, VarType.INT)
            column("kind", KIND, VarType.INT)
            column("obj", OBJ, VarType.OBJ)
            column("count", COUNT, VarType.INT)
            column("holiday_index", HOLIDAY_INDEX, VarType.INT)
            column("style_name", STYLE_NAME, VarType.STRING)

            fun reward(
                index: Int,
                kind: Int,
                obj: String?,
                count: Int = 1,
                holiday: Int? = null,
                style: String? = null,
            ) {
                row("dbrow.halloween_treat_reward_$index") {
                    column(INDEX, index)
                    column(TREATS, index * 80)
                    column(KIND, kind)
                    if (obj != null) {
                        columnRSCM(OBJ, obj)
                        column(COUNT, count)
                    }
                    if (holiday != null) {
                        column(HOLIDAY_INDEX, holiday)
                    }
                    if (style != null) {
                        column(STYLE_NAME, style)
                    }
                }
            }

            reward(1, KIND_ITEM, "obj.hw23_reward_cobwebcape", holiday = 190)
            reward(2, KIND_ITEM, "obj.hw23_reward_spiderhat", holiday = 191)
            reward(3, KIND_EMOTE, null)
            reward(4, KIND_STYLE, "obj.hw23_reward_spiderhat02", style = "Venenatis")
            reward(5, KIND_STYLE, "obj.hw23_reward_spiderhat03", style = "Verzik")
            reward(6, KIND_STYLE, "obj.hw23_reward_spiderhat04", style = "Sarachnis")
            reward(7, KIND_STYLE, "obj.hw23_reward_spiderhat05", style = "Old")
            reward(8, KIND_ITEM, "obj.set_halloween_mask", count = 2)
            reward(9, KIND_ITEM, "obj.pumpkin", count = 2)
        }
}
