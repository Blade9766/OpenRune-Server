package org.rsmod.content.quest.area.falador.recruitmentdrive.rooms

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeath
import org.rsmod.api.npc.access.StandardNpcAccess
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.script.onNpcHit
import org.rsmod.api.script.onNpcQueue
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.falador.recruitmentdrive.RecruitmentTesting
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoom
import org.rsmod.content.quest.area.falador.recruitmentdrive.TestRoomScript
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcUid
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Sir Kuam Ferentse's test: defeat Sir Leye (level 20), who no blade may defeat. The room's four
 * steel weapons all hurt him, but he only falls to a player holding the steel warhammer or nothing
 * at all: the blow that kills him and the weapon held when he dies are both checked, and a blade in
 * either fails the test. Prayer is allowed, and a death here is a real death (Hardcore Ironmen lose
 * their status; the passed rooms are kept for the next visit).
 *
 * Sir Leye belongs to the player he was spawned for: only they may attack him, only their kill
 * counts, and he is removed whenever they leave the room, die or log out.
 */
@Singleton
class CombatRoom
@Inject
constructor(
    private val testing: RecruitmentTesting,
    private val death: NpcDeath,
    private val playerList: PlayerList,
    private val aiInteractions: AiPlayerInteractions,
    private val clock: MapClock,
) : PluginScript(), TestRoomScript {
    override val room = TestRoom.COMBAT

    private val leyeType by lazy { checkNotNull(ServerCacheManager.getNpc(SIR_LEYE.asRSCM(RSCMType.NPC))) }
    private val warhammer by lazy { WARHAMMER.asRSCM(RSCMType.OBJ) }

    /** Whether the blow that brought each Sir Leye to 0 hitpoints was struck with a permitted weapon. */
    private val finishingBlows = HashMap<NpcUid, Boolean>()

    override fun ScriptContext.startup() {
        testing.register(this@CombatRoom)
        onOpNpc1(KUAM) { startDialogue(it.npc) { kuamHint() } }
        onOpNpc1(SIR_LEYE) { startDialogue(it.npc) { chatNpc(angry, LEYE_TAUNT) } }
        onNpcHit(leyeType) { if (npc.hitpoints <= 0 && hit.isFromPlayer) recordFinishingBlow(npc, hit.righthandType()?.id) }
        onNpcQueue(leyeType, "queue.death") { leyeFalls() }
    }

    override suspend fun ProtectedAccess.arrive(attempt: Int) {
        for ((weapon, tile) in WEAPONS) {
            testing.grounds.spawnObj(player, weapon, tile)
        }
        // Sir Leye comes before the greeting: a player who walks off mid-greeting ends it early.
        if (testing.testing(player, room, attempt)) {
            summonLeye(player)
        }
        val kuam = testing.grounds.observer(player, room) ?: return
        val name = player.displayName
        startDialogue(kuam) {
            chatNpc(neutral, "Ah, $name, you're finally here. Your task for this room is to defeat Sir Leye. He has been blessed by Saradomin to be undefeatable by any blade, so it should be quite a challenge for you.")
            kuamHint()
        }
    }

    private suspend fun Dialogue.kuamHint() {
        chatNpc(neutral, "If you are having problems, remember; A true warrior uses his wits as much as his brawn. Fight smarter, not harder.")
    }

    fun summonLeye(player: Player): Npc? {
        val space = testing.grounds.space(player) ?: return null
        space.leye?.let(testing.grounds::removeNpc)
        val leye = testing.grounds.spawnNpc(player, SIR_LEYE, LEYE_TILE) ?: return null
        leye.respawns = false
        leye.assignSpawnOwner(player, clock.cycle)
        space.leye = leye
        leye.say(LEYE_SHOUT)
        leye.opPlayer2(player, aiInteractions)
        return leye
    }

    override fun leave(player: Player) {
        val space = testing.grounds.space(player) ?: return
        val leye = space.leye ?: return
        space.leye = null
        finishingBlows.remove(leye.uid)
        testing.grounds.removeNpc(leye)
    }

    fun permitted(weapon: Int?): Boolean = weapon == null || weapon == warhammer

    private suspend fun StandardNpcAccess.leyeFalls() {
        val owner = npc.spawnOwner.resolve(playerList)
        val blow = finishingBlows.remove(npc.uid)
        owner?.let(testing.grounds::space)?.let { if (it.leye === npc) it.leye = null }
        death.deathNoDrops(this)
        if (owner != null) {
            judge(owner, blow)
        }
    }

    internal fun recordFinishingBlow(npc: Npc, weapon: Int?) {
        finishingBlows[npc.uid] = permitted(weapon)
    }

    /**
     * Sir Leye has fallen to [owner]: the test is passed when neither the finishing blow ([blow], null
     * when unknown) nor the weapon held now is a blade.
     */
    internal fun judge(owner: Player, blow: Boolean?) {
        if (!testing.grounds.inside(owner)) {
            return
        }
        val attempt = testing.attempt(owner)
        val passed = blow != false && permitted(owner.righthand?.id)
        testing.launchWhenFree(owner, attempt) {
            with(testing) {
                if (passed) passRoom(room, attempt) else failRoom(room, attempt)
            }
        }
    }

    companion object {
        const val KUAM = "npc.rd_observer_room_3"
        const val SIR_LEYE = "npc.rd_combat_npc_room_3"
        const val WARHAMMER = "obj.steel_warhammer"

        const val LEYE_SHOUT = "No blade may defeat me!"
        const val LEYE_TAUNT =
            "AH-HA HA HA! You can NEVER defeat ME! I am blessed by Saradomin himself! NO BLADE MAY DEFEAT ME!"

        /** An open tile in the middle of the room, clear of Sir Kuam and the weapon tables. */
        val LEYE_TILE = CoordGrid(2460, 4962, 0)

        /** The four weapons on the room's two tables (`loc.rd_wooden_table`, two tiles each). */
        val WEAPONS =
            listOf(
                "obj.steel_sword" to CoordGrid(2458, 4967, 0),
                "obj.steel_claws" to CoordGrid(2459, 4967, 0),
                "obj.steel_battleaxe" to CoordGrid(2460, 4967, 0),
                WARHAMMER to CoordGrid(2461, 4967, 0),
            )
    }
}

/** Only the player Sir Leye was spawned for may fight him. */
class SirLeyeAttackHook @Inject constructor() : NpcAttackValidateHook {
    private val leye by lazy { CombatRoom.SIR_LEYE.asRSCM(RSCMType.NPC) }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != leye || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("He isn't here to test you.")
    }
}
