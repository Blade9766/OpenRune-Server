package org.rsmod.content.quest.area.desert.thegolem

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.golemSide by intVarBit("varbit.golem_b")
internal var Player.golemClay by intVarBit("varbit.golem_clay")
internal var Player.golemHeadOpen by boolVarBit("varbit.golem_head_open")
internal var Player.golemThroneEmptied by boolVarBit("varbit.golem_throne_gems")
internal var Player.golemStatuetteTaken by boolVarBit("varbit.golem_retrieved_statuette")
internal var Player.golemSeenUnderground by boolVarBit("varbit.golem_seen_underground")
