package org.rsmod.content.quest.area.gnomevillage.treegnomevillage

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Tree Gnome Village.
 *
 * The stage lives in `varp.treequest` (endstate 9 from `dbrow.quest_treegnomevillage`). The
 * values matter to the client: both Elkoys (`npc.elkoy`, `npc.elkoy_village`) are varp-multi npcs
 * that grow their Follow option once the quest is under way, and the small spirit tree on the
 * battlefield (`loc.spirittree_small`) only offers Travel at [STAGE_COMPLETE].
 *
 * The tracker coordinates, the ballista and the orbs Bolren holds are varbits on `varp.279`, a
 * different varp from the stage, so the quest manager never wipes them. They are still kept in
 * quest attributes and mirrored by [syncVars] so `::resetquest` puts everything back. The village
 * spirit tree (`loc.ent`) is a multiloc on `varbit.bolren_got_orbs`: it grows its orbs and its
 * Travel option from [ORBS_RESTORED] upwards.
 */
@Singleton
class TreeGnomeVillageQuest : QuestScript(
    "quest_treegnomevillage",
    "varp.treequest",
    rewards {
        xp("stat.attack", ATTACK_XP)
        extra("A gnome amulet and the use of spirit trees")
    },
    ItemRewardDisplay(GNOME_AMULET),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
) {
    /** The x coordinate (1..4) the mad tracker hints at; rolled when the trackers are sent for. */
    val trackerX = quest.attribute(name = "TRACKER_X", default = 0)

    val knowsHeight = quest.attribute(name = "KNOWS_HEIGHT", default = false)
    val knowsY = quest.attribute(name = "KNOWS_Y", default = false)
    val knowsX = quest.attribute(name = "KNOWS_X", default = false)

    /** [BALLISTA_DAMAGED], [BALLISTA_REPAIRED] or [BALLISTA_FIRED]. */
    val ballistaState = quest.attribute(name = "BALLISTA", default = BALLISTA_DAMAGED)

    /** [ORBS_MISSING], [ORBS_ONE_RETURNED] or [ORBS_RESTORED]; drives the village spirit tree. */
    val orbsState = quest.attribute(name = "ORBS", default = ORBS_MISSING)

    /** Name of the last spirit tree destination, for the tree's Last-destination option. */
    val lastSpiritTree = quest.attribute(name = "LAST_SPIRIT_TREE", default = "")

    private var Player.trackerHeightVar by intVarBit("varbit.gnometracker_h")
    private var Player.trackerYVar by intVarBit("varbit.gnometracker_y")
    private var Player.trackerXVar by intVarBit("varbit.gnometracker_x")
    private var Player.ballistaVar by intVarBit("varbit.ballista")
    private var Player.orbsVar by intVarBit("varbit.bolren_got_orbs")

    override fun ScriptContext.init() {
        // Registered after the quest manager's own login hook.
        onPlayerLogin { syncVars(player) }
    }

    /** Re-applies the client-facing varbits from the persisted attributes. */
    fun syncVars(player: Player) {
        player.trackerHeightVar = if (knowsHeight.get(player)) 1 else 0
        player.trackerYVar = if (knowsY.get(player)) 1 else 0
        player.trackerXVar = if (knowsX.get(player)) 1 else 0
        player.ballistaVar = ballistaState.get(player)
        player.orbsVar = orbsState.get(player)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun hasAllCoordinates(player: Player): Boolean =
        knowsHeight.get(player) && knowsY.get(player) && knowsX.get(player)

    /** Moves the quest forward to [stage] if it is not already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>King Bolren</col> in the centre of the <col=800000>Tree Gnome " +
            "Village</col> maze, south of East Ardougne."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The tree gnomes are in trouble. <red>General Khazard</red>'s forces are hunting " +
                    "them to extinction, and the village is defenceless without the three " +
                    "<red>orbs of protection</red> on its spirit tree.",
            ) {}

            objective(
                "<red>King Bolren</red> asked me to go to the battlefield north of the maze and " +
                    "retrieve the orb Khazard's troops seized. <red>Commander Montai</red> " +
                    "should know how things stand.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "Commander Montai needs <red>six loads of normal logs</red> to strengthen the " +
                    "gnome battlements.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GATHERING_LOGS }
                custom(access.invTotal(access.inv, LOGS) >= LOGS_NEEDED, "I have enough logs.").strike()
            }

            objective(
                "I gave Montai the logs. He is organising his troops and wants me to speak to " +
                    "him again about the next phase of the attack.",
            ) {
                visibleWhen { stage(access.player) == STAGE_LOGS_GIVEN }
            }

            objective(
                "The <red>ballista</red> can breach the Khazard stronghold, but Montai needs its " +
                    "coordinates. Three <red>tracker gnomes</red> were sent into the battlefield " +
                    "to get them and have not returned.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FINDING_TRACKERS }
                attribute(knowsHeight, "The first tracker gave me the height coordinate.", finalise = false).preserveObjective().strike()
                attribute(knowsY, "The second tracker gave me the y coordinate.", finalise = false).preserveObjective().strike()
                attribute(knowsX, "The third tracker has lost his mind, but hinted at the x coordinate.", finalise = false).preserveObjective().strike()
            }

            objective(
                "I have the coordinates. I should fire the <red>ballista</red> in the south-west " +
                    "corner of the battlefield at the stronghold.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FINDING_TRACKERS && hasAllCoordinates(access.player) }
            }

            objective(
                "The ballista reduced the stronghold's front wall to rubble. I should climb over " +
                    "the <red>crumbled wall</red> and search the stronghold for the orb.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BREACHED }
            }

            objective("I found the <red>orb of protection</red>. I should take it back to <red>King Bolren</red>.") {
                visibleWhen { stage(access.player) == STAGE_HAS_ORB }
                hasItem("orb_of_protection", "I have the orb of protection.").strike()
            }

            objective(
                "While I was away Khazard's troops raided the village and took the other two " +
                    "orbs. A <red>Khazard warlord</red> carries them somewhere north of the " +
                    "stronghold, behind West Ardougne.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ORB_RETURNED }
            }

            objective("I defeated the warlord. I should return the <red>orbs of protection</red> to <red>King Bolren</red>.") {
                visibleWhen { stage(access.player) == STAGE_WARLORD_SLAIN }
                hasItem("orbs_of_protection", "I have the orbs of protection.").strike()
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Bolren asked me to recover the orb of protection that Khazard's troops " +
                    "seized on the battlefield. I brought Commander Montai logs for the gnome " +
                    "battlements, found the three tracker gnomes and fired the ballista into " +
                    "the Khazard stronghold, where I took the orb back from a chest.",
            )
            line(
                "Khazard's men raided the village while I was gone and stole the other two " +
                    "orbs. I hunted down the Khazard warlord behind West Ardougne and took them " +
                    "from his remains.",
            )
            line(
                "The gnomes held a ceremony to return the orbs to the spirit tree. King Bolren " +
                    "gave me a gnome amulet and the right to travel between the spirit trees.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_GATHERING_LOGS = 2
        const val STAGE_LOGS_GIVEN = 3
        const val STAGE_FINDING_TRACKERS = 4
        const val STAGE_BREACHED = 5
        const val STAGE_HAS_ORB = 6
        const val STAGE_ORB_RETURNED = 7
        const val STAGE_WARLORD_SLAIN = 8
        const val STAGE_COMPLETE = 9

        const val ATTACK_XP = 11450.0
        const val RECOMMENDED_COMBAT = 45
        const val LOGS_NEEDED = 6

        /** Values of `varbit.ballista`. */
        const val BALLISTA_DAMAGED = 0
        const val BALLISTA_REPAIRED = 1
        const val BALLISTA_FIRED = 2

        /** Values of `varbit.bolren_got_orbs`; the spirit tree shows its orbs from [ORBS_RESTORED]. */
        const val ORBS_MISSING = 0
        const val ORBS_ONE_RETURNED = 1
        const val ORBS_RESTORED = 2

        const val LOGS = "obj.logs"
        const val ORB = "obj.orb_of_protection"
        const val ORBS = "obj.orbs_of_protection"
        const val GNOME_AMULET = "obj.gnome_amulet"

        const val ELKOY_HEAD = "npc.elkoy_1op"
        const val BOLREN = "npc.king_bolren"
    }
}
