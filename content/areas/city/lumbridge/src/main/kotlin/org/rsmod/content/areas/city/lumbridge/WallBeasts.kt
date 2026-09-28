package org.rsmod.content.areas.city.lumbridge

import dev.openrune.ParamMap
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MoveRestrict
import dev.openrune.types.NpcMode
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.npc.queueCombatRetaliate
import org.rsmod.api.config.refs.params
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isAliveInWorld
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.vars.intVarn
import org.rsmod.api.player.hat
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onNpcTimer
import org.rsmod.api.script.onPlayerCoordsChanged
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.hit.HitType
import org.rsmod.game.inv.isType
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap
import org.rsmod.routefinder.flag.CollisionFlag

private var Npc.lastAttack: Int by intVarn("varn.lastattack")
private var Npc.lastCombat: Int by intVarn("varn.lastcombat")

/**
 * The wall beasts of the Lumbridge Swamp Caves: a hand that waits in a hole in the cave wall and
 * grabs whoever stops on the tile in front of it. Without a spiny helmet or slayer helm the player
 * is held and hurt with no chance to fight back; a helmet repels the grab and the hand stays out
 * as an attackable wall beast until it dies or is left alone, then withdraws into the hole.
 */
class WallBeasts
@Inject
constructor(
    private val launcher: ProtectedAccessLauncher,
    private val aiInteractions: AiPlayerInteractions,
    private val collision: CollisionFlagMap,
) : PluginScript() {
    private val holesByTrigger = HashMap<CoordGrid, Npc>()
    private val grabbing = HashSet<Npc>()

    private val holeType by lazy { npcType(HOLE) }
    private val beastType by lazy { npcType(BEAST) }

    override fun ScriptContext.startup() {
        rootInPlace(holeType)
        rootInPlace(beastType)
        applyCombatParams(beastType)

        onEvent<NpcStateEvents.Create> {
            if (npc.type.id == holeType.id) {
                npc.moveRestrict = MoveRestrict.NoMove
                npc.mode = NpcMode.None
                holesByTrigger[frontTile(npc.spawnCoords)] = npc
            }
        }
        onPlayerCoordsChanged {
            if (lastKnownCoords != player.coords) {
                holesByTrigger[player.coords]?.let { ambush(player, it) }
            }
        }
        onNpcTimer(beastType, RETREAT_TIMER) { checkRetreat(npc) }
        onNpcTimer(holeType, RETREAT_TIMER) { npc.clearTimer(RETREAT_TIMER) }
    }

    private fun rootInPlace(type: NpcServerType) {
        type.moveRestrict = MoveRestrict.NoMove
        type.defaultMode = NpcMode.None
        type.wanderRange = 0
        type.giveChase = false
    }

    private fun applyCombatParams(type: NpcServerType) {
        val values =
            mapOf(
                params.attack_anim.id to BEAST_ATTACK.asRSCM(RSCMType.SEQ),
                params.defend_anim.id to BEAST_DEFEND.asRSCM(RSCMType.SEQ),
                params.death_anim.id to BEAST_DEATH.asRSCM(RSCMType.SEQ),
                params.npc_attack_type.id to "category.attacktype_crush".asRSCM(RSCMType.CATEGORY),
                params.attack_sound.id to SOUND_SWIPE.asRSCM(RSCMType.SYNTH),
                params.defend_sound.id to SOUND_HIT.asRSCM(RSCMType.SYNTH),
                params.death_sound.id to SOUND_DEATH.asRSCM(RSCMType.SYNTH),
            )
        type.paramMap = ParamMap(primitiveMap = type.paramMap?.primitiveMap.orEmpty() + values)
    }

    /** The one open tile beside a hole; every hole sits in a wall alcove open to one side. */
    private fun frontTile(hole: CoordGrid): CoordGrid =
        SIDES.map { (dx, dz) -> hole.translate(dx, dz) }
            .firstOrNull { collision[it.x, it.z, it.level] and BLOCKED == 0 }
            ?: hole.translate(0, -1)

    private fun ambush(player: Player, hole: Npc) {
        if (hole.transmog != null || hole in grabbing || !hole.isAliveInWorld()) {
            return
        }
        if (player.wearsSpikedHelmet()) {
            repel(player, hole)
            return
        }
        launcher.launch(player) { grab(hole) }
    }

    private fun repel(player: Player, hole: Npc) {
        player.mes("Your helmet repels the wall beast!")
        player.soundSynth(SOUND_FOILED)
        hole.transmog(beastType, Int.MAX_VALUE)
        hole.anim(BEAST_FAIL)
        hole.queueCombatRetaliate(player)
        hole.timer(RETREAT_TIMER, RETREAT_CHECK_INTERVAL)
        hole.opPlayer2(player, aiInteractions)
    }

    private suspend fun ProtectedAccess.grab(hole: Npc) {
        grabbing += hole
        try {
            stopAction()
            faceSquare(hole.coords)
            hole.facePlayer(player)
            hole.anim(GRAB_START)
            anim(PLAYER_GRAB_START)
            soundSynth(SOUND_ATTACK)
            delay(1)
            // The hold pose is a short loop that the client drops while the start still plays, so
            // it is kept up as the idle pose for the whole hold.
            hole.setIdleAnim(GRAB_HOLD)
            anim(PLAYER_GRAB_HOLD)
            queueHit(hole, delay = 1, type = HitType.Typeless, damage = random.of(0..GRAB_MAX_HIT))
            delay(GRAB_HOLD_TICKS)
            hole.clearIdleAnim()
            hole.anim(GRAB_END)
            anim(PLAYER_GRAB_END)
            delay(1)
        } finally {
            grabbing -= hole
            hole.clearIdleAnim()
            hole.resetFaceEntity()
        }
    }

    private fun checkRetreat(beast: Npc) {
        if (!beast.isAliveInWorld()) {
            return
        }
        val lastFight = maxOf(beast.lastAttack, beast.lastCombat)
        if (beast.currentMapClock - lastFight < RETREAT_IDLE_TICKS) {
            return
        }
        beast.clearTimer(RETREAT_TIMER)
        beast.noneMode()
        beast.anim(BEAST_DISAPPEAR)
        beast.resetTransmog()
        beast.hitpoints = beast.baseHitpointsLvl
    }

    private fun Player.wearsSpikedHelmet(): Boolean {
        val helm = hat ?: return false
        return helm.isType(SPINY_HELMET) || SLAYER_HELMS.any { helm.isType(it) }
    }

    private fun npcType(name: String): NpcServerType =
        requireNotNull(ServerCacheManager.getNpc(name.asRSCM(RSCMType.NPC))) { "Missing $name" }

    private companion object {
        const val HOLE = "npc.swamp_wallbeast"
        const val BEAST = "npc.swamp_wallbeast_combat"
        const val SPINY_HELMET = "obj.wallbeast_spike_helmet"
        const val RETREAT_TIMER = "timer.wall_beast_retreat"

        const val GRAB_START = "seq.wall_beast_specialattack_start"
        const val GRAB_HOLD = "seq.wall_beast_specialattack_ready"
        const val GRAB_END = "seq.wall_beast_specialattack_end"
        const val PLAYER_GRAB_START = "seq.human_wall_beast_specialattack_start"
        const val PLAYER_GRAB_HOLD = "seq.human_wall_beast_specialattack_ready"
        const val PLAYER_GRAB_END = "seq.human_wall_beast_specialattack_end"
        const val BEAST_FAIL = "seq.wall_beast_specialattack_fail"
        const val BEAST_DISAPPEAR = "seq.wall_beast_specialattack_disappear"
        const val BEAST_ATTACK = "seq.wall_beast_specialattack_attack"
        const val BEAST_DEFEND = "seq.wall_beast_specialattack_defend"
        const val BEAST_DEATH = "seq.wall_beast_specialattack_death"

        const val SOUND_ATTACK = "synth.wall_beast_attack"
        const val SOUND_DEATH = "synth.wall_beast_death"
        const val SOUND_FOILED = "synth.wall_beast_foiled"
        const val SOUND_HIT = "synth.wall_beast_hit"
        const val SOUND_SWIPE = "synth.wall_beast_swipe"

        const val GRAB_MAX_HIT = 18
        const val GRAB_HOLD_TICKS = 3
        const val RETREAT_CHECK_INTERVAL = 5
        const val RETREAT_IDLE_TICKS = 16

        const val BLOCKED = CollisionFlag.BLOCK_WALK or CollisionFlag.LOC

        val SIDES = listOf(0 to -1, 0 to 1, -1 to 0, 1 to 0)

        val SLAYER_HELMS =
            listOf(
                    "",
                    "_black",
                    "_green",
                    "_red",
                    "_purple",
                    "_turquoise",
                    "_hydra",
                    "_twisted",
                    "_jad",
                    "_verzik",
                    "_zuk",
                    "_araxyte",
                    "_hooded",
                )
                .flatMap { listOf("obj.slayer_helm$it", "obj.slayer_helm_i$it") }
    }
}
