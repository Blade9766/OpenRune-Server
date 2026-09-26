package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid

internal var Player.dttdTourDuke by boolVarBit("varbit.dttd_tour_duke")
internal var Player.dttdTourPriest by boolVarBit("varbit.dttd_tour_priest")
internal var Player.dttdTourGoblins by boolVarBit("varbit.dttd_tour_goblins")
internal var Player.dttdTourCitizens by boolVarBit("varbit.dttd_tour_citizens")
internal var Player.dttdTourSun by boolVarBit("varbit.dttd_tour_sun")
internal var Player.dttdTourShop by boolVarBit("varbit.dttd_tour_shop")
internal var Player.dttdEdgeWarned by boolVarBit("varbit.dttd_tour_edge_warned")
internal var Player.dttdHamCivilians by boolVarBit("varbit.dttd_tour_ham_civilians")
internal var Player.dttdHamDeacon by boolVarBit("varbit.dttd_tour_ham_deacon")
internal var Player.dttdHamJohanhus by boolVarBit("varbit.dttd_tour_ham_johanhus")
internal var Player.dttdZanikInCellar by boolVarBit("varbit.dttd_zanik_in_cellar")
internal var Player.dttdHamTrapdoor by intVarBit("varbit.dttd_ham_trapdoor_state")
internal var Player.dttdZanikCorpse by boolVarBit("varbit.dttd_zanik_corpse")
internal var Player.dttdCollectingTears by boolVarBit("varbit.dttd_collecting_tears")
internal var Player.dttdMillGuardsDead by intVarBit("varbit.dttd_mill_guards_dead")
internal var Player.dttdTearsCollected by intVarBit("varbit.tog_tears_collected")

internal fun CoordGrid.isWithinDistance(other: CoordGrid, distance: Int): Boolean =
    level == other.level && chebyshevDistance(other) <= distance
