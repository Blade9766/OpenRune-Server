package org.rsmod.content.quest.area.lighthouse.horrorfromthedeep

import org.rsmod.map.CoordGrid

/**
 * The Lighthouse exists three times in the map. The real building (region 39_56) and its basement
 * and caves (39_156) are what players see once the quest is over. Region 38_71 is a copy of the
 * building 64 tiles west and 960 north, wrecked, with the lighting mechanism broken; region
 * 39_72 is a copy of the basement and caves 5376 tiles south. The quest walks the player through
 * the wrecked building until the light is repaired, and through the basement copy until the
 * mother is dead.
 */
internal object LighthouseCoords {
    val DOOR_OUTSIDE = CoordGrid(2509, 3635, 0)
    val DOOR_INSIDE = CoordGrid(2509, 3636, 0)
    val WRECKED_DOOR_INSIDE = CoordGrid(2445, 4596, 0)

    val LADDER_LANDING = CoordGrid(2509, 3643, 0)
    val WRECKED_LADDER_LANDING = CoordGrid(2445, 4603, 0)

    /** Just north of the ladder up out of the basement (2519,z), in the quest copy. */
    val BASEMENT_LANDING = CoordGrid(2519, 4619, 0)

    /** On the basement side of the strange wall, where the mother's death puts the player. */
    val BELOW_WALL = CoordGrid(2515, 4625, 0)

    /** Foot of the ladder down into the caves (2515,z+1), in the quest copy. */
    val FOYER_LANDING = CoordGrid(2515, 4629, 0)

    /** North of the cave ladder (2515,z-1), in the quest copy. */
    val CAVE_LANDING = CoordGrid(2515, 4632, 0)

    val JOSSIK_INJURED = CoordGrid(2518, 4633, 0)
    val DAGANNOTH_EMERGE = CoordGrid(2518, 4640, 0)

    const val WRECKED_DX = -64
    const val WRECKED_DZ = 960
    const val CAVES_COPY_DZ = -5376

    fun inWreckedBuilding(coords: CoordGrid): Boolean =
        coords.x in 2432..2495 && coords.z in 4544..4607

    fun inCavesCopy(coords: CoordGrid): Boolean = coords.z in 4608..4671

    /** The real-world tile for [coords] if it lies in a quest copy. */
    fun toReal(coords: CoordGrid): CoordGrid =
        when {
            inWreckedBuilding(coords) -> coords.translate(-WRECKED_DX, -WRECKED_DZ)
            inCavesCopy(coords) -> coords.translate(0, -CAVES_COPY_DZ)
            else -> coords
        }

    fun toCavesCopy(real: CoordGrid): CoordGrid = real.translate(0, CAVES_COPY_DZ)
}
