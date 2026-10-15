package org.rsmod.content.quest.area.varrock.shieldofarrav

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.phoenixGang by intVarp("varp.phoenixgang")
internal var Player.blackArmGang by intVarp("varp.blackarmgang")

internal var Player.reldoMet by boolVarBit("varbit.reldo_met")
internal var Player.charlieMet by boolVarBit("varbit.soa_charlie_met")
internal var Player.charliePaid by boolVarBit("varbit.soa_charlie_paid")
internal var Player.charlieHalfPaid by boolVarBit("varbit.soa_charlie_hint")
internal var Player.weaponsmasterDead by boolVarBit("varbit.soa_weaponmaster_dead")
internal var Player.shieldGiven by intVarBit("varbit.soa_shield_given")
internal var Player.stravenTrampChat by boolVarBit("varbit.soa_straven_tramp_chat")
internal var Player.stravenShieldChat by boolVarBit("varbit.soa_straven_shield_chat")
internal var Player.phoenixSource by boolVarBit("varbit.soa_phoenix_source")
