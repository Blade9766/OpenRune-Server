package org.rsmod.content.quest.area.desert.icthlarin

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Provider
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerLogout
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_GUARDIAN_DEFEATED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_GUARDIAN_SUMMONED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_DEFEATED
import org.rsmod.content.quest.area.desert.icthlarin.IcthlarinsLittleHelperQuest.Companion.STAGE_PRIEST_POSSESSED
import org.rsmod.content.quest.area.wilderness.magearena.freeFootprint
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The monsters raised against the player in Klenter's pyramid, each spawned for one player and
 * fighting only them: the apparition that guards the stolen canopic jar, the priest the Devourer
 * possesses at the ceremony, and the scarab swarms that crawl out of the floor.
 *
 * The possessed priest does not stay possessed for ever: after [PRIEST_POSSESSION_TICKS] he comes
 * to his senses and the player has to leave the chamber and come back to face him again.
 */
@Singleton
class PyramidFights
@Inject
constructor(
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val collision: CollisionFlagMap,
    private val aiInteractions: AiPlayerInteractions,
) {
    private class Spawn(val npc: Npc, val cycle: Int)

    private val guardians = HashMap<PlayerUid, Spawn>()
    private val priests = HashMap<PlayerUid, Spawn>()
    private val swarms = HashMap<PlayerUid, Spawn>()

    fun guardianOf(player: Player): Npc? = guardians[player.uid]?.npc?.takeIf { it.isSlotAssigned }

    fun priestOf(player: Player): Npc? = priests[player.uid]?.npc?.takeIf { it.isSlotAssigned }

    fun swarmOf(player: Player): Npc? = swarms[player.uid]?.npc?.takeIf { it.isSlotAssigned }

    fun summonGuardian(player: Player, jar: CanopicJar): Npc {
        guardianOf(player)?.let { remove(it) }
        val npc = spawn(player, jar.apparition, GUARDIAN_TILE, lifetime = GUARDIAN_TICKS)
        guardians[player.uid] = Spawn(npc, clock.cycle)
        npc.say("You will not defile this place!")
        npc.opPlayer2(player, aiInteractions)
        return npc
    }

    fun summonPriest(player: Player): Npc {
        priestOf(player)?.let { remove(it) }
        val npc = spawn(player, POSSESSED_PRIEST, SophanemCoords.CEREMONY_POSSESSED, lifetime = PRIEST_POSSESSION_TICKS + LINGER_TICKS)
        priests[player.uid] = Spawn(npc, clock.cycle)
        npc.opPlayer2(player, aiInteractions)
        return npc
    }

    fun summonSwarm(player: Player, near: CoordGrid): Npc? {
        if (swarmOf(player) != null) {
            return null
        }
        val npc = spawn(player, SCARAB_SWARM, near, lifetime = SWARM_TICKS)
        swarms[player.uid] = Spawn(npc, clock.cycle)
        npc.opPlayer2(player, aiInteractions)
        return npc
    }

    private fun spawn(player: Player, type: String, near: CoordGrid, lifetime: Int): Npc {
        val serverType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $type")
        val tile = collision.freeFootprint(near, serverType.size, radius = SPAWN_RADIUS) ?: near
        val npc = Npc(serverType, tile)
        npcRepo.add(npc, lifetime)
        npc.respawns = false
        npc.assignSpawnOwner(player, clock.cycle)
        npc.facePlayer(player)
        return npc
    }

    /** Tells whether the priest came back to himself before the player could subdue him. */
    fun priestRecovered(player: Player): Boolean {
        val spawn = priests[player.uid] ?: return false
        if (!spawn.npc.isSlotAssigned) {
            priests.remove(player.uid)
            return false
        }
        if (clock.cycle - spawn.cycle < PRIEST_POSSESSION_TICKS) {
            return false
        }
        priests.remove(player.uid)
        remove(spawn.npc)
        return true
    }

    /** Scarabs lose interest in a player who has run off. */
    fun tick(player: Player) {
        val swarm = swarms[player.uid] ?: return
        val npc = swarm.npc
        if (!npc.isSlotAssigned || npc.coords.chebyshevDistance(player.coords) > SWARM_LEASH) {
            swarms.remove(player.uid)
            remove(npc)
        }
    }

    fun forget(npc: Npc) {
        guardians.values.removeIf { it.npc === npc }
        priests.values.removeIf { it.npc === npc }
        swarms.values.removeIf { it.npc === npc }
    }

    fun cleanup(player: Player) {
        listOfNotNull(guardians.remove(player.uid), priests.remove(player.uid), swarms.remove(player.uid))
            .forEach { remove(it.npc) }
    }

    private fun remove(npc: Npc) {
        if (npc.isSlotAssigned) {
            npcRepo.del(npc, Int.MAX_VALUE)
        }
    }

    companion object {
        const val POSSESSED_PRIEST = "npc.ics_little_possessedpriest"
        const val SCARAB_SWARM = "npc.ics_scarab_swarm"

        val APPARITIONS: List<String> = CanopicJar.entries.map { it.apparition }
        val OWNED_TYPES: List<String> = APPARITIONS + POSSESSED_PRIEST + SCARAB_SWARM

        /** Between the shelf of jars and the chamber's middle. */
        val GUARDIAN_TILE = CoordGrid(3282, 9195, 0)

        const val SPAWN_RADIUS = 3
        const val GUARDIAN_TICKS = 1000
        const val PRIEST_POSSESSION_TICKS = 500
        const val LINGER_TICKS = 100
        const val SWARM_TICKS = 100
        const val SWARM_LEASH = 12
    }
}

