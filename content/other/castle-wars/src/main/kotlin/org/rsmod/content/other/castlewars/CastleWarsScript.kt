package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.config.constants
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.mechanics.toxins.Toxin.cureAllToxins
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.MiscOutput
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statRestoreAll
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.api.script.onPlayerQueue
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal object CastleWarsQueues {
    const val ENTER_GAME: String = "queue.castlewars_enter_game"
    const val LEAVE_GAME: String = "queue.castlewars_leave_game"
}

/** The game loop, moving players in and out of the arena, and cleaning up after logouts. */
internal class CastleWarsScript
@Inject
constructor(private val game: CastleWarsGame, private val eventBus: EventBus) : PluginScript() {
    private val allStats: List<String> by lazy {
        ServerCacheManager.getStats().values.map { RSCM.getReverseMapping(RSCMType.STAT, it.id) }
    }

    private val gameItemIds: Set<Int> by lazy {
        CastleWars.GAME_ITEMS.map { it.asRSCM(RSCMType.OBJ) }.toSet()
    }

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { game.tick() }
        onPlayerQueue(CastleWarsQueues.ENTER_GAME) { enterGame() }
        onPlayerQueue(CastleWarsQueues.LEAVE_GAME) { returnToLobby() }
        onPlayerLogout { logout(player) }
        onPlayerLogin { login(player) }

        onCommand("cwstart") {
            desc = "Start a Castle Wars game with whoever is waiting"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                game.startGame()
                player.mes("Castle Wars game started.")
            }
        }
        onCommand("cwend") {
            desc = "End the running Castle Wars game now"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                game.endGame()
                player.mes("Castle Wars game ended.")
            }
        }
    }

    private fun logout(player: Player) {
        if (game.isPlaying(player)) {
            game.leaveGame(player)
        }
        game.leaveWaitingRoom(player)
        game.pendingTickets -= player
    }

    /**
     * Nothing about a game survives a logout, so anyone who logs in inside the arena or a waiting
     * room is walked back to the lobby with the arena's items taken away.
     */
    private fun login(player: Player) {
        val inside =
            game.inArena(player.coords) || Team.entries.any { player.coords in it.waitingArea }
        if (inside || hasGameItems(player)) {
            player.strongQueue(CastleWarsQueues.LEAVE_GAME, 1)
        } else {
            game.clearVars(player)
        }
    }

    private fun hasGameItems(player: Player): Boolean {
        val cloaks = Team.entries.map { it.cloak.asRSCM(RSCMType.OBJ) }
        return listOf(player.inv, player.worn).any { inv -> inv.objs.any { it != null && it.id in cloaks } }
    }

    private fun ProtectedAccess.enterGame() {
        val team = game.playingTeamOf(player) ?: return
        resetTransmog()
        rebuildAppearance()
        player.ifCloseOverlay(WAITING_OVERLAY, eventBus)
        telejump(game.scatter(team.spawnRoom), TeleportType.Exempt)
        ifOpenOverlay(team.overlay)
        MiscOutput.setPlayerOp(player, ATTACK_SLOT, "Attack", priority = true)
        MiscOutput.setPlayerOp(player, CastleWars.TAKE_FROM_SLOT, CastleWars.TAKE_FROM_OP)
        if (!inv.contains(CastleWars.RUNE_POUCH)) {
            invAdd(inv, CastleWars.RUNE_POUCH, strict = false)
        }
        chargeBracelet()
        game.syncVars(player)
        mes("The game has begun! Take the enemy standard and capture it on your own.")
    }

    /**
     * Wearing a Castle Wars bracelet into a game, or having charges stored with Lanthus, spends one
     * charge to empower attacks on standard bearers and bandage healing for the whole game.
     */
    private fun ProtectedAccess.chargeBracelet() {
        val hands = player.worn[Wearpos.Hands.slot]
        val braceletIds = CastleWars.BRACELETS.map { it.asRSCM(RSCMType.OBJ) }
        val index = braceletIds.indexOf(hands?.id)
        if (index >= 0) {
            val next = CastleWars.BRACELETS.getOrNull(index + 1)
            player.worn[Wearpos.Hands.slot] =
                next?.let { org.rsmod.game.inv.InvObj(it) }
            if (next == null) {
                player.rebuildAppearance()
                mes("Your Castle wars bracelet crumbles to dust.")
            }
            VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_active", 1)
            mes("Your Castle wars bracelet empowers you for this game.")
            return
        }
        val stored = player.vars["varbit.castlewars_bracelet_charges"]
        if (stored > 0 && player.vars["varbit.castlewars_bracelet_paused"] == 0) {
            VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_charges", stored - 1)
            VarPlayerIntMapSetter.set(player, "varbit.castlewars_bracelet_active", 1)
            mes("Lanthus uses one of your stored bracelet charges. You have ${stored - 1} left.")
        }
    }

    private fun ProtectedAccess.returnToLobby() {
        game.leaveGame(player)
        game.leaveWaitingRoom(player)
        ifClose()
        for (team in Team.entries) {
            player.ifCloseOverlay(team.overlay, eventBus)
        }
        player.ifCloseOverlay(WAITING_OVERLAY, eventBus)
        stripGameItems()
        resetTransmog()
        rebuildAppearance()
        MiscOutput.clearPlayerOp(player, ATTACK_SLOT, "Attack")
        if (MiscOutput.findPlayerOption(player, CastleWars.TAKE_FROM_OP) == CastleWars.TAKE_FROM_SLOT) {
            MiscOutput.setPlayerOp(player, CastleWars.TAKE_FROM_SLOT, FOLLOW_OP)
        }
        player.disablePrayers()
        player.cureAllToxins()
        player.statRestoreAll(allStats)
        player.runEnergy = constants.run_max_energy
        UpdateRun.energy(player, player.runEnergy)
        game.clearVars(player)
        if (CastleWars.LOBBY_AREA.contains(player.coords).not()) {
            telejump(game.scatter(CastleWars.LOBBY), TeleportType.Exempt)
        }
        val tickets = game.pendingTickets.remove(player) ?: return
        val added = invAdd(inv, CastleWars.TICKET, tickets, strict = false)
        if (added.success) {
            mes("You receive $tickets Castle wars ${if (tickets == 1) "ticket" else "tickets"}.")
        } else {
            mes("You have no room for your Castle wars tickets.")
        }
        awardPlaudits(tickets)
    }

    /** Members earn one plaudit for every ticket won, to spend on supply crates with Lanthus. */
    private fun ProtectedAccess.awardPlaudits(tickets: Int) {
        if (!player.members) {
            return
        }
        val total = (player.vars[PLAUDITS].toLong() + tickets).coerceAtMost(Int.MAX_VALUE.toLong()).toInt()
        VarPlayerIntMapSetter.set(player, PLAUDITS, total)
        mes("You receive $tickets ${if (tickets == 1) "plaudit" else "plaudits"}. You now have $total.")
    }

    private fun ProtectedAccess.stripGameItems() {
        var wornChanged = false
        for (inventory in listOf(player.inv, player.worn)) {
            for (slot in inventory.indices) {
                val obj = inventory[slot] ?: continue
                if (obj.id in gameItemIds) {
                    inventory[slot] = null
                    wornChanged = wornChanged || inventory === player.worn
                }
            }
        }
        if (wornChanged) {
            rebuildAppearance()
        }
    }

    companion object {
        const val WAITING_OVERLAY: String = "interface.castlewars_waitingroom"
        const val ATTACK_SLOT: Int = 1
        const val FOLLOW_OP: String = "Follow"
        const val PLAUDITS: String = "varp.castlewars_plaudits"
    }
}
