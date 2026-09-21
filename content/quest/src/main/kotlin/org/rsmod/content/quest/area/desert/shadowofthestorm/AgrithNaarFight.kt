package org.rsmod.content.quest.area.desert.shadowofthestorm

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.combat.commons.npc.attackRate
import org.rsmod.api.combat.commons.player.finishNpcHit
import org.rsmod.api.combat.commons.player.queueCombatRetaliate
import org.rsmod.api.combat.commons.types.MeleeAttackType
import org.rsmod.api.combat.formulas.AccuracyFormulae
import org.rsmod.api.combat.formulas.MaxHitFormulae
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.npc.vars.typePlayerUidVarn
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.isInPvnCombat
import org.rsmod.api.player.isInPvpCombat
import org.rsmod.api.player.isValidTarget
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarp
import org.rsmod.api.player.vars.typeNpcUidVarp
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onAiApPlayer2
import org.rsmod.api.script.onAiOpPlayer2
import org.rsmod.api.script.onModifyNpcHit
import org.rsmod.api.script.onNpcHit
import org.rsmod.content.quest.area.desert.shadowofthestorm.ShadowOfTheStormQuest.Companion.AGRITH_NAAR
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.proj.ProjAnim
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Agrith-Naar, held in the circle the five casters are standing on.
 *
 * He never leaves the marked floor, so the fight is about how the player stands rather than where.
 * Up close and unprotected he swings; protect from melee and he answers with fire, and so does
 * standing out of his reach. A player who backs off and prays against magic as well gets dragged
 * back in by the scruff of the neck - there is no corner of the room he cannot reach.
 *
 * Anything will wound him, but only Silverlight can finish him. A killing blow from anything else
 * is turned aside and the wounds close, which is the whole reason Father Reen insisted the player
 * bring the sword.
 */
