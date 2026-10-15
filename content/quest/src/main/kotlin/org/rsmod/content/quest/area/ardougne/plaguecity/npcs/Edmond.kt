package org.rsmod.content.quest.area.ardougne.plaguecity.npcs

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcMode
import jakarta.inject.Inject
import org.rsmod.api.player.dialogue.Dialogue
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.repo.world.WorldRepository
import org.rsmod.api.script.onOpNpc1
import org.rsmod.content.quest.area.ardougne.GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.BUCKETS_NEEDED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.DWELLBERRIES
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PICTURE
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.PIPE_OPEN
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_DUG_TUNNEL
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_FREED_ELENA
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_CHECKED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_GRILL_REMOVED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_HAS_GAS_MASK
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_ROPE_TIED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_SOIL_SOFTENED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.STAGE_STARTED
import org.rsmod.content.quest.area.ardougne.plaguecity.PlagueCityQuest.Companion.TELEPORT_SCROLL
import org.rsmod.game.entity.Npc
import org.rsmod.game.loc.LocInfo
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext
import org.rsmod.routefinder.collision.CollisionFlagMap

/**
 * Edmond, the quest's starting npc. He is two multi-npcs sharing one dialogue: `npc.edmond_top`
 * stands in his garden until the tunnel is dug, at which point `npc.edmond_bottom` shows in the
 * sewer instead (both follow `varbit.plaguecity_can_see_edmond_up_top`). The ops are registered
 * on the base types because that is the id the event carries.
 */
