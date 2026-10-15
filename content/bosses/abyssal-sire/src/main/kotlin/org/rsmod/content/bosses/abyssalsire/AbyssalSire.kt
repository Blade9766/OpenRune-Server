package org.rsmod.content.bosses.abyssalsire

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.*
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossPluginScript
import org.rsmod.api.bosses.spec.BossSpec
import org.rsmod.api.bosses.spec.Condition
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.LUNG
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SCION
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SIRE_FORMS
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SPAWN
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.TENTACLE_SLEEPING
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The Abyssal Sire in each of the Abyssal Nexus's four chambers. Its spec only claims the Sire's
 * combat and hit events so the generic npc combat stays out of the way; [SireFights] runs the fight.
 * The Sire, its tentacles and its respiratory systems never wander, and the Sire respawns on its
 * throne the moment it dies.
 */
class AbyssalSire
@Inject
constructor(
    deps: BossDeps,
    private val fights: SireFights,
    private val npcDeath: NpcDeath,
) : BossPluginScript(deps) {

    override val spec: BossSpec =
        boss(*SIRE_FORMS.toTypedArray()) {
            stats(attackRate = IDLE_ATTACK_RATE)
            ability("idle", external(IDLE_EXT))
            phase("fight", lockMovement = true) {}
        }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(IDLE_EXT) { _, _, _, _ -> }
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = {
                val attacker = if (hit.isFromPlayer) hit.sourceUid?.let { PlayerUid(it).resolve(deps.playerList) } else null
                val shadow = hit.secondaryType()?.let { SHADOW_STUN_CHANCES[it.internalName] }
                val stuns = shadow != null && deps.random.of(1, 100) <= shadow
                hit.damage = fights.modifySireHit(npc, attacker, hit.type, hit.damage, stuns)
            },
            onHit = { fights.afterSireHit(npc) },
        )

        val lungType = npcType(LUNG)
        onModifyNpcHit(lungType) {
            val attacker = if (hit.isFromPlayer) hit.sourceUid?.let { PlayerUid(it).resolve(deps.playerList) } else null
            val halberd = hit.righthandType()?.isCategoryType(HALBERD) == true
            hit.damage = fights.modifyLungHit(npc, attacker, hit.type, halberd, hit.damage)
        }
        onNpcQueue(lungType, "queue.death") { fights.lungDied(npc) }

        for (form in SIRE_FORMS) {
            onNpcQueue(npcType(form), "queue.death") {
                val fight = fights.onSireDeath(npc)
                noneMode()
                hideAllOps()
                anim(SIRE_DEATH_SEQ)
                delay(SIRE_DEATH_TICKS)
                npcDeath.spawnDrops(this, SireFights.footprintCentre(npc))
                fight?.let(fights::releaseChamber)
                deps.npcRepo.despawn(npc, RESPAWN_TICKS)
            }
        }

        val stationary = (SIRE_FORMS + TENTACLE_SLEEPING + LUNG).map { it.asRSCM(RSCMType.NPC) }.toSet()
        onEvent<NpcStateEvents.Create> { if (npc.type.id in stationary) lockInPlace(npc) }
        onEvent<NpcStateEvents.Respawn> { if (npc.type.id in stationary) lockInPlace(npc) }
    }

    private fun lockInPlace(npc: Npc) {
        npc.movementLocked = true
    }

    private fun npcType(name: String) = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc type: $name")

    private companion object {
        const val IDLE_EXT = "sire.idle"
        const val IDLE_ATTACK_RATE = 4
        const val HALBERD = "category.halberd"
        const val SIRE_DEATH_SEQ = "seq.sire_death"
        const val SIRE_DEATH_TICKS = 6
        const val RESPAWN_TICKS = 1

        /** Percent chance each Shadow spell disorients the Sire, even when it splashes. */
        val SHADOW_STUN_CHANCES =
            mapOf(
                "obj.52_shadow_rush" to 25,
                "obj.64_shadow_burst" to 50,
                "obj.76_shadow_blitz" to 75,
                "obj.88_shadow_barrage" to 100,
            )
    }
}

/**
 * Spawns and scions: they close in to melee, or shoot from range after switching style (see
 * [SireFights]), and both hit through protection prayers. A spawn becomes a scion after [SireFights.SPAWN_GROWTH_TICKS].
 */
class SireMinions @Inject constructor(deps: BossDeps) : BossPluginScript(deps) {
    override val spec: BossSpec =
        boss(SPAWN, SCION) {
            stats(attackRate = MINION_ATTACK_RATE)
            val scion = Condition.Custom { npc, _ -> npc.visType.isType(SCION) }
            val meleeStyle = Condition.Custom { npc, _ -> npc.apRangeOverride == null }
            val spawnMelee =
                ability("spawn_melee") {
                    anim("seq.abyssal_spawn_attack")
                    hit {
                        damage(0..SPAWN_MELEE_MAX).roll()
                        type(Melee)
                        penetration(FULL_PENETRATION)
                    }
                }
            val spawnRanged =
                ability("spawn_ranged") {
                    anim("seq.abyssal_spawn_attack")
                    projectile {
                        spotanim = MINION_PROJECTILE
                        hit {
                            damage(0..SPAWN_RANGED_MAX).roll()
                            type(Ranged)
                            penetration(FULL_PENETRATION)
                        }
                    }
                }
            val scionMelee =
                ability("scion_melee") {
                    anim("seq.abyssal_scion_attack_melee")
                    hit {
                        damage(0..SCION_MELEE_MAX).roll()
                        type(Melee)
                        penetration(FULL_PENETRATION)
                    }
                }
            val scionRanged =
                ability("scion_ranged") {
                    anim("seq.abyssal_scion_attack_ranged")
                    projectile {
                        spotanim = MINION_PROJECTILE
                        hit {
                            damage(0..SCION_RANGED_MAX).roll()
                            type(Ranged)
                            penetration(FULL_PENETRATION)
                        }
                    }
                }
            phase("combat") {
                weightedSelectorRandom {
                    +random(spawnMelee, weight = 1, requires = !scion and meleeStyle and WithinMeleeRange)
                    +random(spawnRanged, weight = 1, requires = !scion and !meleeStyle)
                    +random(scionMelee, weight = 1, requires = scion and meleeStyle and WithinMeleeRange)
                    +random(scionRanged, weight = 1, requires = scion and !meleeStyle)
                }
            }
        }

    private companion object {
        const val MINION_ATTACK_RATE = 4
        const val FULL_PENETRATION = 100
        const val SPAWN_MELEE_MAX = 4
        const val SPAWN_RANGED_MAX = 9
        const val SCION_MELEE_MAX = 10
        const val SCION_RANGED_MAX = 16
        const val MINION_PROJECTILE = "spotanim.abyssal_spawn_projanim"
    }
}
