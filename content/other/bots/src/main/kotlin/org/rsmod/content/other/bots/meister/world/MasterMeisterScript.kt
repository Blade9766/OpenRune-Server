package org.rsmod.content.other.bots.meister.world

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.game.process.GameLifecycle
import org.rsmod.api.player.output.mes
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onEvent
import org.rsmod.game.entity.player.SessionStateEvent
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class MasterMeisterScript @Inject constructor(private val meister: MasterMeister) : PluginScript() {
    private val commands = MeisterCommands(meister)

    override fun ScriptContext.startup() {
        onEvent<GameLifecycle.LateCycle> { meister.tick() }
        onEvent<GameLifecycle.Shutdown> { meister.shutdown() }
        onEvent<SessionStateEvent.Logout> { meister.onLoggedOut(player) }

        onCommand("meister") {
            requiredRights = Rights.ADMINISTRATOR
            desc = "Master Meister, the Grand Exchange trader (::meister help)"
            invalidArgs = MeisterCommands.USAGE
            cheat {
                if (args.firstOrNull() == "help") {
                    player.mes(MeisterCommands.USAGE)
                } else {
                    val admin = player
                    commands.run(admin.displayName, args) { admin.mes(it) }
                }
            }
        }
    }
}
