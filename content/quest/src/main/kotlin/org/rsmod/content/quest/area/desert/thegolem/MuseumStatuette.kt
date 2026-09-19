package org.rsmod.content.quest.area.desert.thegolem

import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.thievingLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onOpNpc3
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.CABINET_KEY
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.CURATOR
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_ASKED_CURATOR
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STAGE_STATUETTE_PLACED
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.STATUETTE
import org.rsmod.content.quest.area.desert.thegolem.TheGolemQuest.Companion.THIEVING_REQ
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Uzer statuette in the Varrock Museum: the curator's pocket holds the key to its display case
 * on the first floor (`loc.vm_timeline_terracotta_statue`, a multiloc on
 * `varbit.golem_retrieved_statuette` that shows the case empty once the statuette is taken).
 */
class MuseumStatuette
@Inject
constructor(private val golem: TheGolemQuest, private val objRepo: ObjRepository) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc3(CURATOR) { pickpocketCurator(it.npc) }
        onOpLoc1(DISPLAY_CASE) { study() }
        onOpLoc2(DISPLAY_CASE) { openCase(usedKey = false) }
        onOpLocU(DISPLAY_CASE, CABINET_KEY) { openCase(usedKey = true) }
    }

    private suspend fun ProtectedAccess.pickpocketCurator(curator: Npc) {
        if (player.thievingLvl < THIEVING_REQ) {
            mes("You need to be at least level $THIEVING_REQ Thieving to pick the curator's pocket.")
            return
        }
        arriveDelay()
        faceEntitySquare(curator)
        anim(STEAL_SEQ)
        spam("You attempt to pick the curator's pocket.")
        delay(2)
        if (!knowsAboutStatuette() || golem.isComplete(player) || owns(CABINET_KEY)) {
            mes("The curator doesn't seem to have anything of value.")
            return
        }
        soundSynth(STEAL_SOUND)
        invAddOrDrop(objRepo, CABINET_KEY)
        objbox(CABINET_KEY, "You steal a tiny key.")
    }

    private suspend fun ProtectedAccess.study() {
        arriveDelay()
        if (player.golemStatuetteTaken) {
            mesbox("Recently this display was stolen and its whereabouts are unknown.")
            return
        }
        mesbox(
            "A worn stone statuette recovered from the ruined desert city of Uzer. The Uzerians " +
                "were famed as expert sculptors.",
        )
    }

    private suspend fun ProtectedAccess.openCase(usedKey: Boolean) {
        arriveDelay()
        if (!usedKey && CABINET_KEY !in inv) {
            mes("The cabinet is locked.")
            return
        }
        if (owns(STATUETTE) || golem.stage(player) >= STAGE_STATUETTE_PLACED) {
            mes("You have already taken the statuette.")
            return
        }
        anim(OPEN_SEQ)
        soundSynth(OPEN_SOUND)
        delay(1)
        player.golemStatuetteTaken = true
        invAddOrDrop(objRepo, STATUETTE)
        objbox(STATUETTE, "You open the cabinet and retrieve the statuette.")
    }

    private fun ProtectedAccess.knowsAboutStatuette(): Boolean =
        golem.stage(player) >= STAGE_ASKED_CURATOR

    private fun ProtectedAccess.owns(obj: String): Boolean = obj in inv || obj in bank

    private companion object {
        const val DISPLAY_CASE = "loc.vm_timeline_terracotta_statue"
        const val STEAL_SEQ = "seq.human_pickpocket"
        const val STEAL_SOUND = "synth.pick"
        const val OPEN_SEQ = "seq.human_pickuptable"
        const val OPEN_SOUND = "synth.cupboard_open"
    }
}
