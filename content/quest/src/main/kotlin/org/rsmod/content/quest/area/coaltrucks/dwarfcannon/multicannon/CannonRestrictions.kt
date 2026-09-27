package org.rsmod.content.quest.area.coaltrucks.dwarfcannon.multicannon

import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.map.CoordGrid

/**
 * Where a multicannon may not be set up, and what the player is told. Named map areas cover most
 * of the wiki's list; places whose music area is missing or shared with other places (the
 * dwarves' land and the Dwarven Mine, Entrana, the Black Knights' Fortress, the TzHaar city, the
 * Void Knights' Outpost, the Barbarian Assault and Castle Wars lobbies) are plain rectangles. Some messages vary by place, so each rule carries its own.
 */
class CannonRestrictions @Inject constructor(private val areas: AreaChecker) {

    fun message(coords: CoordGrid): String? {
        for ((area, message) in AREA_RULES) {
            if (areas.inArea(area, coords)) {
                return message
            }
        }
        return ZONE_RULES.firstOrNull { it.contains(coords) }?.message
    }

    private class Zone(
        val minX: Int,
        val minZ: Int,
        val maxX: Int,
        val maxZ: Int,
        val message: String,
    ) {
        fun contains(coords: CoordGrid): Boolean = coords.x in minX..maxX && coords.z in minZ..maxZ
    }

    private companion object {
        const val CANT = "You can't set up a cannon here."
        const val DWARVES = "The dwarves won't be happy if you set up a cannon here."
        const val ANCIENT_POWER = "An ancient power won't let you set up a cannon here."
        const val ANCIENT_MAGIC = "An ancient magical force won't let you set up a cannon here."
        const val TZHAAR = "The TzHaar won't be happy if you set up a cannon here."

        val AREA_RULES =
            listOf(
                "area.abyss" to CANT,
                "area.ancient_cavern" to "It's far too damp for you to set up a cannon here.",
                "area.the_ancient_prison" to ANCIENT_POWER,
                "area.assault_and_battery" to CANT,
                "area.catacombs_of_kourend" to ANCIENT_POWER,
                "area.castle_wars" to CANT,
                "area.castle_wars_underground" to CANT,
                "area.ice_mountain" to DWARVES,
                "area.forthos_dungeon" to "A mysterious presence won't let you set up a cannon here.",
                "area.fremennik_slayer_dungeon" to "The air is too dank for you to set up a cannon here.",
                "area.lighthouse_dungeon" to "The air is too dank for you to set up a cannon here.",
                "area.grand_exchange" to
                    "The Grand Exchange staff prefer not to have heavy artillery operated around their premises.",
                "area.jormungandprison" to "The ground is too soft for you to set up a cannon here.",
                "area.kraken_cove" to CANT,
                "area.molch_and_lizardman_temple" to
                    "This ancient structure is too unstable to handle the idea of a cannon firing at its walls.",
                "area.mor_ul_rek" to TZHAAR,
                "area.tzhaar" to TZHAAR,
                "area.inferno" to TZHAAR,
                "area.revenant_caves" to CANT,
                "area.revenants" to CANT,
                "area.tapoyauik" to ANCIENT_MAGIC,
                "area.the_guardian_of_tapoyauik" to ANCIENT_MAGIC,
                "area.cryptoftonali" to ANCIENT_MAGIC,
                "area.slayer_tower" to "A strange magical force won't let you set up a cannon here.",
                "area.smoke_dungeon" to "Dusty Aliv won't be happy if you set up a cannon here.",
                "area.welcome_to_the_theatre" to "The vampyres won't be happy if you set up a cannon here.",
                "area.warriors_guild" to CANT,
            )

        val ZONE_RULES =
            listOf(
                Zone(3005, 3504, 3035, 3527, "It is not permitted to set up a cannon this close to the Dwarf Black Guard."),
                Zone(2979, 3417, 3071, 3519, DWARVES),
                Zone(2944, 9740, 3071, 9855, DWARVES),
                Zone(2800, 3325, 2870, 3395, CANT),
                Zone(2368, 5056, 2559, 5183, TZHAAR),
                Zone(2624, 2560, 2687, 2687, CANT),
                Zone(2520, 3560, 2545, 3585, CANT),
                Zone(2435, 3080, 2448, 3100, CANT),
            )
    }
}
