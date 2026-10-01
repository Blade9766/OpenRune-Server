package org.rsmod.content.quest.area.camelot.holygrail

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.game.entity.Player

internal var Player.napkinGiven by boolVarBit("varbit.holygrail_napkin")
internal var Player.whistlesFound by boolVarBit("varbit.holygrail_whistles_found")
internal var Player.titanDefeated by boolVarBit("varbit.holygrail_titan_defeated")
internal var Player.castleEntered by boolVarBit("varbit.holygrail_castle_entered")
internal var Player.heardHealth by boolVarBit("varbit.holygrail_heard_health")
internal var Player.heardSon by boolVarBit("varbit.holygrail_heard_son")
internal var Player.percivalFound by boolVarBit("varbit.holygrail_percival_found")
