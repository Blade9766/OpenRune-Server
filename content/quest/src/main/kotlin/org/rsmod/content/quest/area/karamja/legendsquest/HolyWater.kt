package org.rsmod.content.quest.area.karamja.legendsquest

import jakarta.inject.Inject
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.RangedAmmoManager
import org.rsmod.api.config.constants
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.weapons.RangedWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.content.quest.area.karamja.legendsquest.LegendsQuest.Companion.HOLY_WATER
import org.rsmod.content.skills.magic.spell.attacks.SpellEffects
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.queue.WorldQueueList

/**
 * Holy water from the Book of Binding's enchanted vials, thrown like darts. It only hurts demons:
 * against one its max hit is raised by 60%, and by a further 5 against Nezikchened, and a landed
 * throw lowers the demon's Defence by 5% (not stacking). Anything else it just splashes. Every
 * vial breaks on impact and leaves smashed glass where it lands.
 *
 * The max hit ignores prayers, void and gear other than the vial itself: it comes from the
 * visible Ranged level with a flat stance bonus of 10 and the vial's own ranged strength.
 */
class HolyWaterWeapon
@Inject
constructor(
    private val ammunition: RangedAmmoManager,
    private val objRepo: ObjRepository,
    private val world: WorldRepository,
    private val worldQueues: WorldQueueList,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        register(HOLY_WATER, HolyWater(manager))
    }

    private inner class HolyWater(private val manager: WeaponAttackManager) : RangedWeapon {
        override suspend fun ProtectedAccess.attack(target: Npc, attack: CombatAttack.Ranged): Boolean {
            throwVial(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(target: Player, attack: CombatAttack.Ranged): Boolean {
            throwVial(target, attack)
            return true
        }

        private fun ProtectedAccess.throwVial(target: PathingEntity, attack: CombatAttack.Ranged) {
            val vial = player.righthand ?: return manager.stopCombat(this)
            val vialType = objType(HOLY_WATER)
            if (vial.id != vialType.id) {
                manager.stopCombat(this)
                return
            }
            anim(THROW_SEQ)
            soundSynth(THROW_SOUND)
            spotanim(LAUNCH_SPOTANIM, height = LAUNCH_HEIGHT, slot = constants.spotanim_slot_combat)
            val projanim = manager.spawnProjectile(this, target, TRAVEL_SPOTANIM, PROJANIM)
            val (serverDelay, clientDelay) = projanim.durations

            ammunition.useThrownWeapon(player, vialType, target.coords, dropDelay = serverDelay, dropChance = NEVER_DROPS)
            val landing = target.coords
            val owner = player
            worldQueues.add(serverDelay) {
                objRepo.add(SMASHED_GLASS, landing, GLASS_TICKS, receiver = owner)
                world.soundArea(landing, GLASS_SOUND)
            }

            val demon = target is Npc && isDemon(target)
            val landed =
                demon && manager.rollRangedAccuracy(this, target, attack.type, attack.style, attack.type, 1.0)
            val damage = if (landed) random.of(0..maxHit(target as Npc)) else 0
            manager.giveCombatXp(this, target, attack, damage)
            manager.queueRangedHit(this, target, null, damage, clientDelay, serverDelay)
            if (landed) {
                val npc = target as Npc
                worldQueues.add(serverDelay) {
                    if (npc.isSlotAssigned) {
                        SpellEffects.drainPercent(npc, SpellEffects.DEFENCE, DEFENCE_DRAIN_PERCENT)
                    }
                }
            }

            if (player.righthand == null) {
                mes("That was your last one!")
                manager.stopCombat(this)
                return
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.maxHit(demon: Npc): Int {
            val effective = stat("stat.ranged") + STANCE_BONUS
            val base = (0.5 + effective * (VIAL_RANGED_STRENGTH + 64) / 640.0).toInt()
            val boosted = (base * DEMON_MULTIPLIER).toInt()
            return if (NEZIKCHENED_TYPES.any { demon.isType(it) }) boosted + NEZIKCHENED_BONUS else boosted
        }
    }

    private fun isDemon(npc: Npc): Boolean =
        SpellEffects.isDemon(npc) || NEZIKCHENED_TYPES.any { npc.isType(it) }

    private companion object {
        const val SMASHED_GLASS = "obj.smashed_glass"
        const val THROW_SEQ = "seq.human_throw"
        const val LAUNCH_SPOTANIM = "spotanim.holy_water_launch"
        const val TRAVEL_SPOTANIM = "spotanim.holy_water_travel"
        const val PROJANIM = "projanim.thrown"
        const val THROW_SOUND = "synth.thrown"
        const val GLASS_SOUND = "synth.glass_break"
        const val LAUNCH_HEIGHT = 96
        const val GLASS_TICKS = 200

        /** The vial always breaks, so the thrown weapon itself is never dropped. */
        const val NEVER_DROPS = Int.MAX_VALUE

        const val STANCE_BONUS = 10
        const val VIAL_RANGED_STRENGTH = 12
        const val DEMON_MULTIPLIER = 1.6
        const val NEZIKCHENED_BONUS = 5
        const val DEFENCE_DRAIN_PERCENT = 5

        val NEZIKCHENED_TYPES =
            listOf(Nezikchened.DEMON, "npc.nzone_nezikchened_normal", "npc.nzone_nezikchened_hard")
    }
}
