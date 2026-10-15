package org.rsmod.content.quest.area.mortton.shades

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onEvent
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.LOAR_SHADE
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.LOAR_SHADOW
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.SHADES_NEEDED
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_ALL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_KILL_SHADES
import org.rsmod.content.quest.area.mortton.shades.ShadesOfMorttonQuest.Companion.STAGE_PER_SHADE
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

private var Npc.lastAttack: Int by intVarn("varn.lastattack")
private var Npc.lastCombat: Int by intVarn("varn.lastcombat")

/**
 * The shades of Mort'ton and its catacombs. Each wanders as a flat shadow and rises into its
 * shade form the moment it fights, sinking back into the ground once it has been left alone for a
 * while. The shadow and the shade share their stats, so rising is purely a change of look.
 */
@Singleton
class Shades @Inject constructor(private val world: WorldRepository) : PluginScript() {

    private val forms by lazy {
        SHADOW_TO_SHADE.map { (shadow, shade) -> npcType(shadow) to npcType(shade) }
    }

    override fun ScriptContext.startup() {
        val shadowIds = SHADOW_TO_SHADE.keys.map { it.asRSCM(RSCMType.NPC) }.toSet()
        onEvent<NpcStateEvents.Create> {
            if (npc.id in shadowIds) {
                npc.aiTimer(CHECK_TICKS)
            }
        }
        for ((shadow, shade) in forms) {
            onAiTimer(shadow) { update(npc, shade) }
        }
    }

    private fun update(npc: Npc, shade: NpcServerType) {
        npc.aiTimer(CHECK_TICKS)
        if (!npc.isAliveInWorld()) {
            return
        }
        val lastFight = maxOf(npc.lastAttack, npc.lastCombat)
        val fighting = lastFight > 0 && npc.currentMapClock - lastFight < SINK_IDLE_TICKS
        val risen = npc.transmog != null
        if (fighting && !risen) {
            npc.transmog(shade, Int.MAX_VALUE)
            npc.anim(RISE_SEQ)
            world.soundArea(npc, APPEAR_SOUND)
        } else if (!fighting && risen) {
            npc.anim(SINK_SEQ)
            world.soundArea(npc, SIGH_SOUND)
            npc.resetTransmog()
        }
    }

    private fun npcType(name: String): NpcServerType =
        requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "Missing npc: $name" }

    private companion object {
        const val CHECK_TICKS = 2
        const val SINK_IDLE_TICKS = 30

        const val RISE_SEQ = "seq.shade_rise"
        const val SINK_SEQ = "seq.shadeshadow_rise"
        const val APPEAR_SOUND = "synth.shade_appear"
        const val SIGH_SOUND = "synth.shade_sigh"

        val SHADOW_TO_SHADE =
            mapOf(
                LOAR_SHADOW to LOAR_SHADE,
                "npc.shadeshadow_level2" to "npc.shade_level2",
                "npc.shadeshadow_level3" to "npc.shade_level3",
                "npc.shadeshadow_level4" to "npc.shade_level4",
                "npc.shadeshadow_level5" to "npc.shade_level5",
                "npc.shadeshadow_level6" to "npc.shade_level6",
            )
    }
}

/** Each Loar Shade slain after Razmire asks counts towards his five. */
class LoarShadeKillHook
@Inject
constructor(
    private val shades: ShadesOfMorttonQuest,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
    private val playerList: PlayerList,
) : NpcDeathKillHook {
    private val loarIds = setOf(LOAR_SHADOW.asRSCM(RSCMType.NPC), LOAR_SHADE.asRSCM(RSCMType.NPC))

    override fun onKill(context: NpcDeathKillContext) {
        if (context.npc.id !in loarIds) {
            return
        }
        if (shades.stage(context.hero) !in STAGE_KILL_SHADES until STAGE_ALL_SHADES) {
            return
        }
        countKill(context.hero.uid, ATTEMPTS)
    }

    private fun countKill(uid: PlayerUid, attempts: Int) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            val launched =
                launcher.launch(player) {
                    val stage = shades.stage(player)
                    if (stage !in STAGE_KILL_SHADES until STAGE_ALL_SHADES) {
                        return@launch
                    }
                    shades.advanceTo(this, stage + STAGE_PER_SHADE)
                    val killed = shades.shadesKilled(player)
                    mes(
                        if (killed >= SHADES_NEEDED) {
                            "That's all five Shades!"
                        } else {
                            "That's ${COUNT_WORDS[killed - 1]} Shade${if (killed > 1) "s" else ""}!"
                        },
                    )
                }
            if (!launched && attempts > 0) {
                countKill(uid, attempts - 1)
            }
        }
    }

    private companion object {
        const val ATTEMPTS = 20
        val COUNT_WORDS = listOf("one", "two", "three", "four")
    }
}
