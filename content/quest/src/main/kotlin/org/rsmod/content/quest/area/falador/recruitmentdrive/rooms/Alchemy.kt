package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

/**
 * What happens when two of Miss Cheevers's items are used on each other, as a pure function of the
 * two item names and what the tin has been layered with. Nothing here touches an inventory: the room
 * script checks the player still holds both items and then applies the [Result] in one go.
 *
 * The rules are the OSRS wiki's (the "??? mixture", tin and key pages and the quest transcripts):
 * - Cupric sulfate with a vial of liquid makes the hot mixture; gypsum with a vial of liquid the warm
 *   one; cupric sulfate with any other chemical, or any filled vial on a hot mixture, the horrible
 *   one; a filled vial on a horrible mixture just empties into it. Salt and liquid refuse to mix.
 * - Nitrous oxide never reacts: the player only laughs at it.
 * - The tin takes gypsum and a vial of liquid in either order (the layered tin), then hardens on the
 *   chained key into an impression, takes tin ore powder and cupric ore powder in either order, is
 *   heated into a bronze key, and gives the key up to a knife, chisel or bronze wire. Any other
 *   chemical poured into it turns it into the strange tin, which is of no further use.
 */
object Alchemy {
    const val LIQUID = "obj.rd_dihydrogen_monoxide"
    const val ACETIC_ACID = "obj.rd_acetic_acid"
    const val GYPSUM = "obj.rd_gypsum"
    const val SALT = "obj.rd_sodium_chloride"
    const val NITROUS_OXIDE = "obj.rd_nitorus_oxide"
    const val CUPRIC_SULFATE = "obj.rd_cupric_sulphate"
    const val TIN_POWDER = "obj.rd_tin_ore_powder"
    const val COPPER_POWDER = "obj.rd_copper_ore_powder"
    const val HOT_MIXTURE = "obj.rd_cupric_sulphate2"
    const val WARM_MIXTURE = "obj.rd_plaster_vial"
    const val HORRIBLE_MIXTURE = "obj.rd_spoilt_potion"
    const val EMPTY_VIAL = "obj.vial_empty"

    const val TIN = "obj.rd_tin"
    const val TIN_LAYERED = "obj.rd_tin_of_crap_empty"
    const val TIN_HARDENING = "obj.rd_tinfull"
    const val TIN_IMPRESSION = "obj.rd_keymould"
    const val TIN_WITH_TIN = "obj.rd_full_keymould_tin"
    const val TIN_WITH_COPPER = "obj.rd_full_keymould_copper"
    const val TIN_UNHEATED = "obj.rd_full_keymould_unheated"
    const val TIN_KEY = "obj.rd_full_keymould_complete"
    const val TIN_STRANGE = "obj.rd_tin_of_crap"

    const val BRONZE_KEY = "obj.rd_puzzleroom_key"
    const val KNIFE = "obj.rd_knife"
    const val CHISEL = "obj.rd_chisel"
    const val WIRE = "obj.rd_wire"
    const val MAGNET = "obj.rd_magnet"

    val FILLED_VIALS =
        setOf(LIQUID, ACETIC_ACID, GYPSUM, SALT, NITROUS_OXIDE, CUPRIC_SULFATE, TIN_POWDER, COPPER_POWDER, HOT_MIXTURE, WARM_MIXTURE, HORRIBLE_MIXTURE)

    val TINS = setOf(TIN, TIN_LAYERED, TIN_HARDENING, TIN_IMPRESSION, TIN_WITH_TIN, TIN_WITH_COPPER, TIN_UNHEATED, TIN_KEY, TIN_STRANGE)

    val KEY_TOOLS = setOf(KNIFE, CHISEL, WIRE)

    /** What the layered tin holds; saved on `varbit.rd_gypsum_in_tin` / `varbit.rd_water_in_tin`. */
    enum class Layer {
        NONE,
        GYPSUM,
        LIQUID,
    }

    sealed class Result {
        /**
         * Replace the used items: [into] maps each of the two items to what it becomes (null removes
         * it); [adds] is given on top; [layer] is the tin's new layer when it changes.
         */
        data class Change(
            val into: Map<String, String?>,
            val messages: List<String>,
            val adds: String? = null,
            val layer: Layer? = null,
        ) : Result()

        data class Say(val text: String) : Result()

        data class Message(val text: String) : Result()

        data object Laugh : Result()

        data object Nothing : Result()
    }

    fun combine(first: String, second: String, layer: Layer): Result {
        if (first in TINS || second in TINS) {
            val tin = if (first in TINS) first else second
            val other = if (tin == first) second else first
            return intoTin(tin, other, layer)
        }
        if (first == NITROUS_OXIDE || second == NITROUS_OXIDE) {
            return if (first in FILLED_VIALS && second in FILLED_VIALS) Result.Laugh else Result.Nothing
        }
        if (setOf(first, second) == setOf(MAGNET, WIRE)) {
            return Result.Message("The magnet sticks slightly to the wire.")
        }
        if (first in FILLED_VIALS && second in FILLED_VIALS) {
            return mix(first, second)
        }
        return Result.Nothing
    }

