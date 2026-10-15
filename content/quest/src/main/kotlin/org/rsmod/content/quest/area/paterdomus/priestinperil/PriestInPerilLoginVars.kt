package org.rsmod.content.quest.area.paterdomus.priestinperil

import jakarta.inject.Inject
import org.rsmod.api.player.hook.PlayerLoginVarsHook
import org.rsmod.game.entity.Player

class PriestInPerilLoginVars @Inject constructor(private val priestInPeril: PriestInPerilQuest) :
    PlayerLoginVarsHook {
    override fun onLoginVarsSent(player: Player) {
        priestInPeril.showAssumedCompletion(player)
    }
}
