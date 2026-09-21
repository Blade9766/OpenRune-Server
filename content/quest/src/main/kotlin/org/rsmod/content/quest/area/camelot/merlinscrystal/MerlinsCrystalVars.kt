package org.rsmod.content.quest.area.camelot.merlinscrystal

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.merlinKnowsWords by boolVarBit("varbit.merlin_knows_words")
internal var Player.merlinCandlePromised by boolVarBit("varbit.merlin_candle_promised")
internal var Player.merlinExcaliburTest by intVarBit("varbit.merlin_excalibur_test")
internal var Player.merlinBeehiveFree by boolVarBit("varbit.merlin_beehive_free")
internal var Player.merlinCrateAtKeep by boolVarBit("varbit.merlin_crate_dest")
internal var Player.merlinInCrate by boolVarBit("varbit.merlin_in_crate")
