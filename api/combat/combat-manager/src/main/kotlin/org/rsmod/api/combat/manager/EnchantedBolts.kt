package org.rsmod.api.combat.manager

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.combat.commons.CombatAttack
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.DragonfireProtection
import org.rsmod.api.config.refs.params
import org.rsmod.api.mechanics.toxins.impl.NpcPoison
import org.rsmod.api.mechanics.toxins.impl.PlayerPoison
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.stat.agilityLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.prayerLvl
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.random.GameRandom
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PathingEntity
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.game.type.getInvObj

/**
 * The special effects of enchanted crossbow bolts, rolled in place of a plain ranged damage roll.
 *
 * Opal, jade, pearl, red topaz, ruby and diamond roll their effect before, and independently of,
 * the accuracy roll; the damage-dealing ones among them then land without an accuracy roll.
 * Sapphire, emerald, dragonstone and onyx only roll their effect once the bolt has hit. The
 * hard Kandarin diary raises every chance by 10%, the Armadyl crossbow special doubles it, the
 * Zaryte crossbow strengthens the effects by 10% and its special forces the effect on a hit.
 */
public class EnchantedBolts
@Inject
constructor(private val random: GameRandom, private val manager: PlayerAttackManager) {
    /**
     * Rolls the damage of one bolt fired by [source] at [target], including any bolt effect. Pass
     * the result to [applyEffect] once the hit is queued.
     *
     * @param ammo The ammunition fired; `null` (or anything that is not an enchanted bolt) rolls
     *   a plain ranged hit.
     * @param procChanceMultiplier Scales the effect chance (the Armadyl crossbow special uses 2).
     * @param guaranteedOnHit Forces the effect whenever the bolt passes its accuracy roll.
     */
    public fun shoot(
        source: Player,
        target: PathingEntity,
        attack: CombatAttack.Ranged,
        ammo: ItemServerType?,
        accuracyMultiplier: Double = 1.0,
        maxHitMultiplier: Double = 1.0,
        procChanceMultiplier: Int = 1,
        guaranteedOnHit: Boolean = false,
    ): BoltShot {
        val bolt = ammo?.let { boltsById[it.id] }
        if (bolt == null) {
            val damage =
                manager.rollRangedDamage(
                    source = source,
                    target = target,
                    attack = attack,
                    accuracyMultiplier = accuracyMultiplier,
                    maxHitMultiplier = maxHitMultiplier,
                )
            return BoltShot(damage)
        }
        val roll = Roll(source, target, attack, accuracyMultiplier, maxHitMultiplier)
        val zaryte = getInvObj(attack.weapon).id == zaryteCrossbow
        val eligible = isEligible(source, target, bolt)
        val chance = procChance(source, target, bolt) * procChanceMultiplier

        if (bolt.bypassesAccuracy) {
            var landed: Boolean? = null
            var proc = false
            if (guaranteedOnHit) {
                landed = roll.accuracy()
                proc = landed && eligible
            }
            if (!proc) {
                proc = eligible && random.of(CHANCE_SCALE) < chance
            }
            if (proc && bolt.guaranteesHit) {
                return shot(source, target, bolt, zaryte, roll)
            }
            val hit = landed ?: roll.accuracy()
            val damage = if (hit) roll.maxHit() else 0
            return if (proc) BoltShot(damage, bolt, zaryte) else BoltShot(damage)
        }

        if (!roll.accuracy()) {
            return BoltShot(0)
        }
        val proc = eligible && (guaranteedOnHit || random.of(CHANCE_SCALE) < chance)
        return if (proc) shot(source, target, bolt, zaryte, roll) else BoltShot(roll.maxHit())
    }

    /**
     * Plays the effect graphic and sound of [shot] on [target] as the bolt lands, and applies the
     * effect's non-damage part: ruby's self-damage, onyx's heal, sapphire's prayer drain, emerald's
     * poison, jade's knockdown and red topaz's Magic drain. Does nothing for a shot without one.
     */
    public fun applyEffect(
        source: Player,
        target: PathingEntity,
        shot: BoltShot,
        clientDelay: Int,
        hitDelay: Int,
    ) {
        val bolt = shot.bolt ?: return
        if (bolt == Bolt.Jade && target is Player && dodgesKnockdown(target)) {
            return
        }
        target.spotanim(bolt.spotanim, delay = clientDelay)
        manager.soundArea(
            source = target.coords,
            synth = bolt.synth,
            delay = clientDelay,
            loops = 1,
            radius = EFFECT_SOUND_RADIUS,
            size = 0,
        )
        when (bolt) {
            Bolt.Ruby -> {
                val cost = source.hitpoints / RUBY_SELF_DAMAGE_DIVISOR
                if (cost > 0) {
                    source.queueHit(
                        delay = hitDelay,
                        type = HitType.Typeless,
                        damage = cost,
                        modifier = NoopPlayerHitModifier,
                    )
                }
            }
            Bolt.Onyx -> {
                val heal = min(shot.damage, currentHitpoints(target)) / ONYX_HEAL_DIVISOR
                if (heal > 0) {
                    source.statHeal(HITPOINTS, constant = heal, percent = 0)
                }
            }
            Bolt.Sapphire -> clearMind(source, target, shot)
            Bolt.Emerald -> {
                val damage = if (shot.zaryte) ZARYTE_POISON_DAMAGE else POISON_DAMAGE
                when (target) {
                    is Npc -> NpcPoison.tryPoison(target, damage)
                    is Player -> PlayerPoison.tryPoison(target, initialDamage = damage)
                }
            }
            Bolt.Jade -> if (target is Player) CombatEffects.freeze(target, KNOCKDOWN_TICKS)
            Bolt.Topaz -> if (target is Player) target.statSub(MAGIC, constant = 1, percent = 0)
            Bolt.Opal,
            Bolt.Pearl,
            Bolt.Diamond,
            Bolt.Dragonstone -> Unit
        }
    }

    private fun shot(
        source: Player,
        target: PathingEntity,
        bolt: Bolt,
        zaryte: Boolean,
        roll: Roll,
    ): BoltShot {
        val ranged = source.stat(RANGED)
        val damage =
            when (bolt) {
                Bolt.Opal -> roll.maxHit(boltSpecDamage = ranged / if (zaryte) 9 else 10)
                Bolt.Pearl -> {
                    val divisor =
                        when {
                            isFiery(target) -> if (zaryte) 13 else 15
                            else -> if (zaryte) 18 else 20
                        }
                    roll.maxHit(boltSpecDamage = ranged / divisor)
                }
                Bolt.Ruby -> {
                    val percent = if (zaryte) 22 else 20
                    val cap = if (zaryte) ZARYTE_RUBY_CAP else RUBY_CAP
                    min(currentHitpoints(target) * percent / 100, cap)
                }
                Bolt.Diamond -> roll.maxHit(multiplier = if (zaryte) 1.26 else 1.15)
                Bolt.Dragonstone -> {
                    val percent = if (zaryte) 22 else 20
                    roll.maxHit(boltSpecDamage = ranged * percent / 100)
                }
                Bolt.Onyx -> roll.maxHit(multiplier = if (zaryte) 1.32 else 1.2)
                Bolt.Jade,
                Bolt.Topaz,
                Bolt.Sapphire,
                Bolt.Emerald -> roll.maxHit()
            }
        return BoltShot(damage, bolt, zaryte)
    }

    private fun isEligible(source: Player, target: PathingEntity, bolt: Bolt): Boolean =
        when (bolt) {
            Bolt.Jade -> target is Player && target.vars[PROTECT_FROM_MAGIC] == 0
            Bolt.Topaz -> target is Player
            Bolt.Pearl -> target !is Player || WATER_STAVES.none { it in target.worn }
            Bolt.Ruby -> source.hitpoints / RUBY_SELF_DAMAGE_DIVISOR > 0
            Bolt.Dragonstone ->
                when (target) {
                    is Npc -> !isFiery(target) && target.visType.param(params.draconic) == 0
                    is Player ->
                        !DragonfireProtection.isProtected(target) &&
                            target.vars[PROTECT_FROM_MAGIC] == 0
                }
            Bolt.Onyx -> target !is Npc || target.visType.param(params.undead) == 0
            Bolt.Emerald ->
                target !is Npc || (target.visType.paramOrNull(params.poison_immunity) ?: 0) == 0
            Bolt.Opal,
            Bolt.Sapphire,
            Bolt.Diamond -> true
        }

    private fun procChance(source: Player, target: PathingEntity, bolt: Bolt): Int {
        val base = if (target is Player) bolt.pvpChance else bolt.pvmChance
        val diary = source.vars[KANDARIN_HARD_DIARY] != 0
        return if (diary) base * 11 / 10 else base
    }

    private fun clearMind(source: Player, target: PathingEntity, shot: BoltShot) {
        when (target) {
            is Npc -> {
                val drain = target.rangedLvl / 10
                val scaled = if (shot.zaryte) drain * 11 / 10 else drain
                val restore = scaled / 8
                if (restore > 0) {
                    source.statHeal(PRAYER, constant = restore, percent = 0)
                }
            }
            is Player -> {
                val base = source.stat(RANGED) / if (shot.zaryte) 18 else 20
                val remaining = (target.hitpoints - shot.damage).coerceAtLeast(0)
                val drain = minOf(base, remaining, target.prayerLvl)
                if (drain <= 0) {
                    return
                }
                target.statSub(PRAYER, constant = drain, percent = 0)
                val restore = drain / 2
                if (restore > 0) {
                    source.statHeal(PRAYER, constant = restore, percent = 0)
                }
            }
        }
    }

    /**
     * The chance to shrug off Earth's Fury runs linearly from -16% at Agility 1 to 110% at 99.
     */
    private fun dodgesKnockdown(target: Player): Boolean {
        val dodge = -16 + (target.agilityLvl - 1) * 126 / 98
        return random.of(100) < dodge
    }

    private fun isFiery(target: PathingEntity): Boolean =
        when (target) {
            is Npc -> {
                val name = target.visType.name.lowercase()
                name in FIERY_NPCS || (name.startsWith("brutal ") && name.endsWith(" dragon"))
            }
            is Player -> FIERY_WORN.any { it in target.worn }
        }

    private fun currentHitpoints(target: PathingEntity): Int =
        when (target) {
            is Npc -> target.hitpoints
            is Player -> target.hitpoints
        }

    private inner class Roll(
        private val source: Player,
        private val target: PathingEntity,
        private val attack: CombatAttack.Ranged,
        private val accuracyMultiplier: Double,
        private val maxHitMultiplier: Double,
    ) {
        fun accuracy(): Boolean =
            manager.rollRangedAccuracy(
                source = source,
                target = target,
                attackType = attack.type,
                attackStyle = attack.style,
                blockType = attack.type,
                multiplier = accuracyMultiplier,
            )

        fun maxHit(multiplier: Double = 1.0, boltSpecDamage: Int = 0): Int =
            manager.rollRangedMaxHit(
                source = source,
                target = target,
                attackType = attack.type,
                attackStyle = attack.style,
                multiplier = maxHitMultiplier * multiplier,
                boltSpecDamage = boltSpecDamage,
            )
    }

    /** Chances are in tenths of a percent, as listed on the wiki's PvM/PvP proc table. */
    internal enum class Bolt(
        val pvmChance: Int,
        val pvpChance: Int,
        val bypassesAccuracy: Boolean,
        val guaranteesHit: Boolean,
        val spotanim: String,
        val synth: String,
        val objs: List<String>,
    ) {
        Opal(
            pvmChance = 50,
            pvpChance = 50,
            bypassesAccuracy = true,
            guaranteesHit = true,
            spotanim = "spotanim.xbows_lucky_lightening_strike_spot_anim",
            synth = "synth.lucky_lightning",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_bronze_tipped_opal_enchanted",
                    "obj.dragon_bolts_enchanted_opal",
                ),
        ),
        Jade(
            pvmChance = 0,
            pvpChance = 60,
            bypassesAccuracy = true,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_earths_fury_spot_anim",
            synth = "synth.earths_fury",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_blurite_tipped_jade_enchanted",
                    "obj.dragon_bolts_enchanted_jade",
                ),
        ),
        Pearl(
            pvmChance = 60,
            pvpChance = 60,
            bypassesAccuracy = true,
            guaranteesHit = true,
            spotanim = "spotanim.xbows_sea_curse_waterfall_spot_anim",
            synth = "synth.sea_curse",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_iron_tipped_pearl_enchanted",
                    "obj.dragon_bolts_enchanted_pearl",
                ),
        ),
        Topaz(
            pvmChance = 0,
            pvpChance = 40,
            bypassesAccuracy = true,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_down_to_earth_spot_anim",
            synth = "synth.down_to_earth",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_steel_tipped_redtopaz_enchanted",
                    "obj.dragon_bolts_enchanted_topaz",
                ),
        ),
        Sapphire(
            pvmChance = 250,
            pvpChance = 50,
            bypassesAccuracy = false,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_clear_mind_glowing_spot_anim",
            synth = "synth.clear_mind",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_mithril_tipped_sapphire_enchanted",
                    "obj.dragon_bolts_enchanted_sapphire",
                ),
        ),
        Emerald(
            pvmChance = 550,
            pvpChance = 540,
            bypassesAccuracy = false,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_magical_poison_spot_anim",
            synth = "synth.magical_poison",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_mithril_tipped_emerald_enchanted",
                    "obj.dragon_bolts_enchanted_emerald",
                ),
        ),
        Ruby(
            pvmChance = 60,
            pvpChance = 110,
            bypassesAccuracy = true,
            guaranteesHit = true,
            spotanim = "spotanim.xbows_blood_sacrifice_spot_anim",
            synth = "synth.blood_sacrifice",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_adamantite_tipped_ruby_enchanted",
                    "obj.dragon_bolts_enchanted_ruby",
                ),
        ),
        Diamond(
            pvmChance = 100,
            pvpChance = 50,
            bypassesAccuracy = true,
            guaranteesHit = true,
            spotanim = "spotanim.xbows_diamond_tips_spotanim",
            synth = "synth.crossbow_diamond",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_adamantite_tipped_diamond_enchanted",
                    "obj.dragon_bolts_enchanted_diamond",
                ),
        ),
        Dragonstone(
            pvmChance = 60,
            pvpChance = 60,
            bypassesAccuracy = false,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_dragons_breath_spot_anim",
            synth = "synth.dragons_breath",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_runite_tipped_dragonstone_enchanted",
                    "obj.dragon_bolts_enchanted_dragonstone",
                ),
        ),
        Onyx(
            pvmChance = 110,
            pvpChance = 100,
            bypassesAccuracy = false,
            guaranteesHit = false,
            spotanim = "spotanim.xbows_life_leach_spot_anim",
            synth = "synth.life_leech",
            objs =
                listOf(
                    "obj.xbows_crossbow_bolts_runite_tipped_onyx_enchanted",
                    "obj.dragon_bolts_enchanted_onyx",
                ),
        ),
    }

    private companion object {
        const val CHANCE_SCALE = 1000
        const val EFFECT_SOUND_RADIUS = 10
        const val RUBY_SELF_DAMAGE_DIVISOR = 10
        const val RUBY_CAP = 100
        const val ZARYTE_RUBY_CAP = 110
        const val ONYX_HEAL_DIVISOR = 4
        const val POISON_DAMAGE = 5
        const val ZARYTE_POISON_DAMAGE = 6
        const val KNOCKDOWN_TICKS = 8

        const val HITPOINTS = "stat.hitpoints"
        const val PRAYER = "stat.prayer"
        const val RANGED = "stat.ranged"
        const val MAGIC = "stat.magic"
        const val PROTECT_FROM_MAGIC = "varbit.prayer_protectfrommagic"
        const val KANDARIN_HARD_DIARY = "varbit.kandarin_diary_hard_complete"

        val zaryteCrossbow: Int by lazy { "obj.zaryte_xbow".asRSCM(RSCMType.OBJ) }

        val boltsById: Map<Int, Bolt> by lazy {
            Bolt.entries
                .flatMap { bolt -> bolt.objs.map { it.asRSCM(RSCMType.OBJ) to bolt } }
                .toMap()
        }

        val WATER_STAVES =
            listOf(
                "obj.staff_of_water",
                "obj.water_battlestaff",
                "obj.mystic_water_staff",
                "obj.dramen_staff_water",
            )

        val FIERY_WORN =
            listOf(
                "obj.staff_of_fire",
                "obj.fire_battlestaff",
                "obj.mystic_fire_staff",
                "obj.dramen_staff_fire",
                "obj.tzhaar_cape_fire",
                "obj.tzhaar_cape_fire_trouver",
            )

        /** Monsters with the wiki's fiery attribute; brutal dragons are matched by name. */
        val FIERY_NPCS =
            setOf(
                "adamant dragon",
                "black dragon",
                "blue dragon",
                "branda the fire queen",
                "bronze dragon",
                "fire elemental",
                "fire giant",
                "frost dragon",
                "galvek",
                "green dragon",
                "iron dragon",
                "king black dragon",
                "lava dragon",
                "mithril dragon",
                "pyrefiend",
                "red dragon",
                "rune dragon",
                "steel dragon",
                "vorkath",
            )
    }
}

/**
 * One bolt rolled by [EnchantedBolts.shoot]: the [damage] to queue, and the bolt effect that
 * activated with it, if any.
 */
public class BoltShot
internal constructor(
    public val damage: Int,
    internal val bolt: EnchantedBolts.Bolt? = null,
    internal val zaryte: Boolean = false,
) {
    public val procced: Boolean
        get() = bolt != null
}
