package org.rsmod.content.bosses.abyssalsire

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.InventoryServerType
import kotlin.random.Random
import org.junit.jupiter.api.AfterAll
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.parallel.Execution
import org.junit.jupiter.api.parallel.ExecutionMode
import org.junit.jupiter.api.parallel.ResourceLock
import org.rsmod.annotations.InternalApi
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.runtime.BossExtensionRegistry
import org.rsmod.api.bosses.runtime.EncounterRegistry
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.game.process.npc.mode.NpcWanderModeProcessor
import org.rsmod.api.game.process.world.WorldQueueListProcess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.player.hit.modifier.NoopPlayerHitModifier
import org.rsmod.api.random.DefaultGameRandom
import org.rsmod.api.registry.npc.NpcRegistry
import org.rsmod.api.registry.zone.ZoneUpdateMap
import org.rsmod.api.repo.NpcRevealProcessor
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.route.RouteFactory
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.LUNG
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SCION
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SIRE_SLEEPING
import org.rsmod.content.bosses.abyssalsire.SireFights.Companion.SPAWN
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.Inventory
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.game.seq.EntitySeq
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap
import sun.misc.Unsafe

/**
 * Drives [SireFights] through a whole fight in the north-west chamber with the real per-tick loop:
 * waking, disorientation and the protected respiratory systems, both walks with their damage rules,
 * the panic and explosion stages, spawns maturing, death, and the reset when the player leaves.
 */
@Execution(ExecutionMode.SAME_THREAD)
@ResourceLock("ServerCacheManager")
class AbyssalSireFightTest {

    @Test fun `melee cannot wake the Sire but a ranged hit does`() {
        val f = Fixture()
        assertEquals(0, f.hit(HitType.Melee, 20))
        assertNull(f.fight())
        f.hit(HitType.Ranged, 20)
        val fight = checkNotNull(f.fight())
        assertEquals(SireStage.Waking, fight.stage)
        assertTrue(f.tentacles.all { it.visType.isType(SireFights.TENTACLE_ACTIVE) })
        f.tick(SireFights.WAKE_TICKS)
        assertEquals(SireStage.Lungs, fight.stage)
        assertTrue(f.sire.visType.isType(SireFights.SIRE_AWAKE))
    }

    @Test fun `phase 1 damage heals straight back and melee does nothing`() {
        val f = Fixture()
        f.wake()
        f.hit(HitType.Magic, 40)
        assertEquals(425, f.sire.hitpoints)
        assertEquals(0, f.hit(HitType.Melee, 30))
    }

    @Test fun `a Shadow spell on the sleeping Sire disorients it once it has woken`() {
        val f = Fixture()
        f.hit(HitType.Magic, 5, shadowStun = true)
        val fight = f.fight()!!
        assertTrue(fight.pendingStun)
        assertFalse(fight.tentaclesStunned(f.now))
        f.tick(SireFights.WAKE_TICKS)
        assertTrue(fight.sireStunned(f.now))
        assertTrue(f.sire.visType.isType(SireFights.SIRE_STUNNED))
        assertTrue(f.tentacles.all { it.visType.isType(SireFights.TENTACLE_STUNNED) })
    }

    @Test fun `75 ranged or magic damage disorients the Sire`() {
        val f = Fixture()
        f.wake()
        f.hit(HitType.Ranged, 40)
        assertFalse(f.fight()!!.tentaclesStunned(f.now))
        f.hit(HitType.Magic, 35)
        assertTrue(f.fight()!!.tentaclesStunned(f.now))
        assertEquals(0, f.fight()!!.stunDamage)
    }

    @Test fun `the tentacles recover before the Sire does`() {
        val f = Fixture()
        f.wake()
        f.fights.disorient(f.fight()!!)
        f.tick(SireFights.TENTACLE_STUN_TICKS)
        assertTrue(f.tentacles.all { it.visType.isType(SireFights.TENTACLE_ACTIVE) })
        assertTrue(f.sire.visType.isType(SireFights.SIRE_STUNNED))
        f.tick(SireFights.SIRE_STUN_TICKS - SireFights.TENTACLE_STUN_TICKS)
        assertTrue(f.sire.visType.isType(SireFights.SIRE_AWAKE))
    }

