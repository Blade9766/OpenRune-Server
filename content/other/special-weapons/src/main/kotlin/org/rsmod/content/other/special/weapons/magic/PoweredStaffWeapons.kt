package org.rsmod.content.other.special.weapons.magic

import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.area.checker.isInWilderness
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.manager.CombatChargeManager
import org.rsmod.api.combat.manager.PlayerAttackManager
import org.rsmod.api.mechanics.toxins.impl.PlayerVenom
import org.rsmod.api.obj.charges.ObjChargeManager
import org.rsmod.api.obj.charges.ObjChargeManager.Companion.isFailure
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.magicLvl
import org.rsmod.api.weapons.MagicWeapon
import org.rsmod.api.weapons.WeaponAttackManager
import org.rsmod.api.weapons.WeaponMap
import org.rsmod.api.weapons.WeaponRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player

/**
 * Built-in spells of every powered staff other than Tumeken's shadow (see
 * [TumekensShadowWeapons]): the tridents, the Sanguinesti staff, the revenant sceptres, the warped
 * sceptre, the bone staff, the Eye of Ayak, the deadman starter staff and the corrupted Tumeken's
 * shadow.
 *
 * All of them share one charge counter, `varobj.powered_staff_charges`, and the charging itself
 * (runes, ether, check/uncharge options) lives in `PoweredStaffCharging`.
 */
