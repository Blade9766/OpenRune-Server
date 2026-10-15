package org.rsmod.content.quest.area.zanaris.fairytale1

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.GATEKEEPER
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.QUEENS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.STAGE_HAS_SECATEURS
import org.rsmod.content.quest.area.zanaris.fairytale1.Fairytale1Quest.Companion.TANGLEFOOT
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The tunnel behind the wall south-west of the Zanaris wheat field. The wall is a three-tile-thick
 * band of map-blocked ground with a gap loc on each face, so squeezing through either of them has
 * to step the player straight across to the other side.
 *
 * The Tanglefoot itself has no map spawn: it is raised at the far end of the tunnel for whoever
 * walks in with a pair of magic secateurs, and only that player can fight it.
 */
@Singleton
class TanglefootLair
@Inject
constructor(
    private val fairytale: Fairytale1Quest,
    private val npcRepo: NpcRepository,
) : PluginScript() {

    private val owners = HashMap<Npc, PlayerUid>()

    override fun ScriptContext.startup() {
        for (gap in GAPS) {
            onOpLoc1(gap) { squeezeThrough() }
        }
        onOpNpc1(GATEKEEPER) { startDialogue(it.npc) { gatekeeper() } }
        onEvent<NpcStateEvents.Delete> { owners.remove(npc) }
    }

    fun isTanglefoot(npc: Npc): Boolean = npc.id == tanglefootId

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc]

    private suspend fun ProtectedAccess.squeezeThrough() {
        arriveDelay()
        val inside = Fairytale1Coords.inTanglefootTunnel(player.coords)
        val destination =
            if (inside) Fairytale1Coords.OUTSIDE_THE_WALL else Fairytale1Coords.INSIDE_THE_WALL
        faceSquare(destination)
        anim(SQUEEZE_SEQ)
        soundSynth(SQUEEZE_SOUND)
        delay(SQUEEZE_TICKS)
        telejump(destination, TeleportType.Exempt)
        if (inside) {
            return
        }
        mes("You squeeze through the gap in the wall.")
        raiseTanglefoot()
    }

    /** Raises the Tanglefoot at the end of the tunnel for a player who is ready to fight it. */
    private fun ProtectedAccess.raiseTanglefoot() {
        if (fairytale.stage(player) != STAGE_HAS_SECATEURS ||
            !carries(Fairytale1Quest.MAGIC_SECATEURS) ||
            carries(QUEENS_SECATEURS)
        ) {
            return
        }
        val existing = owners.entries.firstOrNull { it.value == player.uid && it.key.isSlotAssigned }
        if (existing != null) {
            return
        }
        val type =
            ServerCacheManager.getNpc(tanglefootId) ?: error("Missing npc type: $TANGLEFOOT")
        val tanglefoot = Npc(type, Fairytale1Coords.TANGLEFOOT_LAIR)
        npcRepo.add(tanglefoot, LIFETIME)
        owners[tanglefoot] = player.uid
        mes("Something heavy shifts in the dark at the end of the tunnel.")
    }

    private suspend fun Dialogue.gatekeeper() {
        val stage = fairytale.stage(player)
        when {
            fairytale.isComplete(player) -> {
                chatNpc(happy, "The big one's gone, and good riddance. Mind the little ones; they still bite.")
            }
            stage == STAGE_HAS_SECATEURS && access.carries(Fairytale1Quest.MAGIC_SECATEURS) -> {
                chatNpc(quiz, "You're going in there, aren't you.")
                chatPlayer(neutral, "I am.")
                chatNpc(
                    neutral,
                    "Then I won't stop you. Those shears of yours have the right sort of magic " +
                        "in them - which is more than the last four had.",
                )
                chatNpc(
                    worried,
                    "Keep to the tunnel. The little ones are a nuisance; the big one at the end " +
                        "is not.",
                )
            }
            stage >= STAGE_HAS_SECATEURS -> {
                chatNpc(angry, "Where are your secateurs? Don't go in there without them.")
            }
            else -> {
                chatNpc(
                    neutral,
                    "I wouldn't. There's a gap in that wall and there's a reason nobody's mended " +
                        "it: whatever's on the other side would only make another one.",
                )
                chatPlayer(quiz, "What's on the other side?")
                chatNpc(
                    worried,
                    "Tanglefeet. Walking thorn bushes, and the one at the end of the tunnel is " +
                        "the size of a cart. Ordinary weapons don't touch them.",
                )
            }
        }
    }

    private val tanglefootId: Int by lazy { TANGLEFOOT.asRSCM(RSCMType.NPC) }

    private companion object {
        /** Both faces of the wall: the quest multiloc's gap, and the permanent one behind it. */
        val GAPS = listOf("loc.fairy_wall_tangle_door_open", "loc.fairy_wall_tangle_door_open1")

        const val SQUEEZE_SEQ = "seq.human_squeeze"
        const val SQUEEZE_SOUND = "synth.squeeze_thru_crack"
        const val SQUEEZE_TICKS = 2

        /** Long enough to walk the whole tunnel, die, and come back for it. */
        const val LIFETIME = 3000
    }
}

/** Nothing but a pair of magic secateurs will mark a Tanglefoot, and only its own hunter may try. */
class TanglefootAttackHook @Inject constructor(private val lair: TanglefootLair) :
    NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!lair.isTanglefoot(npc)) {
            return NpcAttackValidateResult.Pass
        }
        val owner = lair.ownerOf(npc)
        if (owner != null && owner != player.uid) {
            return NpcAttackValidateResult.Deny("The Tanglefoot pays you no attention.")
        }
        if (!player.wieldsMagicSecateurs()) {
            return NpcAttackValidateResult.Deny(
                "Your weapon just glances off the Tanglefoot. You will need the magic secateurs."
            )
        }
        return NpcAttackValidateResult.Pass
    }
}

/**
 * Marks the Tanglefoot dead so the journal moves on. The Queen's secateurs themselves come from
 * the npc's own guaranteed drop table, not from here.
 */
class TanglefootKillHook
@Inject
constructor(private val fairytale: Fairytale1Quest, private val lair: TanglefootLair) :
    NpcDeathKillHook {
    override fun onKill(context: NpcDeathKillContext) {
        if (!lair.isTanglefoot(context.npc)) {
            return
        }
        val hero = context.hero
        if (fairytale.stage(hero) != STAGE_HAS_SECATEURS) {
            return
        }
        fairytale.tanglefootSlain.set(hero, true)
        hero.mes("The Tanglefoot falls apart, and a pair of secateurs drops out of the wreckage.")
    }
}
