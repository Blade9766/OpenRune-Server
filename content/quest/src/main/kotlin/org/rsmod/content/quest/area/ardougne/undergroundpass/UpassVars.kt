package org.rsmod.content.quest.area.ardougne.undergroundpass

import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.player.vars.intVarp
import org.rsmod.game.entity.Player

/* The cache's own flags, all on `varp.ibanmulti`. */

internal var Player.foundBridge by intVarBit("varbit.upass_found_bridge")
internal var Player.venomOnDoll by intVarBit("varbit.upass_venom_on_doll")
internal var Player.doveOnDoll by intVarBit("varbit.upass_dove_on_doll")
internal var Player.ashesOnDoll by intVarBit("varbit.upass_ashes_on_doll")
internal var Player.shadowOnDoll by intVarBit("varbit.upass_shadow_on_doll")
internal var Player.gaveCat by intVarBit("varbit.upass_gavecat")
internal var Player.seenTemple by intVarBit("varbit.upass_seen_temple")
internal var Player.lathasMet by intVarBit("varbit.upass_lathas_met")
internal var Player.crateFood by intVarBit("varbit.upass_crate_food")
internal var Player.paladinFood by intVarBit("varbit.upass_paladin_food")
internal var Player.readJournal by intVarBit("varbit.upass_read_journal")
internal var Player.readWell by intVarBit("varbit.upass_read_well")
internal var Player.readIbanBook by intVarBit("varbit.upass_read_iban_book")
internal var Player.brewOnTomb by intVarBit("varbit.upass_brew_tomb")
internal var Player.koftikChat by intVarBit("varbit.upass_koftik_chat")
internal var Player.caveUnicorn by intVarBit("varbit.upass_cave_unicorn")
internal var Player.dwarfFood by intVarBit("varbit.upass_dwarf_food")

private val ORB_TAKEN_VARBITS =
    arrayOf(
        "varbit.upass_caveorb_1",
        "varbit.upass_caveorb_2",
        "varbit.upass_caveorb_3",
        "varbit.upass_caveorb_4",
    )

private val BADGE_VARBITS =
    arrayOf(
        "varbit.upass_paladinbadge_1",
        "varbit.upass_paladinbadge_2",
        "varbit.upass_paladinbadge_3",
    )

internal fun orbTakenVarbit(index: Int): String = ORB_TAKEN_VARBITS[index]

internal fun paladinBadgeVarbit(index: Int): String = BADGE_VARBITS[index]

internal val Player.orbsTaken: Int
    get() = ORB_TAKEN_VARBITS.count { vars[it] == 1 }

internal val Player.badgesHeld: Int
    get() = BADGE_VARBITS.count { vars[it] == 1 }

/* The quest's own flags, on `varp.upass_state`, plus the grid seed on `varp.upass_grid`. */

internal var Player.upassState by intVarp("varp.upass_state")
internal var Player.gridSeed by intVarp("varp.upass_grid")

internal var Player.clothTaken by boolVarBit("varbit.upass_cloth_taken")
internal var Player.plankTaken by boolVarBit("varbit.upass_plank_taken")
internal var Player.railingTaken by boolVarBit("varbit.upass_railing_taken")
internal var Player.mudDug by boolVarBit("varbit.upass_mud_dug")
internal var Player.ropeOnSwing by boolVarBit("varbit.upass_rope_on_swing")
internal var Player.portcullisOpen by boolVarBit("varbit.upass_portcullis_open")
internal var Player.gauntletsGiven by boolVarBit("varbit.upass_gauntlets_given")
internal var Player.dollTaken by boolVarBit("varbit.upass_doll_taken")
internal var Player.hornInWell by boolVarBit("varbit.upass_horn_in_well")
internal var Player.badgesInWell by intVarBit("varbit.upass_badges_given")
internal var Player.shadowChestOpen by boolVarBit("varbit.upass_shadow_chest")
internal var Player.mazeGatePicked by boolVarBit("varbit.upass_maze_gate")
internal var Player.niloofTold by boolVarBit("varbit.upass_niloof_told")
internal var Player.klankTold by boolVarBit("varbit.upass_klank_told")
internal var Player.kamenTold by boolVarBit("varbit.upass_kamen_told")
internal var Player.doorsOpen by boolVarBit("varbit.upass_doors_open")
internal var Player.templeEntered by boolVarBit("varbit.upass_temple_entered")

private val ORB_BURNT_VARBITS =
    arrayOf(
        "varbit.upass_orb_burnt_1",
        "varbit.upass_orb_burnt_2",
        "varbit.upass_orb_burnt_3",
        "varbit.upass_orb_burnt_4",
    )

private val AMULET_VARBITS =
    arrayOf(
        "varbit.upass_amulet_doomion",
        "varbit.upass_amulet_othainian",
        "varbit.upass_amulet_holthion",
    )

internal fun orbBurntVarbit(index: Int): String = ORB_BURNT_VARBITS[index]

internal fun amuletVarbit(index: Int): String = AMULET_VARBITS[index]

internal val Player.orbsBurnt: Int
    get() = ORB_BURNT_VARBITS.count { vars[it] == 1 }

internal val Player.amuletsHeld: Int
    get() = AMULET_VARBITS.count { vars[it] == 1 }