    private fun mix(a: String, b: String): Result {
        val pair = setOf(a, b)
        if (a == b) return Result.Nothing
        if (pair == setOf(CUPRIC_SULFATE, LIQUID)) return pour(into = CUPRIC_SULFATE, becomes = HOT_MIXTURE, emptied = LIQUID)
        if (pair == setOf(GYPSUM, LIQUID)) return pour(into = GYPSUM, becomes = WARM_MIXTURE, emptied = LIQUID)
        if (pair == setOf(SALT, LIQUID)) return Result.Say("I have no time for brine!")
        if (CUPRIC_SULFATE in pair) {
            val other = (pair - CUPRIC_SULFATE).single()
            if (other in setOf(SALT, GYPSUM, ACETIC_ACID, COPPER_POWDER, TIN_POWDER, HOT_MIXTURE)) {
                return pour(into = CUPRIC_SULFATE, becomes = HORRIBLE_MIXTURE, emptied = other)
            }
        }
        if (HOT_MIXTURE in pair) {
            val other = (pair - HOT_MIXTURE).single()
            return pour(into = HOT_MIXTURE, becomes = HORRIBLE_MIXTURE, emptied = other)
        }
        if (HORRIBLE_MIXTURE in pair) {
            val other = (pair - HORRIBLE_MIXTURE).single()
            if (other != WARM_MIXTURE) {
                return Result.Change(mapOf(HORRIBLE_MIXTURE to HORRIBLE_MIXTURE, other to EMPTY_VIAL), listOf("You empty the vial into the mixture."))
            }
        }
        return Result.Nothing
    }

    private fun pour(into: String, becomes: String, emptied: String): Result =
        Result.Change(mapOf(into to becomes, emptied to EMPTY_VIAL), listOf("You mix the two vials together."))

    private fun intoTin(tin: String, other: String, layer: Layer): Result {
        if (other in KEY_TOOLS) {
            if (tin != TIN_KEY) return Result.Nothing
            return Result.Change(mapOf(tin to TIN_IMPRESSION, other to other), listOf("You prise the duplicate key out of the tin."), adds = BRONZE_KEY)
        }
        if (other == NITROUS_OXIDE) return Result.Laugh
        if (other !in FILLED_VIALS) return Result.Nothing
        val emptyInto = "You empty the vial into the tin."
        val pourInto = "You pour the vial into the impression of the key."
        return when (tin) {
            TIN ->
                when (other) {
                    GYPSUM -> Result.Change(mapOf(tin to TIN_LAYERED, other to EMPTY_VIAL), listOf(emptyInto), layer = Layer.GYPSUM)
                    LIQUID -> Result.Change(mapOf(tin to TIN_LAYERED, other to EMPTY_VIAL), listOf(emptyInto), layer = Layer.LIQUID)
                    else -> strange(tin, other)
                }
            TIN_LAYERED -> {
                val completes = (layer == Layer.GYPSUM && other == LIQUID) || (layer == Layer.LIQUID && other == GYPSUM)
                when {
                    completes ->
                        Result.Change(
                            mapOf(tin to TIN_HARDENING, other to EMPTY_VIAL),
                            listOf(emptyInto, "You notice the tin gets quite warm as you do this.", "A lumpy white mixture is made, that seems to be hardening."),
                            layer = Layer.NONE,
                        )
                    (layer == Layer.GYPSUM && other == GYPSUM) || (layer == Layer.LIQUID && other == LIQUID) || layer == Layer.NONE -> Result.Nothing
                    else -> strange(tin, other)
                }
            }
            TIN_IMPRESSION ->
                when (other) {
                    TIN_POWDER -> Result.Change(mapOf(tin to TIN_WITH_TIN, other to EMPTY_VIAL), listOf(pourInto))
                    COPPER_POWDER -> Result.Change(mapOf(tin to TIN_WITH_COPPER, other to EMPTY_VIAL), listOf(pourInto))
                    else -> strange(tin, other)
                }
            TIN_WITH_TIN ->
                when (other) {
                    COPPER_POWDER -> Result.Change(mapOf(tin to TIN_UNHEATED, other to EMPTY_VIAL), listOf(pourInto))
                    TIN_POWDER -> Result.Nothing
                    else -> strange(tin, other)
                }
            TIN_WITH_COPPER ->
                when (other) {
                    TIN_POWDER -> Result.Change(mapOf(tin to TIN_UNHEATED, other to EMPTY_VIAL), listOf(pourInto))
                    COPPER_POWDER -> Result.Nothing
                    else -> strange(tin, other)
                }
            TIN_HARDENING, TIN_UNHEATED -> strange(tin, other)
            else -> Result.Nothing
        }
    }

    private fun strange(tin: String, other: String): Result =
        Result.Change(mapOf(tin to TIN_STRANGE, other to EMPTY_VIAL), listOf("You empty the vial into the tin."), layer = Layer.NONE)
}
