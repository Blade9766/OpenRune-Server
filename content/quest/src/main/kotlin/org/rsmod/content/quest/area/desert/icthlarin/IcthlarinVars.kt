package org.rsmod.content.quest.area.desert.icthlarin

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.game.entity.Player

internal var Player.ilhJar by intVarBit("varbit.ics_little_jar_multi")
internal var Player.ilhInFlashback by boolVarBit("varbit.ics_stateofmind")
internal var Player.ilhKlenterVisible by boolVarBit("varbit.ics_specvis")
internal var Player.ilhMetSphinx by boolVarBit("varbit.ics_metsphinx")
internal var Player.ilhSphinxTookCat by boolVarBit("varbit.ics_sphinx_robbedcat")
internal var Player.ilhGaveToken by boolVarBit("varbit.ics_givensphinxstatue")
internal var Player.ilhMetEmbalmer by boolVarBit("varbit.ics_metembalmer")
internal var Player.ilhGaveSalt by boolVarBit("varbit.ics_gotsalt")
internal var Player.ilhGaveSap by boolVarBit("varbit.ics_gotsap")
internal var Player.ilhGaveLinen by boolVarBit("varbit.ics_gotlinen")
internal var Player.ilhMetCarpenter by boolVarBit("varbit.ics_metcarpenter")
internal var Player.ilhGaveLogs by boolVarBit("varbit.ics_little_carpenter_multi")
internal var Player.ilhSarcophagusLoot by intVarBit("varbit.ics_sarcophigi_gotstuffcounter")
internal var Player.ilhChestEmptied by boolVarBit("varbit.ics_chestempty")
internal var Player.ilhPuzzleResets by intVarBit("varbit.ics_little_tilecount")

internal val Player.ilhEmbalmerSupplied: Boolean
    get() = ilhGaveSalt && ilhGaveSap && ilhGaveLinen
