package org.rsmod.content.quest.area.paterdomus.priestinperil

import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.config.refs.params
import org.rsmod.api.death.NpcAttackValidateHook
import org.rsmod.api.death.NpcAttackValidateResult
import org.rsmod.api.death.NpcDeathKillContext
import org.rsmod.api.death.NpcDeathKillHook
import org.rsmod.api.instances.InstanceManager
import org.rsmod.api.instances.InstanceNpc
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.righthand
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.GOLDEN_KEY
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.HOODED_MONK
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_AGREED_TO_KILL_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_KILLED_DOG
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_MET_DREZEL
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.STAGE_ROALD_FURIOUS
import org.rsmod.content.quest.area.paterdomus.priestinperil.PriestInPerilQuest.Companion.TEMPLE_GUARDIAN
import org.rsmod.content.quest.manager.QuestInstances
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.game.interact.InteractionNpcT
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The mausoleum entrance north of the temple and its guardian. The guardian in the shared world
 * is only scenery; a player sent to kill it climbs down into a private copy of the mausoleum
 * where it can be fought, and climbs back out of the copy by the same ladder. Both the trapdoor
 * and the ladder are common locs, so anywhere else they fall through to the generic climbs.
 */
class TempleGuardian
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val instances: QuestInstances,
    private val passages: GenericPassageScript,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpLoc1(TRAPDOOR_OPEN) { climbDown(it.loc, it.type) }
        onOpLoc1(LADDER_UP) { climbUp(it.type) }
    }

    private suspend fun ProtectedAccess.climbDown(loc: BoundLocInfo, type: ObjectServerType) {
        if (loc.coords != PaterdomusCoords.NORTH_TRAPDOOR) {
            with(passages) { passage(loc, type, 0) }
            return
        }
        arriveDelay()
        if (priestInPeril.stage(player) != STAGE_AGREED_TO_KILL_DOG) {
            anim(CLIMB_DOWN_SEQ)
            delay(1)
            telejump(PaterdomusCoords.MAUSOLEUM_ENTRY, TeleportType.Exempt)
            return
        }
        var enter = false
        startDialogue { enter = choice2("Yes.", true, "No.", false, title = "Climb down into the mausoleum?") }
        if (!enter) {
            return
        }
        anim(CLIMB_DOWN_SEQ)
        delay(1)
        with(instances) {
            enterCopy(
                INSTANCE_KEY,
                PaterdomusCoords.MAUSOLEUM_ENTRY,
                PaterdomusCoords.NORTH_TRAPDOOR_LANDING,
                listOf(InstanceNpc(TEMPLE_GUARDIAN, PaterdomusCoords.GUARDIAN_SPAWN)),
                bossName = "Temple Guardian",
            )
        }
    }

    private suspend fun ProtectedAccess.climbUp(type: ObjectServerType) {
        arriveDelay()
        val climbAnim = type.paramOrNull(params.climb_anim)?.let { RSCM.getReverseMapping(RSCMType.SEQ, it.id) }
            ?: CLIMB_UP_SEQ
        anim(climbAnim)
        delay(1)
        if (with(instances) { insideCopy() }) {
            val exit = with(instances) { leaveCopy() }
            telejump(exit ?: PaterdomusCoords.NORTH_TRAPDOOR_LANDING, TeleportType.Exempt)
            return
        }
        telejump(coords.translateZ(-DUNGEON_OFFSET), TeleportType.Exempt)
    }

    private companion object {
        const val TRAPDOOR_OPEN = "loc.trapdoor_open"
        const val LADDER_UP = "loc.ladder_from_cellar"
        const val INSTANCE_KEY = "priestinperil_mausoleum"
        const val CLIMB_DOWN_SEQ = "seq.human_pickupfloor"
        const val CLIMB_UP_SEQ = "seq.human_reachforladder"
        const val DUNGEON_OFFSET = 6400
    }
}

/**
 * Only a player sent to kill the guardian, inside their own copy of the mausoleum, may fight it,
 * and it shrugs off magic. The hooded monk can be fought at any time.
 */
class TempleGuardianAttackHook
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val instances: InstanceManager,
) : NpcAttackValidateHook {
    private val guardianId = TEMPLE_GUARDIAN.asRSCM(RSCMType.NPC)

    override fun validate(player: Player, npc: Npc): NpcAttackValidateResult {
        if (npc.id != guardianId) {
            return NpcAttackValidateResult.Pass
        }
        if (priestInPeril.stage(player) != STAGE_AGREED_TO_KILL_DOG || instances.instanceForNpc(npc) == null) {
            return NpcAttackValidateResult.Deny("You have no reason to do that!")
        }
        if (player.isCastingSpell()) {
            return NpcAttackValidateResult.Deny("Your spells do not seem to affect it.")
        }
        return NpcAttackValidateResult.Pass
    }

    private fun Player.isCastingSpell(): Boolean =
        interaction is InteractionNpcT || (vars[AUTOCAST_SET] == 1 && righthand != null)

    private companion object {
        const val AUTOCAST_SET = "varbit.autocast_set"
    }
}

/**
 * Killing the guardian moves the quest on. The hooded monk drops the golden key for a player who
 * needs it, and then stays out of sight for them while they hold it.
 */
class PriestInPerilKillHook
@Inject
constructor(
    private val priestInPeril: PriestInPerilQuest,
    private val objRepo: ObjRepository,
    private val doors: PaterdomusDoors,
) : NpcDeathKillHook {
    private val guardianId = TEMPLE_GUARDIAN.asRSCM(RSCMType.NPC)
    private val hoodedMonkId = HOODED_MONK.asRSCM(RSCMType.NPC)

    override fun onKill(context: NpcDeathKillContext) {
        val hero = context.hero
        when (context.npc.id) {
            guardianId -> guardianKilled(hero)
            hoodedMonkId -> hoodedMonkKilled(hero, context.npc)
        }
    }

    private fun guardianKilled(hero: Player) {
        if (priestInPeril.stage(hero) != STAGE_AGREED_TO_KILL_DOG) {
            return
        }
        doors.launchWhenFree(hero.uid) {
            if (priestInPeril.stage(player) != STAGE_AGREED_TO_KILL_DOG) {
                return@launchWhenFree
            }
            priestInPeril.advanceTo(this, STAGE_KILLED_DOG)
            startDialogue { chatPlayer(happy, "There we go, one dead dog. I should go and tell Drezel.") }
        }
    }

    private fun hoodedMonkKilled(hero: Player, monk: Npc) {
        if (priestInPeril.stage(hero) !in STAGE_ROALD_FURIOUS..STAGE_MET_DREZEL || hero.inv.contains(GOLDEN_KEY)) {
            return
        }
        objRepo.add(GOLDEN_KEY, monk.coords, KEY_DURATION, receiver = hero)
        hero.hoodedMonkDead = true
    }

    private companion object {
        const val KEY_DURATION = 200
    }
}