    @Test fun `respiratory systems are protected unless the tentacles are stunned`() {
        val f = Fixture()
        f.wake()
        val lung = f.lungs.first()
        repeat(50) { assertTrue(f.fights.modifyLungHit(lung, f.hero, HitType.Ranged, false, 40) <= SireFights.PROTECTED_LUNG_MAX_HIT) }
        assertEquals(0, f.fights.modifyLungHit(lung, f.hero, HitType.Melee, false, 40), "melee needs a halberd")
        f.fights.disorient(f.fight()!!)
        assertEquals(40, f.fights.modifyLungHit(lung, f.hero, HitType.Ranged, false, 40))
        assertEquals(40, f.fights.modifyLungHit(lung, f.hero, HitType.Melee, true, 40))
        f.hero.vars.backing["varp.com_maxhit".asRSCM(RSCMType.VARP)] = 60
        assertEquals(30, f.fights.modifyLungHit(lung, f.hero, HitType.Ranged, false, 5), "at least half the max hit")
        assertEquals(0, f.fights.modifyLungHit(lung, f.hero, HitType.Ranged, false, 0), "misses still miss")
    }

    @Test fun `killing the four systems sends the Sire out to fight in melee`() {
        val f = Fixture()
        f.wake()
        f.killLungs()
        val fight = f.fight()!!
        assertEquals(SireStage.WalkingToMelee, fight.stage)
        assertTrue(f.lungs.all { it.visType.isType(SireFights.LUNG_DYING) })
        assertEquals(200, f.hero.statMap.getXP("stat.slayer"), "50 Slayer xp for each of the first four systems")
        f.tick(4)
        assertEquals(SireStage.Melee, fight.stage)
        assertEquals(SireChamber.NorthWest.meleeSpot, f.sire.coords)
        assertTrue(f.sire.visType.isType(SireFights.SIRE_PUPPET))
        assertTrue(f.tentacles.none { it.visType.isType(SireFights.TENTACLE_ACTIVE) }, "tentacles rest in phase 2")
    }

    @Test fun `a route that ends short of the stop still places the Sire there`() {
        val f = Fixture()
        f.wake()
        f.killLungs()
        val fight = f.fight()!!
        checkNotNull(fight.onArrival).invoke()
        assertEquals(SireStage.Melee, fight.stage)
        assertEquals(SireChamber.NorthWest.meleeSpot, f.sire.coords)
    }

    @Test fun `the engine's idle return to spawn never pulls the Sire off its stop`() {
        val f = f2()
        val fight = f.fight()!!
        val wander = NpcWanderModeProcessor(DefaultGameRandom(Random(1)), f.collision)
        repeat(NpcWanderModeProcessor.RESPAWN_IDLE_REQUIREMENT + 20) {
            fight.heroLastHitSire = f.now
            f.tick(1)
            wander.process(f.sire)
        }
        assertEquals(SireStage.Melee, fight.stage)
        assertEquals(f.chamber.meleeSpot, f.sire.coords)
    }

    @Test fun `the walk halves damage and the Sire cannot die on it`() {
        val f = Fixture()
        f.wake()
        f.killLungs()
        assertEquals(20, f.hit(HitType.Ranged, 40))
        f.sire.hitpoints = 10
        assertEquals(0, f.hit(HitType.Ranged, 60))
        assertEquals(SireFights.WALK_TO_MELEE_RESTORE, f.sire.hitpoints)
    }

    @Test fun `at 212 hitpoints it walks to the centre, then panics with four spawns`() {
        val f = f2()
        f.sire.hitpoints = 230
        f.hit(HitType.Melee, 20)
        val fight = f.fight()!!
        assertEquals(SireStage.WalkingToCentre, fight.stage)
        assertEquals(15, f.hit(HitType.Melee, 30), "half damage on the walk")
        f.tick(4)
        assertEquals(SireStage.Panic, fight.stage)
        assertEquals(SireChamber.NorthWest.centreSpot, f.sire.coords)
        assertTrue(f.sire.visType.isType(SireFights.SIRE_PANICKING))
        assertEquals(4, fight.aliveMinions())
        assertTrue(f.tentacles.all { it.visType.isType(SireFights.TENTACLE_ACTIVE) })
    }

