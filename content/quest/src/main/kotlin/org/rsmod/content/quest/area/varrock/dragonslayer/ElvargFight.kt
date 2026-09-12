package org.rsmod.content.quest.area.varrock.dragonslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.bosses.dsl.Dragonfire
import org.rsmod.api.bosses.dsl.Melee
import org.rsmod.api.bosses.dsl.Roll
import org.rsmod.api.bosses.dsl.WithinMeleeRange
import org.rsmod.api.bosses.dsl.boss
import org.rsmod.api.bosses.runtime.BossCombat
import org.rsmod.api.bosses.runtime.BossDeps
import org.rsmod.api.bosses.spec.Effect
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.table.MusicRow
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.ELVARGS_HEAD
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_ELVARG_SLAIN
import org.rsmod.content.quest.area.varrock.dragonslayer.DragonSlayerQuest.Companion.STAGE_ON_CRANDOR
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Elvarg's lair. Climbing over the wall of stalagmites puts the player in a private copy of the
 * cave with Elvarg waiting inside; climbing back over it leaves. Elvarg bites when she can reach
 * and breathes fire otherwise, and the fire is only survivable behind an anti-dragon shield.
 *
 * The fight uses the Nightmare Zone copy of Elvarg (`npc.nzone_elvarg_normal`): it is the only
 * Elvarg in the cache that is both attackable and fully animated. The cache's own quest Elvarg
 * (`npc.elvarg`) stays in the world lair as scenery, driven by the quest varp.
 *
 * When she dies during the quest the player takes her head and the quest moves to
 * [STAGE_ELVARG_SLAIN]; afterwards she can be fought again for the fun of it.
 */
class ElvargFight
@Inject
constructor(
    private val dragonSlayer: DragonSlayerQuest,
    private val instances: DragonSlayerInstances,
    private val deps: BossDeps,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val musicPlayer: MusicPlayer,
) : PluginScript() {

    private val elvargType =
        ServerCacheManager.getNpc(ELVARG.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $ELVARG")

    private val lairTrack: MusicRow by lazy { MusicRow.getRow(LAIR_TRACK) }

    private val spec =
        boss(ELVARG) {
            stats(attackRate = ATTACK_RATE, aggressionRadius = AGGRESSION_RADIUS)

            val bite =
                ability("bite") {
                    anim("seq.dragon_attack")
                    hit {
                        damage(0..MELEE_MAX).roll()
                        type(Melee)
                    }
                }

            val dragonfire =
                ability("dragonfire") {
                    anim("seq.dragonslayer_elvarg_fire")
                    sound("synth.dragonslayer_dragonbreath")
                    projectile(
                        spotanim = "spotanim.dragon_ranged_fire_attack",
                        travel = "projanim.dragonfire",
                        hit = Effect.Hit(damage = Roll(0..DRAGONFIRE_MAX), type = Dragonfire),
                    )
                }

            phase("combat") {
                weightedSelectorRandom {
                    +random(bite, weight = 3, requires = WithinMeleeRange)
                    +random(dragonfire, weight = 2)
                }
            }
        }

    override fun ScriptContext.startup() {
        BossCombat.register(this, spec, deps)
        onNpcQueue(elvargType, "queue.death") { slain() }
        onOpLoc1(LAIR_WALL) { climbWall() }
    }

    /* The wall */

    private suspend fun ProtectedAccess.climbWall() {
        arriveDelay()
        val inside = player.coords.x > WALL_X
        if (inside) {
            leaveLair()
        } else {
            enterLair()
        }
    }

    private suspend fun ProtectedAccess.enterLair() {
        if (with(instances) { insideCopy() }) {
            return
        }
        anim(CLIMB_SEQ)
        delay(1)
        val visit = with(instances) { enterCopy(KEY, LAIR_ENTRY, LAIR_EXIT, listOf(ELVARG_SPAWN), bossName = "Elvarg") }
            ?: return
        mes("You climb over the wall and drop down into the dragon's lair.")
        delay(1)
        musicPlay(lairTrack)
        soundSynth("synth.dragonslayer_dragonscream")
    }

    private suspend fun ProtectedAccess.leaveLair() {
        anim(CLIMB_SEQ)
        delay(1)
        val exit = with(instances) { leaveCopy() }
        telejump(exit ?: LAIR_EXIT)
        musicPlayer.skipTrack(player)
    }

    /* The kill */

    private suspend fun StandardNpcAccess.slain() {
        val hero = findHero(playerList)
        anim(DEATH_SEQ)
        hero?.soundSynth("synth.dragonslayer_dragonscream")
        delay(DEATH_TICKS)
        npcRepo.del(npc, Int.MAX_VALUE)
        if (hero != null) {
            launcher.launch(hero) { claimVictory() }
        }
    }

    private suspend fun ProtectedAccess.claimVictory() {
        player.midiJingle(SLAYERS_FEAT_JINGLE)
        anim(BEHEAD_SEQ)
        soundSynth("synth.dragonslayer_cutoffhead")
        delay(1)
        mes("You cut off Elvarg's head as proof of your victory!")
        if (dragonSlayer.stage(player) != STAGE_ON_CRANDOR) {
            return
        }
        if (invAdd(inv, ELVARGS_HEAD).failure) {
            mes("You have no room for the head, but Oziach will take your word for it.")
        }
        dragonSlayer.setStage(this, STAGE_ELVARG_SLAIN)
    }

    private companion object {
        const val KEY = "dragonslayer_lair"
        const val ELVARG = "npc.nzone_elvarg_normal"
        const val LAIR_WALL = "loc.dragon_slayer_qip_stalagtite_jump"
        const val LAIR_TRACK = "dbrow.music_elvargs_theme"

        const val CLIMB_SEQ = "seq.human_reachforladder"
        const val DEATH_SEQ = "seq.dragon_death_elvarg"
        const val BEHEAD_SEQ = "seq.human_pickupfloor"

        /** "A Slayer's Feat", by js5 archive 11 group (see [org.rsmod.api.player.midiJingle]). */
        const val SLAYERS_FEAT_JINGLE = 267

        const val ATTACK_RATE = 4
        const val AGGRESSION_RADIUS = 10
        const val MELEE_MAX = 8
        const val DRAGONFIRE_MAX = 70

        /** The death animation runs for about seven cycles. */
        const val DEATH_TICKS = 7

        /** The stalagmite wall stands on x = 2846; the lair is east of it. */
        const val WALL_X = 2846
        val LAIR_ENTRY = CoordGrid(2847, 9636, 0)
        val LAIR_EXIT = CoordGrid(2845, 9636, 0)

        /** Elvarg's world spawn tile, at the back of the lair. */
        val ELVARG_SPAWN = InstanceNpc(ELVARG, CoordGrid(2852, 9637, 0))
    }
}
