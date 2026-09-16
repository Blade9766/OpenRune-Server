package org.rsmod.content.skills.farming.state

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.skills.farming.data.ToolSlot
import org.rsmod.game.entity.Player

/**
 * What each tool leprechaun is holding for a player.
 *
 * The store lives entirely in the `farming_tools_*` varbits the client already reads, so the
 * interface redraws itself whenever one of them changes and the server never has to push the
 * display. [rawValue] and [setRawValue] are the only places that know how a counter is spread
 * across its varbits.
 *
 * The single-item slots store an identity rather than a tally: the watering can slot holds the
 * client's `enum_136` index, which is how it remembers a half-full can, and the bottomless bucket
 * slot holds one for an empty bucket and two for a filled one.
 */
@Singleton
class FarmingToolStore @Inject constructor() {
    fun rawValue(player: Player, slot: ToolSlot): Int {
        val parts = IntArray(slot.varbits.size) { player.vars[slot.varbits[it].varbit] }
        return slot.join(parts)
    }

    fun setRawValue(player: Player, slot: ToolSlot, value: Int) {
        val parts = slot.split(value)
        for (index in slot.varbits.indices) {
            VarPlayerIntMapSetter.set(player, slot.varbits[index].varbit, parts[index])
        }
    }

    /** How many items the slot holds, which for a single-item slot is only ever zero or one. */
    fun count(player: Player, slot: ToolSlot): Int {
        val raw = rawValue(player, slot)
        return if (slot.singleItem) {
            if (raw > 0) 1 else 0
        } else {
            raw
        }
    }

    fun freeSpace(player: Player, slot: ToolSlot): Int = slot.capacity - count(player, slot)

    /**
     * The obj a slot is currently holding. Most slots only ever hold the one thing, but the
     * secateurs slot remembers whether the pair is enchanted and the watering can slot remembers
     * how full the can was, so those two answer from their own varbits.
     */
    fun storedObj(player: Player, slot: ToolSlot): String? {
        val raw = rawValue(player, slot)
        if (raw <= 0) {
            return null
        }
        return when (slot) {
            ToolSlot.SECATEURS ->
                if (player.vars[MAGIC_SECATEURS] != 0) MAGIC_SECATEURS_OBJ else slot.obj
            ToolSlot.WATERING_CAN -> WATERING_CANS.getOrNull(raw - 1)
            ToolSlot.BOTTOMLESS_BUCKET -> if (raw > 1) BOTTOMLESS_FILLED else slot.obj
            else -> slot.obj
        }
    }

    /**
     * The slot [obj] belongs in, or null when a leprechaun will not hold it. The watering can and
     * secateurs slots take a family of objs rather than one, because any fill level or either pair
     * of secateurs goes in the same place.
     */
    fun slotFor(obj: String): ToolSlot? =
        when {
            obj in WATERING_CANS -> ToolSlot.WATERING_CAN
            obj == MAGIC_SECATEURS_OBJ -> ToolSlot.SECATEURS
            obj == BOTTOMLESS_FILLED -> ToolSlot.BOTTOMLESS_BUCKET
            else -> ToolSlot.entries.firstOrNull { it.obj == obj }
        }

    /** The value a single-item slot stores to remember exactly which [obj] it took. */
    fun singleItemValue(slot: ToolSlot, obj: String): Int =
        when (slot) {
            ToolSlot.WATERING_CAN -> WATERING_CANS.indexOf(obj) + 1
            ToolSlot.BOTTOMLESS_BUCKET -> if (obj == BOTTOMLESS_FILLED) 2 else 1
            else -> 1
        }

    fun setMagicSecateurs(player: Player, magic: Boolean) {
        VarPlayerIntMapSetter.set(player, MAGIC_SECATEURS, if (magic) 1 else 0)
    }

    private companion object {
        const val MAGIC_SECATEURS = "varbit.farming_tools_fairysecateurs"
        const val MAGIC_SECATEURS_OBJ = "obj.fairy_enchanted_secateurs"
        const val BOTTOMLESS_FILLED = "obj.bottomless_compost_bucket_filled"

        /**
         * Watering cans in the order the client's `enum_136` lists them, so a can's index here plus
         * one is the value the store varbit takes.
         */
        val WATERING_CANS =
            listOf(
                "obj.watering_can_0",
                "obj.watering_can_1",
                "obj.watering_can_2",
                "obj.watering_can_3",
                "obj.watering_can_4",
                "obj.watering_can_5",
                "obj.watering_can_6",
                "obj.watering_can_7",
                "obj.watering_can_8",
                "obj.zeah_wateringcan",
            )
    }
}
