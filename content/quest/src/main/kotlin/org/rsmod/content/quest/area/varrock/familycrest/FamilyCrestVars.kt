package org.rsmod.content.quest.area.varrock.familycrest

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.calebAsked by boolVarBit("varbit.famcrest_caleb_asked")
internal var Player.calebDone by boolVarBit("varbit.famcrest_caleb_done")
internal var Player.knowsBrothers by boolVarBit("varbit.famcrest_knows_brothers")
internal var Player.avanFound by boolVarBit("varbit.famcrest_avan_found")
internal var Player.avanAsked by boolVarBit("varbit.famcrest_avan_asked")
internal var Player.bootTold by boolVarBit("varbit.famcrest_boot_told")
internal var Player.avanDone by boolVarBit("varbit.famcrest_avan_done")
internal var Player.johnathonCured by boolVarBit("varbit.famcrest_john_cured")
internal var Player.johnathonAsked by boolVarBit("varbit.famcrest_john_asked")
internal var Player.johnathonDone by boolVarBit("varbit.famcrest_john_done")
internal var Player.perfectGoldMined by intVarBit("varbit.famcrest_gold_mined")
internal var Player.witchavenPuzzleSolved by boolVarBit("varbit.famcrest_puzzle_solved")
internal var Player.usedFreeEnchant by boolVarBit("varbit.famcrest_gauntlets_free")

/** Which enchantment the reward gauntlets carry, as a [Gauntlets] ordinal. */
internal var Player.gauntletsKind by intVarBit("varbit.famcrest_gauntlets_kind")

/** Bitmask of the four blast elements that have landed on Chronozon; cleared on logout. */
internal var Player.chronozonWeakness by intVarBit("varbit.famcrest_chronozon_weaken")