/**
 * Deaths and attacks of the pyramid's owned monsters. The possessed priest casts one of four
 * spells at random - wind, water and fire strikes and a purple zap, each with its own cap - and
 * punches anyone who stands next to him.
 */
class PyramidFightScript
@Inject
constructor(
    private val quest: IcthlarinsLittleHelperQuest,
    private val fights: PyramidFights,
    private val death: NpcDeath,
    private val objRepo: ObjRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
    private val random: GameRandom,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (apparition in PyramidFights.APPARITIONS) {
            onNpcQueue(npcType(apparition), DEATH_QUEUE) { guardianFalls() }
        }
        onNpcQueue(npcType(PyramidFights.POSSESSED_PRIEST), DEATH_QUEUE) { priestFalls() }
        onNpcQueue(npcType(PyramidFights.SCARAB_SWARM), DEATH_QUEUE) {
            fights.forget(npc)
            death.deathWithDrops(this)
        }
        val priest = npcType(PyramidFights.POSSESSED_PRIEST)
        onAiOpPlayer2(priest) { priestAttacks(it.target) }
        onAiApPlayer2(priest) { priestAttacks(it.target) }
        onPlayerLogout { fights.cleanup(player) }
    }

    private suspend fun StandardNpcAccess.guardianFalls() {
        val owner = npc.spawnOwnerPlayer()
        fights.forget(npc)
        death.deathWithDrops(this)
        owner ?: return
        launchWhenFree(owner.uid) {
            if (quest.stage(player) == STAGE_GUARDIAN_SUMMONED) {
                quest.advanceTo(this, STAGE_GUARDIAN_DEFEATED)
                mes("The apparition fades away. The jar is unguarded.")
            }
        }
    }

    private suspend fun StandardNpcAccess.priestFalls() {
        val owner = npc.spawnOwnerPlayer()
        val coords = npc.coords
        fights.forget(npc)
        death.deathWithDrops(this)
        owner ?: return
        val jar = quest.jar(owner) ?: CanopicJar.Het
        objRepo.add(jar.potion, coords, POTION_DROP_TICKS, receiver = owner)
        owner.mes("The priest seems to recover from the Devourer's spell.")
        launchWhenFree(owner.uid) {
            val stage = quest.stage(player)
            if (stage in STAGE_PRIEST_POSSESSED until STAGE_PRIEST_DEFEATED) {
                quest.advanceTo(this, STAGE_PRIEST_DEFEATED)
            }
        }
    }

    private fun Npc.spawnOwnerPlayer(): Player? = spawnOwner.resolve(playerList)

    private fun StandardNpcAccess.priestAttacks(target: Player) {
        if (!target.isValidTarget() || npc.isSpawnOwnedByOther(target)) {
            resetMode()
            return
        }
        if (actionDelay > mapClock) {
            return
        }
        if (!npc.isInCombat()) {
            resetMode()
            return
        }
        actionDelay = mapClock + npc.attackRate()
        npc.lastAttack = mapClock
        npc.attackingPlayer = target.uid
        target.lastCombat = mapClock
        target.aggressiveNpc = npc.uid

        if (npc.isWithinDistance(target, 1) && random.of(PUNCH_ONE_IN) == 0) {
            punch(target)
        } else {
            cast(target, SPELLS[random.of(SPELLS.size)])
        }
    }

    private fun StandardNpcAccess.punch(target: Player) {
        anim(PUNCH_SEQ)
        val landed = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Crush, random)
        val damage =
            if (landed) random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Crush).coerceAtMost(PUNCH_MAX_HIT)) else 0
        target.finishNpcHit(npc, 1, HitType.Melee, damage, hitModifier)
    }

    private fun StandardNpcAccess.cast(target: Player, spell: PriestSpell) {
        anim(CAST_SEQ)
        spotanim(spell.casting, height = CAST_HEIGHT)
        worldRepo.soundArea(npc, spell.castSound, radius = SOUND_RADIUS)
        val projType =
            ServerCacheManager.getProjectile(PROJANIM.asRSCM(RSCMType.PROJANIM)) ?: error("Missing projanim: $PROJANIM")
        val proj = ProjAnim.fromNpcToPlayer(npc, target, spell.travel.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(proj)
        if (!accuracy.rollMagicAccuracy(npc, target, random)) {
            target.spotanim(SPLASH_SPOT, delay = proj.clientCycles, height = IMPACT_HEIGHT)
            worldRepo.soundArea(target, SPLASH_SOUND, delay = proj.clientCycles, radius = SOUND_RADIUS)
            target.queueCombatRetaliate(npc, delay = proj.serverCycles)
            return
        }
        val damage = random.of(0..spell.maxHit)
        target.spotanim(spell.impact, delay = proj.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, spell.hitSound, delay = proj.clientCycles, radius = SOUND_RADIUS)
        target.finishNpcHit(npc, proj.serverCycles, HitType.Magic, damage, hitModifier, proj.clientCycles)
    }

    private fun launchWhenFree(uid: PlayerUid, block: suspend ProtectedAccess.() -> Unit) {
        launchWhenFree(uid, LAUNCH_ATTEMPTS, block)
    }

    private fun launchWhenFree(uid: PlayerUid, attempts: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            if (!launcher.launch(player, block = block) && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    private fun npcType(name: String): NpcServerType =
        ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $name")

    private data class PriestSpell(
        val casting: String,
        val travel: String,
        val impact: String,
        val castSound: String,
        val hitSound: String,
        val maxHit: Int,
    )

    private companion object {
        const val DEATH_QUEUE = "queue.death"
        const val POTION_DROP_TICKS = 200
        const val LAUNCH_ATTEMPTS = 20

        const val CAST_SEQ = "seq.human_caststrike"
        const val PUNCH_SEQ = "seq.human_unarmedpunch"
        const val PUNCH_ONE_IN = 3
        const val PUNCH_MAX_HIT = 6
        const val PROJANIM = "projanim.magic_spell"
        const val CAST_HEIGHT = 92
        const val IMPACT_HEIGHT = 124
        const val SOUND_RADIUS = 10
        const val SPLASH_SPOT = "spotanim.failedspell_impact"
        const val SPLASH_SOUND = "synth.spellfail"

        val SPELLS =
            listOf(
                PriestSpell(
                    casting = "spotanim.windstrike_casting",
                    travel = "spotanim.windstrike_travel",
                    impact = "spotanim.windstrike_impact",
                    castSound = "synth.windstrike_cast_and_fire",
                    hitSound = "synth.windstrike_hit",
                    maxHit = 1,
                ),
                PriestSpell(
                    casting = "spotanim.waterstrike_casting",
                    travel = "spotanim.waterstrike_travel",
                    impact = "spotanim.waterstrike_impact",
                    castSound = "synth.waterstrike_cast_and_fire",
                    hitSound = "synth.waterstrike_hit",
                    maxHit = 2,
                ),
                PriestSpell(
                    casting = "spotanim.firestrike_casting",
                    travel = "spotanim.firestrike_travel",
                    impact = "spotanim.firestrike_impact",
                    castSound = "synth.firestrike_cast_and_fire",
                    hitSound = "synth.firestrike_hit",
                    maxHit = 4,
                ),
                PriestSpell(
                    casting = "spotanim.curse_casting",
                    travel = "spotanim.curse_travel",
                    impact = "spotanim.curse_impact",
                    castSound = "synth.curse_cast_and_fire",
                    hitSound = "synth.curse_hit",
                    maxHit = 6,
                ),
            )

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")
    }
}

/**
 * Only the player a pyramid monster was raised for may fight it, and dying in the pyramid ends
 * any flashback and sends the player's monsters away.
 */
class PyramidFightHooks
@Inject
constructor(
    private val fights: Provider<PyramidFights>,
    private val flashbacks: Provider<Flashbacks>,
) : NpcAttackValidateHook, PlayerDeathCleanupHook {
    private val ownedIds by lazy { PyramidFights.OWNED_TYPES.map { it.asRSCM(RSCMType.NPC) }.toSet() }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in ownedIds || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("That isn't after you.")
    }

    override fun cleanup(player: Player) {
        fights.get().cleanup(player)
        flashbacks.get().end(player)
    }
}
