package org.rsmod.content.quest.area.lunarisle.dreammentor

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Provider
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.npc.owner.isSpawnOwnedByOther
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.content.quest.area.lunarisle.dreammentor.DreamMentorQuest.Companion.SEAL_OF_PASSAGE
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player

/**
 * The rules of Cyrisus' dream. Prayer doesn't work there and no teleport can wake the player,
 * leaving the lectern as the only way out. Each player's fears are theirs alone. Dying there is a
 * real death, handled by the normal death rules, except that the Seal of Passage is always lost.
 */
class DreamMentorHooks
@Inject
constructor(
    private val arena: Provider<DreamArena>,
    private val fight: Provider<DreamFight>,
) : PlayerRestrictionHook, PlayerTeleportValidateHook, NpcAttackValidateHook, PlayerDeathCleanupHook, PlayerDeathItemHook {
    private val dreamNpcs by lazy {
        (Fear.TYPES + DreamFight.DOUBT + CombatStyle.entries.map { it.battleType }).map { it.asRSCM(RSCMType.NPC) }.toSet()
    }
    private val seal by lazy { SEAL_OF_PASSAGE.asRSCM(RSCMType.OBJ) }

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (action != RestrictedAction.Prayer || !arena.get().isDreaming(player)) {
            return null
        }
        return "Your prayers can't reach you in Cyrisus' dream."
    }

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (type == TeleportType.Exempt || !arena.get().isDreaming(player)) {
            return null
        }
        return "You can't teleport out of a dream. Use the lectern to wake up."
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id !in dreamNpcs || !npc.isSpawnOwnedByOther(player)) {
            return NpcAttackValidateResult.Pass
        }
        return NpcAttackValidateResult.Deny("That isn't part of your dream.")
    }

    override fun beforeDrops(context: PlayerDeathContext, handling: PlayerDeathHandling) {
        val player = context.player
        if (handling.keepsEverything || !arena.get().isDreaming(player)) {
            return
        }
        for (inv in listOf(player.inv, player.worn)) {
            for (slot in inv.objs.indices) {
                if (inv[slot]?.id == seal) {
                    inv[slot] = null
                }
            }
        }
    }

    override fun cleanup(player: Player) {
        fight.get().cleanup(player)
        arena.get().forget(player)
    }
}
