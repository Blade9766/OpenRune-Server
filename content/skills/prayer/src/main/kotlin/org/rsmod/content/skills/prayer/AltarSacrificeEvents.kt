package org.rsmod.content.skills.prayer

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.events.interact.LocUEvents
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLocU
import org.rsmod.api.script.onPlayerQueueWithArgs
import org.rsmod.api.table.prayer.SkillPrayerRow
import org.rsmod.content.skills.construction.data.Chapel
import org.rsmod.content.skills.prayer.items.ZealotRobes.shouldConsume
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.map.zone.ZoneKey
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Offering bones at the Chaos Temple altar and at player-owned house altars.
 *
 * A house altar's multiplier comes from its tier plus every incense burner lit in its room; a
 * chapel is a single 8x8 zone, so the burners are whatever lit burner locs share the altar's zone.
 */
class AltarSacrificeEvents
@Inject
constructor(private val worldRepo: WorldRepository, private val locRepo: LocRepository) :
    PluginScript() {

    private val litBurners: Set<Int> by lazy {
        Chapel.LIT_BURNERS.keys.mapTo(HashSet()) { it.asRSCM(RSCMType.LOC) }
    }

    override fun ScriptContext.startup() {
        val bones = PrayerBuryEvents.bones.filterNot { it.ashes }

        bones.forEach { row ->
            registerAltar(CHAOS_ALTAR, row, chaos = true)
            Chapel.ALTARS.keys.forEach { altar -> registerAltar(altar, row, chaos = false) }
        }

        onPlayerQueueWithArgs("queue.prayer_altar_sacrifice") { processSacrificeTick(it.args) }
    }

    private fun ScriptContext.registerAltar(altar: String, row: SkillPrayerRow, chaos: Boolean) {
        onOpLocU(altar, row.item.internalName) { startSacrifice(it, row, chaos) }
    }

    private fun ProtectedAccess.startSacrifice(
        event: LocUEvents.Op,
        row: SkillPrayerRow,
        chaos: Boolean,
    ) {
        val task = SacrificeTask(row = row, slot = event.invSlot, altar = event.vis, chaos = chaos)

        if (!canSacrifice(task)) {
            return
        }

        stopAction()
        weakQueue("queue.prayer_altar_sacrifice", 1, task)
    }

    private fun ProtectedAccess.processSacrificeTick(task: SacrificeTask) {
        if (!canSacrifice(task)) {
            return
        }

        performSacrifice(task)

        weakQueue("queue.prayer_altar_sacrifice", 4, task)
    }

    private fun ProtectedAccess.performSacrifice(task: SacrificeTask) {
        anim("seq.human_bone_sacrifice")

        spotanimMap(worldRepo, "spotanim.poh_bone_sacrifice", task.altar.coords)

        val burners = if (task.chaos) 0 else litBurnersAround(task.altar)
        statAdvance("stat.prayer", task.row.exp / 10.0 * multiplier(task, burners))

        if (shouldConsumeBone(task).not()) {
            mes("The Dark Lord spares your sacrifice, but rewards you for your efforts.")
            return
        }

        val result = invDel(inv = inv, type = task.row.item.internalName)

        if (result.failure) {
            return
        }

        if (!task.chaos) {
            val very = if (burners == MAX_BURNERS) "very " else ""
            spam("The gods are ${very}pleased with your offering.")
        }
    }

    private fun multiplier(task: SacrificeTask, burners: Int): Double {
        if (task.chaos) {
            return CHAOS_MULTIPLIER
        }
        val altar = RSCM.getReverseMapping(RSCMType.LOC, task.altar.id)
        return Chapel.multiplier(altar, burners) ?: 1.0
    }

    private fun litBurnersAround(altar: BoundLocInfo): Int =
        locRepo
            .findAll(ZoneKey.from(altar.coords))
            .count { it.id in litBurners }
            .coerceAtMost(MAX_BURNERS)

    private fun ProtectedAccess.canSacrifice(task: SacrificeTask): Boolean {
        val bone = task.row.item.internalName

        return when {
            inv.count(bone) <= 0 -> false
            !isWithinDistance(task.altar, 1) -> false
            task.chaos && !inArea("area.chaos_temple", player.coords) -> false
            else -> true
        }
    }

    private fun ProtectedAccess.shouldConsumeBone(task: SacrificeTask): Boolean {
        val baseConsumeChance = if (task.chaos) 0.5 else 1.0
        return player.shouldConsume(baseConsumeChance)
    }

    private data class SacrificeTask(
        val row: SkillPrayerRow,
        val slot: Int,
        val altar: BoundLocInfo,
        val chaos: Boolean,
    )

    private companion object {
        const val CHAOS_ALTAR = "loc.chaosaltar"
        const val CHAOS_MULTIPLIER = 3.5
        const val MAX_BURNERS = 2
    }
}
