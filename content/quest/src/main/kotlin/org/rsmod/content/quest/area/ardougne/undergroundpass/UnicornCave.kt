package org.rsmod.content.quest.area.ardougne.undergroundpass

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.api.script.onOpNpcU
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.BOULDER
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.RAILING
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SEQ_SEARCH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_RUMBLE
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.SOUND_UNICORN_DEATH
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_DOORS
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_UNICORN
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.STAGE_WELL
import org.rsmod.content.quest.area.ardougne.undergroundpass.UndergroundPassQuest.Companion.UNICORN_HORN
import org.rsmod.content.quest.area.wilderness.magearena.nearestFree
import org.rsmod.game.entity.Npc
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * The caged unicorn and the boulder on the shelf above it.
 *
 * Nothing the player carries will kill a unicorn through a cage, but the railing from its own cage
 * will lever the boulder over the edge. The cave is mapped twice, whole and wrecked, so once the
 * boulder falls the player is put into the wrecked copy, where the horn lies in the ruins.
 */
@Singleton
class UnicornCave
@Inject
constructor(
    private val quest: UndergroundPassQuest,
    private val collision: CollisionFlagMap,
) : PluginScript() {

    private val boulderType by lazy {
        ServerCacheManager.getNpc(BOULDER.asRSCM(RSCMType.NPC)) ?: error("Missing $BOULDER")
    }

    private val railingType by lazy {
        ServerCacheManager.getItem(RAILING.asRSCM(RSCMType.OBJ)) ?: error("Missing $RAILING")
    }

    override fun ScriptContext.startup() {
        onOpNpcU(boulderType, railingType) { pryBoulder(it.npc) }
        onOpNpc1(BOULDER) {
            startDialogue(it.npc) { chatPlayer(sad, "It's too heavy to move with just my hands.") }
        }
        onOpLoc1(SMASHED_CAGE) { searchCage() }
    }

    private suspend fun ProtectedAccess.pryBoulder(boulder: Npc) {
        arriveDelay()
        if (quest.stage(player) != STAGE_WELL) {
            mes("Nothing interesting happens.")
            return
        }
        faceSquare(boulder.coords)
        anim(PUSH_SEQ)
        val rest = boulder.coords
        soundSynth(SOUND_RUMBLE)
        boulder.teleport(collision, rest.translate(0, 1))
        delay(1)
        boulder.teleport(collision, rest.translate(0, 2))
        delay(1)
        soundSynth(SOUND_UNICORN_DEATH)
        quest.advanceTo(this, STAGE_UNICORN)
        UndergroundPassQuest.setVarBit(player, "varbit.upass_cave_unicorn", 1)
        val wrecked = coords.translate(-UpassCoords.UNICORN_COPY_OFFSET, 0)
        telejump(collision.nearestFree(wrecked, LANDING_RADIUS) ?: wrecked, TeleportType.Exempt)
        boulder.teleport(collision, rest)
        startDialogue { chatPlayer(neutral, "I heard something breaking.") }
    }

    private suspend fun ProtectedAccess.searchCage() {
        arriveDelay()
        anim(SEQ_SEARCH)
        if (quest.stage(player) >= STAGE_DOORS) {
            mes("You search the cage remains...")
            delay(2)
            mes("But find nothing.")
            return
        }
        mes("The unicorn was killed by the boulder.")
        if (inv.contains(UNICORN_HORN)) {
            mes("Nothing remains.")
            return
        }
        if (invAdd(inv, UNICORN_HORN).failure) {
            mes("You don't have enough inventory space.")
            return
        }
        startDialogue { chatPlayer(neutral, "All that remains is a damaged horn.") }
    }

    private companion object {
        const val SMASHED_CAGE = "loc.unicorncage_destroyed_upass"
        const val PUSH_SEQ = "seq.human_push"
        const val LANDING_RADIUS = 2
    }
}
