package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.hit.modifier.NpcHitModifier
import org.rsmod.api.npc.hit.queueHit
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.disablePrayers
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.statHeal
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifOpenOverlay
import org.rsmod.api.player.ui.ifSetHide
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.events.EventBus
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.map.CoordGrid
import org.rsmod.routefinder.collision.CollisionFlagMap

/** Cyrisus' four fears, in the order they must be beaten. */
enum class Fear(val type: String, val spawnSeq: String, val spawnSpot: String) {
    Inadequacy("npc.dream_inadequacy", "seq.dream_inadequacy_spawn", "spotanim.dream_inadequacy_spawn_spotanim"),
    Everlasting("npc.dream_everlasting", "seq.dream_everlasting_spawn", "spotanim.dream_everlasting_spawn_spotanim"),
    Untouchable("npc.dream_untouchable", "seq.dream_untouchable_spawn", "spotanim.dream_untouchable_spawn_spotanim"),
    Illusive("npc.dream_illusive", "seq.dream_illusive_spawn", "spotanim.dream_illusive_spawn_spotanim");

    val next: Fear?
        get() = entries.getOrNull(ordinal + 1)

    companion object {
        val TYPES = entries.map { it.type }
    }
}

/**
 * The fight in Cyrisus' dream. Each player's encounter lives only as long as their dream: the four
 * fears come one at a time, each rising where the last fell, and leaving, dying or logging out
 * throws the whole encounter away so the next attempt starts again from The Inadequacy.
 *
 * The Inadequacy keeps calling up Doubts while it lives, and they vanish with it. The Illusive
 * burrows away and surfaces elsewhere after every two hits it takes. Cyrisus' courage grows with
 * the damage the player deals: until it is full he only cheers, heals and casts Vengeance on the
 * player; once full he joins in.
 */
