package org.rsmod.content.quest.area.camelot.holygrail.npcs

import dev.openrune.types.ObjectServerType
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.generic.locs.passages.GenericPassageScript
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.MERLINS_CRYSTAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_ENTRANA
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_MERLIN
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_REALM_ENTERED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.WORKSHOP_DOOR
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Merlin, back in his Camelot workshop now that the crystal is shattered. He knows where the
 * trail starts - Entrana's monks and Galahad - and drops the first hint that the Grail's keeper
 * is guarded by something only Excalibur will finish.
 *
 * His workshop door only opens for those who freed him.
 */
class Merlin
@Inject
constructor(private val quest: HolyGrailQuest, private val passages: GenericPassageScript) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onOpNpc1(MERLIN) { startDialogue(it.npc) { merlin() } }
        onOpLoc1(WORKSHOP_DOOR) { workshopDoor(it.loc, it.type) }
    }

    private suspend fun ProtectedAccess.workshopDoor(door: BoundLocInfo, type: ObjectServerType) {
        if (!QuestRequirements.hasCompleted(player, MERLINS_CRYSTAL)) {
            mes("The door is locked. A muffled voice inside complains about draughts.")
            return
        }
        with(passages) { walkThrough(door, type) }
    }

    private suspend fun Dialogue.merlin() {
        val stage = quest.stage(player)
        when {
            quest.isComplete(player) -> {
                chatNpc(happy, "Ah, the Grail-finder. Arthur has not stopped smiling. It's unbearable.")
            }
            stage == STAGE_STARTED -> briefing()
            stage in STAGE_MERLIN until STAGE_REALM_ENTERED -> reminder()
            stage >= STAGE_REALM_ENTERED -> {
                chatNpc(quiz, "Back already? Did you find the Fisher King?")
                chatPlayer(neutral, "I did. His land is dying with him.")
                chatNpc(
                    sad,
                    "As I feared. A king and his country are one thing, in that realm. Heal one " +
                        "and you heal the other.",
                )
            }
            else -> {
                chatNpc(neutral, "Thank you again for the crystal. Now, unless you can help me, do mind the cauldron.")
            }
        }
    }

    private suspend fun Dialogue.briefing() {
        chatPlayer(neutral, "King Arthur has sent me to find the Holy Grail.")
        chatNpc(
            laugh,
            "Has he now? He sends somebody every spring. Very well, sit down, and don't touch " +
                "anything that glows.",
        )
        chatNpc(
            neutral,
            "The Grail is not lost in the way a purse is lost. It is kept, in a realm that sits " +
                "beside ours, by a king who fishes its river.",
        )
        chatNpc(
            neutral,
            "The monks of Entrana have studied the Grail for centuries. Their High Priest will " +
                "know how that realm is reached.",
        )
        chatNpc(
            neutral,
            "And seek out Galahad. He saw the Grail once, when he sat at the Round Table, and " +
                "has never been the same since. He lives quietly now, west of McGrubor's Wood.",
        )
        quest.advanceTo(access, STAGE_MERLIN)
        val words =
            choice2(
                "Is there anything else I should take?",
                true,
                "Thank you, Merlin.",
                false,
            )
        if (words) {
            chatPlayer(quiz, "Is there anything else I should take?")
            excaliburHint()
        } else {
            chatPlayer(happy, "Thank you, Merlin.")
        }
    }

    private suspend fun Dialogue.reminder() {
        chatNpc(quiz, "Still here? I said Entrana, and Galahad.")
        if (quest.stage(player) == STAGE_MERLIN) {
            chatNpc(
                neutral,
                "Mind, the monks will not let you onto their island with so much as a butter " +
                    "knife. Leave your arms with them at the dock.",
            )
        }
        if (quest.stage(player) >= STAGE_ENTRANA) {
            chatNpc(neutral, "Whistles in Draynor Manor? Of course there are. That house has everything in it.")
        }
        excaliburHint()
    }

    private suspend fun Dialogue.excaliburHint() {
        val carrying = EXCALIBUR in player.inv || EXCALIBUR in player.worn
        chatNpc(
            neutral,
            "Keep Excalibur by you. The Grail's realm is guarded, and its guardian answers only " +
                "to a true king's blade.",
        )
        if (!carrying) {
            chatNpc(
                neutral,
                "If you have mislaid it, the Lady of the Lake will part with it again. For a fee.",
            )
        }
    }
}
