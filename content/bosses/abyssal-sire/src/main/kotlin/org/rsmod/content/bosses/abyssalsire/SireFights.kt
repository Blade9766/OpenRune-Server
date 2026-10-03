package org.rsmod.content.bosses.abyssalsire

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import java.util.IdentityHashMap
import kotlin.math.max
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.bossProjectile
import org.rsmod.api.combat.commons.CombatEffects
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.npc.apPlayer2
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.route.RouteFactory
import org.rsmod.api.route.walkTo
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.flag.CollisionFlag

/**
 * Runs every Abyssal Sire fight. The framework's boss spec only claims the Sire's combat and hit
 * events; the fight itself is this class's per-tick loop, driven by the stage in [SireFight]:
 *
 * - **Waking / Lungs (phase 1).** The Sire stays on its throne at full health while its tentacles
 *   guard the four respiratory systems, which take at most 3 damage a hit until the Sire is
 *   disoriented (a Shadow spell roll, or 75 ranged/magic damage). It throws spawns and miasma at a
 *   player within 10 tiles.
 * - **Melee (phase 2).** Once the systems are dead it walks south (taking half damage, and unable to
 *   die on the way) and meleeing anyone within 2 tiles, else throwing spawns, miasma or pulling the
 *   player in for a portal blast.
 * - **Panic / Apocalypse (phase 3).** At 212 hitpoints it walks to the chamber's centre, summons
 *   four spawns and pours miasma; below 140 it pulls the player to row 2 and explodes, then keeps
 *   spawning up to [MINION_CAP] minions.
 *
 * A fight ends when the Sire dies (it respawns on its throne at once) or its player is gone for a
 * minute, which resets the chamber.
 */
