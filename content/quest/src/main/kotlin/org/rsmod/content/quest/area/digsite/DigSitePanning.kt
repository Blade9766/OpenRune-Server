package org.rsmod.content.quest.area.digsite

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpHeld2
import org.rsmod.api.script.onOpHeld4
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PANNING_TRAY
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PANNING_TRAY_GOLD
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.PANNING_TRAY_MUD
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.SPECIAL_CUP
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEA_VARBIT
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Panning the river at the south-east corner of the digsite.
 *
 * A pan fills the tray with mud; the tray then has to be picked through to see what came up with
 * it. The special cup the student in the orange shirt lost is in that table, and only while he is
 * still waiting for it.
 */
class DigSitePanning
@Inject
constructor(
    private val quest: TheDigSiteQuest,
    private val objRepo: ObjRepository,
    private val xpMods: XpModifiers,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(PANNING_POINT) { pan() }
        onOpLocU(PANNING_POINT, PANNING_TRAY) { pan() }
        onOpHeld4(PANNING_TRAY) { mes("The tray is empty. I should pan the river with it first.") }
        onOpHeld2(PANNING_TRAY_MUD) { searchTray(PANNING_TRAY_MUD) }
        onOpHeld2(PANNING_TRAY_GOLD) { searchTray(PANNING_TRAY_GOLD) }
    }

    private suspend fun ProtectedAccess.pan() {
        if (player.vars[TEA_VARBIT] == 0) {
            mes("The panning guide has not given you permission to pan here.")
            return
        }
        if (inv.contains(PANNING_TRAY_MUD) || inv.contains(PANNING_TRAY_GOLD)) {
            startDialogue {
                chatPlayer(neutral, "I already have a full panning tray; perhaps I should search it first.")
            }
            return
        }
        if (!inv.contains(PANNING_TRAY)) {
            mes("You need a panning tray to pan with.")
            return
        }

        arriveDelay()
        anim(PAN_SEQ)
        soundSynth(PAN_SOUND)
        delay(PAN_CYCLES)
        invReplace(inv, PANNING_TRAY, 1, PANNING_TRAY_MUD)
        statAdvance("stat.fishing", PAN_XP * xpMods.get(player, "stat.fishing"))
        statAdvance("stat.mining", PAN_XP * xpMods.get(player, "stat.mining"))
        mes("You scoop some mud into the tray.")
    }

    private suspend fun ProtectedAccess.searchTray(tray: String) {
        if (!inv.contains(tray)) {
            return
        }
        anim(SEARCH_SEQ)
        delay(1)
        val find = if (wantsCup()) CUP_FINDS.roll(random) else FINDS.roll(random)
        invReplace(inv, tray, 1, PANNING_TRAY)
        if (find == null) {
            mes("You wash the mud away but find nothing of value.")
            return
        }
        if (find.obj == SPECIAL_CUP) {
            invAddOrDrop(objRepo, SPECIAL_CUP, 1)
            objbox(SPECIAL_CUP, "You find a shiny cup covered in mud.")
            return
        }
        invAddOrDrop(objRepo, find.obj, random.of(find.count))
        objbox(find.obj, "You wash the mud away and find ${findName(find.obj)}.")
    }

    private fun ProtectedAccess.wantsCup(): Boolean =
        quest.isStarted(player) &&
            !player.inv.contains(SPECIAL_CUP) &&
            !quest.knowsAnswer(player, 1, DigSiteStudent.OrangeShirt)

    private companion object {
        const val PANNING_POINT = "loc.panning_point"

        const val PAN_SEQ = "seq.qip_digsite_panning_01"
        const val SEARCH_SEQ = "seq.qip_digsite_panning_02"
        const val PAN_SOUND = "synth.digsite_panning"
        const val PAN_CYCLES = 4
        const val PAN_XP = 2.0

        /** The table as the wiki lists it, out of forty. */
        val CUP_FINDS =
            digFinds {
                nothing(20)
                item(4, "obj.coins", 1..10)
                item(4, "obj.nuggets")
                item(3, "obj.oystershell")
                item(3, "obj.uncut_opal")
                item(3, "obj.uncut_jade")
                item(3, SPECIAL_CUP)
            }

        /** The same table once the cup has been found; its slot turns up nothing. */
        val FINDS =
            digFinds {
                nothing(23)
                item(4, "obj.coins", 1..10)
                item(4, "obj.nuggets")
                item(3, "obj.oystershell")
                item(3, "obj.uncut_opal")
                item(3, "obj.uncut_jade")
            }
    }
}