class PoweredStaffWeapons
@Inject
constructor(
    private val charges: CombatChargeManager,
    private val objCharges: ObjChargeManager,
    private val attackManager: PlayerAttackManager,
    private val areaChecker: AreaChecker,
) : WeaponMap {
    override fun WeaponRepository.register(manager: WeaponAttackManager) {
        fun staff(spec: PoweredStaffSpec) =
            PoweredStaff(spec, manager, attackManager, charges, areaChecker)

        fun uncharged(vararg objs: String, message: String) {
            val weapon = UnchargedPoweredStaff(manager, message)
            for (obj in objs) {
                register(obj, weapon)
            }
        }

        // Tridents of the seas: (full) drops turn into the charged trident on first use.
        val seas = staff(PoweredStaffSpec.trident("trident", TRIDENT_SEAS_FX, penalty = 5))
        val seasOrn = staff(PoweredStaffSpec.trident("trident", TRIDENT_SEAS_ORN_FX, penalty = 5))
        register("obj.tots_charged", seas)
        register("obj.tots_i_charged", seas)
        register("obj.tots_charged_orn", seasOrn)
        register("obj.tots_i_charged_orn", seasOrn)
        register("obj.tots", FullTrident(seas, manager, objCharges))
        register("obj.tots_orn", FullTrident(seasOrn, manager, objCharges))
        uncharged(
            "obj.tots_uncharged",
            "obj.tots_i_uncharged",
            "obj.tots_uncharged_orn",
            "obj.tots_i_uncharged_orn",
            message = "Your trident has no charges! You need to charge it with runes.",
        )

        // Tridents of the swamp: 25% chance to envenom on a successful hit.
        val swamp =
            staff(PoweredStaffSpec.trident("trident", TRIDENT_SWAMP_FX, penalty = 2, venom = 25))
        val swampOrn =
            staff(
                PoweredStaffSpec.trident("trident", TRIDENT_SWAMP_ORN_FX, penalty = 2, venom = 25)
            )
        register("obj.toxic_tots_charged", swamp)
        register("obj.toxic_tots_i_charged", swamp)
        register("obj.toxic_tots_charged_orn", swampOrn)
        register("obj.toxic_tots_i_charged_orn", swampOrn)
        uncharged(
            "obj.toxic_tots_uncharged",
            "obj.toxic_tots_i_uncharged",
            "obj.toxic_tots_uncharged_orn",
            "obj.toxic_tots_i_uncharged_orn",
            message = "Your trident has no charges! You need to charge it with runes.",
        )

        // Sanguinesti staff: 1/5 chance to deal 8 extra damage and heal half the damage dealt.
        register(
            "obj.sanguinesti_staff",
            staff(
                PoweredStaffSpec(
                    name = "Sanguinesti staff",
                    fx = SANGUINESTI_FX,
                    maxHit = { magic -> magic / 3 },
                    healSpotanim = "spotanim.sanguinesti_staff_heal",
                )
            ),
        )
        register(
            "obj.sanguinesti_staff_or",
            staff(
                PoweredStaffSpec(
                    name = "Sanguinesti staff",
                    fx = SANGUINESTI_HOLY_FX,
                    maxHit = { magic -> magic / 3 },
                    healSpotanim = "spotanim.sanguinesti_staff_heal_justiciar",
                )
            ),
        )
        uncharged(
            "obj.sanguinesti_staff_uncharged",
            "obj.sanguinesti_staff_uncharged_or",
            message =
                "Your Sanguinesti staff has no charges! You need to charge it with blood runes.",
        )

        // Revenant sceptres: +50% magic accuracy and damage against npcs in the Wilderness.
        register(
            "obj.wild_cave_sceptre_charged",
            staff(
                PoweredStaffSpec(
                    name = "sceptre",
                    fx = SCEPTRE_FX,
                    maxHit = { magic -> magic / 3 - 8 },
                    wildernessBoost = true,
                )
            ),
        )
        register(
            "obj.wild_cave_accursed_charged",
            staff(
                PoweredStaffSpec(
                    name = "sceptre",
                    fx = SCEPTRE_FX,
                    maxHit = { magic -> magic / 3 - 6 },
                    wildernessBoost = true,
                )
            ),
        )
        uncharged(
            "obj.wild_cave_sceptre_uncharged",
            "obj.wild_cave_accursed_uncharged",
            message = "The sceptre has no charges! You need to charge it with revenant ether.",
        )

        register(
            "obj.warped_sceptre",
            staff(
                PoweredStaffSpec(
                    name = "warped sceptre",
                    fx = WARPED_SCEPTRE_FX,
                    maxHit = { magic -> (8 * magic + 96) / 37 },
                )
            ),
        )
        uncharged(
            "obj.warped_sceptre_uncharged",
            message =
                "Your warped sceptre has no charges! You need to charge it with chaos and earth runes.",
        )

        // Bone staff: only usable against rats; it is its own uncharged form.
        register(
            "obj.rat_bone_staff",
            staff(
                PoweredStaffSpec(
                    name = "bone staff",
                    fx = BONE_STAFF_FX,
                    maxHit = { magic -> magic / 3 + 5 },
                    ratsOnly = true,
                    outOfCharges =
                        "Your bone staff has no charges! You need to charge it with chaos runes.",
                )
            ),
        )

        register(
            "obj.eye_of_ayak",
            staff(
                PoweredStaffSpec(
                    name = "Eye of Ayak",
                    fx = EYE_OF_AYAK_FX,
                    maxHit = { magic -> magic / 3 - 6 },
                    attackRate = 3,
                )
            ),
        )
        uncharged(
            "obj.eye_of_ayak_uncharged",
            message =
                "The Eye of Ayak has no charges! You need to charge it with demon tears, " +
                    "or death and chaos runes.",
        )

        // Deadman starter staff: a free Fire Strike (max hit 8) with no charges to track.
        val starter =
            staff(
                PoweredStaffSpec(
                    name = "starter staff",
                    fx = STARTER_STAFF_FX,
                    maxHit = { STARTER_STAFF_MAX_HIT },
                    usesCharges = false,
                )
            )
        register("obj.deadman_starter_staff", starter)
        register("obj.deadman_apocalypse_staff", starter)

        // Corrupted (deadman) Tumeken's shadow behaves like the regular shadow.
        register(
            "obj.deadman_blighted_tumekens_shadow",
            staff(
                PoweredStaffSpec(
                    name = "Tumeken's shadow",
                    fx = TUMEKENS_SHADOW_FX,
                    maxHit = { magic -> magic / 3 + 1 },
                    attackRate = 5,
                )
            ),
        )
        uncharged(
            "obj.deadman_blighted_tumekens_shadow_uncharged",
            message =
                "Tumeken's Shadow has no charges! You need to " +
                    "charge it with soul runes and chaos runes.",
        )
    }

    /** Animation, graphics and sound of a staff's built-in spell. */
    class StaffFx(
        val castAnim: String,
        val castSpotanim: String?,
        val travelSpotanim: String,
        val impactSpotanim: String?,
        val castSound: String?,
        val hitSound: String?,
        val castSpotanimHeight: Int = 92,
        val impactHeight: Int = 124,
        val projanim: String = "projanim.magic_spell",
    )

    class PoweredStaffSpec(
        /** Used in chat messages, e.g. "Your trident has run out of charges." */
        val name: String,
        val fx: StaffFx,
        /** Base max hit from the player's current (visible) magic level. */
        val maxHit: (magicLvl: Int) -> Int,
        val attackRate: Int = 4,
        val usesCharges: Boolean = true,
        /** +50% magic accuracy and damage against npcs in the Wilderness (revenant sceptres). */
        val wildernessBoost: Boolean = false,
        /** Percent chance to envenom the target on a successful hit (trident of the swamp). */
        val venomChance: Int = 0,
        /** When set, enables the Sanguinesti passive and plays this spotanim on the heal. */
        val healSpotanim: String? = null,
        /** The bone staff only works against rats. */
        val ratsOnly: Boolean = false,
        val outOfCharges: String = "Your $name has run out of charges.",
    ) {
        companion object {
            fun trident(name: String, fx: StaffFx, penalty: Int, venom: Int = 0) =
                PoweredStaffSpec(
                    name = name,
                    fx = fx,
                    maxHit = { magic -> magic / 3 - penalty },
                    venomChance = venom,
                )
        }
    }

    private class PoweredStaff(
        private val spec: PoweredStaffSpec,
        private val manager: WeaponAttackManager,
        private val attackManager: PlayerAttackManager,
        private val charges: CombatChargeManager,
        private val areaChecker: AreaChecker,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            cast(target, attack)
            return true
        }

        fun ProtectedAccess.cast(target: PathingEntity, attack: CombatAttack.Staff) {
            if (spec.ratsOnly && !isRat(target)) {
                manager.stopCombat(this)
                mes("You can only use this weapon against rats.")
                return
            }

            var chargesLeft = -1
            if (spec.usesCharges) {
                val chargeResult = charges.attemptDetractWeapon(player, CHARGES_VAROBJ)
                if (chargeResult.isFailure()) {
                    manager.stopCombat(this)
                    mes(spec.outOfCharges)
                    return
                }
                chargesLeft = chargeResult.chargesLeft
            }

            manager.setNextAttackDelay(this, spec.attackRate)

            val fx = spec.fx
            anim(fx.castAnim)
            if (fx.castSpotanim != null) {
                spotanim(fx.castSpotanim, height = fx.castSpotanimHeight)
            }

            val proj = manager.spawnProjectile(this, target, fx.travelSpotanim, fx.projanim)
            val (serverDelay, clientDelay) = proj.durations

            val boosted =
                spec.wildernessBoost && target is Npc && target.coords.isInWilderness(areaChecker)
            val multiplier = if (boosted) WILDERNESS_MULTIPLIER else 1.0

            val accurate = attackManager.rollStaffAccuracy(player, target, attack.style, multiplier)
            if (!accurate) {
                manager.playSplashFx(this, target, clientDelay, fx.castSound, soundRadius = 10)
                manager.queueSplashHit(this, target, clientDelay, serverDelay)
            } else {
                val baseMaxHit = spec.maxHit(player.magicLvl).coerceAtLeast(1)
                var damage = attackManager.rollStaffMaxHit(player, target, baseMaxHit, multiplier)

                val healSpotanim = spec.healSpotanim
                if (healSpotanim != null && random.of(SANGUINESTI_HEAL_CHANCE) == 0) {
                    damage += SANGUINESTI_BONUS_DAMAGE
                    val heal = SanguinestiHeal(amount = damage / 2, spotanim = healSpotanim)
                    queue("queue.sanguinesti_heal", serverDelay.coerceAtLeast(1), heal)
                }

                manager.playMagicHitFx(
                    source = this,
                    target = target,
                    clientDelay = clientDelay,
                    castSound = fx.castSound,
                    soundRadius = 10,
                    hitSpot = fx.impactSpotanim,
                    hitSpotHeight = fx.impactHeight,
                    hitSound = fx.hitSound,
                )
                manager.giveCombatXp(this, target, attack, damage)
                manager.queueMagicHit(this, target, damage, clientDelay, serverDelay)

                // Npcs have no venom mechanic yet, so the trident's venom only applies to players.
                val envenom =
                    spec.venomChance > 0 &&
                        damage > 0 &&
                        target is Player &&
                        random.of(100) < spec.venomChance
                if (envenom) {
                    PlayerVenom.tryVenom(target as Player)
                }
            }

            if (chargesLeft == 0) {
                manager.stopCombat(this)
                mes(spec.outOfCharges)
                return
            }
            manager.continueCombat(this, target)
        }

        private fun ProtectedAccess.isRat(target: PathingEntity): Boolean {
            if (target !is Npc) {
                return false
            }
            return RAT_NAME.containsMatchIn(npcVisType(target).name)
        }
    }

    /**
     * A "(full)" trident: the first cast turns it into the charged trident holding
     * [TRIDENT_MAX_CHARGES] charges, then attacks with it.
     */
    private class FullTrident(
        private val charged: PoweredStaff,
        private val manager: WeaponAttackManager,
        private val objCharges: ObjChargeManager,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!fill()) {
                return true
            }
            return with(charged) { attack(target, attack) }
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            if (!fill()) {
                return true
            }
            return with(charged) { attack(target, attack) }
        }

        private fun ProtectedAccess.fill(): Boolean {
            val result =
                objCharges.addCharges(
                    inventory = player.worn,
                    slot = Wearpos.RightHand.slot,
                    add = TRIDENT_MAX_CHARGES,
                    internal = CHARGES_VAROBJ,
                    max = TRIDENT_MAX_CHARGES,
                )
            if (result.isFailure()) {
                manager.stopCombat(this)
                mes("Your trident fails to respond.")
                return false
            }
            return true
        }
    }

    private class UnchargedPoweredStaff(
        private val manager: WeaponAttackManager,
        private val message: String,
    ) : MagicWeapon {
        override suspend fun ProtectedAccess.attack(
            target: Npc,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminateAttack()
            return true
        }

        override suspend fun ProtectedAccess.attack(
            target: Player,
            attack: CombatAttack.Staff,
        ): Boolean {
            terminateAttack()
            return true
        }

        private fun ProtectedAccess.terminateAttack() {
            mes(message)
            manager.stopCombat(this)
        }
    }

    /** Args of the `queue.sanguinesti_heal` player queue. */
    data class SanguinestiHeal(val amount: Int, val spotanim: String)

    companion object {
        const val CHARGES_VAROBJ = "varobj.powered_staff_charges"

        const val TRIDENT_MAX_CHARGES = 2_500

        private const val WILDERNESS_MULTIPLIER = 1.5
        private const val SANGUINESTI_HEAL_CHANCE = 5
        private const val SANGUINESTI_BONUS_DAMAGE = 8
        private const val STARTER_STAFF_MAX_HIT = 8

        private val RAT_NAME = Regex("\\brats?\\b|scurrius", RegexOption.IGNORE_CASE)

        private const val CAST_STAFF = "seq.human_castwave_staff"
        private const val CAST_SOUND = "synth.shadow_cast"

        private val TRIDENT_SEAS_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.slayer_tots_casting",
                travelSpotanim = "spotanim.slayer_tots_projectile",
                impactSpotanim = "spotanim.slayer_tots_impact",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val TRIDENT_SEAS_ORN_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.slayer_tots_casting_orn_leagues6",
                travelSpotanim = "spotanim.slayer_tots_projectile_orn_leagues6",
                impactSpotanim = "spotanim.slayer_tots_impact_orn_leagues6",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val TRIDENT_SWAMP_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.toxic_tots_casting",
                travelSpotanim = "spotanim.toxic_tots_projectile",
                impactSpotanim = "spotanim.toxic_tots_impact",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val TRIDENT_SWAMP_ORN_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.toxic_tots_casting_orn_leagues6",
                travelSpotanim = "spotanim.toxic_tots_projectile_orn_leagues6",
                impactSpotanim = "spotanim.toxic_tots_impact_orn_leagues6",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val SANGUINESTI_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.sanguinesti_staff_casting",
                travelSpotanim = "spotanim.sanguinesti_staff_travel",
                impactSpotanim = "spotanim.sanguinesti_staff_impact",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val SANGUINESTI_HOLY_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = "spotanim.sanguinesti_staff_casting_justiciar",
                travelSpotanim = "spotanim.sanguinesti_staff_travel_justiciar",
                impactSpotanim = "spotanim.sanguinesti_staff_impact_justiciar",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val SCEPTRE_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = null,
                travelSpotanim = "spotanim.spells_thammaron01_travel01",
                impactSpotanim = null,
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val WARPED_SCEPTRE_FX =
            StaffFx(
                castAnim = "seq.pog_warped_sceptre_attack",
                castSpotanim = "spotanim.vfx_warped_sceptre_cast",
                travelSpotanim = "spotanim.vfx_warped_sceptre_projectile_projectile",
                impactSpotanim = "spotanim.vfx_warped_sceptre_projectile_impact",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val BONE_STAFF_FX =
            StaffFx(
                castAnim = CAST_STAFF,
                castSpotanim = null,
                travelSpotanim = "spotanim.spells_ratbone01_travel01",
                impactSpotanim = null,
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val EYE_OF_AYAK_FX =
            StaffFx(
                castAnim = "seq.human_eye_of_ayak_normal",
                castSpotanim = "spotanim.vfx_ayak_player_normal_spotanim",
                travelSpotanim = "spotanim.vfx_ayak_normal_projectile",
                impactSpotanim = "spotanim.vfx_ayak_normal_impact",
                castSound = CAST_SOUND,
                hitSound = null,
            )

        private val STARTER_STAFF_FX =
            StaffFx(
                castAnim = "seq.human_caststrike_staff",
                castSpotanim = "spotanim.starterspell_casting",
                travelSpotanim = "spotanim.starterspell_travel",
                impactSpotanim = "spotanim.starterspell_impact",
                castSound = "synth.firestrike_cast_and_fire",
                hitSound = "synth.firestrike_hit",
            )

        private val TUMEKENS_SHADOW_FX =
            StaffFx(
                castAnim = "seq.toa_sot_cast_b",
                castSpotanim = "spotanim.tumekens_shadow_casting",
                travelSpotanim = "spotanim.tumekens_shadow_travel",
                impactSpotanim = "spotanim.tumekens_shadow_impact",
                castSound = "synth.toa_shadow_weapon_cast_fire_01",
                hitSound = "synth.contact_darkness_impact",
                castSpotanimHeight = 0,
                projanim = "projanim.tumekens_shadow",
            )
    }
}
