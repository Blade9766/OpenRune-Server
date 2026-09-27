package org.rsmod.content.other.barrows

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.combatachievements.CombatAchievements
import org.rsmod.api.player.midiJingle
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.interfaces.collectionlog.CollectionLog
import org.rsmod.game.loc.BoundLocInfo

@Singleton
class BarrowsChest
@Inject
constructor(
    private val spawns: BarrowsSpawns,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val achievements: CombatAchievements,
    private val random: GameRandom,
) {
    fun open(access: ProtectedAccess, loc: BoundLocInfo) {
        with(access) {
            if (!BarrowsCoords.inTunnels(player.coords)) {
                return
            }
            if (BarrowsRun.isChestOpen(player)) {
                search(this, loc)
                return
            }
            BarrowsRun.markChestOpen(player)
            showOpen(loc)
            val tunnel = BarrowsRun.tunnelBrother(player)
            if (
                tunnel != null &&
                    !BarrowsRun.isKilled(player, tunnel) &&
                    spawns.activeBrother(player) == null
            ) {
                spawns.summonBrother(player, tunnel, "You dare steal from us!")
            }
        }
    }

    fun search(access: ProtectedAccess, loc: BoundLocInfo) {
        with(access) {
            if (!BarrowsCoords.inTunnels(player.coords)) {
                return
            }
            if (!BarrowsRun.isChestOpen(player)) {
                open(this, loc)
                return
            }
            if (BarrowsRun.isLooted(player)) {
                mes("The chest is empty.")
                return
            }
            loot()
        }
    }

    fun close(loc: BoundLocInfo) {
        locRepo.add(
            loc.coords,
            BarrowsTunnelScript.CHEST_CLOSED,
            Int.MAX_VALUE,
            loc.angle,
            loc.shape,
        )
    }

    private fun showOpen(loc: BoundLocInfo) {
        locRepo.add(
            loc.coords,
            BarrowsTunnelScript.CHEST_OPEN,
            OPEN_DURATION,
            loc.angle,
            loc.shape,
            onDespawn = { close(loc) },
        )
    }

    private fun ProtectedAccess.loot() {
        val potential = BarrowsRun.rewardPotential(player)
        if (potential <= 0) {
            mes("You need to kill at least one creature in the crypts before looting the chest.")
            return
        }
        val rewards = BarrowsLoot.roll(BarrowsRun.killed(player), potential, random)
        BarrowsRun.markLooted(player)

        val display = inv(REWARD_INV)
        invClear(display)
        for (reward in rewards) {
            invAdd(display, reward.obj, reward.count, strict = false)
        }
        invTransmit(display)
        ifOpenMainModal(REWARD_INTERFACE)

        for (reward in rewards) {
            CollectionLog.grant(player, reward.obj, reward.count)
            invAddOrDrop(objRepo, reward.obj, reward.count)
        }

        val count = player.vars[BarrowsRun.CHEST_COUNT]
        mes("Your Barrows chest count is: <col=ff0000>$count</col>.")
        player.midiJingle(GRAVE_ROBBER_JINGLE)
        achievements.checkKillcounts(player)
        softTimer(BarrowsAreaScript.COLLAPSE_TIMER, BarrowsAreaScript.COLLAPSE_INTERVAL)
    }

    private companion object {
        const val OPEN_DURATION = 100
        const val REWARD_INV = "inv.trail_rewardinv"
        const val REWARD_INTERFACE = "interface.barrows_reward"
        const val GRAVE_ROBBER_JINGLE = 77
    }
}
