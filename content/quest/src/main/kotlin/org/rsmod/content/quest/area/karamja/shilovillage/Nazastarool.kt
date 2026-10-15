package org.rsmod.content.quest.area.karamja.shilovillage

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.script.onEvent
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.NAZASTAROOL_FORMS
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.NAZASTAROOL_FORM_COUNT
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.RASHILIYIA_CORPSE
import org.rsmod.content.quest.area.karamja.shilovillage.ShiloVillageQuest.Companion.ZOMBIE_UNDEAD_ONES
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.entity.npc.NpcStateEvents
import org.rsmod.game.entity.player.PlayerUid
import org.rsmod.game.queue.WorldQueueList
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Nazastarool, the guardian of Rashiliyia's remains, who rises from her dolmen as a giant zombie,
 * then a giant skeleton, then a ghost. Each form is bound to the player who disturbed the dolmen
 * and lingers for five minutes; the forms a player has already destroyed stay destroyed until
 * they take the remains.
 */
@Singleton
class Nazastarool
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val npcRepo: NpcRepository,
    private val playerList: PlayerList,
    private val worldQueues: WorldQueueList,
    private val aiInteractions: AiPlayerInteractions,
) : PluginScript() {

    private val owners = HashMap<Npc, PlayerUid>()
    private val formIds = NAZASTAROOL_FORMS.map { it.asRSCM(RSCMType.NPC) }

    override fun ScriptContext.startup() {
        onEvent<NpcStateEvents.Delete> { owners.remove(npc) }
    }

    fun isForm(npc: Npc): Boolean = npc.id in formIds

    fun ownerOf(npc: Npc): PlayerUid? = owners[npc]

    fun hasLivingForm(player: Player): Boolean = owners.any { (npc, uid) -> uid == player.uid && npc.isSlotAssigned }

    /** Raises the next form [player] has yet to destroy beside them. */
    fun ProtectedAccess.rise() {
        if (owns(RASHILIYIA_CORPSE) || !ShiloCoords.inRashiliyiaTomb(player.coords)) {
            return
        }
        val form = shilo.nazastaroolForms.get(player)
        if (form >= NAZASTAROOL_FORM_COUNT) {
            return
        }
        val type = ServerCacheManager.getNpc(NAZASTAROOL_FORMS[form].asRSCM(RSCMType.NPC)) ?: return
        val spot = mapFindSquareLineOfWalk(player.coords, 1, SPAWN_RADIUS) ?: return
        val guardian = Npc(type, spot)
        npcRepo.add(guardian, FORM_DURATION)
        owners[guardian] = player.uid
        guardian.facePlayer(player)
        val (first, second) = CRIES[form]
        guardian.say(first)
        val uid = player.uid
        worldQueues.add(CRY_DELAY) {
            if (!guardian.isSlotAssigned) {
                return@add
            }
            guardian.say(second)
            val target = uid.resolve(playerList) ?: return@add
            guardian.opPlayer2(target, aiInteractions)
        }
    }

    private companion object {
        const val SPAWN_RADIUS = 2
        const val FORM_DURATION = 500
        const val CRY_DELAY = 2

        val CRIES =
            listOf(
                "Who dares disturb Rashiliyia's rest?" to "I am Nazastarool! Prepare to die!",
                "Quake in fear, for I am reborn!" to "Your death will be swift.",
                "Nazastarool returns with vengeance!" to "Soon you will serve Rashiliyia.",
            )
    }
}

/** Only the player who disturbed the dolmen may fight the Nazastarool that rose for them. */
class NazastaroolAttackHook @Inject constructor(private val nazastarool: Nazastarool) : NpcAttackValidateHook {
    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (!nazastarool.isForm(npc)) {
            return NpcAttackValidateResult.Pass
        }
        val owner = nazastarool.ownerOf(npc) ?: return NpcAttackValidateResult.Pass
        if (owner != player.uid) {
            return NpcAttackValidateResult.Deny("Nazastarool is not interested in you.")
        }
        return NpcAttackValidateResult.Pass
    }
}

/**
 * Destroying a form of Nazastarool raises the next; the third leaves Rashiliyia's remains on her
 * dolmen. A slain zombie undead one sometimes leaves a cloud of choking mist behind it.
 */
class ShiloVillageKillHook
@Inject
constructor(
    private val shilo: ShiloVillageQuest,
    private val nazastarool: Nazastarool,
    private val undead: ShiloUndead,
    private val random: GameRandom,
    private val worldQueues: WorldQueueList,
) : NpcDeathKillHook {
    private val zombieIds = ZOMBIE_UNDEAD_ONES.map { it.asRSCM(RSCMType.NPC) }.toSet()

    override fun onKill(context: NpcDeathKillContext) {
        val npc = context.npc
        when {
            npc.id in zombieIds -> {
                if (random.randomBoolean()) {
                    val coords = npc.coords
                    worldQueues.add(MIST_DELAY) { undead.greenMist(coords) }
                }
            }
            nazastarool.isForm(npc) -> formDestroyed(context.hero, npc)
        }
    }

    private fun formDestroyed(hero: Player, npc: Npc) {
        val owner = nazastarool.ownerOf(npc)
        if (owner != null && owner != hero.uid) {
            return
        }
        if (shilo.isComplete(hero)) {
            return
        }
        val form = NAZASTAROOL_FORMS.indexOfFirst { it.asRSCM(RSCMType.NPC) == npc.id }
        if (form != shilo.nazastaroolForms.get(hero)) {
            return
        }
        shilo.nazastaroolForms.set(hero, form + 1)
        undead.launchWhenFree(hero.uid) {
            when (form) {
                0 -> {
                    mesbox("You defeat Nazastarool and the corpse falls to the ground. The bones start to move again and soon they reform into a grisly giant skeleton.")
                    with(nazastarool) { rise() }
                }
                1 -> {
                    mesbox("You defeat Nazastarool and the skeleton falls to the ground. An ethereal form starts taking shape above the bones and you soon face the vengeful ghost of Nazastarool.")
                    with(nazastarool) { rise() }
                }
                else -> {
                    mes("Nazastarool: May you perish in the fires of Zamorak's furnace! Rashiliyia's")
                    mes("Nazastarool: curse is upon you!")
                    mesbox("You hear a disembodied voice fading away into the distance, 'May you perish in the fires of Zamorak's furnace! Rashiliyia's curse is upon you!'")
                    mesbox("You see something appear on the dolmen.")
                }
            }
        }
    }

    private companion object {
        const val MIST_DELAY = 4
    }
}
