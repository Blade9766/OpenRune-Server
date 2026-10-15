package org.rsmod.content.other.castlewars.bots

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.content.other.castlewars.CastleWarsGame
import org.rsmod.content.other.castlewars.CatapultState
import org.rsmod.content.other.castlewars.Team
import org.rsmod.game.MapClock
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal class CastleWarsBotScript
@Inject
constructor(private val bots: CastleWarsBots, private val game: CastleWarsGame, private val mapClock: MapClock) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.StartCycle> { bots.tick(mapClock.cycle) }
        onCommand("cwbots") {
            desc = "Fill both Castle Wars teams with bots and start a game (::cwbots [max])"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val limit = args.firstOrNull()?.toIntOrNull()?.coerceIn(1, MAX_BOTS) ?: MAX_BOTS
                val added = bots.fill(player, limit)
                if (added > 0 && !game.running) {
                    game.startGame()
                }
                player.mes("Added $added Castle Wars ${if (added == 1) "bot" else "bots"}.")
            }
        }
        onCommand("cwbotinfo") {
            desc = "List every Castle Wars bot with its position, role and current order"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                for (team in Team.entries) {
                    val state = game.state(team)
                    player.mes("${team.displayName}: score=${state.score} flag=${state.flag} door=${state.mainDoor} catapult=${state.catapult}")
                }
                bots.describe().forEach { player.mes(it) }
            }
        }
        onCommand("cwcatapult") {
            desc = "Set a Castle Wars catapult's state (::cwcatapult saradomin|zamorak operational|burning|broken)"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val team = Team.entries.firstOrNull { it.displayName.equals(args.getOrNull(0), ignoreCase = true) }
                val next = CatapultState.entries.firstOrNull { it.name.equals(args.getOrNull(1), ignoreCase = true) }
                if (team == null || next == null || !game.running) {
                    player.mes("Usage: ::cwcatapult saradomin|zamorak operational|burning|broken (during a game)")
                    return@cheat
                }
                game.setCatapult(team, next)
                player.mes("${team.displayName} catapult is now $next.")
            }
        }
        onCommand("cwbotsoff") {
            desc = "Remove every Castle Wars bot"
            requiredRights = Rights.ADMINISTRATOR
            cheat {
                val removed = bots.count
                bots.removeAll()
                player.mes("Removed $removed Castle Wars ${if (removed == 1) "bot" else "bots"}.")
            }
        }
    }

    private companion object {
        const val MAX_BOTS = 40
    }
}
