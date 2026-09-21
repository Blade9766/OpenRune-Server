package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.enumVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.SpadeDigging
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_EQUIPMENT
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The scenery of Lunar Isle the quest uses: Baba Yaga's walking chicken house, the sinks and well
 * where her vial is filled, the mine under the north-east of the island, the patch of blue flowers
 * hiding Selene's grandfather's ring, and the Astral altar where the Lunar spellbook is learnt.
 * The lamp oil still in Rimmington lives here too, since the emerald lantern needs its oil.
 */
class LunarIsle
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val travel: LunarTravel,
    private val digging: SpadeDigging,
    private val search: NpcSearch,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(BABA_YAGA_HOUSE) { enterHouse(it.npc) }
        onOpLoc1(BABA_YAGA_DOOR) { leaveHouse() }

        for (source in WATER_SOURCES) {
            onOpLocU(source, EMPTY_VIAL) { fillVial() }
        }

        onOpLoc1(MINE_LADDER_DOWN) { climb(LunarCoords.MINE_BOTTOM, up = false) }
        onOpLoc1(MINE_LADDER_UP) { climb(LunarCoords.MINE_TOP, up = true) }

        onOpLoc2(ASTRAL_ALTAR) { pray() }
        onOpLoc1(LECTERN) { study() }

        onOpLocU(LAMP_OIL_STILL, SWAMP_TAR) { refineTar() }
        for ((empty, filled) in LANTERNS_TO_FILL) {
            onOpLocU(LAMP_OIL_STILL, empty) { fillLantern(empty, filled) }
        }

        digging.register(LunarCoords.RING_SPOT) { digForRing() }
    }

    private suspend fun ProtectedAccess.enterHouse(house: Npc) {
        if (with(travel) { expelWithoutSeal(null) }) {
            return
        }
        faceEntitySquare(house)
        soundSynth(HOUSE_SOUND)
        mes("You climb the steps into the chicken-legged house.")
        delay(1)
        telejump(LunarCoords.BABA_YAGA_INSIDE, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.leaveHouse() {
        val house = search.find(LunarCoords.BABA_YAGA_OUTSIDE, BABA_YAGA_HOUSE, HOUSE_SEARCH_RADIUS, HuntVis.Off)
        val outside = house?.coords?.translate(HOUSE_DOOR_DX, HOUSE_DOOR_DZ) ?: LunarCoords.BABA_YAGA_OUTSIDE
        soundSynth(DOOR_SOUND)
        delay(1)
        telejump(outside, TeleportType.Exempt)
        if (player.coords != outside) {
            telejump(LunarCoords.BABA_YAGA_OUTSIDE, TeleportType.Exempt)
        }
    }

    private fun ProtectedAccess.fillVial() {
        anim(FILL_SEQ)
        soundSynth(FILL_SOUND)
        invReplace(inv, EMPTY_VIAL, 1, WATER_VIAL)
        mes("You fill the vial with water.")
    }

    private suspend fun ProtectedAccess.climb(dest: CoordGrid, up: Boolean) {
        anim(if (up) LADDER_UP_SEQ else LADDER_DOWN_SEQ)
        delay(1)
        telejump(dest, TeleportType.Exempt)
    }

    private suspend fun ProtectedAccess.pray() {
        if (!lunar.isComplete(player)) {
            mes("You have to earn the right to use the altar.")
            return
        }
        anim(PRAY_SEQ)
        spotanim(SPELLBOOK_SPOT)
        soundSynth(SPELLBOOK_SOUND)
        delay(2)
        if (player.spellbook == Spellbook.Lunars) {
            player.spellbook = Spellbook.Standard
            mes("Modern spells activated!")
        } else {
            player.spellbook = Spellbook.Lunars
            mes("Lunar spells activated!")
        }
    }

    private fun ProtectedAccess.study() {
        if (!lunar.isComplete(player)) {
            mes("The writing in the book is in the Moon Clan's own script. You can't make any sense of it.")
            return
        }
        mes("The book describes the Moon Clan's spells, drawing their power from astral runes and a knowledge of self.")
    }

    private fun ProtectedAccess.refineTar() {
        if (player.lampOilStill == STILL_FULL) {
            mes("The still is already full of lamp oil.")
            return
        }
        invDel(inv, SWAMP_TAR)
        anim(FILL_SEQ)
        soundSynth(STILL_SOUND)
        player.lampOilStill = STILL_FULL
        mes("You refine some swamp tar into lamp oil.")
    }

    private fun ProtectedAccess.fillLantern(empty: String, filled: String) {
        if (player.lampOilStill != STILL_FULL) {
            mes("The still is empty. You'll need to refine some swamp tar in it first.")
            return
        }
        anim(FILL_SEQ)
        invReplace(inv, empty, 1, filled)
        player.lampOilStill = STILL_EMPTY
        mes("You fill the lantern with lamp oil.")
    }

    private suspend fun ProtectedAccess.digForRing() {
        anim(DIG_SEQ)
        soundSynth(DIG_SOUND)
        delay(1)
        val ring = LunarPiece.Ring
        val owned = player.inv.contains(ring.obj) || ring.obj in player.worn || bank.contains(ring.obj)
        if (lunar.stage(player) != STAGE_EQUIPMENT || !player.lunarRingIntro || ring.given(player) || owned) {
            mes("You dig, but find nothing of interest.")
            return
        }
        if (inv.freeSpace() == 0) {
            mesbox("There appears to be something in the dirt, but you have no space to hold it.")
            return
        }
        invAdd(inv, ring.obj)
        objbox(ring.obj, "You've found a lunar ring!")
    }

    private companion object {
        private var Player.spellbook by enumVarBit<Spellbook>("varbit.spellbook")
        private var Player.lampOilStill by intVarBit("varbit.lamp_oil_still_state")

        const val BABA_YAGA_HOUSE = "npc.lunar_baba_yaga_house"
        const val BABA_YAGA_DOOR = "loc.lunar_moonclan_door_baba_yaga"
        const val HOUSE_SEARCH_RADIUS = 20
        const val HOUSE_DOOR_DX = 2
        const val HOUSE_DOOR_DZ = -1

        val WATER_SOURCES = listOf("loc.lunar_moonclan_sink", "loc.lunar_moonclan_sink_small", "loc.lunar_moonclan_well")
        const val EMPTY_VIAL = "obj.lunar_moonclan_liminal_vial_empty"
        const val WATER_VIAL = "obj.lunar_moonclan_liminal_vial_water"

        const val MINE_LADDER_DOWN = "loc.lunar_mine_slanty_ladder_down"
        const val MINE_LADDER_UP = "loc.lunar_mine_slanty_ladder_up"

        const val ASTRAL_ALTAR = "loc.astral_altar"
        const val LECTERN = "loc.lunar_moonclan_lectern"

        const val LAMP_OIL_STILL = "loc.lamp_oil_still"
        const val SWAMP_TAR = "obj.swamp_tar"
        const val STILL_EMPTY = 0
        const val STILL_FULL = 1
        val LANTERNS_TO_FILL =
            listOf(
                "obj.bullseye_lantern_empty_lunar_quest" to "obj.bullseye_lantern_unlit_lunar_quest",
                "obj.bullseye_lantern_empty" to "obj.bullseye_lantern_unlit",
            )

        const val HOUSE_SOUND = "synth.moon_babahouse"
        const val DOOR_SOUND = "synth.door_open"
        const val FILL_SEQ = "seq.human_pickuptable"
        const val FILL_SOUND = "synth.well_fill"
        const val STILL_SOUND = "synth.bubbling_vials"
        const val LADDER_UP_SEQ = "seq.human_reachforladder"
        const val LADDER_DOWN_SEQ = "seq.human_pickupfloor"
        const val PRAY_SEQ = "seq.human_pray"
        const val SPELLBOOK_SPOT = "spotanim.lunar_teleport_spotanim"
        const val SPELLBOOK_SOUND = "synth.lunar_change_spellbook"
        const val DIG_SEQ = "seq.human_dig"
        const val DIG_SOUND = "synth.digspade"
    }
}
