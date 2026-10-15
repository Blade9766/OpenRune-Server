package org.rsmod.content.skills.hunter.traps

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocInfo
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

enum class TrapState {
    Set,
    Luring,
    Trapping,
    Full,
    Failing,
    Failed,
}

/**
 * A placed trap. [coords] is the tile of the trap itself: the laid item, the young tree or the
 * boulder's anchor. Net traps also own [netCoords], the tile prey must reach, and switch to a
 * two-tile loc anchored at [wideAnchor] once something walks into the net. [baseLoc] is the map
 * loc a placed trap was built on and is restored when the trap goes away.
 */
class Trap(
    val kind: TrapKind,
    val owner: PlayerUid,
    val ownerName: String,
    val coords: CoordGrid,
    val angle: LocAngle,
    val shape: LocShape,
    val baseLoc: LocInfo?,
    val footprint: List<CoordGrid>,
    val netCoords: CoordGrid?,
) {
    var loc: LocInfo? = null
    var netLoc: LocInfo? = null
    var baseHidden: Boolean = false

    var state: TrapState = TrapState.Set
    var stateCycle: Int = 0
    var expireCycle: Int = 0
    var nextHuntCycle: Int = 0
    var smoked: Boolean = false

    var prey: TrapPrey? = null
    var target: Npc? = null
    var targetUid: NpcUid = NpcUid.NULL
    var lureDeadline: Int = 0

    val catchCoords: CoordGrid
        get() = netCoords ?: coords

    val wideAnchor: CoordGrid
        get() {
            val net = netCoords ?: return coords
            return CoordGrid(minOf(coords.x, net.x), minOf(coords.z, net.z), coords.level)
        }

    val tiles: Set<CoordGrid>
        get() = buildSet {
            addAll(footprint)
            netCoords?.let(::add)
        }

    val isCheckable: Boolean
        get() = state == TrapState.Full

    val isDismantlable: Boolean
        get() = state == TrapState.Set || state == TrapState.Failed
}
