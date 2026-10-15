package org.rsmod.content.quest.area.draynor.vampyreslayer

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import jakarta.inject.Singleton
import kotlin.math.sign
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.invtx.invDel
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.output.Camera
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.HAMMER
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAGE_COMPLETE
import org.rsmod.content.quest.area.draynor.vampyreslayer.VampyreSlayerQuest.Companion.STAKE
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Count Draynor's coffin in the Draynor Manor basement. Opening it while the quest is in
 * progress plays the coffin opening and the Count rising out of it, then sets him on the player.
 *
 * Normal damage never kills him: at zero hitpoints the player must be carrying a stake and a
 * hammer, or he regenerates to full. He restores a point of every stat each [RESTORE_INTERVAL]
 * cycles (far faster than other monsters), and sinks back into his coffin, healed, if the player
 * leaves the basement or takes longer than [LIFETIME] cycles. Garlic is handled by
 * [GarlicAttackHook].
 */
@Singleton
class CountDraynor
@Inject
constructor(
    private val vampyreSlayer: VampyreSlayerQuest,
    private val locRepo: LocRepository,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val death: NpcDeath,
    private val mapClock: MapClock,
) : PluginScript() {

    private data class Summon(val owner: PlayerUid, val spawnedAt: Int)

    private val countType = ServerCacheManager.getNpc(COUNT.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $COUNT")
    private val risingType = ServerCacheManager.getNpc(RISING.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $RISING")

    private val summons = HashMap<Npc, Summon>()

    override fun ScriptContext.startup() {
        onOpLoc1(COFFIN) { openCoffin(it.loc) }
        onAiTimer(COUNT) { tick(npc) }
        onNpcQueue(countType, "queue.death") { defeated() }
    }

    private suspend fun ProtectedAccess.openCoffin(coffin: BoundLocInfo) {
        arriveDelay()
        val stage = vampyreSlayer.stage(player)
        if (stage == 0 || stage >= STAGE_COMPLETE) {
            mes("The coffin is sealed shut.")
            return
        }
        if (summons.values.any { it.owner == player.uid }) {
            return
        }
        faceLoc(coffin)
        anim("seq.human_openchest")
        soundSynth("synth.coffin_open")
        Camera.camMoveTo(player, CAMERA_FROM, CAMERA_HEIGHT, CAMERA_RATE, CAMERA_RATE)
        Camera.camLookAt(player, CAMERA_AT, LOOK_HEIGHT, CAMERA_RATE, CAMERA_RATE)
        riseFrom(player.uid)
        // Swapping the loc ends this script, so it is the last thing done here.
        locRepo.change(coffin, OPENING, OPENING_TICKS + 1)
    }

    /** The Count climbs out of the coffin and, once he is on his feet, goes for [owner]. */
    private fun riseFrom(owner: PlayerUid) {
        worldQueues.add(OPENING_TICKS) {
            locRepo.add(COFFIN_COORDS, OPENED, RISE_TICKS + LIFETIME + DESPAWN_MARGIN, COFFIN_ANGLE, COFFIN_SHAPE)
            val rising = Npc(risingType, COFFIN_COORDS)
            npcRepo.add(rising, RISE_TICKS + 1)
            rising.mode = NpcMode.None
            rising.anim(ARISE_SEQ)
            owner.resolve(playerList)?.soundSynth(ARRIVE_SOUND)
            worldQueues.add(RISE_TICKS) {
                if (rising.isSlotAssigned) {
                    npcRepo.del(rising, Int.MAX_VALUE)
                }
                val player = owner.resolve(playerList)
                if (player == null) {
                    closeCoffin()
                    return@add
                }
                Camera.camReset(player)
                spawnCount(player)
            }
        }
    }

    private fun spawnCount(player: Player) {
        val count = Npc(countType, COUNT_TILE)
        npcRepo.add(count, LIFETIME + DESPAWN_MARGIN)
        count.combatXpMultiplier = COMBAT_XP_MULTIPLIER
        summons[count] = Summon(player.uid, mapClock.cycle)
        count.aiTimer(1)
        count.opPlayer2(player, aiInteractions)
    }

    private fun tick(count: Npc) {
        val summon = summons[count] ?: return
        val owner = summon.owner.resolve(playerList)
        val elapsed = mapClock.cycle - summon.spawnedAt
        if (owner == null || !owner.isNearby(count) || elapsed >= LIFETIME) {
            owner?.takeIf { it.isNearby(count) }?.mes("Count Draynor retreats to his coffin to recover.")
            retreat(count)
            return
        }
        if (elapsed % RESTORE_INTERVAL == 0) {
            count.restoreStep()
        }
    }

    private fun Player.isNearby(count: Npc): Boolean =
        coords.level == count.coords.level && coords.chebyshevDistance(count.coords) <= LEASH_RANGE

    private fun Npc.restoreStep() {
        attackLvl += (baseAttackLvl - attackLvl).sign
        strengthLvl += (baseStrengthLvl - strengthLvl).sign
        defenceLvl += (baseDefenceLvl - defenceLvl).sign
        if (hitpoints in 1 until baseHitpointsLvl) {
            hitpoints++
        }
    }

    private fun retreat(count: Npc) {
        summons.remove(count)
        if (count.isSlotAssigned) {
            npcRepo.del(count, Int.MAX_VALUE)
        }
        closeCoffin()
    }

    private fun closeCoffin() {
        if (summons.isEmpty()) {
            locRepo.add(COFFIN_COORDS, COFFIN, Int.MAX_VALUE, COFFIN_ANGLE, COFFIN_SHAPE)
        }
    }

    /** Zero hitpoints: only a stake driven in with a hammer finishes him. */
    private suspend fun StandardNpcAccess.defeated() {
        val summon = summons[npc]
        val hero = findHero(playerList) ?: summon?.owner?.resolve(playerList)
        if (hero == null) {
            regenerate(npc, null)
            return
        }
        if (hero.inv.count(STAKE) == 0) {
            regenerate(npc, hero)
            return
        }
        if (!hero.carriesHammer()) {
            hero.mes(red("You're unable to push the stake far enough in!"))
            regenerate(npc, hero)
            return
        }
        hero.mes(red("You hammer the stake into the vampyre's chest!"))
        hero.invDel(hero.inv, STAKE)
        hero.anim(STAKE_SEQ)
        summons.remove(npc)
        val heroUid = hero.uid
        death.deathNoDrops(this)
        closeCoffin()
        completeQuest(heroUid, COMPLETE_DELAY, attempts = COMPLETE_ATTEMPTS)
    }

    private fun regenerate(count: Npc, hero: Player?) {
        count.heal(count.baseHitpointsLvl, showHitsplat = true)
        if (hero == null) {
            return
        }
        hero.mes(red("The vampyre seems to regenerate!"))
        count.opPlayer2(hero, aiInteractions)
    }

    private fun completeQuest(uid: PlayerUid, delay: Int, attempts: Int) {
        worldQueues.add(delay) {
            val hero = uid.resolve(playerList) ?: return@add
            if (vampyreSlayer.stage(hero) >= STAGE_COMPLETE) {
                return@add
            }
            val launched = launcher.launch(hero) { vampyreSlayer.quest.completeQuest(this) }
            if (!launched && attempts > 0) {
                completeQuest(uid, delay = 1, attempts = attempts - 1)
            }
        }
    }

    private fun Player.carriesHammer(): Boolean =
        HAMMERS.any { inv.count(it) > 0 } || worn.count(IMCANDO_OFFHAND) > 0

    private fun red(text: String): String = "<col=ef1020>$text</col>"

    companion object {
        const val COUNT = "npc.count_draynor"
        private const val RISING = "npc.count_draynor_coffin"

        private const val COFFIN = "loc.vampcoffin"
        private const val OPENING = "loc.vampcoffin_animated"
        private const val OPENED = "loc.vampcoffinopen"
        private val COFFIN_COORDS = CoordGrid(3077, 9775, 0)
        private val COFFIN_ANGLE = LocAngle.South
        private val COFFIN_SHAPE = LocShape.CentrepieceStraight

        /** South of the coffin, beside the tile the coffin is opened from. */
        private val COUNT_TILE = CoordGrid(3078, 9774, 0)

        private const val ARISE_SEQ = "seq.vampireslayer_arise"
        private const val STAKE_SEQ = "seq.human_stake"
        private const val ARRIVE_SOUND = "synth.vampire_arrives"

        /** `seq.vampireslayer_coffin_open` on the animated coffin lasts three cycles. */
        private const val OPENING_TICKS = 3

        /** `seq.vampireslayer_arise` lasts six cycles. */
        private const val RISE_TICKS = 6

        private const val LIFETIME = 500
        private const val DESPAWN_MARGIN = 10
        private const val LEASH_RANGE = 10
        private const val RESTORE_INTERVAL = 5

        /** The Count gives 2.5% of normal combat experience to balance his regeneration. */
        private const val COMBAT_XP_MULTIPLIER = 25

        private const val COMPLETE_DELAY = 3
        private const val COMPLETE_ATTEMPTS = 20

        private val HAMMERS = listOf(HAMMER, "obj.imcando_hammer", "obj.imcando_hammer_offhand")
        private const val IMCANDO_OFFHAND = "obj.imcando_hammer_offhand"

        private val CAMERA_FROM = CoordGrid(3077, 9770, 0)
        private val CAMERA_AT = CoordGrid(3078, 9776, 0)
        private const val CAMERA_HEIGHT = 700
        private const val LOOK_HEIGHT = 150
        private const val CAMERA_RATE = 100
    }
}
