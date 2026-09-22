package org.rsmod.content.quest.area.varrock.shieldofarrav.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest
import org.rsmod.content.quest.area.varrock.shieldofarrav.ShieldOfArravQuest.Companion.BLACKARM_TASKED
import org.rsmod.content.quest.area.varrock.shieldofarrav.blackArmGang
import org.rsmod.content.quest.area.varrock.shieldofarrav.weaponsmasterDead
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

internal const val WEAPONSMASTER = "npc.weaponsmaster"
private const val WEAPONSMASTER_VIS = "npc.weaponsmaster_vis"

/**
 * The Weaponsmaster, guarding the Phoenix Gang's weapon store above the depot. He chats with gang
 * members and sets on anyone else. `npc.weaponsmaster` is a multinpc on
 * `varbit.soa_weaponmaster_dead`, so once a player kills him he stays out of their sight.
 */
class Weaponsmaster
@Inject
constructor(
    private val arrav: ShieldOfArravQuest,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(WEAPONSMASTER) { startDialogue(it.npc) { weaponsmaster(it.npc) } }
    }

    private suspend fun Dialogue.weaponsmaster(npc: Npc) {
        chatPlayer(neutral, "Hello.")
        if (!arrav.isPhoenix(player)) {
            chatNpc(
                angry,
                "Oi! Who are you? I'll teach you to go poking your nose where it doesn't belong!",
            )
            npc.opPlayer2(player, aiInteractions)
            return
        }
        chatNpc(happy, "Hello, fellow Phoenix! What can I do for you?")
        if (choice2("I'm after a weapon or two.", true, "I'm looking for treasure.", false)) {
            chatPlayer(neutral, "I'm after a weapon or two.")
            chatNpc(neutral, "No problem. Have a look around.")
        } else {
            chatPlayer(neutral, "I'm looking for treasure.")
            chatNpc(
                laugh,
                "Aren't we all? There's none up here. Go and rob someone if you want treasure.",
            )
        }
    }
}

/**
 * A Black Arm recruit who kills the Weaponsmaster no longer sees him, and may take the crossbows
 * he guarded; Katrine clears the flag once she has them.
 */
class WeaponsmasterKillHook : NpcDeathKillHook {
    private val ids: Set<Int> by lazy {
        setOf(WEAPONSMASTER.asRSCM(RSCMType.NPC), WEAPONSMASTER_VIS.asRSCM(RSCMType.NPC))
    }

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id in ids && context.hero.blackArmGang == BLACKARM_TASKED) {
            context.hero.weaponsmasterDead = true
        }
    }
}
