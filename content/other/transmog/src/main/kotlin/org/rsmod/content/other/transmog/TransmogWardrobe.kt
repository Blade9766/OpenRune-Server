package org.rsmod.content.other.transmog

import dev.openrune.util.Wearpos
import org.rsmod.api.attr.AttributeKey
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerPersistenceHints
import org.rsmod.game.entity.player.Appearance

internal const val PRESET_COUNT = 10
internal const val NO_OVERRIDE = 0
internal const val HIDDEN = Appearance.HIDDEN_WORN_OVERRIDE

private const val OUTFIT_SIZE = 12
private const val ENCODED_SIZE = OUTFIT_SIZE * (PRESET_COUNT + 1)

private val SLOTS: List<Int> = Wearpos.visibleWearpos.map { it.slot }

/**
 * The active outfit followed by [PRESET_COUNT] presets, each [OUTFIT_SIZE] ints indexed by worn
 * slot: [NO_OVERRIDE] shows the real worn obj, [HIDDEN] hides the slot, anything else is the obj
 * shown in its place.
 */
private val TRANSMOG_ATTR: AttributeKey<MutableList<Int>> = AttributeKey(persistenceKey = "transmog")

internal class TransmogWardrobe(private val values: IntArray = IntArray(ENCODED_SIZE)) {
    fun active(slot: Int): Int = values[slot]

    fun setActive(slot: Int, value: Int) {
        values[slot] = value
    }

    fun clearActive() {
        values.fill(NO_OVERRIDE, 0, OUTFIT_SIZE)
    }

    fun hasActive(): Boolean = (0 until OUTFIT_SIZE).any { values[it] != NO_OVERRIDE }

    fun hasPreset(preset: Int): Boolean {
        val start = presetStart(preset)
        return (start until start + OUTFIT_SIZE).any { values[it] != NO_OVERRIDE }
    }

    fun savePreset(preset: Int) {
        values.copyInto(values, presetStart(preset), 0, OUTFIT_SIZE)
    }

    fun loadPreset(preset: Int) {
        val start = presetStart(preset)
        values.copyInto(values, 0, start, start + OUTFIT_SIZE)
    }

    fun deletePreset(preset: Int) {
        val start = presetStart(preset)
        values.fill(NO_OVERRIDE, start, start + OUTFIT_SIZE)
    }

    fun savedPresetMask(): Int =
        (0 until PRESET_COUNT).fold(0) { mask, preset ->
            if (hasPreset(preset)) mask or (1 shl preset) else mask
        }

    fun hiddenMask(): Int =
        SLOTS.fold(0) { mask, slot -> if (values[slot] == HIDDEN) mask or (1 shl slot) else mask }

    fun isEmpty(): Boolean = values.all { it == NO_OVERRIDE }

    fun encode(): MutableList<Int> = values.toMutableList()

    private fun presetStart(preset: Int): Int = OUTFIT_SIZE * (preset + 1)

    companion object {
        fun decode(encoded: List<Number>): TransmogWardrobe {
            val values = IntArray(ENCODED_SIZE)
            for (index in 0 until minOf(encoded.size, ENCODED_SIZE)) {
                val slot = index % OUTFIT_SIZE
                values[index] = validated(slot, encoded[index].toInt())
            }
            return TransmogWardrobe(values)
        }

        private fun validated(slot: Int, value: Int): Int =
            when {
                slot !in SLOTS -> NO_OVERRIDE
                value == NO_OVERRIDE || value == HIDDEN -> value
                TransmogIndex.slotOf(value) == slot -> value
                else -> NO_OVERRIDE
            }
    }
}

internal fun Player.loadWardrobe(): TransmogWardrobe =
    attr[TRANSMOG_ATTR]?.let(TransmogWardrobe::decode) ?: TransmogWardrobe()

internal fun Player.saveWardrobe(wardrobe: TransmogWardrobe) {
    if (wardrobe.isEmpty()) {
        attr.remove(TRANSMOG_ATTR)
    } else {
        attr[TRANSMOG_ATTR] = wardrobe.encode()
    }
    PlayerPersistenceHints.notify(this)
}

internal fun Player.applyWardrobe(wardrobe: TransmogWardrobe) {
    for (slot in SLOTS) {
        when (val value = wardrobe.active(slot)) {
            NO_OVERRIDE -> appearance.clearWornOverride(slot)
            else -> appearance.setWornOverride(slot, value)
        }
    }
    rebuildAppearance()
}

internal fun isTransmogSlot(slot: Int): Boolean = slot in SLOTS
