package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.aconverted.SpotanimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.hit.modifier.PlayerHitModifier
import org.rsmod.api.player.hit.queueHit
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.hit.HitType
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid

/**
 * Rashiliyia's servants: the undead ones she and Mosol Rei's warnings call up, the choking green
 * mist a fallen zombie leaves behind, and the queen's own apparition, which chokes anyone in her
 * tomb who is not protected by the Beads of the Dead and sets her minions on them.
 */
@Singleton
class ShiloUndead
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val npcRepo: NpcRepository,
    private val world: WorldRepository,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val hitModifier: PlayerHitModifier,
    private val mapClock: MapClock,
    private val random: GameRandom,
) {
    private val shilomist = SpotanimType(MIST_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
    private val smokepuff = SpotanimType(SMOKE_SPOTANIM.asRSCM(RSCMType.SPOTANIM))
    private val summonCooldowns = HashMap<PlayerUid, Int>()

    /** Raises one to three undead ones around [player] and sets them on them. */
    fun ProtectedAccess.raiseUndeadOnes(duration: Int) {
        repeat(random.of(1, MAX_MINIONS)) {
            val spot = mapFindSquareLineOfWalk(player.coords, 1, MINION_RADIUS) ?: return@repeat
            val type = ServerCacheManager.getNpc(ShiloVillageQuest.UNDEAD_ONES.random().asRSCM(RSCMType.NPC)) ?: return@repeat
            val undead = Npc(type, spot)
            npcRepo.add(undead, duration)
            world.spotanimMap(smokepuff, spot)
            world.soundArea(spot, SMOKE_SOUND)
            MINION_CRIES.randomOrNull()?.let(undead::say)
            undead.opPlayer2(player, aiInteractions)
        }
    }

    /** The mist that rises where a zombie undead one fell, choking everyone beside it. */
    fun greenMist(coords: CoordGrid) {
        greenMistPulse(coords, 0)
    }

    /** The village mist catches up with a player the zombies dragged into the gatehouse. */
    fun gatehouseMist(uid: PlayerUid) {
        worldQueues.add(GATEHOUSE_MIST_DELAY) {
            val victim = uid.resolve(playerList) ?: return@add
            if (ShiloCoords.inGatehouse(victim.coords)) {
                greenMist(victim.coords)
            }
        }
    }

    private fun greenMistPulse(coords: CoordGrid, pulse: Int) {
        if (pulse >= MIST_PULSES) {
            return
        }
        world.spotanimMap(shilomist, coords, height = MIST_HEIGHT)
        for (victim in playerList) {
            if (victim.coords.level != coords.level || victim.coords.chebyshevDistance(coords) > 1) {
                continue
            }
            if (pulse == 0) {
                victim.mes("A green thick mist rises from the ground and starts choking you.")
            }
            victim.queueHit(delay = 1, type = HitType.Typeless, damage = random.of(MIST_MIN, MIST_MAX), modifier = hitModifier)
        }
        worldQueues.add(MIST_INTERVAL) { greenMistPulse(coords, pulse + 1) }
    }

    /**
     * Rashiliyia appears to a player inside her tomb who is not wearing the Beads of the Dead,
     * strangles them and summons her minions. She will not come again for two minutes.
     */
    fun ProtectedAccess.rashiliyiaAppears() {
        if (shilo.isComplete(player) || player.wearsBeadsOfTheDead() || !ShiloCoords.inRashiliyiaTomb(player.coords)) {
            return
        }
        val now = this@ShiloUndead.mapClock.cycle
        val cooldown = summonCooldowns[player.uid]
        if (cooldown != null && cooldown > now) {
            return
        }
        summonCooldowns[player.uid] = now + SUMMON_COOLDOWN
        val spot = mapFindSquareLineOfWalk(player.coords, 1, 1) ?: player.coords
        val type = ServerCacheManager.getNpc(ShiloVillageQuest.RASHILIYIA.asRSCM(RSCMType.NPC)) ?: return
        mes("Rashiliyia appears!")
        world.spotanimMap(shilomist, spot, height = MIST_HEIGHT)
        faceSquare(spot)
        val queen = Npc(type, spot)
        npcRepo.add(queen, QUEEN_DURATION)
        queen.facePlayer(player)
        haunt(queen, player.uid, 0)
    }

    private fun haunt(queen: Npc, uid: PlayerUid, step: Int) {
        if (!queen.isSlotAssigned) {
            return
        }
        val victim = uid.resolve(playerList)
        when (step) {
            0 -> queen.say("Which non-kin dares enter my tomb?")
            1 -> {
                queen.say("Let me squeeze the life from your mortal frame.")
                queen.anim(GHOST_ATTACK_SEQ)
                world.soundArea(queen, GHOST_ATTACK_SOUND)
            }
            2 ->
                if (victim != null && ShiloCoords.inRashiliyiaTomb(victim.coords)) {
                    victim.mes("You feel invisible hands starting to choke you...")
                    victim.say("* Gaaaa.... *")
                    victim.queueHit(delay = 1, type = HitType.Typeless, damage = random.of(1, CHOKE_MAX), modifier = hitModifier)
                }
            3 -> queen.say("My minions will finish thee!")
            4 -> {
                queen.anim(GHOST_ATTACK_SEQ)
                world.soundArea(queen, GHOST_ATTACK_SOUND)
            }
            else -> {
                world.spotanimMap(shilomist, queen.coords, height = MIST_HEIGHT)
                world.soundArea(queen.coords, SMOKE_SOUND)
                npcRepo.del(queen, Int.MAX_VALUE)
                worldQueues.add(MINION_DELAY) {
                    val target = uid.resolve(playerList) ?: return@add
                    if (!ShiloCoords.inRashiliyiaTomb(target.coords)) {
                        return@add
                    }
                    launchWhenFree(target.uid) { raiseUndeadOnes(TOMB_MINION_DURATION) }
                }
                return
            }
        }
        worldQueues.add(HAUNT_STEP_TICKS) { haunt(queen, uid, step + 1) }
    }

    /** Runs [block] for the player behind [uid] once they are free of whatever holds them. */
    fun launchWhenFree(uid: PlayerUid, block: suspend ProtectedAccess.() -> Unit) {
        launchWhenFree(uid, LAUNCH_ATTEMPTS, block)
    }

    private fun launchWhenFree(uid: PlayerUid, attempts: Int, block: suspend ProtectedAccess.() -> Unit) {
        worldQueues.add(1) {
            val player = uid.resolve(playerList) ?: return@add
            val free = player.queueList.strongQueues == 0 && launcher.launch(player, block = block)
            if (!free && attempts > 0) {
                launchWhenFree(uid, attempts - 1, block)
            }
        }
    }

    fun spawnApparition(npc: String, coords: CoordGrid, duration: Int, facing: Player): Npc? {
        val type = ServerCacheManager.getNpc(npc.asRSCM(RSCMType.NPC)) ?: return null
        world.spotanimMap(smokepuff, coords)
        world.soundArea(coords, SMOKE_SOUND)
        val spirit = Npc(type, coords)
        npcRepo.add(spirit, duration)
        spirit.facePlayer(facing)
        return spirit
    }

    fun dismissApparition(spirit: Npc) {
        if (!spirit.isSlotAssigned) {
            return
        }
        world.spotanimMap(shilomist, spirit.coords, height = MIST_HEIGHT)
        npcRepo.del(spirit, Int.MAX_VALUE)
    }

    private companion object {
        const val MIST_SPOTANIM = "spotanim.shilomist"
        const val SMOKE_SPOTANIM = "spotanim.smokepuff"
        const val SMOKE_SOUND = "synth.smokepuff"
        const val GHOST_ATTACK_SEQ = "seq.ghost_attack"
        const val GHOST_ATTACK_SOUND = "synth.ghost_attack"

        const val MAX_MINIONS = 3
        const val MINION_RADIUS = 4
        const val MINION_DELAY = 4
        const val TOMB_MINION_DURATION = 500

        const val GATEHOUSE_MIST_DELAY = 16
        const val MIST_HEIGHT = 124
        const val MIST_PULSES = 4
        const val MIST_INTERVAL = 4
        const val MIST_MIN = 2
        const val MIST_MAX = 3

        const val SUMMON_COOLDOWN = 200
        const val QUEEN_DURATION = 100
        const val HAUNT_STEP_TICKS = 2
        const val CHOKE_MAX = 3
        const val LAUNCH_ATTEMPTS = 20

        val MINION_CRIES =
            listOf(
                "You cannot escape me, prepare to die.",
                "Prepare to go to the other side!",
                "Soon you will be undead!",
                "Rahhhhh! OOoohhhhhh!",
            )
    }
}
