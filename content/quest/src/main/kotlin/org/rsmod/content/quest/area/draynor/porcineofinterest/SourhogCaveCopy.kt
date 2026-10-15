package org.rsmod.content.quest.area.draynor.porcineofinterest

import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.CORPSE_WITH_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.QUEST_SOURHOG
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.SOURHOG_FOOT
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_GOGGLES
import org.rsmod.content.quest.area.draynor.porcineofinterest.PorcineOfInterestQuest.Companion.STAGE_SLAIN
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.loc.LocAngle
import org.rsmod.game.loc.LocShape
import org.rsmod.map.CoordGrid

/**
 * Until the quest is over the hole leads to a private copy of the Sourhog Cave, holding nothing
 * but the quest's own scenery and, once the player has the goggles, its one poison-immune
 * sourhog. The eight aggressive slayer sourhogs of the real cave would otherwise maul a novice on
 * the way to the skeleton and during the ambush, and only become fair game once Spria hands out
 * tasks.
 */
@Singleton
class SourhogCaveCopy
@Inject
constructor(
    private val porcine: PorcineOfInterestQuest,
    private val instances: QuestInstances,
    private val manager: InstanceManager,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
) {
    fun ProtectedAccess.enter(): Boolean {
        val stage = porcine.stage(player)
        val spawns =
            if (stage == STAGE_GOGGLES) {
                listOf(InstanceNpc(QUEST_SOURHOG, PorcineCoords.SOURHOG_LAIR))
            } else {
                emptyList()
            }
        val visit =
            with(instances) {
                enterCopy(KEY, PorcineCoords.CAVE_ENTRANCE, PorcineCoords.HOLE_SIDE, spawns)
            } ?: return false
        for (npc in instances.npcsIn(visit)) {
            npc.respawns = false
        }
        objRepo.add(SCIMITAR, visit.at(PorcineCoords.SCIMITAR), SCIMITAR_TICKS, receiver = player)
        if (stage == STAGE_SLAIN && !carries(SOURHOG_FOOT)) {
            player.porcineFootCut = CORPSE_WITH_FOOT
            locRepo.addSourhogCarcass(visit.at(PorcineCoords.SOURHOG_LAIR))
        }
        return true
    }

    fun ProtectedAccess.leave() {
        with(instances) { leaveCopy() }
    }

    /** World tile [world] as it lies in whichever copy of the cave the player is standing in. */
    fun ProtectedAccess.local(world: CoordGrid): CoordGrid {
        val session = manager.sessionForPlayer(player) ?: return world
        return manager.resolveCoord(session, world) ?: world
    }

    private companion object {
        const val KEY = "porcine_sourhog_cave"
        const val SCIMITAR = "obj.bronze_scimitar"
        const val SCIMITAR_TICKS = 3000
    }
}

internal fun LocRepository.addSourhogCarcass(coords: CoordGrid) {
    add(coords, CARCASS, CARCASS_LIFETIME, LocAngle.West, LocShape.CentrepieceStraight)
}

private const val CARCASS = "loc.porcine_dead_sourhog"

/** Long enough to come back for the foot after a trip to the bank. */
private const val CARCASS_LIFETIME = 3000
