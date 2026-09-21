package org.rsmod.api.player.output

import net.rsprot.protocol.game.outgoing.misc.client.HintArrow
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

/**
 * The yellow marker the client draws over a tile, npc or player. Only one exists at a time, so
 * setting a new one replaces whatever was showing.
 */
public object HintArrows {
    private const val DEFAULT_HEIGHT = 0
    private const val CENTER = 2

    public fun hintCoord(player: Player, coords: CoordGrid, height: Int = DEFAULT_HEIGHT) {
        val arrow = HintArrow.TileHintArrow(coords.x, coords.z, height, CENTER)
        player.client.write(HintArrow(arrow))
    }

    public fun hintNpc(player: Player, npc: Npc) {
        player.client.write(HintArrow(HintArrow.NpcHintArrow(npc.slotId)))
    }

    public fun hintStop(player: Player) {
        player.client.write(HintArrow(HintArrow.ResetHintArrow))
    }
}
