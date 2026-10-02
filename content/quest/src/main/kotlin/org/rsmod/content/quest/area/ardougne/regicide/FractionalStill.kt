package org.rsmod.content.quest.area.ardougne.regicide

import dev.openrune.definition.type.widget.IfEvent
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invAddOrDrop
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onIfClose
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_COAL_TAR
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.BARREL_OF_NAPHTHA
import org.rsmod.content.quest.area.ardougne.regicide.RegicideQuest.Companion.COAL
import org.rsmod.content.quest.area.ardougne.regicide.StillSimulation.Outcome
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The fractionalising still outside the Chemist's house in Rimmington, which draws naphtha off
 * coal tar once the Chemist has explained it.
 *
 * Using a barrel of coal tar on it pours the tar in (`varbit.regicide_had_tar`) and opens the
 * still's own interface; "Operate" reopens it while there is tar inside. Each player has one
 * session at most, run by a soft timer every tick through [StillSimulation] while the interface
 * is open. Buttons only turn the valves or add one ordinary (never noted) coal each, and only
 * while that player's session is running. The bar (`varp.regicide_still_total`) is kept when the
 * interface closes or the player logs out, and the gauges start cold again on the next visit.
 * Filling the bar empties the still and hands over the barrel of naphtha in the same tick, once;
 * a full pack drops it at the player's feet. The interface stays open on the full bar until the
 * player closes it. The still keeps working after the quest, for anyone wanting more naphtha.
 */
@Singleton
class FractionalStill
@Inject
constructor(private val objRepo: ObjRepository) : PluginScript() {
    private val sessions = HashMap<PlayerUid, StillSimulation>()

    override fun ScriptContext.startup() {
        onOpLocU(STILL, BARREL_OF_COAL_TAR) { pourTar() }
        onOpLoc1(STILL) { operate() }
        onIfModalButton(ADD_COAL) { addCoal() }
        onIfModalButton(PRESSURE_DOWN) { session(player)?.turnPressureValve(-1)?.also { sync(player) } }
        onIfModalButton(PRESSURE_UP) { session(player)?.turnPressureValve(1)?.also { sync(player) } }
        onIfModalButton(TAR_DOWN) { session(player)?.turnTarRegulator(-1)?.also { sync(player) } }
        onIfModalButton(TAR_UP) { session(player)?.turnTarRegulator(1)?.also { sync(player) } }
        onIfClose(INTERFACE) { stop(player) }
        onPlayerSoftTimer(TIMER) { tick(player) }
        onPlayerLogout { sessions.remove(player.uid) }
    }

    fun session(player: Player): StillSimulation? = sessions[player.uid]

    private fun allowed(player: Player): Boolean = player.chemistChat == 1

    private suspend fun ProtectedAccess.pourTar() {
        arriveDelay()
        if (!allowed(player)) {
            mes("You should ask the Chemist before using his still.")
            return
        }
        if (player.tarInStill == 1) {
            mes("There is already coal tar in the still.")
            open()
            return
        }
        if (invDel(inv, BARREL_OF_COAL_TAR).failure) {
            return
        }
        player.tarInStill = 1
        mes("You pour the coal tar into the still.")
        open()
    }

    private suspend fun ProtectedAccess.operate() {
        arriveDelay()
        if (!allowed(player)) {
            mes("You should ask the Chemist before using his still.")
            return
        }
        if (player.tarInStill == 0) {
            mes("The still is empty. You'll need some coal tar to distil.")
            return
        }
        open()
    }

    private fun ProtectedAccess.open() {
        val simulation = sessions.getOrPut(player.uid) { StillSimulation(total = player.stillTotal.coerceIn(0, StillSimulation.TOTAL)) }
        ifOpenMainModal(INTERFACE)
        for (button in listOf(ADD_COAL, PRESSURE_DOWN, PRESSURE_UP, TAR_DOWN, TAR_UP)) {
            ifSetEvents(button, 0..0, IfEvent.Op1)
        }
        sync(player, simulation)
        softTimer(TIMER, 1)
    }

    internal fun ProtectedAccess.addCoal() {
        val simulation = session(player) ?: return
        if (player.tarInStill == 0) {
            return
        }
        if (invDel(inv, COAL).failure) {
            mes("You have no coal to put in the still.")
            return
        }
        report(player, simulation.addCoal())
        sync(player, simulation)
    }

    fun tick(player: Player) {
        val simulation = sessions[player.uid] ?: return player.clearSoftTimer(TIMER)
        if (player.tarInStill == 0) {
            return stop(player)
        }
        val outcome = simulation.step()
        if (outcome == Outcome.DISTILLED) {
            settle(player)
        } else {
            report(player, outcome)
        }
        sync(player, simulation)
        player.softTimer(TIMER, 1)
    }

    /** Empties the still and hands over the naphtha. Clearing the tar first makes it once only. */
    private fun settle(player: Player) {
        if (player.tarInStill == 0) {
            return
        }
        player.tarInStill = 0
        player.stillTotal = 0
        sessions.remove(player.uid)
        player.invAddOrDrop(objRepo, BARREL_OF_NAPHTHA)
        player.mes("The still finishes and you draw off a barrel of naphtha.")
    }

    private fun report(player: Player, outcome: Outcome) {
        when (outcome) {
            Outcome.BURNT_OUT -> player.mes("The still overheats and burns out with a pop! You'll have to start again.")
            Outcome.OVER_PRESSURE -> player.mes("The pressure gets too high and the still blows its valve! You'll have to start again.")
            else -> Unit
        }
    }

    fun stop(player: Player) {
        sessions.remove(player.uid)
        player.clearSoftTimer(TIMER)
    }

    private fun ProtectedAccess.sync(player: Player) {
        session(player)?.let { sync(player, it) }
    }

    private fun sync(player: Player, simulation: StillSimulation) {
        player.stillTotal = simulation.total
        player.stillSettings = simulation.settings()
    }

    companion object {
        const val STILL = "loc.regicide_fractionalizing_still"
        const val INTERFACE = "interface.regicide_still"
        const val ADD_COAL = "component.regicide_still:regicide_add_coal"
        const val PRESSURE_DOWN = "component.regicide_still:regicide_pressure_valve_down"
        const val PRESSURE_UP = "component.regicide_still:regicide_pressure_valve_up"
        const val TAR_DOWN = "component.regicide_still:regicide_tar_valve_down"
        const val TAR_UP = "component.regicide_still:regicide_tar_valve_up"
        const val TIMER = "timer.regicide_still"
    }
}