class Edmond
@Inject
constructor(
    private val plagueCity: PlagueCityQuest,
    private val objRepo: ObjRepository,
    private val collision: CollisionFlagMap,
    private val locRepo: LocRepository,
    private val worldRepo: WorldRepository,
) : PluginScript() {

    private val quest
        get() = plagueCity.quest

    override fun ScriptContext.startup() {
        onOpNpc1(EDMOND_TOP) { startDialogue(it.npc) { edmond(it.npc) } }
        onOpNpc1(EDMOND_BOTTOM) { startDialogue(it.npc) { edmond(it.npc) } }
    }

    private suspend fun Dialogue.edmond(npc: Npc) {
        when (val stage = plagueCity.stage(player)) {
            0 -> notStarted()
            STAGE_STARTED -> dwellberries()
            STAGE_HAS_GAS_MASK -> digging()
            STAGE_SOIL_SOFTENED -> {
                chatPlayer(happy, "I've soaked the soil with water.")
                chatNpc(happy, "That's great, it should be soft enough to dig through now. There should be a spade nearby that you can use.")
            }
            STAGE_DUG_TUNNEL -> {
                chatNpc(neutral, "I think it's the pipe to the south that comes up in West Ardougne.")
                chatPlayer(neutral, "Alright I'll check it out.")
            }
            STAGE_GRILL_CHECKED -> {
                chatPlayer(worried, "Edmond, I can't get through to West Ardougne! There's an iron grill blocking my way, I can't pull it off alone.")
                chatNpc(neutral, "If you get some rope you could tie to the grill, then we could both pull it at the same time.")
            }
            STAGE_ROPE_TIED -> {
                chatPlayer(neutral, "I've tied a rope to the grill over there, will you help me pull it off?")
                chatNpc(happy, "Alright, let's get to it...")
                access.pullGrill(npc)
                chatNpc(neutral, "Once you're in the city look for a man called Jethick. He's an old friend of the family. Hopefully he can help you.")
                chatPlayer(happy, "Alright, thanks I will.")
            }
            in STAGE_GRILL_REMOVED until STAGE_FREED_ELENA -> searching()
            STAGE_FREED_ELENA -> reward()
            else -> afterQuest()
        }
    }

    private suspend fun Dialogue.notStarted() {
        chatPlayer(happy, "Hello old man.")
        chatNpc(worried, "Sorry, I can't stop to talk...")
        chatPlayer(quiz, "Why, what's wrong?")
        chatNpc(worried, "I've got to find my daughter. I pray that she is still alive...")
        when (
            choice2(
                "What's happened to her?", 1,
                "Well, good luck finding her.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "What's happened to her?")
                chatNpc(sad, "Elena's a healer. Three weeks ago she managed to cross the wall into West Ardougne. No one's allowed to cross the wall in case they spread the plague.")
                chatPlayer(quiz, "Plague?")
                chatNpc(neutral, "Not that long ago, West Ardougne was hit by a deadly plague. They had the wall built to try and keep it contained. No one is allowed to enter the city now apart from the mourners.")
                chatNpc(sad, "They say the plague is a horrible way to go... That's why Elena felt she had to go help. She said she'd be gone for a few days but we've heard nothing since.")
                chatPlayer(neutral, "Maybe I could help find her?")
                chatNpc(happy, "Really, would you? I've been working on a plan to get into West Ardougne, but I'm too old and tired to carry it through. But you on the other hand, you should have no problem.")
                when (choice2("Yes.", 1, "No.", 2, title = "Start the Plague City quest?")) {
                    1 -> {
                        quest.advanceQuestStage(access)
                        chatPlayer(quiz, "Where should I start?")
                        chatNpc(neutral, "If you're going into West Ardougne you'll need protection from the plague. My wife made a special gas mask for Elena with dwellberries rubbed into it.")
                        chatNpc(neutral, "They help to repel the plague apparently. We need some more though...")
                        chatPlayer(quiz, "Where can I find these dwellberries?")
                        chatNpc(neutral, "The only place I know of is McGrubor's Wood, just west of Seers' Village. The berries are bright blue so they're easy to spot.")
                        chatPlayer(happy, "Okay, I'll go and get some.")
                        chatNpc(neutral, "The foresters keep a close eye on it, but there is a back way in.")
                    }
                    2 -> {
                        chatPlayer(neutral, "On second thoughts, I'd better not.")
                        chatNpc(sad, "Well if you hear anything about Elena please tell me.")
                        chatPlayer(neutral, "I will. Goodbye.")
                    }
                }
            }
            2 -> chatPlayer(neutral, "Well, good luck finding her.")
        }
    }

    private suspend fun Dialogue.dwellberries() {
        chatPlayer(happy, "Hello Edmond.")
        chatNpc(quiz, "Have you got the dwellberries yet?")
        if (player.inv.contains(DWELLBERRIES)) {
            chatPlayer(happy, "Yes I've got some here.")
            chatNpc(happy, "Take them to my wife Alrena, she's inside.")
            return
        }
        chatPlayer(sad, "Sorry, I'm afraid not.")
        chatNpc(neutral, "You'll probably find them in McGrubor's Wood, just west of Seers' Village. The berries are bright blue so they're easy to spot.")
        chatPlayer(happy, "Okay, I'll go and get some.")
        chatNpc(neutral, "The foresters keep a close eye on it, but there is a back way in.")
    }

    private suspend fun Dialogue.digging() {
        if (!plagueCity.toldToDig.get(player)) {
            plagueCity.toldToDig.set(player, true)
            chatPlayer(happy, "Hi Edmond, I've got the gas mask now.")
            chatNpc(happy, "Good stuff, now for the digging. Beneath us are the Ardougne sewers. I've done some research, and I reckon you can use them to enter West Ardougne.")
            chatNpc(neutral, "I've already tried digging down to them but the soil is rock hard. You'll need to pour on several buckets of water to soften it up. I reckon four buckets should do it.")
            return
        }
        val remaining = BUCKETS_NEEDED - plagueCity.waterPoured.get(player)
        chatNpc(quiz, "How's it going?")
        val word =
            when (remaining) {
                1 -> "one more bucket"
                2 -> "two more buckets"
                3 -> "three more buckets"
                else -> "four more buckets"
            }
        chatPlayer(neutral, "I still need to pour $word of water on the soil.")
    }

    /**
     * The two of them heave on the rope until the grill comes away from the pipe. Both are walked
     * (and, if the corridor gets in the way, put) onto the tiles at the rope's end, facing the
     * grill, then the rope and grill locs play their lift animations before the varbit hides them.
     */
    private suspend fun ProtectedAccess.pullGrill(edmond: Npc) {
        // Edmond sits out his wander AI for the scene so he cannot drift off the rope.
        edmond.mode = NpcMode.None
        playerWalk(PLAYER_PULL_TILE)
        edmond.walk(EDMOND_PULL_TILE)
        var waited = 0
        while (waited < MAX_WALK_TICKS && (player.coords != PLAYER_PULL_TILE || edmond.coords != EDMOND_PULL_TILE)) {
            delay(1)
            waited++
        }
        if (player.coords != PLAYER_PULL_TILE) {
            teleport(PLAYER_PULL_TILE)
        }
        if (edmond.coords != EDMOND_PULL_TILE) {
            edmond.teleport(collision, EDMOND_PULL_TILE)
        }
        delay(1)
        faceSquare(GRILL_TILE)
        edmond.faceSquare(GRILL_TILE)
        say("1... 2... 3... Pull!")
        delay(2)
        anim(PULL_SEQ)
        edmond.anim(PULL_SEQ)
        for ((loc, seq) in ROPE_ANIMS) {
            ropeLoc(loc)?.let { locAnim(worldRepo, it, seq) }
        }
        delayBySeq(PULL_SEQ)
        soundSynth(GRILL_SOUND)
        ropeLoc(GRILL_LOC)?.let { locAnim(worldRepo, it, GRILL_SEQ) }
        delay(2)
        plagueCity.pipeState.set(player, PIPE_OPEN)
        plagueCity.syncVars(player)
        if (plagueCity.stage(player) == STAGE_ROPE_TIED) {
            plagueCity.advanceTo(this, STAGE_GRILL_REMOVED)
        }
        mesbox("The player and Edmond pull on the rope, opening the grill on the pipe.")
        edmond.mode = edmond.type.defaultMode
        edmond.walk(EDMOND_HOME_TILE)
    }

    /** The rope pieces and grill are varbit multilocs; the base loc is what sits in the map. */
    private fun ropeLoc(type: String): LocInfo? {
        val id = type.asRSCM(RSCMType.LOC)
        return ROPE_TILES.asSequence().flatMap { locRepo.findAll(it) }.firstOrNull { it.id == id }
    }

    private suspend fun Dialogue.searching() {
        chatPlayer(happy, "Hello.")
        chatNpc(quiz, "Have you found Elena yet?")
        if (plagueCity.pictureAsked.get(player) && !player.inv.contains(PICTURE)) {
            chatPlayer(neutral, "Not yet, it's a big city over there. Do you have a picture of Elena?")
            chatNpc(neutral, "There should be a picture of Elena in the house. Please find her quickly, I hope it's not too late.")
            return
        }
        chatPlayer(neutral, "Not yet, it's a big city over there.")
        chatNpc(worried, "I hope it's not too late.")
    }

    private suspend fun Dialogue.reward() {
        chatNpc(happy, "Thank you, thank you! Elena beat you back by minutes. Now I said I'd give you a reward. What can I give you as a reward I wonder? Here take this magic scroll, I have little use for it but it may help you.")
        access.invAddOrDrop(objRepo, TELEPORT_SCROLL)
        // The garden Edmond and Elena's own house show again once the varbits mirror the reset.
        plagueCity.edmondBelow.set(player, false)
        plagueCity.syncVars(player)
        plagueCity.advanceTo(access, quest.maxSteps)
        chatNpc(happy, "Now I'd recommend you go and see Elena. She'll want to thank you herself. She lives in the house opposite ours.")
    }

    private suspend fun Dialogue.afterQuest() {
        chatPlayer(happy, "Hello there.")
        chatNpc(happy, "Ah hello. Thank you again for rescuing my daughter.")
        if (plagueCity.readScroll.get(player)) {
            chatPlayer(happy, "No problem.")
            return
        }
        when (
            choice2(
                "Do you have any more of those scrolls?", 1,
                "No problem.", 2,
            )
        ) {
            1 -> {
                chatPlayer(quiz, "Do you have any more of those scrolls?")
                chatNpc(happy, "Yes, here you go.")
                access.invAddOrDrop(objRepo, TELEPORT_SCROLL)
            }
            2 -> chatPlayer(happy, "No problem.")
        }
    }

    companion object {
        /** The multi-npc in the garden; shows `npc.edmond` until he goes down the hole. */
        const val EDMOND_TOP = "npc.edmond_top"

        /** The multi-npc in the sewer under the garden; shows `npc.edmond` while he is down there. */
        const val EDMOND_BOTTOM = "npc.edmond_bottom"

        /** The chat head used for lines spoken away from him. */
        const val EDMOND_HEAD = "npc.edmond"

        /** The cache's own animations for this scene. */
        const val PULL_SEQ = "seq.pull_on_pipe"
        const val GRILL_SEQ = "seq.pipe_grill"
        const val GRILL_SOUND = "synth.grill_pulled"

        const val GRILL_LOC = "loc.plague_grill"
        val ROPE_ANIMS =
            listOf(
                "loc.plague_hanging_rope_multi" to "seq.hanging_rope_lift",
                "loc.plague_straight_rope_multi" to "seq.straight_rope_lift",
                "loc.plague_straight_rope_end_multi" to "seq.lift_and_pull",
            )

        /** The grill hangs at 9739; the rope runs north from it to its end at 9741. */
        val GRILL_TILE = CoordGrid(2514, 9739, 0)
        val ROPE_TILES = listOf(GRILL_TILE, CoordGrid(2514, 9740, 0), CoordGrid(2514, 9741, 0))

        /** The player takes the rope's end and Edmond stands right behind, both facing the grill. */
        val PLAYER_PULL_TILE = CoordGrid(2514, 9741, 0)
        val EDMOND_PULL_TILE = CoordGrid(2514, 9742, 0)
        val EDMOND_HOME_TILE = CoordGrid(2517, 9755, 0)
        const val MAX_WALK_TICKS = 20

        /** Kept for scripts that want to remind the player what Edmond insists on. */
        const val REQUIRED_MASK = GAS_MASK
    }
}
