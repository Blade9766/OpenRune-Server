package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.rsmod.game.entity.Player

/**
 * Draws a player's own light in the temple. The beams, the rising light in the pillars and the
 * doors are all multilocs on the player's vars, so nobody else sees another player's arrangement,
 * and they are always derived from the saved pillars, never read back.
 */
class TempleLights {
    fun trace(player: Player): LightResult = LightNetwork.trace(TemplePuzzleStore.load(player))

    fun refresh(player: Player): LightResult {
        val result = trace(player)
        for ((varbit, value) in displayValues(result)) {
            MourningsEndPart2Quest.setVarBit(player, varbit, value)
        }
        return result
    }

    companion object {
        /**
         * Every client varbit the light drives, for [result]: each beam path's colour, each
         * vertical beam's, each cross loc's (a beam one way, the other, rising, or two crossing)
         * and each door's open flag.
         */
        fun displayValues(result: LightResult): Map<String, Int> {
            val values = LinkedHashMap<String, Int>()
            for (link in TempleGeometry.links) {
                values[link.varbit] = display(result.links[link] ?: 0)
            }
            for ((key, varbit) in TempleGeometry.gapVarbits) {
                values[varbit] = display(result.gaps[key] ?: 0)
            }
            for ((varbit, place) in TempleGeometry.crosses) {
                values[varbit] = crossValue(result, varbit, place)
            }
            for (door in TempleGeometry.doors) {
                values[door.varbit] = if (result.isOpen(door)) 1 else 0
            }
            return values
        }

        private fun crossValue(result: LightResult, varbit: String, place: Pair<Int, Int>): Int {
            var northSouth = 0
            var eastWest = 0
            for (link in TempleGeometry.links) {
                val colour = result.links[link] ?: continue
                for (pass in link.crosses) {
                    if (pass.cross != varbit) continue
                    if (pass.axis == Axis.NORTH_SOUTH) northSouth = northSouth or colour else eastWest = eastWest or colour
                }
            }
            val (floor, column) = place
            val vertical = (result.gaps[floor to column] ?: 0) or (result.gaps[floor - 1 to column] ?: 0)
            val lit = listOf(northSouth, eastWest, vertical).count { it != 0 }
            return when {
                lit == 0 -> 0
                lit > 1 -> CROSSED + display(northSouth or eastWest or vertical)
                northSouth != 0 -> display(northSouth)
                eastWest != 0 -> EAST_WEST + display(eastWest)
                else -> VERTICAL + display(vertical)
            }
        }

        /** A beam multiloc shows one colour; where beams of several share a path the first is drawn. */
        private fun display(colours: Int): Int = LightColour.ofBits(colours).firstOrNull()?.displayIndex ?: 0

        /** Offsets into a cross loc's transforms: a north-south beam, east-west, rising, crossing. */
        private const val EAST_WEST = 7
        private const val VERTICAL = 14
        private const val CROSSED = 21
    }
}
