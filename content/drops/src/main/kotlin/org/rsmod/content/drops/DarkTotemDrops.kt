package org.rsmod.content.drops

import org.rsmod.game.entity.Player

private val TOTEM_PIECES = listOf("obj.cata_totem1", "obj.cata_totem2", "obj.cata_totem3")

/** The piece the player holds fewest of across inventory and bank; ties go base, middle, top. */
public fun Player.darkTotemPieceObj(): String =
    TOTEM_PIECES.minBy { piece -> heldCount(piece) }

private fun Player.heldCount(obj: String): Int =
    listOf("inv.inv", "inv.bank").sumOf { inv -> invMap[inv]?.count(obj) ?: 0 }
