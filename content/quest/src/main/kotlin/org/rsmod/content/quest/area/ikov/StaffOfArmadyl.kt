package org.rsmod.content.quest.area.ikov

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpObj3
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAFF_OF_ARMADYL
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_LUCIEN
import org.rsmod.content.quest.area.ikov.TempleOfIkovQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ikov.npcs.GuardiansOfArmadyl
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.game.obj.Obj
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The wall into the temple of Armadyl and the staff lying behind it.
 *
 * Lifting the staff is the moment the quest forks: it is done in plain sight of the order that
 * has guarded it for generations, and every guardian in the chamber comes for the thief.
 *
 * The wall is `loc.secretdoor2`, which the map also uses for one other secret wall; pushing
 * either of them aside is the same thing, so the handler is not narrowed to this one.
 */
class StaffOfArmadyl
@Inject
constructor(
    private val quest: TempleOfIkovQuest,
    private val guardians: GuardiansOfArmadyl,
    private val passages: GenericPassageScript,
    private val objRepo: ObjRepository,
) : PluginScript() {

    private val staffType: ItemServerType =
        ServerCacheManager.getItem(STAFF_OF_ARMADYL.asRSCM(RSCMType.OBJ))
            ?: error("Missing obj: $STAFF_OF_ARMADYL")

    override fun ScriptContext.startup() {
        onOpLoc1(SECRET_WALL) { pushWall(it.loc, it.type) }
        onOpObj3(staffType) { takeStaff(it.obj) }
    }

    private suspend fun ProtectedAccess.pushWall(wall: BoundLocInfo, type: ObjectServerType) {
        arriveDelay()
        soundSynth(PUSH_SOUND)
        mes("The wall grinds aside on a hidden pivot.")
        with(passages) { walkThrough(wall, type) }
    }

    private suspend fun ProtectedAccess.takeStaff(staff: Obj) {
        if (inv.isFull()) {
            mes("You don't have enough inventory space.")
            return
        }
        if (!quest.isStarted(player)) {
            mes("The staff is not yours to take, and the men around it are watching.")
            return
        }
        if (quest.stage(player) >= TempleOfIkovQuest.STAGE_ARMADYL) {
            mes("You gave your word to the guardians. Leave it where it lies.")
            return
        }
        if (coords != staff.coords) {
            delay(1)
        }
        anim(TAKE_SEQ)
        if (!objRepo.del(staff)) {
            return
        }
        invAdd(inv, STAFF_OF_ARMADYL)
        mes("You lift the Staff of Armadyl off its rest. It hums against your palm.")
        if (quest.stage(player) == STAGE_STARTED) {
            quest.advanceTo(this, STAGE_LUCIEN)
        }
        with(guardians) { setGuardiansOn() }
    }

    private companion object {
        const val SECRET_WALL = "loc.secretdoor2"
        const val TAKE_SEQ = "seq.human_pickupfloor"
        const val PUSH_SOUND = "synth.stone_door"
    }
}
