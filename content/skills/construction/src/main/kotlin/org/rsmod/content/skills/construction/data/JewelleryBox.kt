package org.rsmod.content.skills.construction.data

import org.rsmod.content.travel.jewellery.Jewellery
import org.rsmod.content.travel.jewellery.JewelleryDestination
import org.rsmod.content.travel.jewellery.JewelleryTeleports

/**
 * The achievement gallery's jewellery box and the quest hall's mounted amulet of glory: unlimited
 * teleports to the destinations of the jewellery they hold, taken straight from the jewellery
 * module so the two never drift apart.
 *
 * The box's interface numbers its buttons 0 to 26 across six panels, in the order of [FAMILIES]
 * and each family's own destinations. A basic box offers the first two families, a fancy box the
 * first four and an ornate box all six. The box remembers the last place it sent its owner in
 * `varbit.poh_jewellerybox_multi` (the button plus one), which is also how the client shows that
 * place as the box's third op.
 */
object JewelleryBox {
    /** Each family by the first charge state it is listed with, in the interface's panel order. */
    private val FAMILIES: List<String> =
        listOf(
            "obj.ring_of_dueling_8",
            "obj.necklace_of_minigames_8",
            "obj.jewl_bracelet_of_combat_6",
            "obj.jewl_necklace_of_skills_6",
            "obj.ring_of_wealth_5",
            "obj.amulet_of_glory_6",
        )

    private fun family(obj: String): Jewellery = JewelleryTeleports.all.first { it.charged.firstOrNull() == obj }

    /** Every destination the ornate box offers, in button order. */
    val DESTINATIONS: List<JewelleryDestination> by lazy { FAMILIES.flatMap { family(it).destinations } }

    /** The mounted amulet's four places, one per op. */
    val GLORY: List<JewelleryDestination> by lazy { family("obj.amulet_of_glory_6").destinations }

    /** Each box tier's built loc, mapped to how many families it holds. */
    val BOXES: Map<String, Int> =
        mapOf("loc.poh_jewellery_box_1" to 2, "loc.poh_jewellery_box_2" to 4, "loc.poh_jewellery_box_3" to 6)

    /** How many buttons a box holding [families] families offers. */
    fun buttons(families: Int): Int = FAMILIES.take(families).sumOf { family(it).destinations.size }

    const val MOUNTED_GLORY: String = "loc.poh_trophy_amuletofglory_4"
    const val LAST_DESTINATION_VARBIT: String = "varbit.poh_jewellerybox_multi"
}
