package org.rsmod.content.quest.area.lunarisle.lunardiplomacy.dream

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onPlayerSoftTimer
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_DEFEATED_SELF
import org.rsmod.content.quest.area.lunarisle.lunardiplomacy.LunarDiplomacyQuest.Companion.STAGE_FACE_SELF
import org.rsmod.content.quest.area.varrock.demonslayer.fadeFromBlack
import org.rsmod.content.quest.area.varrock.demonslayer.fadeToBlack
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.entity.util.PathingEntityCommon
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The last challenge of the Dream World: the player fights "Me", their own reflection, in the
 * arena half of their dream. Every so often the dream shifts the player to another corner of the
 * arena, and Me muses on which of them is real.
 */
@Singleton
class MeFight
@Inject
constructor(
    private val dream: DreamWorld,
    private val npcRepo: NpcRepository,
    private val clock: MapClock,
    private val aiInteractions: AiPlayerInteractions,
    private val random: GameRandom,
    private val collision: CollisionFlagMap,
) {
    private val reflections = HashMap<PlayerUid, Npc>()

    fun reflectionOf(player: Player): Npc? = reflections[player.uid]?.takeIf { it.isSlotAssigned }

    suspend fun begin(access: ProtectedAccess) {
        val player = access.player
        cleanup(player)
        access.fadeToBlack()
        access.telejump(dream.at(player, PLAYER_START), TeleportType.Exempt)
        val type = if (access.isBodyTypeB()) ME_FEMALE else ME_MALE
        val serverType = ServerCacheManager.getNpc(type.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $type")
        val me = Npc(serverType, dream.at(player, ME_START))
        npcRepo.add(me, ME_LIFETIME)
        me.respawns = false
        me.assignSpawnOwner(player, clock.cycle)
        dream.attach(player, me)
        reflections[player.uid] = me
        access.delay(1)
        access.fadeFromBlack()
        access.closeFadeOverlay()
        me.facePlayer(player)
        me.say("Are you me? Am I you?")
        me.opPlayer2(player, aiInteractions)
        access.softTimer(FIGHT_TIMER, SHIFT_TICKS)
    }

    fun tick(player: Player) {
        val me = reflectionOf(player)
        if (me == null || !dream.isDreaming(player) || !DreamWorld.inArena(dream.worldCoords(player))) {
            cleanup(player)
            return
        }
        if (random.of(0, SAY_ONE_IN - 1) == 0) {
            me.say("Are you me? Am I you?")
            player.mes("Me: Are you me? Am I you?")
            return
        }
        val slot = (player.battlePos + 1 + random.of(0, SHIFT_TILES.size - 2)) % SHIFT_TILES.size
        player.battlePos = slot
        player.anim(SHIFT_SEQ)
        player.spotanim(SHIFT_SPOT)
        val tile = SHIFT_TILES[slot]
        PathingEntityCommon.telejump(player, collision, dream.at(player, tile))
        player.mes("You've been teleported for some unknown reason...")
        me.teleport(collision, dream.at(player, tile.translateX(ME_SHIFT_OFFSET)))
        me.spotanim(SHIFT_SPOT)
        me.opPlayer2(player, aiInteractions)
    }

    /** Called from Me's death; the player's quest moves on and they are sent back to the centre. */
    fun defeated(player: Player): Boolean {
        reflections.remove(player.uid)
        player.clearSoftTimer(FIGHT_TIMER)
        return dream.isDreaming(player)
    }

    fun cleanup(player: Player) {
        player.clearSoftTimer(FIGHT_TIMER)
        val me = reflections.remove(player.uid) ?: return
        if (me.isSlotAssigned) {
            npcRepo.del(me, Int.MAX_VALUE)
        }
    }

    companion object {
        const val ME_MALE = "npc.quest_lunar_mirror_of_player"
        const val ME_FEMALE = "npc.quest_lunar_mirror_of_player_female"
        const val FIGHT_TIMER = "timer.lunar_me_fight"

        private var Player.battlePos by intVarBit("varbit.lunar_battle_pos")

        private const val ME_LIFETIME = 3000
        private const val SHIFT_TICKS = 15
        private const val SAY_ONE_IN = 3
        private const val SHIFT_SEQ = "seq.lunar_fighting_me_tele"
        private const val SHIFT_SPOT = "spotanim.lunar_fighting_me_tele_spotanim"

        val PLAYER_START = CoordGrid(1816, 5087, 2)

        /** On the outer floor: npcs walk straight at their target and cannot leave the middle. */
        val ME_START = CoordGrid(1816, 5091, 2)

        /** Every shift tile has open floor two tiles east of it, where Me follows the player. */
        private const val ME_SHIFT_OFFSET = 2
        val CENTRE_RETURN = DreamWorld.CENTRE

        val SHIFT_TILES =
            listOf(
                CoordGrid(1816, 5087, 2),
                CoordGrid(1816, 5094, 2),
                CoordGrid(1816, 5081, 2),
                CoordGrid(1824, 5095, 2),
                CoordGrid(1824, 5079, 2),
                CoordGrid(1832, 5093, 2),
                CoordGrid(1832, 5081, 2),
                CoordGrid(1830, 5087, 2),
            )
    }
}

/** Me's death, and the arena's shifting, for [MeFight]. */
class MeFightScript
@Inject
constructor(
    private val lunar: LunarDiplomacyQuest,
    private val fight: MeFight,
    private val dream: DreamWorld,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val worldQueues: WorldQueueList,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for (type in listOf(MeFight.ME_MALE, MeFight.ME_FEMALE)) {
            onNpcQueue(npcType(type), DEATH_QUEUE) { reflectionFalls() }
        }
        onPlayerSoftTimer(MeFight.FIGHT_TIMER) { fight.tick(player) }
    }

    private suspend fun StandardNpcAccess.reflectionFalls() {
        val owner = npc.spawnOwner.resolve(playerList)
        death.deathNoDrops(this)
        owner ?: return
        if (!fight.defeated(owner)) {
            return
        }
        launchWhenFree(owner.uid, LAUNCH_ATTEMPTS) {
            if (lunar.stage(player) == STAGE_FACE_SELF) {
                lunar.advanceTo(this, STAGE_DEFEATED_SELF)
            }
            say("Leave me alone... Me...")
            delay(2)
            fadeToBlack()
            telejump(dream.at(player, MeFight.CENTRE_RETURN), TeleportType.Exempt)
            delay(1)
            fadeFromBlack()
            closeFadeOverlay()
            mes("You have defeated yourself. Speak to the Ethereal Being at the centre of the dream.")
        }
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

    private companion object {
        const val DEATH_QUEUE = "queue.death"
        const val LAUNCH_ATTEMPTS = 20
    }
}
