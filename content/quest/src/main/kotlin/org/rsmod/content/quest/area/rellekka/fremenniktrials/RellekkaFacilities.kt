package org.rsmod.content.quest.area.rellekka.fremenniktrials

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocCategoryEvents
import org.rsmod.api.player.events.interact.LocContentEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLoc2
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Rellekka keeps its workshops, the stile to the north and the gate to its mine for Fremenniks.
 * Anyone who has passed the trials is handed straight on to the usual crafting, smithing and
 * passage handlers.
 */
class RellekkaFacilities
@Inject
constructor(private val quest: FremennikTrialsQuest, private val passages: GenericPassageScript) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onOpLoc2(SPINNING_WHEEL) { useFacility("spinning wheel") { publishContent(it.loc, it.type, op = 2) } }
        onOpLoc1(POTTERY_WHEEL) { useFacility("pottery wheel") { publishContent(it.loc, it.type, op = 1) } }
        onOpLoc1(POTTERY_OVEN) { useFacility("pottery oven") { publishContent(it.loc, it.type, op = 1) } }
        onOpLoc2(FURNACE) { useFacility("furnace") { publish(LocCategoryEvents.Op2(it.loc, it.loc, it.type)) } }
        onOpLoc1(ANVIL) { useFacility("anvil") { publish(LocCategoryEvents.Op1(it.loc, it.loc, it.type)) } }
        onOpLoc1(STILE) { useFacility("stile") { with(passages) { passage(it.loc, it.type, 0) } } }
        for (gate in MINE_GATES) {
            onOpLoc1(gate) { mineGate(it.loc, it.type) }
        }
    }

    private suspend fun ProtectedAccess.useFacility(name: String, use: suspend ProtectedAccess.() -> Unit) {
        if (!quest.isComplete(player)) {
            arriveDelay()
            mes("Only Fremenniks may use this $name.")
            return
        }
        use()
    }

    private suspend fun ProtectedAccess.publishContent(loc: BoundLocInfo, type: ObjectServerType, op: Int) {
        val event =
            if (op == 1) {
                LocContentEvents.Op1(loc, loc, type, type.contentGroup)
            } else {
                LocContentEvents.Op2(loc, loc, type, type.contentGroup)
            }
        publish(event)
    }

    private suspend fun ProtectedAccess.mineGate(gate: BoundLocInfo, type: ObjectServerType) {
        if (!quest.isComplete(player)) {
            arriveDelay()
            mes("Only Fremenniks may pass this gate.")
            return
        }
        with(passages) { passage(gate, type, 0) }
    }

    private companion object {
        const val SPINNING_WHEEL = "loc.viking_spinningwheel"
        const val POTTERY_WHEEL = "loc.viking_potterywheel"
        const val POTTERY_OVEN = "loc.viking_potteryoven"
        const val FURNACE = "loc.viking_furnace"
        const val ANVIL = "loc.viking_anvil"
        const val STILE = "loc.viking_fence_stile"
        val MINE_GATES = listOf("loc.viking_fencegate_l", "loc.viking_fencegate_r")
    }
}
