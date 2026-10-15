package org.rsmod.content.quest.area.lumbridge.losttribe

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.lostTribeWitness by intVarBit("varbit.lost_tribe_contact")
internal var Player.lostTribeHam by intVarBit("varbit.lost_tribe_ham")
internal var Player.lostTribeBookmark by intVarBit("varbit.lost_tribe_bookmark")
internal var Player.lostTribeBroochReturned by boolVarBit("varbit.lost_tribe_returned_brooch")
internal var Player.lostTribeMistagDenied by boolVarBit("varbit.lost_tribe_mistag_denial")
internal var Player.lostTribeMazeWarned by boolVarBit("varbit.lost_tribe_maze_warned")
internal var Player.lostTribeHole2Dug by boolVarBit("varbit.lost_tribe_hole_2_dug")
internal var Player.lostTribeSigmundAccused by boolVarBit("varbit.lost_tribe_sigmund_accused")
