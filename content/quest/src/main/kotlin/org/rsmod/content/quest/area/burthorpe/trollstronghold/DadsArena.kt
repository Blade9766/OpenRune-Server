package org.rsmod.content.quest.area.burthorpe.trollstronghold

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import dev.openrune.types.hunt.HuntVis
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.config.constants
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.hunt.NpcSearch
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.heal
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.queueDeath
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.StairNavigator
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.DAD
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_DAD_BEATEN
import org.rsmod.content.quest.area.burthorpe.trollstronghold.TrollStrongholdQuest.Companion.STAGE_STARTED
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Dad, the troll champion, who holds his arena against anyone heading for the stronghold.
 *
 * He only fights a player who is on the quest and has taken up his challenge; anyone else is
 * turned away by [DadAttackHook]. When a challenger brings him down he does not die but yields
 * at full health: the player may leave him be, or finish him off for good, which is the only
 * way he actually dies.
 */
@Singleton
class DadsArena
@Inject
constructor(
    private val quest: TrollStrongholdQuest,
    private val death: NpcDeath,
    private val search: NpcSearch,
    private val stairs: StairNavigator,
    private val playerList: PlayerList,
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val dadType: NpcServerType =
        ServerCacheManager.getNpc(DAD.asRSCM(RSCMType.NPC)) ?: error("Missing npc: $DAD")

    /** Dads a player chose to finish off; their next death is a real one. */
    private val condemned = HashSet<Npc>()

    override fun ScriptContext.startup() {
        onOpNpc1(DAD) { startDialogue(it.npc) { talkToDad() } }
        onNpcQueue(dadType, "queue.death") { dadDowned() }
    }

    private suspend fun Dialogue.talkToDad() {
        when {
            quest.isComplete(player) -> {
                chatPlayer(quiz, "Why are you called Dad?")
                chatNpc(neutral, "Troll named after first thing try to eat!")
            }
            quest.hasBeatenDad(player) -> access.mes("He doesn't seem interested in talking right now.")
            quest.stage(player) == STAGE_STARTED -> challenge()
            else -> chatNpc(angry, "No human pass through arena without defeating Dad!")
        }
    }

    suspend fun Dialogue.challenge() {
        chatNpcSpecific("Dad", DAD, angry, "No human pass through arena without defeating Dad!")
        chatNpcSpecific(
            "Dad",
            DAD,
            angry,
            "What tiny human do in troll arena? Dad challenge human to fight!",
        )
        when (
            choice3(
                "Why are you called Dad?",
                1,
                "I accept your challenge!",
                2,
                "Eek! No thanks.",
                3,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Why are you called Dad?")
                chatNpcSpecific("Dad", DAD, neutral, "Troll named after first thing try to eat!")
            }
            2 -> {
                chatPlayer(angry, "I accept your challenge!")
                chatNpcSpecific("Dad", DAD, laugh, "Tiny human brave. Dad squish!")
                player.tsAcceptedChallenge = true
                access.fightDad()
            }
            else -> {
                chatPlayer(worried, "Eek! No thanks.")
                chatNpcSpecific("Dad", DAD, bored, "Coward. Dad wait for braver fighter.")
            }
        }
    }

    /** Dad comes for the player, swinging his log to send them flying if they stand close. */
    private suspend fun ProtectedAccess.fightDad() {
        val dad = findDad() ?: return
        dad.facePlayer(player)
        if (coords.chebyshevDistance(dad.coords) <= KNOCKBACK_REACH) {
            dad.anim(SWING_SEQ)
            soundSynth(SWING_SOUND)
        }
        val landing = knockbackTile(dad)
        if (landing != null) {
            anim(FLYBACK_SEQ)
            exactMove(
                start = coords,
                end = landing,
                delay1 = 0,
                delay2 = KNOCKBACK_TICKS * CLIENT_CYCLES_PER_TICK,
                dir = constants.em_face_south,
                teleportType = TeleportType.Exempt,
            )
            delay(KNOCKBACK_TICKS)
        }
        // Dad steps straight at his target and snags on the arena walls, so the player closes in.
        dad.opPlayer2(player, aiInteractions)
        opNpc2(dad)
    }

    private fun ProtectedAccess.findDad(): Npc? =
        npcFindAll(coords, DAD, ARENA_RADIUS, HuntVis.Off, search).firstOrNull()

    /** Two tiles straight back from Dad, if the player is close enough to be hit and it is clear. */
    private fun ProtectedAccess.knockbackTile(dad: Npc): CoordGrid? {
        if (coords.chebyshevDistance(dad.coords) > KNOCKBACK_REACH) {
            return null
        }
        val dx = Integer.signum(coords.x - dad.coords.x)
        val dz = Integer.signum(coords.z - dad.coords.z)
        val landing = coords.translate(dx * KNOCKBACK_DISTANCE, dz * KNOCKBACK_DISTANCE)
        return landing.takeIf { stairs.walkable(it) }
    }

    private suspend fun StandardNpcAccess.dadDowned() {
        if (condemned.remove(npc)) {
            death.deathWithDrops(this)
            return
        }
        val hero = findHero(playerList)
        if (hero == null || !hero.isChallenging()) {
            npc.heal(npc.baseHitpointsLvl)
            resetMode()
            return
        }
        npc.heal(npc.baseHitpointsLvl)
        resetMode()
        say(YIELD)
        val dad = npc
        launcher.launch(hero) { yielded(dad) }
    }

    private fun Player.isChallenging(): Boolean =
        tsAcceptedChallenge && quest.stage(this) == STAGE_STARTED

    private suspend fun ProtectedAccess.yielded(dad: Npc) {
        stopAction()
        player.tsAcceptedChallenge = false
        quest.advanceTo(this, STAGE_DAD_BEATEN)
        startDialogue {
            chatNpcSpecific("Dad", DAD, sad, YIELD)
            val finish =
                choice2("I'll be going now.", false, "I'm not done yet! Prepare to die!", true)
            if (!finish) {
                chatPlayer(neutral, "I'll be going now.")
                return@startDialogue
            }
            chatPlayer(angry, "I'm not done yet! Prepare to die!")
            player.tsKilledDad = true
            if (dad.isSlotAssigned) {
                condemned += dad
                dad.hitpoints = 0
                dad.queueDeath()
            }
        }
    }

    private companion object {
        const val YIELD = "Stop! You win. Not hurt Dad."
        const val SWING_SEQ = "seq.troll_treetrunk_attack"
        const val FLYBACK_SEQ = "seq.human_troll_flyback"
        const val SWING_SOUND = "synth.troll_champion_swing"
        const val ARENA_RADIUS = 30
        const val KNOCKBACK_REACH = 4
        const val KNOCKBACK_DISTANCE = 2
        const val KNOCKBACK_TICKS = 2
        const val CLIENT_CYCLES_PER_TICK = 30
    }
}

/** Dad fights only a challenger on the quest; once beaten, he is left alone. */
class DadAttackHook @Inject constructor(private val quest: TrollStrongholdQuest) :
    NpcAttackValidateHook {
    private val dadId by lazy { DAD.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != dadId) {
            return NpcAttackValidateResult.Pass
        }
        if (quest.hasBeatenDad(player)) {
            return NpcAttackValidateResult.Deny("You don't need to fight him again.")
        }
        if (quest.stage(player) == STAGE_STARTED && player.tsAcceptedChallenge) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("Dad isn't interested in fighting you.")
    }
}
