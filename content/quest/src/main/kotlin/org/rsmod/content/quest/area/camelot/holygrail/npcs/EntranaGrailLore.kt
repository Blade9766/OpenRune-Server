package org.rsmod.content.quest.area.camelot.holygrail.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.MesAnimType
import jakarta.inject.Inject
import jakarta.inject.Singleton
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.output.soundSynth
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.CRONE
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_ENTRANA
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_MERLIN
import org.rsmod.game.entity.Npc
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * What Entrana knows of the Grail. The High Priest can name the Fisher King and his realm but not
 * the way in; a crone who has been listening at the chapel door shuffles up and tells the rest:
 * the whistles in Draynor Manor, who can see them, where to blow them, and the black titan who
 * guards the bridge beyond.
 *
 * The crone only stands in the chapel while she is needed, and is talked to again for a recap.
 */
@Singleton
class EntranaGrailLore
@Inject
constructor(
    private val quest: HolyGrailQuest,
    private val npcRepo: NpcRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(CRONE) { startDialogue(it.npc) { croneRecap() } }
    }

    /** The High Priest's Grail lines; false when the quest has nothing to add and he should just greet. */
    suspend fun Dialogue.highPriest(): Boolean {
        val stage = quest.stage(player)
        if (stage < STAGE_MERLIN || quest.isComplete(player)) {
            return false
        }
        if (stage > STAGE_MERLIN) {
            chatNpc(happy, "Ah, the Grail-seeker. Did the old woman's whistles serve you?")
            chatNpc(neutral, "Remember: Draynor Manor for the whistles, the Brimhaven tower to blow them.")
            return true
        }
        chatPlayer(neutral, "Merlin sent me. I am searching for the Holy Grail.")
        chatNpc(
            neutral,
            "Then you seek what our order has studied for longer than this chapel has stood.",
        )
        chatNpc(
            neutral,
            "The Grail is not of this world alone. It is kept by the Fisher King, in a realm laid " +
                "alongside ours like a page in a book. While he is strong, his land is strong.",
        )
        chatNpc(
            sad,
            "But we hear that he is not strong. The fishing has failed there, and the fields have " +
                "turned to ash. How one reaches it, I confess I do not know.",
        )
        summonCrone()
        chatNpc(confused, "Who let you in, grandmother?")
        croneTalks()
        quest.advanceTo(access, STAGE_ENTRANA)
        chatNpc(neutral, "Well. You heard her. Go with Saradomin, and keep that sword of yours close.")
        return true
    }

    private suspend fun Dialogue.croneTalks() {
        crone(laugh, "Nobody lets old Ellyn in, priest. She lets herself in.")
        crone(
            neutral,
            "You want the Fisher King's country, dearie? Then you want a whistle. Magic ones, tin, " +
                "hidden on the top floor of Draynor Manor, in the room at its southern end.",
        )
        crone(
            neutral,
            "Only you won't see them. Not unless you carry something that once touched the Grail. " +
                "Galahad sat at its table once; he may have kept a keepsake.",
        )
        crone(
            neutral,
            "Blow a whistle standing under the old tower north-west of Brimhaven, where the world " +
                "wears thin, and you'll be there.",
        )
        crone(
            neutral,
            "And mind the bridge. A titan in black armour stands on it, and no common blade can " +
                "finish him. Only the sword of a true king.",
        )
        chatPlayer(quiz, "Excalibur?")
        crone(shifty, "Did I say that? I'm old, dearie. I say all sorts.")
    }

    private suspend fun Dialogue.croneRecap() {
        if (quest.stage(player) < STAGE_ENTRANA) {
            chatNpc(neutral, "Lovely chapel. Draughty, though.")
            return
        }
        chatNpc(quiz, "Forgotten already? Listen, then.")
        chatNpc(neutral, "Whistles: Draynor Manor, top floor, the southern room. You see them with a Grail keepsake.")
        chatNpc(neutral, "Blow one under the tower north-west of Brimhaven. A true king's blade for the titan.")
    }

    private suspend fun Dialogue.crone(mood: MesAnimType, text: String) =
        chatNpcSpecific("Crone", CRONE, mood, text)

    /** Puts the crone beside the High Priest, unless she is already there. */
    private fun Dialogue.summonCrone(): Npc {
        val croneId = CRONE.asRSCM(RSCMType.NPC)
        val present = npcRepo.findAll(CRONE_TILE).firstOrNull { it.id == croneId && it.isSlotAssigned }
        if (present != null) {
            present.facePlayer(player)
            return present
        }
        val crone = Npc(CRONE, CRONE_TILE)
        crone.respawns = false
        npcRepo.add(crone, CRONE_TICKS)
        crone.facePlayer(player)
        access.spotanimMap(worldRepo, ARRIVE_SPOTANIM, CRONE_TILE, SPOTANIM_HEIGHT)
        player.soundSynth(ARRIVE_SOUND)
        return crone
    }

    companion object {
        /** Beside the High Priest at the altar end of Entrana's chapel. */
        val CRONE_TILE = CoordGrid(2852, 3348, 0)

        const val CRONE_TICKS = 200
        const val ARRIVE_SPOTANIM = "spotanim.smokepuff"
        const val ARRIVE_SOUND = "synth.smokepuff"
        const val SPOTANIM_HEIGHT = 124
    }
}
