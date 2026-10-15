package org.rsmod.content.quest.area.seers.murdermystery

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

internal var Player.murderSuspect by intVarp("varp.murdersus")
internal var Player.murderPoisonProgress by intVarBit("varbit.murder_poison_progress")
internal var Player.murderFoundThread by boolVarBit("varbit.murder_found_thread")
internal var Player.murderFoundPrints by boolVarBit("varbit.murder_found_prints")

internal val Player.murderer: Suspect?
    get() = Suspect.byId(murderSuspect)
