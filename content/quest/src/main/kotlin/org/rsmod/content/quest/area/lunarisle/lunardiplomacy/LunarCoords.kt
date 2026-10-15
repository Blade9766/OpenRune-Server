package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import org.rsmod.map.CoordGrid

/**
 * The Lady Zay exists twice in the map: moored at the Pirates' Cove (region 34_59) and at the
 * Lunar Isle pier (regions 33_60/33_61), the second a copy of the first [LUNAR_SHIP_DX] tiles
 * west and [LUNAR_SHIP_DZ] north. Both decks keep their levels, so a crossing is a straight
 * translation between the two.
 */
internal object LunarCoords {
    /** On the jetty beside Lokar in Rellekka. */
    val RELLEKKA_DOCK = CoordGrid(2621, 3692, 0)

    /** At the foot of the ladder up to the Pirates' Cove pier. */
    val COVE_DOCK = CoordGrid(2213, 3794, 0)

    /** On the Lady Zay's main deck at the Pirates' Cove, by the gangplank. */
    val COVE_DECK = CoordGrid(2220, 3797, 2)

    /** On the Lady Zay's main deck at Lunar Isle, by the gangplank. */
    val LUNAR_DECK = CoordGrid(2138, 3900, 2)

    const val LUNAR_SHIP_DX = -84
    const val LUNAR_SHIP_DZ = 103

    /** Beside the ceremonial brazier in the lodge on the west side of the town. */
    val BRAZIER_SIDE = CoordGrid(2074, 3913, 0)

    /** Inside Baba Yaga's chicken-legged house. */
    val BABA_YAGA_INSIDE = CoordGrid(2451, 4646, 0)

    /** Where the house's door lets out when the house itself cannot be found. */
    val BABA_YAGA_OUTSIDE = CoordGrid(2085, 3928, 0)

    val MINE_BOTTOM = CoordGrid(2329, 10353, 2)
    val MINE_TOP = CoordGrid(2143, 3943, 0)

    /** The patch of blue flowers where Selene's grandfather buried his ring. */
    val RING_SPOT = CoordGrid(2078, 3863, 0)

    fun onCoveShip(coords: CoordGrid): Boolean = coords.x in 2208..2238 && coords.z in 3782..3824

    fun onLunarShip(coords: CoordGrid): Boolean = coords.x in 2130..2150 && coords.z in 3885..3922

    fun toLunarShip(coords: CoordGrid): CoordGrid = coords.translate(LUNAR_SHIP_DX, LUNAR_SHIP_DZ)

    fun toCoveShip(coords: CoordGrid): CoordGrid = coords.translate(-LUNAR_SHIP_DX, -LUNAR_SHIP_DZ)

    /** Lunar Isle itself, including the town, the pier and the Oneiromancer's altar. */
    fun onLunarIsle(coords: CoordGrid): Boolean =
        coords.x in 2048..2175 && coords.z in 3841..3968 && !onLunarShip(coords)

    fun inBabaYagaHouse(coords: CoordGrid): Boolean =
        coords.x in 2446..2456 && coords.z in 4642..4652

    fun inLunarMine(coords: CoordGrid): Boolean = coords.x in 2300..2370 && coords.z in 10310..10360
}
