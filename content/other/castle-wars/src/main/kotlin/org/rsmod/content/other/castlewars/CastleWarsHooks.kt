package org.rsmod.content.other.castlewars

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.area.checker.AreaChecker
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.PlayerDeathCleanupHook
import org.rsmod.api.death.PlayerDeathContext
import org.rsmod.api.death.PlayerDeathHandling
import org.rsmod.api.death.PlayerDeathHook
import org.rsmod.api.death.PlayerDeathItemHook
import org.rsmod.api.death.PlayerRespawnHook
import org.rsmod.api.death.PvPAttackValidateHook
import org.rsmod.api.death.PvPAttackValidateResult
import org.rsmod.api.death.PvPCombatXpHook
import org.rsmod.api.death.PvPMaxHitHook
import org.rsmod.api.death.RangedAmmoSaveHook
import org.rsmod.api.death.UntradeableHandling
import org.rsmod.api.player.hook.PlayerRestrictionHook
import org.rsmod.api.player.hook.PlayerTeleportValidateHook
import org.rsmod.api.player.hook.RestrictedAction
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.ironman.isAnyIronman
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.type.getInvObj
import org.rsmod.map.CoordGrid

/**
 * What the engine asks Castle Wars while a game runs: only opposing teams may fight, nobody
 * teleports out, an empowered bracelet hits standard bearers harder, a device shown to Lanthus
 * saves ammunition without being worn, dying costs nothing and wakes you in your team's respawn room, a carried standard
 * falls where its bearer died, and animals in the waiting rooms can't gear up.
 */
internal class CastleWarsHooks @Inject constructor(private val game: CastleWarsGame) :
    PvPAttackValidateHook,
    NpcAttackValidateHook,
    PvPCombatXpHook,
    PvPMaxHitHook,
    RangedAmmoSaveHook,
    PlayerRestrictionHook,
    PlayerTeleportValidateHook,
    PlayerDeathHook,
    PlayerDeathItemHook,
    PlayerDeathCleanupHook,
    PlayerRespawnHook {
    override fun validate(attacker: Player, target: Player): PvPAttackValidateResult {
        val attackerTeam = game.playingTeamOf(attacker)
        val targetTeam = game.playingTeamOf(target)
        return when {
            attackerTeam == null && targetTeam == null -> PvPAttackValidateResult.Pass
            attackerTeam == null || targetTeam == null -> PvPAttackValidateResult.Deny("")
            attackerTeam == targetTeam -> PvPAttackValidateResult.Deny("You can't attack members of your own team.")
            else -> PvPAttackValidateResult.Pass
        }
    }

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        val owner = game.barricadeTeam(npc) ?: return NpcAttackValidateResult.Pass
        val team = game.playingTeamOf(player) ?: return NpcAttackValidateResult.Deny("")
        return if (team == owner) {
            NpcAttackValidateResult.Deny("You can't attack your own team's barricade.")
        } else {
            NpcAttackValidateResult.Pass
        }
    }

    override fun blocksCombatXp(attacker: Player, target: Player): Boolean {
        if (!game.isPlaying(attacker)) {
            return false
        }
        return attacker.isAnyIronman || attacker.vars["varbit.castlewars_xp_disabled"] == 1
    }

    override fun maxHitBonusPercent(attacker: Player, target: Player): Int {
        if (attacker.vars["varbit.castlewars_bracelet_active"] == 0 || game.carriedFlag(target) == null) {
            return 0
        }
        val attackerTeam = game.playingTeamOf(attacker) ?: return 0
        return if (attackerTeam != game.playingTeamOf(target)) BRACELET_BONUS_PERCENT else 0
    }

    override fun ammoSavePercent(player: Player): Int {
        val tier = player.vars["varbit.castlewars_ava_reward_tier"]
        if (tier == 0 || !game.isPlaying(player)) {
            return 0
        }
        val shown = CastleWars.AVAS_DEVICES.entries.firstOrNull { it.value == tier }?.let { ammoRecoveryRate(it.key) } ?: 0
        val worn = player.worn[Wearpos.Back.slot]?.let(::getInvObj)?.paramOrNull(params.ammo_recovery_rate) ?: 0
        return maxOf(shown, worn)
    }

    private fun ammoRecoveryRate(device: String): Int =
        ServerCacheManager.getItem(device.asRSCM(RSCMType.OBJ))?.paramOrNull(params.ammo_recovery_rate) ?: 0

    override fun restriction(player: Player, action: RestrictedAction): String? {
        if (player.transmog != null && game.waitingTeamOf(player) != null) {
            return when (action) {
                RestrictedAction.Walk -> null
                else -> "You can't do that while you're transformed."
            }
        }
        if (action is RestrictedAction.Equip && game.carriedFlag(player) != null) {
            val slots = listOf(action.obj.wearpos1, action.obj.wearpos2, action.obj.wearpos3)
            if (Wearpos.RightHand.slot in slots || Wearpos.LeftHand.slot in slots) {
                return "You can't do that while you're holding a standard."
            }
        }
        if (action is RestrictedAction.Equip && game.isParticipant(player)) {
            val slots = listOf(action.obj.wearpos1, action.obj.wearpos2, action.obj.wearpos3)
            if (Wearpos.Back.slot in slots) {
                return "You can't wear a cape in Castle Wars."
            }
        }
        return null
    }

    override fun validate(player: Player, type: TeleportType, areaChecker: AreaChecker): String? {
        if (type == TeleportType.Exempt || !game.isParticipant(player)) {
            return null
        }
        return "You can't teleport out of Castle Wars."
    }

    override val priority: Int
        get() = PlayerDeathHook.PRIORITY_SAFE_ACTIVITY

    override fun handleDeath(context: PlayerDeathContext): PlayerDeathHandling? {
        if (!game.isPlaying(context.player)) {
            return null
        }
        return PlayerDeathHandling(
            keepCount = Int.MAX_VALUE,
            dropReceiver = null,
            dropDuration = 0,
            revealDelay = 0,
            supplyPile = false,
            untradeableHandling = UntradeableHandling.KEEP,
        )
    }

    override fun beforeDrops(context: PlayerDeathContext, handling: PlayerDeathHandling) {
        val player = context.player
        if (!game.isPlaying(player)) {
            return
        }
        val flag = game.carriedFlag(player)
        if (flag != null) {
            game.removeBanner(player)
            game.flagDropped(flag, context.coords)
        }
        val killer = context.killer ?: return
        if (game.playingTeamOf(killer) != null && game.playingTeamOf(killer) != game.playingTeamOf(player)) {
            val kills = killer.vars["varbit.castlewars_kills"]
            VarPlayerIntMapSetter.set(killer, "varbit.castlewars_kills", (kills + 1).coerceAtMost(MAX_COUNTER))
        }
    }

    override fun respawn(player: Player): CoordGrid? {
        val team = game.playingTeamOf(player) ?: return null
        return team.spawnRoom
    }

    override fun cleanup(player: Player) {
        if (game.isPlaying(player)) {
            game.markDirty()
        }
    }

    private companion object {
        const val MAX_COUNTER = 2047
        const val BRACELET_BONUS_PERCENT = 20
    }
}