@Singleton
class AgrithNaarFight
@Inject
constructor(
    private val sots: ShadowOfTheStormQuest,
    private val accuracy: AccuracyFormulae,
    private val maxHits: MaxHitFormulae,
    private val worldRepo: WorldRepository,
    private val hitModifier: PlayerHitModifier,
    private val areaChecker: AreaChecker,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
) : PluginScript() {

    override fun ScriptContext.startup() {
        val type =
            ServerCacheManager.getNpc(AGRITH_NAAR.asRSCM(RSCMType.NPC))
                ?: error("Missing npc: $AGRITH_NAAR")

        onAiOpPlayer2(type) { attack(it.target) }
        onAiApPlayer2(type) { attack(it.target) }

        onModifyNpcHit(type) {
            if (!hit.isFromPlayer || hit.damage <= 0) {
                return@onModifyNpcHit
            }
            val uid = hit.sourceUid ?: return@onModifyNpcHit
            val source = PlayerUid(uid).resolve(playerList) ?: return@onModifyNpcHit
            if (hit.damage >= npc.hitpoints && !SotsItems.wieldsSilverlight(source)) {
                hit.damage = npc.hitpoints - 1
            }
        }

        onNpcHit(type) {
            if (npc.hitpoints > 1 || !hit.isFromPlayer) {
                return@onNpcHit
            }
            val source = hit.resolvePlayerSource(playerList) ?: return@onNpcHit
            if (SotsItems.wieldsSilverlight(source)) {
                return@onNpcHit
            }
            knitBackTogether(npc, source)
        }
    }

    /**
     * The wound that should have killed him closes. Hits are capped when they are queued rather
     * than when they land, so a capped hit can still arrive after an earlier one has finished him;
     * clearing the death queue undoes that.
     */
    private fun knitBackTogether(npc: Npc, source: Player) {
        npc.clearQueue(DEATH_QUEUE)
        npc.heal(npc.baseHitpointsLvl - npc.hitpoints)
        npc.spotanim(REGENERATE_SPOTANIM)
        npc.say("Steel? You brought me STEEL?")
        source.mes("Agrith-Naar's wounds close over. Only Silverlight can finish him.")
    }

    private fun StandardNpcAccess.attack(target: Player) {
        if (!canAttack(target)) {
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

        val inReach = npc.isWithinDistance(target, MELEE_RANGE)
        when {
            inReach && !target.protectingFromMelee() -> claw(target)
            !inReach && target.protectingFromMagic() -> haul(target)
            else -> fireBlast(target)
        }
    }

    private fun StandardNpcAccess.claw(target: Player) {
        anim(MELEE_SEQ)
        worldRepo.soundArea(npc, MELEE_SOUND, radius = SOUND_RADIUS)
        val landed = accuracy.rollMeleeAccuracy(npc, target, MeleeAttackType.Slash, random)
        val damage =
            if (landed) {
                random.of(0..maxHits.getMeleeMaxHit(npc, target, MeleeAttackType.Slash))
            } else {
                0
            }
        target.finishNpcHit(npc, MELEE_HIT_DELAY, HitType.Melee, damage, hitModifier)
    }

    private fun StandardNpcAccess.fireBlast(target: Player) {
        anim(CAST_SEQ)
        npc.spotanim(CAST_SPOTANIM, height = CAST_HEIGHT)
        worldRepo.soundArea(npc, CAST_SOUND, radius = SOUND_RADIUS)

        val projType =
            ServerCacheManager.getProjectile(PROJANIM.asRSCM(RSCMType.PROJANIM))
                ?: error("Missing projanim: $PROJANIM")
        val flight =
            ProjAnim.fromNpcToPlayer(npc, target, TRAVEL_SPOTANIM.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(flight)

        val landed = accuracy.rollMagicAccuracy(npc, target, random)
        if (!landed) {
            target.spotanim(SPLASH_SPOTANIM, delay = flight.clientCycles, height = IMPACT_HEIGHT)
            worldRepo.soundArea(target, SPLASH_SOUND, delay = flight.clientCycles, radius = SOUND_RADIUS)
            target.queueCombatRetaliate(npc, delay = flight.serverCycles)
            target.finishNpcHit(npc, flight.serverCycles, HitType.Magic, 0, hitModifier, flight.clientCycles)
            return
        }
        val maxHit = maxHits.getMagicMaxHit(npc, target).coerceIn(1, FIRE_BLAST_MAX_HIT)
        target.spotanim(IMPACT_SPOTANIM, delay = flight.clientCycles, height = IMPACT_HEIGHT)
        worldRepo.soundArea(target, HIT_SOUND, delay = flight.clientCycles, radius = SOUND_RADIUS)
        target.finishNpcHit(
            npc,
            flight.serverCycles,
            HitType.Magic,
            random.of(0..maxHit),
            hitModifier,
            flight.clientCycles,
        )
    }

    /** Telekinetic grab, used on the player rather than on an item: he pulls them back in. */
    private fun StandardNpcAccess.haul(target: Player) {
        anim(CAST_SEQ)
        npc.spotanim(GRAB_CAST_SPOTANIM, height = CAST_HEIGHT)
        worldRepo.soundArea(npc, GRAB_SOUND, radius = SOUND_RADIUS)

        val projType =
            ServerCacheManager.getProjectile(PROJANIM.asRSCM(RSCMType.PROJANIM))
                ?: error("Missing projanim: $PROJANIM")
        val flight =
            ProjAnim.fromNpcToPlayer(npc, target, GRAB_TRAVEL_SPOTANIM.asRSCM(RSCMType.SPOTANIM), projType)
        worldRepo.projAnim(flight)

        target.spotanim(GRAB_IMPACT_SPOTANIM, delay = flight.clientCycles, height = IMPACT_HEIGHT)
        target.queueCombatRetaliate(npc, delay = flight.serverCycles)
        val landing = adjacentTile(target)
        launcher.launch(target) {
            mes("Agrith-Naar drags you back within reach.")
            telejump(landing, TeleportType.Exempt)
        }
    }

    /** The tile just outside his reach nearest the player, so the pull never lands in a wall. */
    private fun StandardNpcAccess.adjacentTile(target: Player): CoordGrid {
        val half = npc.size / 2
        val centre = npc.coords.translate(half, half)
        return centre.translate(
            (target.coords.x - centre.x).coerceIn(-PULL_RADIUS, PULL_RADIUS),
            (target.coords.z - centre.z).coerceIn(-PULL_RADIUS, PULL_RADIUS),
        )
    }

    private fun StandardNpcAccess.canAttack(target: Player): Boolean {
        if (!target.isValidTarget()) {
            return false
        }
        if (!sots.inProgress(target)) {
            return false
        }
        val singleCombat = !mapMultiway(areaChecker)
        if (singleCombat) {
            if (target.isInPvpCombat()) {
                return false
            }
            if (target.isInPvnCombat()) {
                val aggressor = target.aggressiveNpc
                if (aggressor != null && aggressor != npc.uid) {
                    return false
                }
            }
        }
        return true
    }

    private companion object {
        const val DEATH_QUEUE = "queue.death"

        const val MELEE_SEQ = "seq.demon_update_attack"
        const val CAST_SEQ = "seq.demon_update_fireball_cast"
        const val REGENERATE_SPOTANIM = "spotanim.weaken_impact"

        const val CAST_SPOTANIM = "spotanim.fireblast_casting"
        const val TRAVEL_SPOTANIM = "spotanim.fireblast_travel"
        const val IMPACT_SPOTANIM = "spotanim.fireblast_impact"
        const val SPLASH_SPOTANIM = "spotanim.failedspell_impact"
        const val GRAB_CAST_SPOTANIM = "spotanim.telegrab_casting"
        const val GRAB_TRAVEL_SPOTANIM = "spotanim.telegrab_travel"
        const val GRAB_IMPACT_SPOTANIM = "spotanim.telegrab_impact"

        const val PROJANIM = "projanim.magic_spell"

        const val MELEE_SOUND = "synth.demon_attack"
        const val CAST_SOUND = "synth.fireblast_cast_and_fire"
        const val HIT_SOUND = "synth.fireblast_hit"
        const val SPLASH_SOUND = "synth.spellfail"
        const val GRAB_SOUND = "synth.telegrab_all"

        const val SOUND_RADIUS = 10
        const val CAST_HEIGHT = 92
        const val IMPACT_HEIGHT = 124

        /** Agrith-Naar is 3x3, so "next to the player" is two tiles from his south-west corner. */
        const val MELEE_RANGE = 2

        const val MELEE_HIT_DELAY = 1
        const val FIRE_BLAST_MAX_HIT = 16
        const val PULL_RADIUS = 2

        private var Npc.lastAttack: Int by intVarn("varn.lastattack")
        private var Npc.attackingPlayer: PlayerUid? by typePlayerUidVarn("varn.attacking_player")
        private var Player.lastCombat: Int by intVarp("varp.lastcombat")
        private var Player.aggressiveNpc: NpcUid? by typeNpcUidVarp("varp.aggressive_npc")

        fun Player.protectingFromMelee(): Boolean = vars["varbit.prayer_protectfrommelee"] > 0

        fun Player.protectingFromMagic(): Boolean = vars["varbit.prayer_protectfrommagic"] > 0
    }
}
