package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BOULDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.RAILING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_RUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_UNICORN_DEATH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_UNICORN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_WELL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.UNICORN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.UNICORN_HORN
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The caged unicorn and the boulder above it.
 *
 * The horn has to come off a unicorn that is already dead, and nothing the player carries will
 * kill one through a cage. The boulder sitting on the shelf over the cage will, once the length
 * of railing from the cells is under it.
 */
@Singleton
class UnicornCave
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val boulderType by lazy {
        ServerCacheManager.getNpc(BOULDER.asRSCM(RSCMType.NPC)) ?: error("Missing $BOULDER")
    }

    private val railingType by lazy {
        ServerCacheManager.getItem(RAILING.asRSCM(RSCMType.OBJ)) ?: error("Missing $RAILING")
    }

    override fun ScriptContext.startup() {
        onOpNpcU(boulderType, railingType) { pryBoulder() }
        onOpNpc1(BOULDER) { pushBoulder() }
        for (unicorn in listOf(UNICORN, UndergroundPassQuest.visibleTwin(UNICORN))) {
            onOpNpc1(unicorn) { lookAtUnicorn() }
        }
        onOpLoc1(SMASHED_CAGE) { searchCage() }
    }

    private suspend fun ProtectedAccess.lookAtUnicorn() {
        mesbox(
            "The unicorn has been in that cage a very long time. It watches you without any " +
                "interest at all.",
        )
    }

    private suspend fun ProtectedAccess.pushBoulder() {
        arriveDelay()
        anim(PUSH_SEQ)
        delay(2)
        if (quest.stage(player) >= STAGE_UNICORN) {
            mes("The boulder is wedged in the rubble it made. It isn't going anywhere.")
            return
        }
        mes("The boulder will not shift. There is no purchase on it.")
    }

    private suspend fun ProtectedAccess.pryBoulder() {
        arriveDelay()
        if (quest.stage(player) >= STAGE_UNICORN) {
            mes("The boulder has already done its work.")
            return
        }
        if (quest.stage(player) < STAGE_WELL) {
            mes("There is no reason to bring that down yet.")
            return
        }
        faceSquare(UpassCoords.UNICORN_CAGE)
        anim(PUSH_SEQ)
        mes("You work the railing under the boulder and lean on it.")
        delay(2)
        soundSynth(SOUND_RUMBLE)
        delay(1)
        soundSynth(SOUND_UNICORN_DEATH)
        quest.advanceTo(this, STAGE_UNICORN)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_cave_unicorn", 1)
        mesbox(
            "The boulder goes over the edge and through the roof of the cage. When the dust " +
                "settles there is nothing moving under it.",
        )
    }

    private suspend fun ProtectedAccess.searchCage() {
        arriveDelay()
        anim(SEQ_SEARCH)
        delay(1)
        if (quest.stage(player) < STAGE_UNICORN) {
            mes("The cage is sound, and the unicorn inside it is very much alive.")
            return
        }
        if (inv.contains(UNICORN_HORN) || player.hornInWell) {
            mes("There is nothing else in the wreckage worth having.")
            return
        }
        if (invAdd(inv, UNICORN_HORN).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        mesbox("You work the horn free of the wreckage. It is withered, but it is whole.")
    }

    private companion object {
        const val SMASHED_CAGE = "loc.unicorncage_destroyed_upass"
        const val PUSH_SEQ = "seq.human_push"
    }
}