    @Test fun `a Sire knocked to 0 on its walk to the centre restores to 85 and explodes on arrival`() {
        val f = f2()
        f.sire.hitpoints = 220
        f.hit(HitType.Melee, 10)
        val fight = f.fight()!!
        f.sire.hitpoints = 5
        assertEquals(0, f.hit(HitType.Melee, 50))
        assertEquals(SireFights.WALK_TO_CENTRE_RESTORE, f.sire.hitpoints)
        f.tick(4)
        assertEquals(SireStage.Apocalypse, fight.stage)
    }

    @Test fun `below 140 it pulls the player to row 2, explodes and floods the room with spawns`() {
        val f = f2()
        f.sire.hitpoints = 213
        f.hit(HitType.Melee, 10)
        f.tick(4)
        val fight = f.fight()!!
        fight.nextAttack = Int.MAX_VALUE
        f.sire.hitpoints = 150
        f.hit(HitType.Melee, 20)
        assertEquals(SireStage.Apocalypse, fight.stage)
        assertTrue(f.sire.visType.isType(SireFights.SIRE_APOCALYPSE))
        assertEquals(SireChamber.NorthWest.rowTwo, f.hero.coords)
        assertEquals(4, fight.aliveMinions())
        f.tick(SireFights.EXPLOSION_DELAY + 1)
        assertEquals(10, fight.aliveMinions(), "the explosion opens a portal of spawns")
    }

    @Test fun `a spawn left alive grows into a scion`() {
        val f = Fixture()
        f.wake()
        val fight = f.fight()!!
        f.fights.spawnMinion(fight, SireChamber.NorthWest.rowThree.translateX(1))
        val spawn = fight.minions.keys.single()
        assertTrue(spawn.visType.isType(SPAWN))
        assertFalse(spawn.respawns)
        f.tick(SireFights.SPAWN_GROWTH_TICKS + 1)
        assertTrue(spawn.visType.isType(SCION))
        assertEquals(50, spawn.hitpoints)
    }

    @Test fun `minions close in to melee and now and then switch to ranged`() {
        val f = Fixture()
        f.wake()
        val fight = f.fight()!!
        f.fights.spawnMinion(fight, SireChamber.NorthWest.rowThree.translateX(1))
        val minion = fight.minions.keys.single()
        assertNull(minion.apRangeOverride, "melee style closes the gap")
        val styles = mutableSetOf<Int?>()
        repeat(SireFights.MINION_IDLE_TICKS) {
            f.tick(1)
            styles += minion.apRangeOverride
        }
        assertEquals(setOf(null, SireFights.MINION_RANGED_REACH), styles)
    }

    @Test fun `in phase 1 the Sire stops throwing spawns at 15 minions`() {
        val f = Fixture()
        f.wake()
        val fight = f.fight()!!
        val front = f.chamber.alcoveFront
        f.hero.coords = front.first()
        repeat(SireFights.MINION_CAP) { f.fights.spawnMinion(fight, front[it % front.size]) }
        fight.nextAttack = f.now
        var poured = false
        repeat(SireFights.SPAWN_GROWTH_TICKS * 2) {
            f.tick(1)
            assertEquals(SireFights.MINION_CAP, fight.aliveMinions())
            poured = poured || fight.pools.isNotEmpty()
        }
        assertTrue(poured, "it pours miasma instead")
    }

    @Test fun `in phase 2 the Sire stops throwing spawns at 15 minions`() {
        val f = f2()
        val fight = f.fight()!!
        val stand = f.chamber.rowThree
        repeat(SireFights.MINION_CAP) { f.fights.spawnMinion(fight, stand.translateX(1 + it % 3)) }
        fight.nextAttack = f.now
        var poured = false
        repeat(SireFights.SPAWN_GROWTH_TICKS * 2) {
            f.hero.coords = stand
            f.tick(1)
            assertEquals(SireFights.MINION_CAP, fight.aliveMinions())
            poured = poured || fight.pools.isNotEmpty()
        }
        assertTrue(poured, "it pours miasma instead")
    }

