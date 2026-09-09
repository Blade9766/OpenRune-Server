package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import kotlin.math.min
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.dsl.external
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.bosses.runtime.encounter
import org.rsmod.api.bosses.runtime.lob
import org.rsmod.api.bosses.runtime.repeatTick
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/** How one of the three followers fights. */
private class FollowerProfile(
    val god: God,
    val magicAnim: String,
    val meleeAnim: String,
    val meleeType: MeleeAttackType,
    val magicMaxHit: Int,
    val meleeMaxHit: Int,
    /** The stat the follower's god spell lowers by 1 + 5% when it lands. */
    val drainStat: String,
)

/**
 * Justiciar Zachariah, Derwen and Porazdir, the god followers of Mage Arena II.
 *
 * All three cast a strengthened version of their god's spell, swing at anyone in reach, freeze
 * with a dodgeable Ice Barrage and Tele Block for a minute or two. Each also has its own trick:
 * Porazdir lobs an energy ball that hurts less the further away it lands, Zachariah's sword wave
 * drags a player who stands still into his reach and binds them there, and Derwen scatters
 * energy balls that heal him until they are destroyed. Only the matching god spell can hurt
 * them.
 */
class GodFollowers
@Inject
constructor(
    private val deps: BossDeps,
    private val mageArena2: MageArena2Quest,
    private val npcRepo: NpcRepository,
    private val launcher: ProtectedAccessLauncher,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val profiles: Map<Int, FollowerProfile> = PROFILES.associateBy { it.god.followerId }

    private val spellObjs: Map<God, ItemServerType> =
        God.entries.associateWith { god ->
            ServerCacheManager.getItem(god.spellId) ?: error("Missing spell obj: ${god.spell}")
        }

    private val healerType: NpcServerType =
        ServerCacheManager.getNpc(HEALER.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $HEALER")

    private val spec =
        boss(*God.entries.map { it.follower }.toTypedArray()) {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)
            val godSpell = ability("follower_godspell", external(GOD_SPELL_HANDLER))
            val melee = ability("follower_melee", external(MELEE_HANDLER))
            val barrage = ability("follower_barrage", external(BARRAGE_HANDLER))
            val teleBlock = ability("follower_teleblock", external(TELEBLOCK_HANDLER))
            val special = ability("follower_special", external(SPECIAL_HANDLER))
            phase("fight") {
                forceEveryAttacks(SPECIAL_MIN_ATTACKS, SPECIAL_MAX_ATTACKS, special)
                weightedSelectorRandom {
                    +random(melee, weight = MELEE_WEIGHT, requires = WithinMeleeRange)
                    +random(godSpell, weight = GOD_SPELL_WEIGHT)
                    +random(barrage, weight = BARRAGE_WEIGHT, cooldown = BARRAGE_COOLDOWN)
                    +random(teleBlock, weight = TELEBLOCK_WEIGHT, cooldown = TELEBLOCK_COOLDOWN)
                }
            }
        }

    override fun ScriptContext.startup() {
        deps.extensionRegistry.register(GOD_SPELL_HANDLER) { access, npc, target, _ -> castGodSpell(access, npc, target) }
        deps.extensionRegistry.register(MELEE_HANDLER) { access, npc, target, _ -> melee(access, npc, target) }
        deps.extensionRegistry.register(BARRAGE_HANDLER) { access, npc, target, _ -> iceBarrage(access, npc, target) }
        deps.extensionRegistry.register(TELEBLOCK_HANDLER) { access, npc, target, _ -> teleBlock(access, npc, target) }
        deps.extensionRegistry.register(SPECIAL_HANDLER) { access, npc, target, _ -> special(access, npc, target) }
        BossCombat.register(
            this,
            spec,
            deps,
            onModifyHit = {
                val god = God.byFollower(npc.id)
                if (god != null && hit.isFromPlayer && !(hit.type == HitType.Magic && hit.isSecondaryObj(spellObjs.getValue(god)))) {
                    if (hit.damage > 0) {
                        hit.sourceSlot?.let { deps.playerList[it] }?.mes("${god.followerName} can only be harmed by ${god.spellName}.")
                    }
                    hit.damage = 0
                }
            },
        )
    }

    private fun castGodSpell(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        access.anim(profile.magicAnim)
        deps.worldRepo.soundArea(npc, profile.god.castSound, radius = SOUND_RADIUS)
        val hit = deps.accuracy.rollMagicAccuracy(npc, target, deps.random)
        val damage = if (hit) deps.random.of(0..profile.magicMaxHit) else 0
        target.spotanim(profile.god.impact, delay = IMPACT_CLIENT_DELAY, height = 0)
        target.finishNpcHit(npc, HIT_DELAY, HitType.Magic, damage, deps.playerHitModifier)
        if (damage > 0) {
            target.statSub(profile.drainStat, constant = DRAIN_CONSTANT, percent = DRAIN_PERCENT)
        }
    }

    private fun melee(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        access.anim(profile.meleeAnim)
        val hit = deps.accuracy.rollMeleeAccuracy(npc, target, profile.meleeType, deps.random)
        val damage = if (hit) deps.random.of(0..profile.meleeMaxHit) else 0
        target.finishNpcHit(npc, MELEE_HIT_DELAY, HitType.Melee, damage, deps.playerHitModifier)
    }

    /** Ice Barrage aimed at the tile the player is on; stepping off it before it lands dodges it. */
    private fun iceBarrage(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        access.anim(profile.magicAnim)
        val tile = target.coords
        val uid = target.uid
        deps.bossProjectile(
            spotanim = ICE_BARRAGE_TRAVEL,
            src = npc.coords.translate(1, 1),
            target = tile,
            startHeight = PROJECTILE_START_HEIGHT,
            endHeight = 0,
            delay = 0,
            travel = BARRAGE_TRAVEL_CYCLES,
            curve = BARRAGE_CURVE,
        )
        deps.worldQueues.add(BARRAGE_LAND_TICKS) {
            deps.worldRepo.spotanimMap(SpotanimType(ICE_BARRAGE_IMPACT), tile, 0)
            val player = uid.resolve(deps.playerList) ?: return@add
            if (player.coords != tile || player.hitpoints <= 0 || !npc.isSlotAssigned) {
                return@add
            }
            val hit = deps.accuracy.rollMagicAccuracy(npc, player, deps.random)
            val damage = if (hit) deps.random.of(0..BARRAGE_MAX_HIT) else 0
            player.finishNpcHit(npc, 1, HitType.Magic, damage, deps.playerHitModifier)
            if (hit) {
                CombatEffects.freeze(player, FREEZE_TICKS)
            }
        }
    }

    /** A follower's Tele Block lasts two minutes, one under Protect from Magic. */
    private fun teleBlock(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        access.anim(profile.magicAnim)
        if (mageArena2.isTeleBlocked(target)) {
            return
        }
        val ticks = if (target.isProtectingFromMagic()) TELEBLOCK_PRAYED_TICKS else TELEBLOCK_TICKS
        mageArena2.teleBlock(target, ticks)
        target.spotanim(TELE_BLOCK_IMPACT, delay = IMPACT_CLIENT_DELAY, height = TELE_BLOCK_HEIGHT)
        deps.worldRepo.soundArea(target, TELE_BLOCK_SOUND, radius = SOUND_RADIUS)
        target.mes("<col=ef1020>You have been teleblocked!</col>")
    }

    private fun special(access: StandardNpcAccess, npc: Npc, target: Player) {
        when (God.byFollower(npc.id)) {
            God.ZAMORAK -> energyBall(access, npc, target)
            God.SARADOMIN -> swordWave(access, npc, target)
            God.GUTHIX -> healingBalls(access, npc, target)
            null -> {}
        }
    }

    /** Porazdir's energy ball: up to 30 through prayer next to him, nothing twelve tiles away. */
    private fun energyBall(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        target.mes("<col=ef1020>Porazdir fires a ball of energy directly linked to his power!</col>")
        access.anim(profile.magicAnim)
        deps.lob(
            npc = npc,
            targetTile = target.coords,
            targetUid = target.uid,
            spotanim = ENERGY_BALL,
            startHeight = PROJECTILE_START_HEIGHT,
            endHeight = 0,
            delay = 0,
            travel = ENERGY_BALL_TRAVEL_CYCLES,
            curve = ENERGY_BALL_CURVE,
            landTicks = ENERGY_BALL_LAND_TICKS,
            landGfx = ENERGY_BALL,
        ) { player ->
            val distance = npc.distanceTo(player)
            val maxHit = (ENERGY_BALL_MAX_HIT * (ENERGY_BALL_RANGE - distance)) / ENERGY_BALL_RANGE
            if (maxHit > 0) {
                player.finishNpcHit(npc, 1, HitType.Typeless, deps.random.of(0..maxHit), deps.playerHitModifier)
            }
        }
    }

    /**
     * Zachariah's sword wave: aimed at the tile the player is on. Anyone still there when it
     * lands is dragged to his side and held, and his sword falls every three ticks for a while.
     */
    private fun swordWave(access: StandardNpcAccess, npc: Npc, target: Player) {
        target.mes("<col=ef1020>Zachariah swings his sword, sending a wave of energy towards you!</col>")
        access.anim(SWORD_WAVE_ANIM)
        val tile = target.coords
        val uid = target.uid
        deps.bossProjectile(
            spotanim = SWORD_WAVE,
            src = npc.coords.translate(1, 1),
            target = tile,
            startHeight = SWORD_WAVE_HEIGHT,
            endHeight = 0,
            delay = 0,
            travel = SWORD_WAVE_TRAVEL_CYCLES,
            curve = SWORD_WAVE_CURVE,
        )
        deps.worldQueues.add(SWORD_WAVE_LAND_TICKS) {
            val player = uid.resolve(deps.playerList) ?: return@add
            if (player.coords != tile || player.hitpoints <= 0 || !npc.isSlotAssigned) {
                return@add
            }
            val dest = collision.tilesAround(npc.coords, npc.type.size, player.coords).firstOrNull() ?: return@add
            CombatEffects.stun(player, BIND_TICKS)
            launcher.launch(player) {
                telejump(dest, TeleportType.Exempt)
                mes("<col=ef1020>The wave drags you to Zachariah and holds you in place!</col>")
            }
            val encounter = deps.encounter(npc)
            encounter.attackRateOverride = FAST_SWORD_RATE
            deps.worldQueues.add(FAST_SWORD_TICKS) {
                if (npc.isSlotAssigned) {
                    deps.encounter(npc).attackRateOverride = null
                }
            }
        }
    }

    /** Derwen's energy balls: each heals him 5 hitpoints every few ticks until it is destroyed. */
    private fun healingBalls(access: StandardNpcAccess, npc: Npc, target: Player) {
        val profile = profiles[npc.id] ?: return
        target.mes("<col=ef1020>Derwen releases balls of energy that begin to heal him!</col>")
        access.anim(profile.magicAnim)
        val center = npc.coords.translate(1, 1)
        val tiles = collision.tilesAround(npc.coords, npc.type.size, target.coords).shuffled().take(HEALER_COUNT)
        for (tile in tiles) {
            deps.bossProjectile(
                spotanim = HEALER_PROJECTILE,
                src = center,
                target = tile,
                startHeight = PROJECTILE_START_HEIGHT,
                endHeight = 0,
                delay = 0,
                travel = HEALER_TRAVEL_CYCLES,
                curve = HEALER_CURVE,
            )
            deps.worldQueues.add(1) {
                if (!npc.isSlotAssigned) {
                    return@add
                }
                val ball = Npc(healerType, tile)
                ball.mode = NpcMode.None
                npcRepo.add(ball, HEALER_LIFETIME)
                healWhileAlive(npc, ball)
            }
        }
    }

    private fun healWhileAlive(follower: Npc, ball: Npc) {
        deps.repeatTick(
            HEALER_LIFETIME,
            onTick = { remaining ->
                if (!ball.isSlotAssigned || !follower.isSlotAssigned || follower.hitpoints <= 0) {
                    false
                } else {
                    if (remaining % HEAL_INTERVAL == 0) {
                        follower.hitpoints = min(follower.baseHitpointsLvl, follower.hitpoints + HEAL_AMOUNT)
                        ball.spotanim(HEAL_SPOTANIM)
                    }
                    true
                }
            },
        )
    }

    private companion object {
        val PROFILES =
            listOf(
                FollowerProfile(
                    god = God.SARADOMIN,
                    magicAnim = "seq.wild_zealot_magic",
                    meleeAnim = "seq.wild_zealot_slash",
                    meleeType = MeleeAttackType.Slash,
                    magicMaxHit = 26,
                    meleeMaxHit = 43,
                    drainStat = "stat.prayer",
                ),
                FollowerProfile(
                    god = God.GUTHIX,
                    magicAnim = "seq.ent_boss_attack_magic",
                    meleeAnim = "seq.ent_boss_attack_melee",
                    meleeType = MeleeAttackType.Crush,
                    magicMaxHit = 43,
                    meleeMaxHit = 16,
                    drainStat = "stat.defence",
                ),
                FollowerProfile(
                    god = God.ZAMORAK,
                    magicAnim = "seq.zamorak_demon_boss_attack_magic",
                    meleeAnim = "seq.zamorak_demon_boss_attack_melee",
                    meleeType = MeleeAttackType.Slash,
                    magicMaxHit = 43,
                    meleeMaxHit = 16,
                    drainStat = "stat.magic",
                ),
            )

        const val HEALER = "npc.ma2_guthix_healer"

        const val GOD_SPELL_HANDLER = "ma2.godspell"
        const val MELEE_HANDLER = "ma2.melee"
        const val BARRAGE_HANDLER = "ma2.barrage"
        const val TELEBLOCK_HANDLER = "ma2.teleblock"
        const val SPECIAL_HANDLER = "ma2.special"

        const val ATTACK_RATE = 6
        const val AGGRESSION_RADIUS = 12
        const val MELEE_WEIGHT = 3
        const val GOD_SPELL_WEIGHT = 4
        const val BARRAGE_WEIGHT = 1
        const val BARRAGE_COOLDOWN = 25
        const val TELEBLOCK_WEIGHT = 1
        const val TELEBLOCK_COOLDOWN = 120
        const val SPECIAL_MIN_ATTACKS = 4
        const val SPECIAL_MAX_ATTACKS = 6

        const val DRAIN_CONSTANT = 1
        const val DRAIN_PERCENT = 5
        const val SOUND_RADIUS = 8
        const val IMPACT_CLIENT_DELAY = 30
        const val HIT_DELAY = 2
        const val MELEE_HIT_DELAY = 1
        const val PROJECTILE_START_HEIGHT = 100

        const val ICE_BARRAGE_TRAVEL = 368
        const val ICE_BARRAGE_IMPACT = 369
        const val BARRAGE_TRAVEL_CYCLES = 50
        const val BARRAGE_CURVE = 15
        const val BARRAGE_LAND_TICKS = 2
        const val BARRAGE_MAX_HIT = 30
        const val FREEZE_TICKS = 16

        const val TELE_BLOCK_IMPACT = "spotanim.tele_block_impact"
        const val TELE_BLOCK_HEIGHT = 124
        const val TELE_BLOCK_SOUND = "synth.teleportblock_impact"

        /** Two minutes, or one under Protect from Magic. */
        const val TELEBLOCK_TICKS = 200
        const val TELEBLOCK_PRAYED_TICKS = 100

        const val ENERGY_BALL = 1514
        const val ENERGY_BALL_TRAVEL_CYCLES = 60
        const val ENERGY_BALL_CURVE = 25
        const val ENERGY_BALL_LAND_TICKS = 3
        const val ENERGY_BALL_MAX_HIT = 30
        const val ENERGY_BALL_RANGE = 12

        const val SWORD_WAVE = 1515
        const val SWORD_WAVE_ANIM = "seq.wild_zealot_projectile"
        const val SWORD_WAVE_HEIGHT = 30
        const val SWORD_WAVE_TRAVEL_CYCLES = 40
        const val SWORD_WAVE_CURVE = 5
        const val SWORD_WAVE_LAND_TICKS = 2
        const val BIND_TICKS = 5
        const val FAST_SWORD_RATE = 3
        const val FAST_SWORD_TICKS = 12

        const val HEALER_PROJECTILE = 1512
        const val HEALER_TRAVEL_CYCLES = 30
        const val HEALER_CURVE = 15
        const val HEALER_COUNT = 3
        const val HEALER_LIFETIME = 60
        const val HEAL_INTERVAL = 5
        const val HEAL_AMOUNT = 5
        const val HEAL_SPOTANIM = "spotanim.ma2_guthix_proj_small"
    }
}

/** A dead follower stops answering the symbol until the miniquest is done, and its Tele Block lifts. */
class GodFollowerKillHook @Inject constructor(private val mageArena2: MageArena2Quest) : NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        val god = God.byFollower(context.npc.id) ?: return
        val hero = context.hero
        mageArena2.killed.getValue(god).set(hero, true)
        mageArena2.clearTeleBlock(hero)
        hero.mes("You have defeated ${god.followerName}. Take the remains to Kolodion.")
    }
}

/** A follower's Tele Block stops every teleport, including the arena levers. */
class MageArenaTeleBlockHook @Inject constructor(private val mageArena2: MageArena2Quest) : PlayerTeleportValidateHook {
    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? =
        if (mageArena2.isTeleBlocked(player)) "A magical force stops you from teleporting." else null
}
