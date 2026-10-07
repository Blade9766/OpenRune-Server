package org.rsmod.content.quest.area.lumbridge

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerLoginVarsHook
import org.rsmod.game.entity.Player

class RuneMysteriesLoginVars @Inject constructor(private val runeMysteries: RuneMysteriesQuest) :
    PlayerLoginVarsHook {
    override fun onLoginVarsSent(player: Player) {
        runeMysteries.showAssumedCompletion(player)
    }
}