@Singleton
class SireFights
@Inject
constructor(
    private val deps: BossDeps,
    private val npcList: NpcList,
    private val routeFactory: RouteFactory,
    private val aiPlayerInteractions: AiPlayerInteractions,
) {
    private val fights: MutableMap<Npc, SireFight> = IdentityHashMap()

    private val now: Int
        get() = deps.mapClock.cycle

    fun fightOf(sire: Npc): SireFight? = fights[sire]

    fun fightIn(chamber: SireChamber): SireFight? = fights.values.firstOrNull { it.chamber == chamber }

    fun fightFor(player: Player): SireFight? = fights.values.firstOrNull { it.hero === player }

    fun allFights(): Collection<SireFight> = fights.values

    /* Hits on the Sire */

    /**
     * Adjusts a hit before it lands: no melee reaches the throne in phase 1, walking Sires take half
     * damage and survive at [WALK_TO_MELEE_RESTORE] / [WALK_TO_CENTRE_RESTORE] hitpoints, and
     * phase 1 damage only feeds the disorientation counter.
     */
    fun modifySireHit(sire: Npc, attacker: Player?, type: HitType, damage: Int, shadowStun: Boolean): Int {
        var fight = fights[sire]
        if (fight == null) {
            if (attacker == null || type == HitType.Melee) return 0
            fight = wake(sire, attacker)
        }
        if (attacker != null && attacker === fight.hero) {
            fight.heroLastActive = now
            fight.heroLastHitSire = now
        }
        return when (fight.stage) {
            SireStage.Waking,
            SireStage.Lungs -> {
                if (type == HitType.Melee) return 0
                if (type == HitType.Ranged || type == HitType.Magic) fight.stunDamage += damage
                if (shadowStun || fight.stunDamage >= STUN_DAMAGE) {
                    if (fight.stage == SireStage.Waking) fight.pendingStun = true else disorient(fight)
                }
                damage
            }
            SireStage.WalkingToMelee -> walkingDamage(sire, damage, WALK_TO_MELEE_RESTORE)
            SireStage.WalkingToCentre -> walkingDamage(sire, damage, WALK_TO_CENTRE_RESTORE)
            SireStage.Dying -> 0
            else -> damage
        }
    }

    private fun walkingDamage(sire: Npc, damage: Int, restore: Int): Int {
        val halved = damage / 2
        if (sire.hitpoints - halved > 0) return halved
        sire.hitpoints = restore
        return 0
    }

    /** Runs once a hit has landed: phase 1 heals the damage back, later phases check thresholds. */
    fun afterSireHit(sire: Npc) {
        val fight = fights[sire] ?: return
        when (fight.stage) {
            SireStage.Waking,
            SireStage.Lungs -> sire.hitpoints = sire.baseHitpointsLvl
            SireStage.Melee -> if (sire.hitpoints in 1..PANIC_HITPOINTS) startWalkToCentre(fight)
            SireStage.Panic -> if (sire.hitpoints in 1 until APOCALYPSE_HITPOINTS) startApocalypse(fight)
            else -> Unit
        }
    }

    /* Phase 1 */

    private fun wake(sire: Npc, hero: Player): SireFight {
        val chamber = SireChamber.byThrone(sire.spawnCoords) ?: SireChamber.containing(sire.coords)!!
        val fight = SireFight(sire, chamber, hero, now)
        fights[sire] = fight
        fight.lungs += chamberNpcs(chamber.lungs, LUNG)
        fight.tentacles += chamberNpcs(chamber.tentacles, null)
        sire.movementLocked = true
        sire.anim(SIRE_WAKE_SEQ)
        for (tentacle in fight.tentacles) {
            val reversed = tentacle.type.isType(TENTACLE_NORTH)
            transform(tentacle, TENTACLE_ACTIVE, keepHitpoints = false)
            tentacle.anim(if (reversed) TENTACLE_WAKE_REVERSED_SEQ else TENTACLE_WAKE_SEQ)
        }
        fight.nextAttack = now + WAKE_TICKS + FIRST_ATTACK_DELAY
        tick(fight)
        return fight
    }

    private fun finishWaking(fight: SireFight) {
        fight.stage = SireStage.Lungs
        fight.stageStartedAt = now
        transform(fight.sire, SIRE_AWAKE)
        if (fight.pendingStun) {
            fight.pendingStun = false
            disorient(fight)
        }
        if (deadLungs(fight) >= fight.lungs.size) startWalkToMelee(fight)
    }

    fun disorient(fight: SireFight) {
        fight.stunDamage = 0
        fight.sireStunnedUntil = now + SIRE_STUN_TICKS
        val wereActive = !fight.tentaclesStunned(now)
        fight.tentaclesStunnedUntil = now + TENTACLE_STUN_TICKS
        transform(fight.sire, SIRE_STUNNED)
        if (wereActive) {
            for (tentacle in fight.tentacles) {
                transform(tentacle, TENTACLE_STUNNED, keepHitpoints = false)
                tentacle.anim(TENTACLE_STUN_SEQ)
            }
        }
    }

    private fun endTentacleStun(fight: SireFight) {
        fight.tentaclesStunnedUntil = -1
        for (tentacle in fight.tentacles) {
            transform(tentacle, TENTACLE_ACTIVE, keepHitpoints = false)
            tentacle.anim(TENTACLE_UNSTUN_SEQ)
        }
    }

    /**
     * Hits on a respiratory system land in full only while its chamber's tentacles are stunned, and
     * then never for less than half the attacker's max hit.
     */
    fun modifyLungHit(lung: Npc, attacker: Player?, type: HitType, halberd: Boolean, damage: Int): Int {
        if (attacker == null) return damage
        if (type == HitType.Melee && !halberd) return 0
        val fight = SireChamber.containing(lung.coords)?.let(::fightIn)
        if (fight != null && attacker === fight.hero) fight.heroLastActive = now
        if (fight != null && fight.tentaclesStunned(now)) {
            return if (damage > 0) maxOf(damage, attacker.vars[MAX_HIT_VARP] / 2) else 0
        }
        return minOf(damage, deps.random.of(0, PROTECTED_LUNG_MAX_HIT))
    }

    /** A respiratory system has died: it stays as a silent vent until the chamber resets. */
    fun lungDied(lung: Npc) {
        transform(lung, LUNG_DYING, keepHitpoints = false)
        lung.hideAllOps()
        lung.anim(LUNG_DEATH_SEQ)
        val fight = SireChamber.containing(lung.coords)?.let(::fightIn) ?: return
        if (fight.lungXpGiven < fight.lungs.size) {
            fight.lungXpGiven++
            fight.hero.statAdvance(SLAYER, LUNG_SLAYER_XP)
        }
        if (fight.stage == SireStage.Lungs && deadLungs(fight) >= fight.lungs.size) startWalkToMelee(fight)
    }

    private fun deadLungs(fight: SireFight): Int = fight.lungs.count { it.visType.isType(LUNG_DYING) }

    /* Transitions */

    private fun startWalkToMelee(fight: SireFight) {
        fight.stage = SireStage.WalkingToMelee
        fight.stageStartedAt = now
        fight.sireStunnedUntil = -1
        fight.tentaclesStunnedUntil = -1
        fight.sire.hitpoints = fight.sire.baseHitpointsLvl
        for (tentacle in fight.tentacles) tentacle.resetTransmog()
        transform(fight.sire, SIRE_WANDERING)
        walk(fight, fight.chamber.meleeSpot) { arriveForMelee(fight) }
    }

    private fun arriveForMelee(fight: SireFight) {
        fight.stage = SireStage.Melee
        fight.stageStartedAt = now
        transform(fight.sire, SIRE_PUPPET)
        fight.nextAttack = now + 2
    }

    private fun startWalkToCentre(fight: SireFight) {
        fight.stage = SireStage.WalkingToCentre
        fight.stageStartedAt = now
        transform(fight.sire, SIRE_WANDERING)
        walk(fight, fight.chamber.centreSpot) { arriveForPanic(fight) }
    }

    private fun arriveForPanic(fight: SireFight) {
        fight.stage = SireStage.Panic
        fight.stageStartedAt = now
        transform(fight.sire, SIRE_PANICKING)
        fight.sire.anim(SIRE_PANIC_SEQ)
        for (tentacle in fight.tentacles) {
            transform(tentacle, TENTACLE_ACTIVE, keepHitpoints = false)
            tentacle.anim(TENTACLE_WAKE_SEQ)
        }
        repeat(PANIC_SPAWNS) { spawnMinion(fight, randomSpawnTile(fight)) }
        fight.nextAttack = now + ATTACK_TICKS
        if (fight.sire.hitpoints < APOCALYPSE_HITPOINTS) startApocalypse(fight)
    }

    /**
     * Walks the Sire to [dest] with combat ignored so nothing can cancel the walk. The throne alcove
     * and the phase 2 stop overlap solid ground a size 6 npc cannot path through, so a Sire that
     * stands still for [WALK_STUCK_TICKS], or has not arrived within [WALK_TIMEOUT_TICKS], is placed
     * at [dest].
     */
    private fun walk(fight: SireFight, dest: CoordGrid, onArrival: () -> Unit) {
        val sire = fight.sire
        val stage = fight.stage
        sire.movementLocked = false
        sire.ignoreCombatInteractions = true
        sire.resetFaceEntity()
        fight.walkDeadline = now + WALK_TIMEOUT_TICKS
        val arrive = {
            if (fights[sire] === fight && fight.stage == stage) {
                if (sire.coords != dest) sire.telejump(deps.collision, dest)
                fight.walkDeadline = -1
                fight.onArrival = null
                sire.ignoreCombatInteractions = false
                sire.movementLocked = true
                onArrival()
            }
        }
        fight.walkLastCoords = sire.coords
        fight.walkStillTicks = 0
        fight.onArrival = arrive
        sire.walkTo(routeFactory, dest) { arrive() }
    }

    private fun startApocalypse(fight: SireFight) {
        if (fight.stage == SireStage.Apocalypse) return
        fight.stage = SireStage.Apocalypse
        fight.stageStartedAt = now
        fight.poolsSinceExplosion = 0
        val sire = fight.sire
        transform(sire, SIRE_APOCALYPSE)
        sire.anim(SIRE_APOCALYPSE_SEQ)
        val hero = fight.hero
        if (hero.isValidTarget() && fight.chamber.contains(hero.coords)) {
            PathingEntityCommon.telejump(hero, deps.collision, fight.chamber.rowTwo)
        }
        fight.nextAttack = now + EXPLOSION_DELAY + ATTACK_TICKS
        deps.worldQueues.add(EXPLOSION_DELAY) {
            if (fights[sire] !== fight || fight.stage != SireStage.Apocalypse) return@add
            for (player in chamberPlayers(fight)) {
                if (footprintDistance(sire, player.coords) <= EXPLOSION_REACH) {
                    player.finishNpcHit(sire, 1, HitType.Typeless, deps.random.of(0, EXPLOSION_MAX_HIT), deps.playerHitModifier)
                }
            }
            repeat(EXPLOSION_SPAWNS) { spawnMinion(fight, randomSpawnTile(fight)) }
        }
    }

    /* The per-tick loop */

    private fun tick(fight: SireFight) {
        deps.worldQueues.add(1) {
            if (fights[fight.sire] !== fight || !fight.sire.isSlotAssigned) return@add
            step(fight)
            if (fights[fight.sire] === fight) tick(fight)
        }
    }

    fun step(fight: SireFight) {
        val time = now
        val hero = fight.hero
        // An npc idle off its spawn tile for 500 cycles is teleported home; the Sire stands still for
        // whole phases, and only a reset or its death may send it back to the throne.
        fight.sire.wanderIdleCycles = 0
        val heroPresent = hero.isValidTarget() && fight.chamber.contains(hero.coords)
        if (heroPresent) fight.heroLastActive = time
        if (!heroPresent && time - fight.heroLastActive >= HERO_LOST_TICKS) {
            reset(fight)
            return
        }
        if (fight.stage == SireStage.Melee && time - fight.heroLastHitSire >= HERO_LOST_TICKS) {
            reset(fight)
            return
        }

        if (fight.stage == SireStage.Waking && time - fight.stageStartedAt >= WAKE_TICKS) finishWaking(fight)
        if (fight.inTransition()) trackWalk(fight, time)

        if (fight.tentaclesStunnedUntil in 0..time && fight.stage == SireStage.Lungs) endTentacleStun(fight)
        if (fight.sireStunnedUntil in 0..time && fight.stage == SireStage.Lungs) {
            fight.sireStunnedUntil = -1
            transform(fight.sire, SIRE_AWAKE)
        }

        if (time % LUNG_REGEN_TICKS == 0) {
            for (lung in fight.lungs) {
                if (lung.visType.isType(LUNG) && lung.hitpoints in 1 until lung.baseHitpointsLvl) lung.hitpoints++
            }
        }
        if (time % TENTACLE_ATTACK_TICKS == 0 && fight.tentaclesActive(time)) tentacleAttacks(fight)
        tickPools(fight, time)
        tickMinions(fight, time)

        if (heroPresent && time >= fight.nextAttack) attack(fight, hero, time)
    }

    private fun trackWalk(fight: SireFight, time: Int) {
        val coords = fight.sire.coords
        if (coords == fight.walkLastCoords) fight.walkStillTicks++ else fight.walkStillTicks = 0
        fight.walkLastCoords = coords
        if (fight.walkStillTicks >= WALK_STUCK_TICKS || fight.walkDeadline in 0..time) forceArrival(fight)
    }

    private fun forceArrival(fight: SireFight) {
        fight.onArrival?.invoke()
    }

    private fun attack(fight: SireFight, hero: Player, time: Int) {
        val sire = fight.sire
        when (fight.stage) {
            SireStage.Lungs -> {
                if (fight.sireStunned(time)) return
                if (footprintDistance(sire, hero.coords) > PHASE_ONE_REACH) return
                if (fight.aliveMinions() >= MINION_CAP || deps.random.randomBoolean()) {
                    miasma(fight, hero, SIRE_MIASMA_SEQ)
                } else {
                    launchSpawn(fight, hero, SIRE_SPAWN_SEQ)
                }
                fight.nextAttack = time + ATTACK_TICKS
            }
            SireStage.Melee -> {
                if (footprintDistance(sire, hero.coords) <= MELEE_REACH) {
                    melee(fight, hero)
                } else {
                    val capped = fight.aliveMinions() >= MINION_CAP
                    when (deps.random.of(0, 4)) {
                        0, 1 ->
                            if (capped) {
                                miasma(fight, hero, SIRE_MIASMA_TWO_SEQ)
                            } else {
                                launchSpawn(fight, hero, SIRE_SPAWN_TWO_SEQ)
                            }
                        2, 3 -> miasma(fight, hero, SIRE_MIASMA_TWO_SEQ)
                        else -> portalBlast(fight, hero)
                    }
                }
                fight.nextAttack = time + MELEE_ATTACK_TICKS
            }
            SireStage.Panic -> {
                miasma(fight, hero, null)
                fight.nextAttack = time + ATTACK_TICKS
            }
            SireStage.Apocalypse -> {
                if (fight.poolsSinceExplosion < EXPLOSION_POOLS || fight.aliveMinions() < MINION_CAP) {
                    miasma(fight, hero, null)
                    fight.poolsSinceExplosion++
                }
                if (fight.aliveMinions() < MINION_CAP) spawnMinion(fight, randomSpawnTile(fight))
                fight.nextAttack = time + ATTACK_TICKS
            }
            else -> Unit
        }
    }

    /* Attacks */

    private fun melee(fight: SireFight, hero: Player) {
        val sire = fight.sire
        val attack = MeleeAttack.entries[deps.random.of(0, MeleeAttack.entries.size - 1)]
        sire.facePlayer(hero)
        sire.anim(attack.seq)
        val lands = deps.accuracy.rollMeleeAccuracy(sire, hero, attack.style, deps.random)
        var damage = if (lands) deps.random.of(0, attack.maxHit) else 0
        if (hero.vars[PROTECT_FROM_MELEE] == 1) damage = minOf(damage, attack.prayedMaxHit)
        hero.finishNpcHit(sire, 1, HitType.Melee, damage, deps.playerHitModifier, penetration = FULL_PENETRATION)
    }

    private fun miasma(fight: SireFight, hero: Player, seq: String?) {
        if (seq != null) fight.sire.anim(seq)
        val centre = hero.coords
        deps.worldQueues.add(MIASMA_DELAY) {
            if (fights[fight.sire] !== fight) return@add
            fight.pools[centre] = now + MIASMA_DURATION
            deps.worldRepo.spotanimMap(SpotanimType(MIASMA_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), centre)
        }
    }

    private fun tickPools(fight: SireFight, time: Int) {
        if (fight.pools.isEmpty()) return
        fight.pools.entries.removeIf { it.value < time }
        val players = chamberPlayers(fight)
        for ((centre, expires) in fight.pools) {
            if ((time - fight.stageStartedAt) % MIASMA_REFRESH_TICKS == 0) {
                deps.worldRepo.spotanimMap(SpotanimType(MIASMA_SPOTANIM.asRSCM(RSCMType.SPOTANIM)), centre)
            }
            if (time <= expires - MIASMA_DURATION) continue
            for (player in players) {
                val distance = player.coords.chebyshevDistance(centre)
                val damage =
                    when {
                        player.coords.level != centre.level -> continue
                        distance == 0 -> deps.random.of(MIASMA_CENTRE_MIN, MIASMA_CENTRE_MAX)
                        distance == 1 -> deps.random.of(MIASMA_EDGE_MIN, MIASMA_EDGE_MAX)
                        else -> continue
                    }
                player.finishNpcHit(fight.sire, 0, HitType.Typeless, damage, deps.playerHitModifier)
                CombatEffects.poison(player, MIASMA_POISON)
            }
        }
    }

    private fun launchSpawn(fight: SireFight, hero: Player, seq: String) {
        val sire = fight.sire
        sire.anim(seq)
        val tile = randomSpawnTile(fight)
        deps.bossProjectile(
            spotanim = SPAWN_PROJECTILE.asRSCM(RSCMType.SPOTANIM),
            src = footprintCentre(sire),
            target = tile,
            startHeight = SPAWN_PROJECTILE_START_HEIGHT,
            endHeight = 0,
            delay = SPAWN_PROJECTILE_DELAY,
            travel = SPAWN_PROJECTILE_TRAVEL,
            curve = SPAWN_PROJECTILE_CURVE,
        )
        deps.worldQueues.add(SPAWN_LAND_TICKS) { if (fights[sire] === fight) spawnMinion(fight, tile) }
    }

    /**
     * The phase 2 answer to a player keeping their distance: they are pulled up to row 1 and the
     * Sire's stomach discharges a moment later, hitting anyone still within reach.
     */
    private fun portalBlast(fight: SireFight, hero: Player) {
        val sire = fight.sire
        sire.anim(SIRE_TELEPORT_SEQ)
        PathingEntityCommon.telejump(hero, deps.collision, fight.chamber.rowOne)
        hero.mes(PORTAL_BLAST_MESSAGE)
        deps.worldQueues.add(PORTAL_BLAST_DELAY) {
            if (fights[sire] !== fight || fight.stage != SireStage.Melee) return@add
            for (player in chamberPlayers(fight)) {
                if (footprintDistance(sire, player.coords) <= PORTAL_BLAST_REACH) {
                    player.finishNpcHit(sire, 1, HitType.Typeless, deps.random.of(0, PORTAL_BLAST_MAX_HIT), deps.playerHitModifier)
                }
            }
        }
    }

    private fun tentacleAttacks(fight: SireFight) {
        val players = chamberPlayers(fight)
        if (players.isEmpty()) return
        for (tentacle in fight.tentacles) {
            val victim = players.firstOrNull { insideFootprint(tentacle, it.coords, CHAMBER_TENTACLE_SIZE) } ?: continue
            tentacle.anim(TENTACLE_ATTACK_SEQ)
            val lands = deps.accuracy.rollMeleeAccuracy(tentacle, victim, MeleeAttackType.Crush, deps.random)
            val damage = if (lands) deps.random.of(0, TENTACLE_MAX_HIT) else 0
            victim.finishNpcHit(tentacle, 1, HitType.Typeless, damage, deps.playerHitModifier)
        }
    }

    /* Minions */

    fun spawnMinion(fight: SireFight, tile: CoordGrid) {
        val type = npcType(SPAWN) ?: return
        val spawn = Npc(type, tile)
        deps.npcRepo.add(spawn, Int.MAX_VALUE)
        spawn.respawns = false
        fight.minions[spawn] = SireMinion(now)
        engage(spawn, fight.hero)
    }

    private fun engage(minion: Npc, hero: Player) {
        if (!hero.isValidTarget()) return
        minion.apRequiresLineOfSight = false
        minion.apPlayer2(hero, aiPlayerInteractions)
    }

    /** Minions close in to melee, now and then switching to ranged from where they stand and back. */
    private fun switchStyle(minion: Npc) {
        minion.apRangeOverride = if (minion.apRangeOverride == null) MINION_RANGED_REACH else null
    }

    private fun tickMinions(fight: SireFight, time: Int) {
        if (fight.minions.isEmpty()) return
        val iterator = fight.minions.entries.iterator()
        while (iterator.hasNext()) {
            val (minion, state) = iterator.next()
            if (!minion.isSlotAssigned || minion.hitpoints <= 0) {
                iterator.remove()
                continue
            }
            if (canReachHero(fight, minion)) state.lastInReach = time
            if (deps.random.of(1, MINION_STYLE_SWITCH_CHANCE) == 1) switchStyle(minion)
            if (time - state.lastInReach > MINION_IDLE_TICKS) {
                iterator.remove()
                dieOff(minion)
                continue
            }
            if (minion.visType.isType(SPAWN) && time - state.spawnedAt >= SPAWN_GROWTH_TICKS) {
                grow(fight, minion)
            } else if (minion.interaction == null && fight.chamber.contains(fight.hero.coords)) {
                engage(minion, fight.hero)
            }
        }
    }

    private fun grow(fight: SireFight, spawn: Npc) {
        transform(spawn, SCION, keepHitpoints = false)
        spawn.anim(SCION_GROW_SEQ)
        engage(spawn, fight.hero)
    }

    /** Within ranged reach, and inside the leash beyond which an npc drops its target. */
    private fun canReachHero(fight: SireFight, minion: Npc): Boolean {
        val hero = fight.hero
        return hero.isValidTarget() &&
            fight.chamber.contains(hero.coords) &&
            footprintDistance(minion, hero.coords) <= MINION_RANGED_REACH &&
            minion.spawnCoords.chebyshevDistance(hero.coords) <= minion.type.maxRange + minion.type.attackRange
    }

    /** Kills every minion with its dying form, as they do when the Sire falls. */
    fun killMinions(fight: SireFight) {
        for (minion in fight.minions.keys.toList()) dieOff(minion)
        fight.minions.clear()
    }

    private fun dieOff(minion: Npc) {
        if (!minion.isSlotAssigned) return
        val scion = minion.visType.isType(SCION)
        minion.clearInteraction()
        minion.noneMode()
        transform(minion, if (scion) SCION_DYING else SPAWN_DYING, keepHitpoints = false)
        minion.anim(if (scion) SCION_DEATH_SEQ else SPAWN_DEATH_SEQ)
        deps.worldQueues.add(MINION_DEATH_TICKS) { if (minion.isSlotAssigned) deps.npcRepo.del(minion, Int.MAX_VALUE) }
    }

    /* Ending a fight */

    /** The Sire is dying: its minions die with it and its pools stop burning. */
    fun onSireDeath(sire: Npc): SireFight? {
        val fight = fights[sire] ?: return null
        fight.stage = SireStage.Dying
        fight.pools.clear()
        killMinions(fight)
        return fight
    }

    /** Puts the chamber back as it was: revived respiratory systems, sleeping tentacles. */
    fun releaseChamber(fight: SireFight) {
        fights.remove(fight.sire)
        fight.pools.clear()
        for (lung in fight.lungs) {
            if (lung.isSlotAssigned) deps.npcRepo.despawn(lung, CHAMBER_RESPAWN_TICKS)
        }
        for (tentacle in fight.tentacles) tentacle.resetTransmog()
    }

    /** The player has gone: the Sire and its chamber reset without a kill. */
    fun reset(fight: SireFight) {
        killMinions(fight)
        releaseChamber(fight)
        if (fight.sire.isSlotAssigned) deps.npcRepo.despawn(fight.sire, CHAMBER_RESPAWN_TICKS)
    }

    /* Helpers */

    private fun chamberNpcs(tiles: List<CoordGrid>, type: String?): List<Npc> {
        val wanted = tiles.toSet()
        return npcList.filterNotNull().filter {
            it.isSlotAssigned && it.spawnCoords in wanted && (type == null || it.type.isType(type))
        }
    }

    private fun chamberPlayers(fight: SireFight): List<Player> =
        deps.playerList.filter { it.isValidTarget() && fight.chamber.contains(it.coords) }

    /** An open tile in front of the Sire, or in front of the alcove while it is still enthroned. */
    private fun randomSpawnTile(fight: SireFight): CoordGrid {
        val candidates =
            if (fight.stage == SireStage.Lungs || fight.stage == SireStage.Waking) {
                fight.chamber.alcoveFront
            } else {
                spawnArea(fight.sire.coords)
            }
        val open = candidates.filter { (deps.collision[it.x, it.z, it.level] and SPAWN_BLOCKING_FLAGS) == 0 }
        val tiles = open.ifEmpty { candidates }
        return tiles[deps.random.of(0, tiles.size - 1)]
    }

    @OptIn(InternalApi::class)
    private fun transform(npc: Npc, form: String, keepHitpoints: Boolean = true) {
        val type = npcType(form) ?: return
        val hitpoints = npc.hitpoints
        val baseHitpoints = npc.baseHitpointsLvl
        npc.transmog(type, Int.MAX_VALUE)
        npc.copyStats(type)
        if (keepHitpoints) {
            npc.baseHitpointsLvl = baseHitpoints
            npc.hitpoints = hitpoints
        }
        npc.assignUid()
    }

    private fun npcType(name: String): NpcServerType? = ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))

    private enum class MeleeAttack(val seq: String, val style: MeleeAttackType, val maxHit: Int, val prayedMaxHit: Int) {
        Swipe("seq.sire_right_hook", MeleeAttackType.Slash, SINGLE_MAX_HIT, SINGLE_PRAYED_MAX_HIT),
        Flick("seq.sire_attack_right_whip", MeleeAttackType.Slash, SINGLE_MAX_HIT, SINGLE_PRAYED_MAX_HIT),
        DoubleFlick("seq.sire_attack_double_whips", MeleeAttackType.Slash, DOUBLE_MAX_HIT, DOUBLE_PRAYED_MAX_HIT),
    }

    companion object {
        const val SIRE_SLEEPING = "npc.abyssalsire_sire_stasis_sleeping"
        const val SIRE_AWAKE = "npc.abyssalsire_sire_stasis_awake"
        const val SIRE_STUNNED = "npc.abyssalsire_sire_stasis_stunned"
        const val SIRE_PUPPET = "npc.abyssalsire_sire_puppet"
        const val SIRE_WANDERING = "npc.abyssalsire_sire_wandering"
        const val SIRE_PANICKING = "npc.abyssalsire_sire_panicking"
        const val SIRE_APOCALYPSE = "npc.abyssalsire_sire_apocalypse"
        val SIRE_FORMS =
            listOf(SIRE_SLEEPING, SIRE_AWAKE, SIRE_STUNNED, SIRE_PUPPET, SIRE_WANDERING, SIRE_PANICKING, SIRE_APOCALYPSE)

        const val TENTACLE_NORTH = "npc.abyssalsire_tentacle_sleeping_north"
        val TENTACLE_SLEEPING =
            listOf(TENTACLE_NORTH, "npc.abyssalsire_tentacle_sleeping_south", "npc.abyssalsire_tentacle_sleeping_upright")
        const val TENTACLE_ACTIVE = "npc.abyssalsire_tentacle_active"
        const val TENTACLE_STUNNED = "npc.abyssalsire_tentacle_stunned"
        const val LUNG = "npc.abyssalsire_lung"
        const val LUNG_DYING = "npc.abyssalsire_lung_dying"
        const val SPAWN = "npc.abyssalsire_spawn"
        const val SPAWN_DYING = "npc.abyssalsire_spawn_dying"
        const val SCION = "npc.abyssalsire_scion"
        const val SCION_DYING = "npc.abyssalsire_scion_dying"

        const val STUN_DAMAGE = 75
        const val SIRE_STUN_TICKS = 50
        const val TENTACLE_STUN_TICKS = 45
        const val WAKE_TICKS = 10
        const val PANIC_HITPOINTS = 212
        const val APOCALYPSE_HITPOINTS = 140
        const val WALK_TO_MELEE_RESTORE = 170
        const val WALK_TO_CENTRE_RESTORE = 85
        const val PROTECTED_LUNG_MAX_HIT = 3
        const val LUNG_SLAYER_XP = 50.0
        const val MINION_CAP = 15
        const val SPAWN_GROWTH_TICKS = 20
        const val MINION_IDLE_TICKS = 50
        const val HERO_LOST_TICKS = 100
        const val PHASE_ONE_REACH = 10
        const val MELEE_REACH = 2
        const val EXPLOSION_REACH = 2
        const val EXPLOSION_MAX_HIT = 96
        const val EXPLOSION_DELAY = 2
        const val PORTAL_BLAST_MAX_HIT = 60
        const val TENTACLE_MAX_HIT = 30
        const val SINGLE_MAX_HIT = 32
        const val DOUBLE_MAX_HIT = 66
        const val SINGLE_PRAYED_MAX_HIT = 6
        const val DOUBLE_PRAYED_MAX_HIT = 26
        const val MIASMA_CENTRE_MIN = 10
        const val MIASMA_CENTRE_MAX = 30
        const val MIASMA_EDGE_MIN = 2
        const val MIASMA_EDGE_MAX = 8
        const val MIASMA_POISON = 8
        const val MIASMA_DURATION = 6

        private const val SLAYER = "stat.slayer"
        private const val PROTECT_FROM_MELEE = "varbit.prayer_protectfrommelee"
        private const val MAX_HIT_VARP = "varp.com_maxhit"
        private const val FULL_PENETRATION = 100
        private const val CHAMBER_TENTACLE_SIZE = SireChamber.TENTACLE_SIZE
        private const val FIRST_ATTACK_DELAY = 4
        private const val ATTACK_TICKS = 6
        private const val MELEE_ATTACK_TICKS = 7
        private const val TENTACLE_ATTACK_TICKS = 4
        private const val LUNG_REGEN_TICKS = 8
        private const val WALK_TIMEOUT_TICKS = 30
        private const val WALK_STUCK_TICKS = 3
        private const val PANIC_SPAWNS = 4
        private const val EXPLOSION_SPAWNS = 6
        private const val EXPLOSION_POOLS = 3
        private const val PORTAL_BLAST_DELAY = 3
        private const val PORTAL_BLAST_REACH = 1
        private const val MIASMA_DELAY = 2
        private const val MIASMA_REFRESH_TICKS = 2
        private const val SPAWN_SCATTER = 3
        private const val SPAWN_BLOCKING_FLAGS = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC
        private const val SPAWN_LAND_TICKS = 2
        private const val SPAWN_PROJECTILE_START_HEIGHT = 120
        private const val SPAWN_PROJECTILE_DELAY = 20
        private const val SPAWN_PROJECTILE_TRAVEL = 40
        private const val SPAWN_PROJECTILE_CURVE = 30
        const val MINION_RANGED_REACH = 7
        private const val MINION_STYLE_SWITCH_CHANCE = 20
        private const val MINION_DEATH_TICKS = 3
        private const val CHAMBER_RESPAWN_TICKS = 1

        private const val MIASMA_SPOTANIM = "spotanim.abyssal_miasma_spotanim"
        private const val SPAWN_PROJECTILE = "spotanim.abyssal_spawn_projanim"
        private const val SIRE_WAKE_SEQ = "seq.sire_waking"
        private const val SIRE_MIASMA_SEQ = "seq.sire_attack_miasma"
        private const val SIRE_MIASMA_TWO_SEQ = "seq.sire_attack_miasma_two"
        private const val SIRE_SPAWN_SEQ = "seq.sire_attack_spawns"
        private const val SIRE_SPAWN_TWO_SEQ = "seq.sire_attack_spawns_two"
        private const val SIRE_TELEPORT_SEQ = "seq.sire_attack_teleport_player"
        private const val SIRE_PANIC_SEQ = "seq.sire_panic_mode"
        private const val SIRE_APOCALYPSE_SEQ = "seq.sire_apocalypse"
        private const val TENTACLE_WAKE_SEQ = "seq.abyssal_tentacle_waking"
        private const val TENTACLE_WAKE_REVERSED_SEQ = "seq.abyssal_tentacle_waking_reversed"
        private const val TENTACLE_STUN_SEQ = "seq.abyssal_tentacle_stunned"
        private const val TENTACLE_UNSTUN_SEQ = "seq.abyssal_tentacle_unstunned"
        private const val TENTACLE_ATTACK_SEQ = "seq.abyssal_tentacle_attack"
        private const val LUNG_DEATH_SEQ = "seq.nexus_lung_death"
        private const val SCION_GROW_SEQ = "seq.abyssal_scion_spawn"
        private const val SPAWN_DEATH_SEQ = "seq.abyssal_spawn_death"
        private const val SCION_DEATH_SEQ = "seq.abyssal_scion_death"
        private const val PORTAL_BLAST_MESSAGE = "The Sire pulls you towards it!"

        /** The tiles just in front of a Sire standing at [sire], where its spawns land. */
        fun spawnArea(sire: CoordGrid): List<CoordGrid> =
            (-SPAWN_SCATTER..SireChamber.SIRE_SIZE - 1 + SPAWN_SCATTER).flatMap { dx ->
                (1..SPAWN_SCATTER).map { dz -> sire.translate(dx, -dz) }
            }

        fun footprintDistance(npc: Npc, coords: CoordGrid): Int {
            val size = npc.visType.size
            val dx = max(max(npc.coords.x - coords.x, coords.x - (npc.coords.x + size - 1)), 0)
            val dz = max(max(npc.coords.z - coords.z, coords.z - (npc.coords.z + size - 1)), 0)
            return max(dx, dz)
        }

        fun insideFootprint(npc: Npc, coords: CoordGrid, size: Int): Boolean =
            coords.level == npc.coords.level &&
                coords.x in npc.coords.x until npc.coords.x + size &&
                coords.z in npc.coords.z until npc.coords.z + size

        fun footprintCentre(npc: Npc): CoordGrid {
            val half = npc.visType.size / 2
            return npc.coords.translate(half, half)
        }
    }
}
