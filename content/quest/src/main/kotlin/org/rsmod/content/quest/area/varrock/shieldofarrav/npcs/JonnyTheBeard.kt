package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.PHOENIX_TASKED
import org.rsmod.content.quest.area.varrock.shieldofarrav.phoenixGang
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private const val JONNY = "npc.jonny_the_beard"

/**
 * Jonny the Beard, drinking in the Blue Moon Inn. `npc.jonny_the_beard` is a multinpc on
 * `varp.phoenixgang`: talk-only until Straven sets the player on him, attackable at that stage,
 * and gone for good once they have joined the Phoenix Gang. His drop table carries the
 * intelligence report.
 */
class JonnyTheBeard : PluginScript() {
    override fun ScriptContext.startup() {
        onOpNpc1(JONNY) { startDialogue(it.npc) { chatNpc(angry, "Whatever it is, I'm not interested!") } }
    }
}

/** The client only offers Attack at Straven's stage; this holds the server to the same rule. */
class JonnyAttackHook : NpcAttackValidateHook {
    private val ids: Set<Int> by lazy {
        listOf(JONNY, "npc.jonny_the_beard_1op", "npc.jonny_the_beard_2op")
            .map { it.asRSCM(RSCMType.NPC) }
            .toSet()
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in ids || player.phoenixGang == PHOENIX_TASKED) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("Jonny the Beard isn't worth picking a fight with.")
    }
}
