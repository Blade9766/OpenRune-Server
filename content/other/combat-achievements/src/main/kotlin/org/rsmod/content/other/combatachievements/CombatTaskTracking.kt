package org.rsmod.content.other.combatachievements

import jakarta.inject.Inject
import org.rsmod.api.combatachievements.CombatAchievementTasks
import org.rsmod.api.combatachievements.CombatAchievements
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.events.NpcHitEvents
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onPlayerHit
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.game.MapClock
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.PlayerList
import org.rsmod.plugin.module.PluginModule
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class CombatAchievementsModule : PluginModule() {
    override fun bind() {
        addSetBinding<NpcDeathKillHook>(CombatTaskKillHook::class.java)
    }
}

class CombatTaskTrackingScript
@Inject
constructor(
    private val fights: CombatFights,
    private val players: PlayerList,
    private val npcs: NpcList,
    private val clock: MapClock,
) : PluginScript() {
    override fun ScriptContext.startup() {
        onEvent<NpcHitEvents.AnyImpact> {
            if (!hit.isFromPlayer) {
                return@onEvent
            }
            val player = hit.resolvePlayerSource(players) ?: return@onEvent
            fights.onPlayerHitNpc(player, npc, hit, clock.cycle)
        }
        onPlayerHit {
            if (!hit.isFromNpc) {
                return@onPlayerHit
            }
            val npc = hit.resolveNpcSource(npcs) ?: return@onPlayerHit
            fights.onNpcHitPlayer(player, npc, hit)
        }
        onPlayerLogout { fights.forget(player) }
    }
}

class CombatTaskKillHook
@Inject
constructor(
    private val achievements: CombatAchievements,
    private val fights: CombatFights,
    private val npcs: NpcList,
    private val clock: MapClock,
) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val player = context.hero
        val npc = context.npc
        achievements.checkKillcounts(player)
        achievements.onNamedKill(player, npc.name)

        val fight = fights.of(player)?.takeIf { it.npcSlot == npc.slotId && it.npcType == npc.id }
        val streak = fights.recordKill(player, npc, clock.cycle)
        fights.end(player)
        val tasks = TrackedTasks.byNpcName[npc.name.lowercase()] ?: return
        val kill = KillContext(player, npc, fight, clock.cycle, streak, fights, npcs)
        for (tracked in tasks) {
            val task = CombatAchievementTasks.named(tracked.task)
            if (!achievements.isComplete(player, task) && tracked.condition(kill)) {
                achievements.complete(player, task)
            }
        }
    }
}
