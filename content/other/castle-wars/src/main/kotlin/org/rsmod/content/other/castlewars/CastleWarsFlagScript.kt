package org.rsmod.content.other.castlewars

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.worn.HeldEquipOp
import org.rsmod.api.player.worn.HeldEquipResult
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpWorn1
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The team standards: taking the enemy's from its stand, capturing it on your own, dropping it,
 * and picking a dropped one back up - including holding your own standard to keep it from the
 * enemy until you walk it home.
 */
internal class CastleWarsFlagScript
@Inject
constructor(private val game: CastleWarsGame, private val equipOp: HeldEquipOp) : PluginScript() {
    override fun ScriptContext.startup() {
        for (team in Team.entries) {
            onOpLoc1(team.standLoc) { captureAtStand(team) }
            onOpLoc1(team.emptyStandLoc) { captureAtEmptyStand(team) }
            onOpLoc1(team.droppedBannerLoc) { takeDropped(team) }
            onOpWorn1(team.banner) { dropBanner(team) }
        }
    }

    private suspend fun ProtectedAccess.captureAtStand(standTeam: Team) {
        val team = game.playingTeamOf(player) ?: return
        if (team == standTeam) {
            if (game.carriedFlag(player) == standTeam.opponent) {
                score(team)
            } else {
                mes("This is your team's standard. Protect it!")
            }
            return
        }
        if (game.state(standTeam).flag != FlagState.Safe) {
            return
        }
        if (!holdBanner(standTeam)) {
            return
        }
        game.takeFromStand(standTeam, player)
        mes("You take the ${standTeam.displayName} standard! Bring it back to your own castle.")
    }

    private suspend fun ProtectedAccess.captureAtEmptyStand(standTeam: Team) {
        val team = game.playingTeamOf(player) ?: return
        if (team == standTeam && game.carriedFlag(player) == standTeam.opponent) {
            score(team)
            return
        }
        mes("There should be a standard here!")
    }

    private suspend fun ProtectedAccess.score(team: Team) {
        anim("seq.human_pickuptable")
        game.removeBanner(player)
        game.score(team, team.opponent)
        val caps = player.vars["varbit.castlewars_caps"]
        org.rsmod.api.player.vars.VarPlayerIntMapSetter.set(player, "varbit.castlewars_caps", (caps + 1).coerceAtMost(MAX_COUNTER))
        soundSynth("synth.godspell_charge")
        delay(1)
    }

    private suspend fun ProtectedAccess.takeDropped(flagTeam: Team) {
        val team = game.playingTeamOf(player) ?: return
        if (game.carriedFlag(player) != null) {
            mes("You can only carry one standard at a time.")
            return
        }
        if (team == flagTeam && coords in team.castle) {
            anim("seq.human_pickupfloor")
            game.returnFlag(flagTeam)
            mes("You return your team's standard to its stand.")
            return
        }
        if (!holdBanner(flagTeam)) {
            return
        }
        game.pickUpDropped(flagTeam, player)
        mes(
            if (team == flagTeam) {
                "You pick up your team's standard. Get it back into your castle!"
            } else {
                "You pick up the ${flagTeam.displayName} standard!"
            },
        )
    }

    private fun ProtectedAccess.dropBanner(flagTeam: Team) {
        if (game.carriedFlag(player) != flagTeam) {
            game.removeBanner(player)
            return
        }
        game.removeBanner(player)
        game.flagDropped(flagTeam, coords)
    }

    /**
     * Frees [player]'s hands and puts [flagTeam]'s banner in them, the same way wielding it from
     * the inventory would; false (with a message) when the backpack can't take what they held.
     */
    private suspend fun ProtectedAccess.holdBanner(flagTeam: Team): Boolean {
        val handsFull =
            player.worn[Wearpos.RightHand.slot] != null && player.worn[Wearpos.LeftHand.slot] != null
        val needed = if (handsFull) 2 else 1
        if (inv.freeSpace() < needed) {
            mes("You need more free inventory space to carry the standard.")
            return false
        }
        anim("seq.human_pickuptable")
        invAdd(inv, flagTeam.banner, 1)
        val slot = inv.indices.firstOrNull { inv[it]?.id == flagTeam.banner.asRSCM(RSCMType.OBJ) } ?: return false
        val result = equipOp.equip(player, slot, inv)
        if (result !is HeldEquipResult.Success) {
            invDel(inv, flagTeam.banner, 1)
            (result as? HeldEquipResult.Fail)?.messages?.firstOrNull()?.let(::mes)
            return false
        }
        rebuildAppearance()
        return true
    }

    private companion object {
        const val MAX_COUNTER = 2047
    }
}