@Singleton
class DreamFight
@Inject
constructor(
    private val dreamMentor: DreamMentorQuest,
    private val arena: DreamArena,
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val aiInteractions: AiPlayerInteractions,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
    private val npcHitModifier: NpcHitModifier,
    private val eventBus: EventBus,
) {
    class Encounter(val owner: PlayerUid) {
        var fear: Fear = Fear.Inadequacy
        var boss: Npc? = null
        var cyrisus: Npc? = null
        val doubts = mutableListOf<Npc>()
        var illusiveHits = 0
        var ticks = 0
        var vengeance = false
        var engaged = false
        var finished = false
    }

    private val encounters = HashMap<PlayerUid, Encounter>()

    fun encounterOf(player: Player): Encounter? = encounters[player.uid]

    fun isFighting(player: Player): Boolean = encounters.containsKey(player.uid)

    /** Sets up a fresh encounter: Cyrisus beside the player and The Inadequacy in the middle. */
    fun start(player: Player): Encounter {
        cleanup(player)
        player.disablePrayers()
        player.dreamDamageDealt = 0
        val encounter = Encounter(player.uid)
        encounters[player.uid] = encounter
        encounter.cyrisus = spawn(player, CombatStyle.of(player).battleType, arena.at(player, DreamArena.CYRISUS_START))
        player.ifOpenOverlay(COURAGE_INTERFACE, OVERLAY_TARGET, eventBus)
        showCourage(player)
        encounter.boss = spawn(player, Fear.Inadequacy.type, arena.at(player, DreamArena.BOSS_SPAWN))
        return encounter
    }

    /** Sends the current fear at the player once the encounter's introduction is over. */
    fun engage(player: Player) {
        val encounter = encounterOf(player) ?: return
        encounter.engaged = true
        val boss = encounter.boss ?: return
        if (boss.isSlotAssigned) {
            boss.opPlayer2(player, aiInteractions)
        }
    }

    fun tick(player: Player) {
        val encounter = encounterOf(player) ?: return
        if (encounter.finished) {
            return
        }
        if (!arena.isDreaming(player) || !DreamArena.inArena(arena.worldCoords(player))) {
            cleanup(player)
            return
        }
        if (!encounter.engaged) {
            if (++encounter.ticks >= INTRO_TICKS) {
                engage(player)
            }
            return
        }
        encounter.ticks++
        encounter.doubts.removeAll { !it.isSlotAssigned }
        val boss = encounter.boss
        if (boss != null && boss.isSlotAssigned) {
            if (encounter.fear == Fear.Inadequacy && encounter.ticks % DOUBT_TICKS == 0 && encounter.doubts.size < MAX_DOUBTS) {
                summonDoubt(player, encounter, boss)
            }
            if (encounter.ticks % REENGAGE_TICKS == 0) {
                boss.opPlayer2(player, aiInteractions)
            }
        }
        if (encounter.ticks % CYRISUS_TICKS == 0) {
            cyrisusActs(player, encounter)
        }
    }

    /** A hit landed on one of the player's fears or Doubts. */
    fun onHit(owner: Player, npc: Npc, damage: Int) {
        val encounter = encounterOf(owner) ?: return
        if (damage <= 0) {
            return
        }
        if (!encounter.engaged) {
            engage(owner)
        }
        owner.dreamDamageDealt = (owner.dreamDamageDealt + damage).coerceAtMost(MAX_DAMAGE_TRACKED)
        showCourage(owner)
        if (npc === encounter.boss && encounter.fear == Fear.Illusive && npc.hitpoints > 0) {
            encounter.illusiveHits++
            if (encounter.illusiveHits >= ILLUSIVE_HITS_BEFORE_BURROW) {
                encounter.illusiveHits = 0
                burrow(owner, npc)
            }
        }
    }

    /** One of the player's own fears has died; returns the fear that comes next, if any. */
    fun onFearDeath(owner: Player, npc: Npc): Fear? {
        val encounter = encounterOf(owner) ?: return null
        if (npc !== encounter.boss) {
            encounter.doubts.remove(npc)
            return null
        }
        if (encounter.fear == Fear.Inadequacy) {
            removeDoubts(encounter)
        }
        val next = encounter.fear.next ?: return null
        encounter.fear = next
        encounter.illusiveHits = 0
        val boss = spawn(owner, next.type, npc.coords)
        encounter.boss = boss
        boss.anim(next.spawnSeq)
        boss.spotanim(next.spawnSpot)
        boss.opPlayer2(owner, aiInteractions)
        owner.mes("Cyrisus' fear takes another form...")
        return next
    }

    /** The Illusive falls; Cyrisus finds his courage and stamps it out. */
    suspend fun ProtectedAccess.conquer(illusive: Npc) {
        val encounter = encounterOf(player) ?: return
        encounter.finished = true
        val tile = illusive.coords
        npcRepo.del(illusive, Int.MAX_VALUE)
        val beaten = spawn(player, BEATEN_ILLUSIVE, tile)
        beaten.anim(BEATEN_SEQ)
        encounter.boss = beaten
        val cyrisus = encounter.cyrisus?.takeIf { it.isSlotAssigned }
        startDialogue {
            chatPlayer(happy, "It's down! Now, Cyrisus! Finish it!")
        }
        if (cyrisus != null) {
            cyrisus.teleport(collision, tile.translateX(1))
            cyrisus.say("Right! Take that!")
            cyrisus.anim(STOMP_SEQ)
            beaten.anim(STOMPED_SEQ)
            beaten.spotanim(STOMPED_SPOT)
        }
        delay(STOMP_TICKS)
        dreamMentor.advanceTo(this, DreamMentorQuest.STAGE_CONQUERED)
        player.dreamCyrisusSpot = CyrisusSpot.oneiromancer(CombatStyle.of(player))
        startDialogue {
            chatPlayer(happy, "You did it!")
            chatNpcSpecific("Cyrisus", CombatStyle.of(player).cyrisusType, happy, "I did! I really did! Thank you!")
            chatPlayer(happy, "I never doubted you.")
            chatNpcSpecific("Cyrisus", CombatStyle.of(player).cyrisusType, quiz, "So what happens now?")
            chatPlayer(neutral, "Back to the waking world, then on to the Oneiromancer.")
            chatNpcSpecific("Cyrisus", CombatStyle.of(player).cyrisusType, happy, "I'll see you there!")
        }
    }

    fun cleanup(player: Player) {
        val encounter = encounters.remove(player.uid) ?: return
        removeDoubts(encounter)
        for (npc in listOfNotNull(encounter.boss, encounter.cyrisus)) {
            if (npc.isSlotAssigned) {
                npcRepo.del(npc, Int.MAX_VALUE)
            }
        }
        player.clearSoftTimer(FIGHT_TIMER)
        player.ifCloseOverlay(COURAGE_INTERFACE, eventBus)
    }

    /** Called before a fear's hit on the player lands: Cyrisus' Vengeance sends most of it back. */
    fun avenge(player: Player, attacker: Npc, damage: Int) {
        val encounter = encounterOf(player) ?: return
        if (!encounter.vengeance || damage <= 0 || !attacker.isSlotAssigned) {
            return
        }
        encounter.vengeance = false
        player.say("Taste vengeance!")
        attacker.queueHit(player, 1, HitType.Typeless, damage * VENGEANCE_PERCENT / 100, npcHitModifier)
    }

    fun courage(player: Player): Int = (player.dreamDamageDealt / COURAGE_PER_SEGMENT).coerceAtMost(COURAGE_SEGMENTS)

    private fun showCourage(player: Player) {
        val segments = courage(player)
        for (segment in 1..COURAGE_SEGMENTS) {
            player.ifSetHide("component.dream_couragebar:couragebar$segment", segment > segments)
        }
    }

    private fun cyrisusActs(player: Player, encounter: Encounter) {
        val cyrisus = encounter.cyrisus?.takeIf { it.isSlotAssigned } ?: return
        val boss = encounter.boss?.takeIf { it.isSlotAssigned }
        if (boss != null && boss.coords.chebyshevDistance(cyrisus.coords) <= 1 + boss.type.size) {
            cyrisus.teleport(collision, randomArenaTile(player))
            cyrisus.say("You'll never catch me!")
            return
        }
        if (courage(player) >= COURAGE_SEGMENTS && boss != null) {
            cyrisus.facePathingEntitySquare(boss)
            cyrisus.say("Take this!")
            boss.queueHit(cyrisus, 1, HitType.Melee, random.of(0, CYRISUS_MAX_HIT), npcHitModifier)
            return
        }
        val hitpoints = player.hitpoints
        val base = player.baseHitpointsLvl
        when {
            hitpoints * 2 < base && random.of(0, 1) == 0 -> {
                cyrisus.say("Hang in there, ${player.displayName}!")
                player.statHeal("stat.hitpoints", CYRISUS_HEAL, 0)
            }
            !encounter.vengeance && random.of(0, VENGEANCE_ONE_IN - 1) == 0 -> {
                cyrisus.say("Vengeance be yours!")
                cyrisus.anim(VENGEANCE_OTHER_SEQ)
                player.spotanim(VENGEANCE_OTHER_SPOT)
                encounter.vengeance = true
            }
            else -> cyrisus.say(FEAR_LINES[random.of(0, FEAR_LINES.lastIndex)])
        }
    }

    private fun summonDoubt(player: Player, encounter: Encounter, boss: Npc) {
        val tile = randomArenaTile(player, near = player.coords, radius = DOUBT_RADIUS)
        boss.anim(SUMMON_SEQ)
        val doubt = spawn(player, DOUBT, tile)
        doubt.anim(DOUBT_SPAWN_SEQ)
        doubt.spotanim(DOUBT_SPAWN_SPOT)
        doubt.opPlayer2(player, aiInteractions)
        encounter.doubts += doubt
    }

    private fun burrow(player: Player, illusive: Npc) {
        illusive.spotanim(DIG_OUT_SPOT)
        illusive.teleport(collision, randomArenaTile(player, away = illusive.coords))
        illusive.anim(Fear.Illusive.spawnSeq)
        illusive.spotanim(DIG_BACK_SPOT)
        illusive.opPlayer2(player, aiInteractions)
        player.mes("The Illusive burrows into the ground and surfaces somewhere else!")
    }

    private fun removeDoubts(encounter: Encounter) {
        for (doubt in encounter.doubts) {
            if (doubt.isSlotAssigned) {
                npcRepo.del(doubt, Int.MAX_VALUE)
            }
        }
        encounter.doubts.clear()
    }

    private fun spawn(player: Player, typeName: String, at: CoordGrid): Npc {
        val type = ServerCacheManager.getNpc(typeName.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $typeName")
        val npc = Npc(type, at)
        npcRepo.add(npc, LIFETIME)
        npc.respawns = false
        npc.assignSpawnOwner(player, clock.cycle)
        arena.attach(player, npc)
        return npc
    }

    /** A random open tile of the round arena floor, away from its walls and the lectern. */
    private fun randomArenaTile(
        player: Player,
        near: CoordGrid? = null,
        radius: Int = ARENA_RADIUS,
        away: CoordGrid? = null,
    ): CoordGrid {
        val centre = arena.at(player, ARENA_CENTRE)
        repeat(TILE_ATTEMPTS) {
            val from = near ?: centre
            val tile = from.translate(random.of(-radius, radius), random.of(-radius, radius))
            val dx = tile.x - centre.x
            val dz = tile.z - centre.z
            val inside = dx * dx + dz * dz <= ARENA_RADIUS * ARENA_RADIUS
            val farEnough = away == null || tile.chebyshevDistance(away) >= BURROW_DISTANCE
            if (inside && farEnough) {
                return tile
            }
        }
        return centre
    }

    private fun CoordGrid.chebyshevDistance(other: CoordGrid): Int = maxOf(kotlin.math.abs(x - other.x), kotlin.math.abs(z - other.z))

    companion object {
        const val FIGHT_TIMER = "timer.dream_mentor_fight"
        const val COURAGE_INTERFACE = "interface.dream_couragebar"
        const val OVERLAY_TARGET = "component.toplevel_osrs_stretch:overlay_hud"

        const val DOUBT = "npc.dream_razorbeast"
        const val BEATEN_ILLUSIVE = "npc.dream_illusive_noncombat"

        const val COURAGE_SEGMENTS = 10
        const val COURAGE_PER_SEGMENT = 25
        const val MAX_DAMAGE_TRACKED = 255

        const val DOUBT_TICKS = 20
        const val MAX_DOUBTS = 3
        const val DOUBT_RADIUS = 3
        const val REENGAGE_TICKS = 10
        const val CYRISUS_TICKS = 10
        const val CYRISUS_MAX_HIT = 10
        const val CYRISUS_HEAL = 10
        const val VENGEANCE_ONE_IN = 4
        const val VENGEANCE_PERCENT = 75
        const val ILLUSIVE_HITS_BEFORE_BURROW = 2
        const val BURROW_DISTANCE = 6
        const val STOMP_TICKS = 3

        /** How long the fears wait for an interrupted introduction before attacking anyway. */
        const val INTRO_TICKS = 25

        private const val LIFETIME = 3000
        private const val TILE_ATTEMPTS = 20
        private const val ARENA_RADIUS = 12
        private val ARENA_CENTRE = CoordGrid(1824, 5151, 2)

        private const val SUMMON_SEQ = "seq.dream_inadequacy_ranged_attack"
        private const val DOUBT_SPAWN_SEQ = "seq.dream_razorbeast_spawn"
        private const val DOUBT_SPAWN_SPOT = "spotanim.dream_razor_beast_spawn_spotanim"
        private const val DIG_OUT_SPOT = "spotanim.dream_illusive_diggin_spotanim_1"
        private const val DIG_BACK_SPOT = "spotanim.dream_illusive_diggin_spotanim_2"
        private const val BEATEN_SEQ = "seq.dream_illusive_death_ready"
        private const val STOMP_SEQ = "seq.dream_cyrisus_stomp"
        private const val STOMPED_SEQ = "seq.dream_illusive_death_stomped"
        private const val STOMPED_SPOT = "spotanim.dream_illusive_stomped_spotanim"
        private const val VENGEANCE_OTHER_SEQ = "seq.vengeance_spell_anim_nostalling"
        private const val VENGEANCE_OTHER_SPOT = "spotanim.quest_lunar_spellbook_vengeance_other_spot_anim"

        val FEAR_LINES =
            listOf(
                "I can't... I'm too scared to attack!",
                "Give me a moment, I'm working up my nerve.",
                "Keep fighting! Watching you makes me braver.",
                "I don't know if I can do this.",
            )
    }
}
