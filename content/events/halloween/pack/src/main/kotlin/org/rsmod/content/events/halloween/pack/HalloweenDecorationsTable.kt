package org.rsmod.content.events.halloween.pack

import dev.openrune.definition.constants.ConstantProvider
import dev.openrune.definition.dbtables.dbTable
import dev.openrune.definition.util.VarType
import org.rsmod.map.CoordGrid

/**
 * Autumn and Halloween decorations put up in towns for October, one row per town. The pieces come
 * from `halloween_decorations.txt`, written by `DecorationPlacementGenerator` from the map
 * collision.
 */
object HalloweenDecorationsTable {
    const val PIECES = 0

    private const val RESOURCE = "/halloween_decorations.txt"

    fun decorations() =
        dbTable("dbtable.halloween_decorations", serverOnly = true) {
            column("pieces", PIECES, VarType.LOC, VarType.COORDGRID, VarType.INT, VarType.INT)

            for ((town, pieces) in readPieces()) {
                row("dbrow.halloween_decorations_$town") {
                    column(PIECES, pieces.flatten())
                }
            }
        }

    private fun readPieces(): Map<String, List<List<Any>>> {
        val text =
            checkNotNull(HalloweenDecorationsTable::class.java.getResource(RESOURCE)) { RESOURCE }
                .readText()
        return text
            .lineSequence()
            .map(String::trim)
            .filter { it.isNotEmpty() && !it.startsWith("#") }
            .map { it.split(' ') }
            .groupBy(
                keySelector = { it[0] },
                valueTransform = { (_, loc, x, z, shape, angle) ->
                    listOf(
                        ConstantProvider.getMapping(loc),
                        CoordGrid(x.toInt(), z.toInt(), 0).packed,
                        shape.toInt(),
                        angle.toInt(),
                    )
                },
            )
    }

    private operator fun <T> List<T>.component6(): T = this[5]
}
