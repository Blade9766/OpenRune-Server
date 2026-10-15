package org.rsmod.content.travel.magiccarpet

import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest
import org.rsmod.map.CoordGrid

/**
 * A carpet station: the rug merchant who runs it and the pad beside his rugs where carpets take
 * off and land. Uzer, Sophanem and Menaphos merchants are multi-npcs that only appear once the
 * matching quest has progressed far enough, so their ops are bound on the base npc.
 */
internal enum class CarpetStation(val merchant: String, val pad: CoordGrid, val place: String) {
    ShantayPass("npc.magic_carpet_seller1", CoordGrid(3310, 3108, 0), "the Shantay Pass"),
    Uzer("npc.magic_carpet_seller5", CoordGrid(3466, 3109, 0), "Uzer"),
    BedabinCamp("npc.magic_carpet_seller2", CoordGrid(3182, 3042, 0), "the Bedabin Camp"),
    PollnivneachNorth("npc.magic_carpet_seller4", CoordGrid(3350, 3002, 0), "Pollnivneach"),
    PollnivneachSouth("npc.magic_carpet_seller3", CoordGrid(3347, 2941, 0), "Pollnivneach"),
    Nardah("npc.magic_carpet_seller8", CoordGrid(3401, 2917, 0), "Nardah"),
    Sophanem("npc.magic_carpet_seller6", CoordGrid(3287, 2814, 0), "Sophanem"),
    Menaphos("npc.magic_carpet_seller7", CoordGrid(3243, 2814, 0), "Menaphos"),
}

/**
 * One flight between two stations. [via] lists the turning points between the two pads in the
 * [from] -> [to] direction; the return flight uses them reversed. [quest] must be complete to fly
 * this route in either direction.
 */
internal class CarpetRoute(
    val from: CarpetStation,
    val to: CarpetStation,
    val via: List<CoordGrid>,
    val quest: String? = null,
) {
    fun reversed(): CarpetRoute = CarpetRoute(to, from, via.reversed(), quest)
}

internal object CarpetRoutes {
    private val outbound =
        listOf(
            CarpetRoute(
                CarpetStation.ShantayPass,
                CarpetStation.Uzer,
                listOf(CoordGrid(3318, 3103, 0), CoordGrid(3455, 3109, 0)),
                TheGolemQuest.QUEST_KEY,
            ),
            CarpetRoute(
                CarpetStation.ShantayPass,
                CarpetStation.BedabinCamp,
                listOf(CoordGrid(3311, 3099, 0), CoordGrid(3192, 3042, 0)),
            ),
            CarpetRoute(
                CarpetStation.ShantayPass,
                CarpetStation.PollnivneachNorth,
                listOf(CoordGrid(3311, 3099, 0), CoordGrid(3350, 3010, 0)),
            ),
            CarpetRoute(
                CarpetStation.PollnivneachSouth,
                CarpetStation.Nardah,
                listOf(CoordGrid(3348, 2934, 0), CoordGrid(3395, 2911, 0)),
            ),
            CarpetRoute(
                CarpetStation.PollnivneachSouth,
                CarpetStation.Sophanem,
                listOf(CoordGrid(3347, 2934, 0), CoordGrid(3290, 2822, 0)),
                IcthlarinsLittleHelperQuest.QUEST_KEY,
            ),
            CarpetRoute(
                CarpetStation.PollnivneachSouth,
                CarpetStation.Menaphos,
                listOf(CoordGrid(3346, 2934, 0), CoordGrid(3245, 2822, 0)),
                IcthlarinsLittleHelperQuest.QUEST_KEY,
            ),
        )

    private val all = outbound + outbound.map(CarpetRoute::reversed)

    fun from(station: CarpetStation): List<CarpetRoute> = all.filter { it.from == station }

    /** Every tile of the flight from [route]'s pad to its destination pad, one tile apart. */
    fun flightPath(route: CarpetRoute): List<CoordGrid> {
        val points = listOf(route.from.pad) + route.via + route.to.pad
        return points.zipWithNext().flatMap { (a, b) -> line(a, b) }
    }

    private fun line(from: CoordGrid, to: CoordGrid): List<CoordGrid> {
        val dx = to.x - from.x
        val dz = to.z - from.z
        val steps = maxOf(kotlin.math.abs(dx), kotlin.math.abs(dz))
        return (1..steps).map { step ->
            CoordGrid(
                from.x + Math.round(dx * step / steps.toDouble()).toInt(),
                from.z + Math.round(dz * step / steps.toDouble()).toInt(),
                from.level,
            )
        }
    }
}
