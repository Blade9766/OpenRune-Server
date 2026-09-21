package org.rsmod.content.quest.area.feldip.bigchompy

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.chompyState by intVarp("varp.chompy_state")
internal var Player.chompyKills by intVarp("varp.chompy_kills")

internal var Player.chompyMadeArrows by boolVarBit("varbit.chompy_made_arrows")
internal var Player.rantzFlavour by intVarBit("varbit.chompy_rantz_flavour")
internal var Player.bugsFlavour by intVarBit("varbit.chompy_bugs_flavour")
internal var Player.fycieFlavour by intVarBit("varbit.chompy_fycie_flavour")
internal var Player.chompyBoughtFeathers by boolVarBit("varbit.chompy_bought_feathers")
internal var Player.chompyBoughtTools by boolVarBit("varbit.chompy_bought_tools")

/** Mirrors the quest stage so the fletching recipes can gate on a varbit. */
internal var Player.chompyQuestStarted by boolVarBit("varbit.chompy_quest_started")
