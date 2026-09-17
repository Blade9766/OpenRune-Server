package org.rsmod.content.quest.area.digsite

import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.LEATHER_BOOTS
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.LEATHER_GLOVES
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ROCK_PICK
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.SPECIMEN_BRUSH
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.SPECIMEN_JAR
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_LEVEL3
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.STAGE_TALISMAN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TALISMAN
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEDDY
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TROWEL
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Digging the site: the marked soil, the specimen tray by the tents and the bush the student in
 * the purple skirt dropped her teddy behind.
 *
 * Which dig a patch of soil belongs to comes from where it is ([DigSiteCoords.digArea]), because
 * the same three soil locs are used across the whole site. Each dig has its own tool, its own
 * exam requirement and its own table of finds; only the level 3 digs turn up the ancient talisman,
 * and only while the quest is waiting for it.
 */
class DigSiteDigging
@Inject
constructor(
    private val quest: TheDigSiteQuest,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (soil in SOIL) {
            onOpLocU(soil, TROWEL) { dig(DigTool.Trowel) }
            onOpLocU(soil, ROCK_PICK) { dig(DigTool.RockPick) }
            onOpLocU(soil, SPECIMEN_BRUSH) {
                mes("A workman laughs at you. The brush is for cleaning specimens, not the dirt.")
            }
        }

        onOpLoc2(SPECIMEN_TRAY) { searchSpecimenTray() }

        onOpLoc1(TEDDY_BUSH) { searchTeddyBush(it.loc.coords) }
        onOpLoc1(BUSH) {
            arriveDelay()
            mes("You search the bush but find nothing.")
        }
    }

    private suspend fun ProtectedAccess.dig(tool: DigTool) {
        val area = DigSiteCoords.digArea(coords)
        if (area == null) {
            mes("The ground here has not been marked out for digging.")
            return
        }
        if (tool != area.tool()) {
            mes(area.wrongTool(tool))
            return
        }
        if (quest.examLevel(player) < area.examLevel) {
            mes(
                "You need to have passed the Earth Sciences level ${area.examLevel} exam before " +
                    "you may dig here.",
            )
            return
        }
        val missing = missingKit(area)
        if (missing != null) {
            mes(missing)
            return
        }

        arriveDelay()
        anim(tool.seq)
        soundSynth(tool.sound)
        delay(DIG_CYCLES)
        statAdvance("stat.mining", area.xp * xpMods.get(player, "stat.mining"))

        if (area == DigSiteArea.Level3 && rollTalisman()) {
            invAdd(inv, TALISMAN)
            quest.advanceTo(this, STAGE_TALISMAN)
            objbox(TALISMAN, "You find a strange talisman.")
            return
        }
        val find = area.finds.roll(random)
        if (find == null) {
            mes("You find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, find.obj, random.of(find.count))
        objbox(find.obj, "You find ${findName(find.obj)}.")
    }

    /**
     * The talisman is the level 3 table's rarest slot and only while the quest wants it; after
     * that the slot pays coins like every other digger's does.
     */
    private fun ProtectedAccess.rollTalisman(): Boolean {
        if (quest.stage(player) !in STAGE_LEVEL3..STAGE_TALISMAN || carriesOrBanks(TALISMAN)) {
            return false
        }
        return random.of(maxExclusive = TALISMAN_ONE_IN) == 0
    }

    private fun ProtectedAccess.missingKit(area: DigSiteArea): String? =
        when (area) {
            DigSiteArea.Level1 ->
                when {
                    !player.worn.contains(LEATHER_GLOVES) ->
                        "A workman stops you: leather gloves must be worn on this dig."
                    !player.worn.contains(LEATHER_BOOTS) ->
                        "A workman stops you: leather boots must be worn on this dig."
                    else -> null
                }
            DigSiteArea.Level3 ->
                when {
                    !player.inv.contains(SPECIMEN_BRUSH) ->
                        "You need a specimen brush with you to work this dig."
                    !player.inv.contains(SPECIMEN_JAR) ->
                        "You need a specimen jar with you to work this dig."
                    else -> null
                }
            else -> null
        }

    /** The tray of finds by the tents; nothing may be taken out of it without a jar to put it in. */
    private suspend fun ProtectedAccess.searchSpecimenTray() {
        arriveDelay()
        if (!inv.contains(SPECIMEN_JAR)) {
            mes("A workman stops you. 'Where's your specimen jar? Nothing leaves that tray without one.'")
            return
        }
        anim(TRAY_SEQ)
        delay(DIG_CYCLES)
        statAdvance("stat.mining", TRAY_XP * xpMods.get(player, "stat.mining"))
        val find = SPECIMEN_TRAY_FINDS.roll(random)
        if (find == null) {
            mes("You sift through the tray but find nothing of interest.")
            return
        }
        invAddOrDrop(objRepo, find.obj, random.of(find.count))
        objbox(find.obj, "You find ${findName(find.obj)} in the tray.")
    }

    private suspend fun ProtectedAccess.searchTeddyBush(bush: CoordGrid) {
        arriveDelay()
        val wanted =
            quest.isStarted(player) &&
                !player.inv.contains(TEDDY) &&
                !quest.knowsAnswer(player, 1, DigSiteStudent.PurpleSkirt)
        if (!wanted || bush != DigSiteCoords.TEDDY_BUSH) {
            mes("You search the bush but find nothing.")
            return
        }
        startDialogue { chatPlayer(happy, "Hey, something has been dropped here...") }
        invAdd(inv, TEDDY)
        objbox(TEDDY, "You find... something")
    }

    private fun DigSiteArea.tool(): DigTool =
        if (this == DigSiteArea.Level2) DigTool.RockPick else DigTool.Trowel

    private fun DigSiteArea.wrongTool(used: DigTool): String =
        if (used == DigTool.Trowel) {
            "A workman shakes his head. 'Rock picks on the level 2 digs, not trowels.'"
        } else {
            "A workman shakes his head. 'Rock picks are for the level 2 digs only.'"
        }

    private val DigSiteArea.xp: Double
        get() =
            when (this) {
                DigSiteArea.Training -> 5.0
                DigSiteArea.Level1 -> 6.0
                DigSiteArea.Level2 -> 7.0
                DigSiteArea.Level3 -> 8.0
            }

    private val DigSiteArea.finds: DigFindTable
        get() =
            when (this) {
                DigSiteArea.Training -> TRAINING_FINDS
                DigSiteArea.Level1 -> LEVEL1_FINDS
                DigSiteArea.Level2 -> LEVEL2_FINDS
                DigSiteArea.Level3 -> LEVEL3_FINDS
            }

    private enum class DigTool(val seq: String, val sound: String) {
        Trowel("seq.farming_trowel_dig", "synth.digsite_dig_trowel"),
        RockPick("seq.qip_digisite_rockpick_anim", "synth.digsite_dig_pick"),
    }

    private companion object {
        val SOIL = listOf("loc.digdugupsoil1", "loc.digdugupsoil2", "loc.digdugupsoil3")
        const val SPECIMEN_TRAY = "loc.specimen_tray"
        const val TEDDY_BUSH = "loc.digsitebushsample"
        const val BUSH = "loc.digsitebush"

        const val TRAY_SEQ = "seq.human_pickuptable"
        const val DIG_CYCLES = 3
        const val TRAY_XP = 0.5
        const val TALISMAN_ONE_IN = 20

        val TRAINING_FINDS =
            digFinds {
                nothing(8)
                item(1, "obj.charcoal")
                item(1, "obj.coins", 1..10)
                item(1, "obj.digsitearrow")
                item(1, "obj.cracked_sample")
                item(1, "obj.digsitevase")
            }

        val LEVEL1_FINDS =
            digFinds {
                nothing(2)
                item(1, "obj.digsitebuttons")
                item(1, "obj.digsitevase")
                item(1, "obj.copper_ore")
                item(1, "obj.leather_boots")
                item(1, "obj.opal")
                item(1, "obj.old_tooth")
                item(1, "obj.rottenapples")
                item(1, "obj.digsiteglass")
                item(1, "obj.digsitesword")
                item(1, "obj.bones")
            }

        val LEVEL2_FINDS =
            digFinds {
                nothing(3)
                item(2, "obj.bones")
                item(1, "obj.digsitearmour1")
                item(1, "obj.leather_boots")
                item(1, "obj.bowl_empty")
                item(1, "obj.digsitestaff")
                item(1, "obj.digsitearmour2")
                item(1, "obj.uncut_jade")
                item(1, "obj.digsiteglass")
                item(1, "obj.jug_empty")
                item(1, "obj.pot_empty")
                item(1, "obj.clay")
                item(1, "obj.uncut_opal")
            }

        val LEVEL3_FINDS =
            digFinds {
                nothing(1)
                item(1, "obj.digsitebuckle")
                item(1, "obj.black_med_helm")
                item(1, "obj.bones")
                item(1, "obj.digsitearmour2")
                item(1, "obj.digsitearrow")
                item(1, "obj.digsitestaff")
                item(1, "obj.bronze_spear")
                item(1, "obj.digsitebuttons")
                item(1, "obj.digsitepottery")
                item(1, "obj.clay")
                item(3, "obj.coins", 10..10)
                item(1, "obj.digsitearmour1")
                item(1, "obj.iron_knife")
                item(1, "obj.leather_boots")
                item(1, "obj.needle")
                item(1, "obj.old_tooth")
                item(1, "obj.piedish")
            }

        val SPECIMEN_TRAY_FINDS =
            digFinds {
                nothing(2)
                item(1, "obj.coins", 1..10)
                item(1, "obj.iron_dagger")
                item(1, "obj.charcoal")
                item(2, "obj.bones")
                item(1, "obj.digsitearrow")
                item(1, "obj.digsiteglass")
                item(1, "obj.digsitepottery")
                item(1, "obj.cracked_sample")
            }
    }
}
