package org.rsmod.content.skills.construction.house

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

/**
 * What the owner has set at a throne room lever: off, challenge mode, or PvP challenge mode. Both
 * modes turn the dungeon's traps and guards on; PvP mode also lets everyone in the dungeon fight.
 */
internal enum class HouseMode {
    OFF,
    CHALLENGE,
    PVP,
}

internal var Player.houseMode: HouseMode
    get() = HouseMode.entries.getOrElse(houseModeId) { HouseMode.OFF }
    set(value) {
        houseModeId = value.ordinal
    }

private var Player.houseModeId by intVarBit("varbit.poh_house_mode")

internal val Player.challengeMode: Boolean
    get() = houseMode != HouseMode.OFF

internal val Player.pvpMode: Boolean
    get() = houseMode == HouseMode.PVP

/** Set while this plugin is showing the player an Attack op for a house dungeon in PvP mode. */
internal var Player.houseAttackOp by boolVarBit("varbit.poh_house_attack_op")
