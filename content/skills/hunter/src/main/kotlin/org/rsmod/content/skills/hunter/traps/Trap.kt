package org.rsmod.content.skills.hunter.traps

import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid

enum class TrapState {
    Set,
    Luring,
    Trapping,
    Full,
    Failing,
    Failed,
}

class Trap(
    val kind: TrapKind,
    val owner: PlayerUid,
    val ownerName: String,
    val coords: CoordGrid,
    var loc: LocInfo,
) {
    var state: TrapState = TrapState.Set
    var stateCycle: Int = 0
    var expireCycle: Int = 0
    var nextHuntCycle: Int = 0
    var smoked: Boolean = false

    var prey: TrapPrey? = null
    var target: Npc? = null
    var targetUid: NpcUid = NpcUid.NULL
    var lureDeadline: Int = 0

    val isCheckable: Boolean
        get() = state == TrapState.Full

    val isDismantlable: Boolean
        get() = state == TrapState.Set || state == TrapState.Failed
}