    @Test fun `minions that cannot attack the player for 30 seconds die off`() {
        val f = Fixture()
        f.wake()
        val fight = f.fight()!!
        f.fights.spawnMinion(fight, SireChamber.NorthWest.rowThree.translateX(1))
        val near = fight.minions.keys.single()
        f.fights.spawnMinion(fight, f.chamber.throne.translateZ(-1))
        val far = fight.minions.keys.single { it !== near }
        f.tick(SireFights.MINION_IDLE_TICKS)
        assertEquals(2, fight.aliveMinions())
        f.tick(1)
        assertTrue(far.visType.isType(SireFights.SCION_DYING))
        assertEquals(setOf(near), fight.minions.keys)
    }

    @Test fun `the Sire's death takes its minions and frees the chamber`() {
        val f = Fixture()
        f.wake()
        val fight = f.fight()!!
        f.fights.spawnMinion(fight, SireChamber.NorthWest.rowThree.translateX(1))
        val spawn = fight.minions.keys.single()
        f.killLungs()
        assertNotNull(f.fights.onSireDeath(f.sire))
        assertTrue(spawn.visType.isType(SireFights.SPAWN_DYING))
        assertEquals(SireStage.Dying, fight.stage)
        f.fights.releaseChamber(fight)
        assertNull(f.fight())
        assertTrue(f.tentacles.none { it.visType.isType(SireFights.TENTACLE_ACTIVE) })
    }

    @OptIn(InternalApi::class)
    @Test fun `the chamber resets a minute after the player leaves`() {
        val f = Fixture()
        f.wake()
        f.hero.coords = CoordGrid(3039, 4800)
        f.tick(SireFights.HERO_LOST_TICKS - 1)
        assertNotNull(f.fight())
        f.tick(2)
        assertNull(f.fight())
        f.tick(2)
        assertFalse(f.sire.hidden)
        assertEquals(f.chamber.throne, f.sire.coords)
        assertTrue(f.sire.visType.isType(SIRE_SLEEPING))
    }

    @Test fun `phase 2 resets after a minute without the player hitting the Sire`() {
        val f = f2()
        f.tick(SireFights.HERO_LOST_TICKS + 1)
        assertNull(f.fight())
    }

