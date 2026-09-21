package org.rsmod.content.quest.area.lunarisle.lunardiplomacy

import jakarta.inject.Inject
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_AT_LUNAR_ISLE
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_ENTERED_TOWN
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_FOUND_CULPRIT
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_JINX_LIFTED
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.SYMBOL_STAGE_STEP
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Lady Zay and her piers, at the Pirates' Cove and Lunar Isle alike: the ladders and
 * staircases between the ship's four decks (all of which the cache only labels "Climb"), the tar
 * barrels the cabin boy mentions, and the five jinx symbols he drew around the ship, which only
 * show in the light of the emerald lantern.
 *
 * Stepping down onto Lunar Isle for the first time is where the Moon Clan welcome the player.
 */
class LadyZay
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val stairs: StairNavigator,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (loc in CLIMB_UP) {
            onOpLoc1(loc) { climb(it.loc, up = true) }
        }
        for (loc in CLIMB_DOWN) {
            onOpLoc1(loc) { climb(it.loc, up = false) }
        }
        onOpLoc1(TAR_BARREL) { takeTar() }
        for (barrel in APPLE_BARRELS) {
            onOpLoc1(barrel) { mes("The barrel is full of rotting fruit. You decide to leave it alone.") }
        }
        for (symbol in ShipSymbol.entries) {
            onOpLocU(symbol.loc, LIT_LANTERN) { inspect(symbol) }
            onOpLocU(symbol.loc, UNLIT_LANTERN) { mes("You'll need to light the lantern first.") }
        }
    }

    private suspend fun ProtectedAccess.climb(loc: BoundLocInfo, up: Boolean) {
        val dest = stairs.destination(loc, coords, up)
        if (dest == null) {
            mes(if (up) "You cannot see a way up from here." else "You cannot see a way down from here.")
            return
        }
        anim(if (up) LADDER_UP_SEQ else LADDER_DOWN_SEQ)
        delay(1)
        telejump(dest, TeleportType.Exempt)
        if (!up && dest.level == 0 && LunarCoords.onLunarIsle(dest)) {
            arriveOnLunarIsle()
        }
    }

    private fun ProtectedAccess.arriveOnLunarIsle() {
        if (lunar.stage(player) != STAGE_AT_LUNAR_ISLE) {
            return
        }
        lunar.advanceTo(this, STAGE_ENTERED_TOWN)
        player.midiJingle(WELCOME_JINGLE)
        mes("You set foot on Lunar Isle, home of the Moon Clan.")
    }

    private fun ProtectedAccess.takeTar() {
        if (inv.freeSpace() == 0) {
            mes("You don't have enough room to carry any tar.")
            return
        }
        anim(TAKE_SEQ)
        invAdd(inv, SWAMP_TAR)
        mes("You take some swamp tar from the barrel.")
    }

    private suspend fun ProtectedAccess.inspect(symbol: ShipSymbol) {
        val stage = lunar.stage(player)
        if (stage !in STAGE_FOUND_CULPRIT until STAGE_JINX_LIFTED || symbol.isRubbed(player)) {
            anim(LOOK_LOW_SEQ)
            mes("Nope, nothing here.")
            return
        }
        anim(if (symbol == ShipSymbol.Wallchart || symbol == ShipSymbol.Cannon) LOOK_HIGH_SEQ else LOOK_LOW_SEQ)
        delay(2)
        if (symbol.state(player) == ShipSymbol.HIDDEN) {
            LunarDiplomacyQuest.setVarBit(player, symbol.varbit, ShipSymbol.REVEALED)
        }
        startDialogue {
            chatPlayer(happy, "Ah-ha! I've found one!")
            val rub = choice2("Rub away!", true, "I'm no cleaner!", false, title = "Rub out the symbol?")
            if (!rub) {
                return@startDialogue
            }
            access.anim(RUB_SEQ)
            access.soundSynth(RUB_SOUND)
            LunarDiplomacyQuest.setVarBit(player, symbol.varbit, ShipSymbol.RUBBED)
            val rubbed = LunarDiplomacyQuest.symbolsRubbed(player)
            lunar.advanceTo(access, STAGE_FOUND_CULPRIT + rubbed * SYMBOL_STAGE_STEP)
            val remark =
                when (rubbed) {
                    1 -> "One down, four more to go!"
                    2 -> "Two down, three more to go!"
                    3 -> "Three down, only two more to go!"
                    4 -> "Four down, only one more to go!"
                    else -> "That's the last of them. Hopefully the jinx has gone with them."
                }
            chatPlayer(happy, remark)
        }
    }

    private companion object {
        val CLIMB_UP =
            listOf(
                "loc.quest_lunar_galleon_pier_stairs_base",
                "loc.quest_lunar_galleon_pier_stairs_base_lower",
                "loc.quest_lunar_galleon_stairs_base",
                "loc.quest_lunar_galleon_stairs_base_level1",
            )
        val CLIMB_DOWN =
            listOf(
                "loc.quest_lunar_galleon_pier_stairs_top",
                "loc.quest_lunar_galleon_pier_stairs_top_lower",
                "loc.quest_lunar_galleon_stairs_top",
                "loc.quest_lunar_galleon_stairs_top_level1",
            )

        const val TAR_BARREL = "loc.lunar_pirate_small_tar_barrel"
        val APPLE_BARRELS = listOf("loc.lunar_pirate_apple_barrel_apples", "loc.lunar_pirate_apple_barrel_brownstuff")

        const val LIT_LANTERN = "obj.bullseye_lantern_lit_lunar_quest"
        const val UNLIT_LANTERN = "obj.bullseye_lantern_unlit_lunar_quest"
        const val SWAMP_TAR = "obj.swamp_tar"

        const val LADDER_UP_SEQ = "seq.human_reachforladder"
        const val LADDER_DOWN_SEQ = "seq.human_pickupfloor"
        const val TAKE_SEQ = "seq.human_pickuptable"
        const val LOOK_HIGH_SEQ = "seq.quest_lunar_look_high_lantern"
        const val LOOK_LOW_SEQ = "seq.quest_lunar_look_low_lantern"
        const val RUB_SEQ = "seq.human_pickuptable"
        const val RUB_SOUND = "synth.moon_rubdown"

        /** "Welcome to the Moon Clan", by its cache group. */
        const val WELCOME_JINGLE = 119
    }
}