    @Test fun `only an 85 Slayer player on an abyssal demon task may fight it, one at a time`() {
        val f = Fixture()
        val hook = SireAttackHook(f.fights, f.deps)
        f.hero.statMap.setBaseLevel("stat.slayer", 84)
        assertEquals(NpcAttackValidateResult.Deny(SireAttackHook.SLAYER_LEVEL_MESSAGE), hook.validate(f.hero, f.sire))
        f.hero.statMap.setBaseLevel("stat.slayer", 85)
        f.hero.vars.backing["varp.slayer_target".asRSCM(RSCMType.VARP)] = 0
        assertEquals(NpcAttackValidateResult.Deny(SireAttackHook.OFF_TASK_MESSAGE), hook.validate(f.hero, f.sire))
        val demons = checkNotNull(ServerCacheManager.getNpc(SIRE_SLEEPING.asRSCM(RSCMType.NPC))!!.paramOrNull(org.rsmod.api.config.refs.BaseParams.slayer_task_id))
        f.hero.vars.backing["varp.slayer_target".asRSCM(RSCMType.VARP)] = demons
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(f.hero, f.sire))
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(f.hero, f.lungs.first()))

        f.wake()
        val other = f.newPlayer(2, SireChamber.NorthWest.rowThree.translateX(2))
        other.statMap.setBaseLevel("stat.slayer", 99)
        other.vars.backing["varp.slayer_target".asRSCM(RSCMType.VARP)] = demons
        assertEquals(NpcAttackValidateResult.Deny(SireAttackHook.BUSY_MESSAGE), hook.validate(other, f.sire))
        f.tick(SireAttackHook.CLAIM_TICKS + 1)
        assertEquals(NpcAttackValidateResult.Pass, hook.validate(other, f.sire))
    }

    /** A fight already in phase 2, attacks held off. */
    private fun f2(): Fixture {
        val f = Fixture()
        f.wake()
        f.killLungs()
        f.tick(4)
        f.fight()!!.nextAttack = Int.MAX_VALUE
        return f
    }

    private class Fixture {
        val clock = MapClock(100)
        private val queues = WorldQueueList()
        private val queueProcess = WorldQueueListProcess(queues)
        val collision = CollisionFlagMap()
        private val events = EventBus()
        private val npcs = NpcList()
        private val players = PlayerList()
        private val npcRepo = NpcRepository(clock, NpcRegistry(npcs, collision, events), npcs)
        private val reveal = NpcRevealProcessor(npcRepo)
        val deps =
            BossDeps(
                DefaultGameRandom(Random(17)), WorldRepository(ZoneUpdateMap()), npcRepo, unused(), players, clock, queues,
                collision, EncounterRegistry(clock), BossExtensionRegistry(), unused(), unused(), NoopPlayerHitModifier,
            )
        val fights = SireFights(deps, npcs, RouteFactory(collision), AiPlayerInteractions(events, players))

        val chamber = SireChamber.NorthWest
        val sire: Npc
        val lungs: List<Npc>
        val tentacles: List<Npc>
        val hero: Player

        val now: Int
            get() = clock.cycle

        init {
            for (x in 2944 until 3008 step 8) for (z in 4736 until 4864 step 8) for (level in 0..1) collision.allocateIfAbsent(x, z, level)
            for (x in 3008 until 3072 step 8) for (z in 4800 until 4808 step 8) collision.allocateIfAbsent(x, z, 0)
            sire = add(SIRE_SLEEPING, chamber.throne)
            lungs = chamber.lungs.map { add(LUNG, it) }
            tentacles = chamber.tentacles.mapIndexed { index, tile -> add(SireFights.TENTACLE_SLEEPING[index % 3], tile) }
            hero = newPlayer(1, chamber.rowThree)
        }

        private fun add(type: String, at: CoordGrid): Npc {
            val npc = Npc(checkNotNull(ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC))), at)
            npcRepo.add(npc, Int.MAX_VALUE)
            return npc
        }

        @OptIn(InternalApi::class)
        fun newPlayer(slot: Int, at: CoordGrid): Player =
            Player().apply {
                slotId = slot
                uuid = slot.toLong()
                assignUid()
                coords = at
                currentMapClock = clock.cycle
                processedMapClock = clock.cycle
                pendingSequence = EntitySeq.NULL
                inv = Inventory(InventoryServerType(size = 28, flags = 0), arrayOfNulls(28))
                worn = Inventory(InventoryServerType(size = 14, flags = 0), arrayOfNulls(14))
                statMap.setBaseLevel("stat.hitpoints", 99)
                statMap.setCurrentLevel("stat.hitpoints", 99)
                players[slot] = this
            }

        fun fight(): SireFight? = fights.fightOf(sire)

        fun hit(type: HitType, damage: Int, shadowStun: Boolean = false): Int {
            val dealt = fights.modifySireHit(sire, hero, type, damage, shadowStun)
            sire.hitpoints = (sire.hitpoints - dealt).coerceAtLeast(0)
            fights.afterSireHit(sire)
            return dealt
        }

        fun wake() {
            hit(HitType.Ranged, 1)
            tick(SireFights.WAKE_TICKS)
            fight()!!.nextAttack = Int.MAX_VALUE
        }

        fun killLungs() {
            for (lung in lungs) {
                lung.hitpoints = 0
                fights.lungDied(lung)
            }
        }

        fun tick(n: Int) {
            repeat(n) {
                clock.tick()
                for (p in players) p?.currentMapClock = clock.cycle
                queueProcess.process()
                for (npc in npcs) reveal.process(npc)
            }
        }
    }

    companion object {
        private inline fun <reified T> unused(): T {
            val field = Unsafe::class.java.getDeclaredField("theUnsafe").apply { isAccessible = true }
            return (field.get(null) as Unsafe).allocateInstance(T::class.java) as T
        }

        @JvmStatic @BeforeAll fun cache() {
            ServerCacheManager.init(240).close()
        }

        @JvmStatic @AfterAll fun done() {}
    }
}
