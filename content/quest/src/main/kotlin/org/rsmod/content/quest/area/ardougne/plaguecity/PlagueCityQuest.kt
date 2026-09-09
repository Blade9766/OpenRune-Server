package org.rsmod.content.quest.area.ardougne.plaguecity

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.area.ardougne.GAS_MASK
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Plague City.
 *
 * The stage lives in `varp.elenaquest` (endstate 29 from `dbrow.quest_plaguecity`). The values
 * matter to the client: Elena's cell form (`npc.elenap`) is a varp-multi npc that only shows
 * while the varp is below [STAGE_FREED_ELENA].
 *
 * The quest's other client state (whether Edmond is down in the sewer, Elena being home, the dug
 * hole, the grill and the rope) is a set of varbits on `varp.elenaquest_extra_bits`, a different
 * varp from the stage, so they are not wiped by the quest manager. They are still kept in quest
 * attributes and mirrored by [syncVars] so `::resetquest` puts everything back.
 */
@Singleton
class PlagueCityQuest : QuestScript(
    "quest_plaguecity",
    "varp.elenaquest",
    rewards {
        xp("stat.mining", MINING_XP)
        extra("An Ardougne teleport scroll")
    },
    ItemRewardDisplay(GAS_MASK),
) {
    /** Edmond has followed the player down the hole and is waiting in the sewer. */
    val edmondBelow = quest.attribute(name = "EDMOND_BELOW", default = false)

    /** Elena has been freed and is back at her own house. */
    val elenaHome = quest.attribute(name = "ELENA_HOME", default = false)

    /** Buckets of water poured on the mud patch so far (0..[BUCKETS_NEEDED]). */
    val waterPoured = quest.attribute(name = "WATER_POURED", default = 0)

    /** [MUD_PATCH], [MUD_HOLE] or [MUD_FILLED]: what the patch behind Edmond's house shows. */
    val mudState = quest.attribute(name = "MUD_STATE", default = MUD_PATCH)

    /** The player has tried to pull the grill off the sewer pipe by hand. */
    val checkedGrill = quest.attribute(name = "CHECKED_GRILL", default = false)

    /** [PIPE_BLOCKED], [PIPE_ROPE_TIED] or [PIPE_OPEN]. */
    val pipeState = quest.attribute(name = "PIPE_STATE", default = PIPE_BLOCKED)

    /** Jethick asked what Elena looks like, so her parents can be asked for a picture. */
    val pictureAsked = quest.attribute(name = "PICTURE_ASKED", default = false)

    /** Elena told the player the cell key is stashed somewhere in the plague house. */
    val keyAsked = quest.attribute(name = "KEY_ASKED", default = false)

    /** Edmond has explained the digging plan, so later chats just ask how it is going. */
    val toldToDig = quest.attribute(name = "TOLD_TO_DIG", default = false)

    /** Jethick has already introduced himself. */
    val metJethick = quest.attribute(name = "MET_JETHICK", default = false)

    /** Bravek's scruffy recipe note has been handed over at least once. */
    val gotNote = quest.attribute(name = "GOT_NOTE", default = false)

    /** The teleport scroll has been read, unlocking the spell; further scrolls now explode. */
    val readScroll = quest.attribute(name = "READ_SCROLL", default = false)

    private var Player.edmondBelowVar by intVarBit("varbit.plaguecity_can_see_edmond_up_top")
    private var Player.elenaHomeVar by intVarBit("varbit.plaguecity_elena_at_home")
    private var Player.mudVar by intVarBit("varbit.plaguecity_dug_mud_pile")
    private var Player.checkedGrillVar by intVarBit("varbit.plaguecity_checked_grill")
    private var Player.pipeVar by intVarBit("varbit.plaguecity_pipe")
    private var Player.pictureAskedVar by intVarBit("varbit.plaguecity_picture_asked")
    private var Player.keyAskedVar by intVarBit("varbit.plaguecity_key_asked")

    override fun ScriptContext.init() {
        // Registered after the quest manager's own login hook.
        onPlayerLogin { syncVars(player) }
    }

    /** Re-applies the client-facing varbits from the persisted attributes. */
    fun syncVars(player: Player) {
        player.edmondBelowVar = if (edmondBelow.get(player)) 1 else 0
        player.elenaHomeVar = if (elenaHome.get(player)) 1 else 0
        // The cache varbit is a single bit: the hole either shows or it does not. "Filled in" is
        // remembered in the attribute (the spade says so) but renders as the plain mud patch.
        player.mudVar = if (mudState.get(player) == MUD_HOLE) 1 else 0
        player.checkedGrillVar = if (checkedGrill.get(player)) 1 else 0
        player.pipeVar = pipeState.get(player)
        player.pictureAskedVar = if (pictureAsked.get(player)) 1 else 0
        player.keyAskedVar = if (keyAsked.get(player)) 1 else 0
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] if it is not already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Edmond</col> behind his house in the north-west corner of " +
            "<col=800000>East Ardougne</col>, beside the wall."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Edmond</red>'s daughter <red>Elena</red>, a healer, crossed the wall into " +
                    "plague-stricken <red>West Ardougne</red> three weeks ago and has not been " +
                    "heard from since. I agreed to help find her.",
            ) {}

            objective(
                "<red>Alrena</red> can make me a gas mask if I bring her some " +
                    "<red>dwellberries</red>. They grow in McGrubor's Wood, west of Seers' Village.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                hasItem("dwellberries", "I have some dwellberries.").strike()
            }

            objective(
                "Alrena made me a <red>gas mask</red>. Edmond wants me to dig down into the " +
                    "sewers from the <red>mud patch</red> behind his house, but the soil needs " +
                    "softening with <red>four buckets of water</red> first.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAS_GAS_MASK }
            }

            objective(
                "The soil is soft enough now. I should dig into it with a <red>spade</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SOIL_SOFTENED }
            }

            objective(
                "I fell through into the sewers and Edmond followed me down. He thinks the " +
                    "<red>pipe to the south</red> comes up in West Ardougne.",
            ) {
                visibleWhen { stage(access.player) in STAGE_DUG_TUNNEL..STAGE_ROPE_TIED }
            }

            objective(
                "There is an iron <red>grill</red> over the pipe that I cannot pull off alone. " +
                    "If I tie a <red>rope</red> to it, Edmond and I could pull together.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GRILL_CHECKED }
                hasItem("rope", "I have a rope.").strike()
            }

            objective("I tied the rope to the grill. I should ask <red>Edmond</red> to help pull.") {
                visibleWhen { stage(access.player) == STAGE_ROPE_TIED }
            }

            objective(
                "We pulled the grill off. With my <red>gas mask</red> on I can climb the pipe " +
                    "into West Ardougne, where I should look for <red>Jethick</red>, an old " +
                    "friend of the family.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GRILL_REMOVED }
            }

            objective(
                "Jethick wants to see a <red>picture of Elena</red> before he can help. There " +
                    "should be one in Edmond's house.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GRILL_REMOVED && pictureAsked.get(access.player) }
                hasItem("elena_picture", "I have a picture of Elena.").strike()
            }

            objective(
                "Jethick says Elena was staying with the <red>Rehnison family</red> in the " +
                    "timbered house at the north end of town. He asked me to return a " +
                    "<red>book</red> he borrowed from them.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_BOOK }
            }

            objective(
                "The Rehnisons let me in. Their daughter <red>Milli</red>, upstairs, saw what " +
                    "happened to Elena.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BOOK_RETURNED }
            }

            objective(
                "Milli saw Elena dragged into the boarded-up <red>plague house</red> in the " +
                    "south-east corner of West Ardougne.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TALKED_MILLI }
            }

            objective(
                "The mourners will not let me into the plague house. I need clearance from the " +
                    "<red>head mourner</red> or <red>Bravek</red>, the city warder, at the " +
                    "civic office north of the town square.",
            ) {
                visibleWhen { stage(access.player) in STAGE_MOURNER_REFUSED..STAGE_CLERK_PERMISSION }
            }

            objective(
                "Bravek is too hungover to help. He gave me a note with his herbalist's " +
                    "<red>hangover cure</red>: chocolate dust mixed into a bucket of milk, " +
                    "then snape grass.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TALKED_BRAVEK }
                hasItem("hangover_cure", "I have a hangover cure.").strike()
            }

            objective(
                "Bravek is feeling much better. I should tell him the mourners will not " +
                    "listen to me.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CURED_BRAVEK }
            }

            objective(
                "Bravek gave me a <red>warrant</red> to enter the plague house.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_WARRANT }
                hasItem("warrant", "I have the warrant.").strike()
            }

            objective(
                "I sneaked into the plague house. Elena is locked in the cell downstairs and " +
                    "the <red>key</red> is stashed somewhere in the house.",
            ) {
                visibleWhen { stage(access.player) == STAGE_SNEAKED_IN }
                hasItem("elenakey", "I found a small key.").strike()
            }

            objective("I unlocked the cell door. I should tell <red>Elena</red> she is free to go.") {
                visibleWhen { stage(access.player) == STAGE_UNLOCKED_CELL }
            }

            objective(
                "I freed Elena. She said her father would reward me; I should climb down the " +
                    "<red>manhole</red> in the town square, go back up the mud pile and talk " +
                    "to <red>Edmond</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FREED_ELENA }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Edmond's daughter Elena went into plague-ridden West Ardougne to help the " +
                    "sick and never came back. With a gas mask from Alrena and a lot of water " +
                    "I dug down into the sewers, and Edmond and I pulled the grill off a pipe " +
                    "that came up inside the city.",
            )
            line(
                "Jethick pointed me to the Rehnisons, whose daughter Milli saw Elena dragged " +
                    "into the plague house. After curing the city warder Bravek's hangover he " +
                    "gave me a warrant, I slipped past the mourners, found the key and set " +
                    "Elena free.",
            )
            line("Edmond gave me a magic scroll that teaches the Ardougne Teleport spell.")
        }

    companion object {
        const val STAGE_STARTED = 1
        const val STAGE_HAS_GAS_MASK = 2
        const val STAGE_SOIL_SOFTENED = 3
        const val STAGE_DUG_TUNNEL = 4
        const val STAGE_GRILL_CHECKED = 5
        const val STAGE_ROPE_TIED = 6
        const val STAGE_GRILL_REMOVED = 7
        const val STAGE_GOT_BOOK = 8
        const val STAGE_BOOK_RETURNED = 9
        const val STAGE_TALKED_MILLI = 10
        const val STAGE_MOURNER_REFUSED = 11
        const val STAGE_CLERK_PERMISSION = 12
        const val STAGE_TALKED_BRAVEK = 13
        const val STAGE_CURED_BRAVEK = 14
        const val STAGE_GOT_WARRANT = 15
        const val STAGE_SNEAKED_IN = 16
        const val STAGE_UNLOCKED_CELL = 17

        /** `npc.elenap` (Elena in her cell) is hidden from this value upwards. */
        const val STAGE_FREED_ELENA = 28

        const val MINING_XP = 2425.0
        const val BUCKETS_NEEDED = 4

        /** States of the mud patch; only [MUD_HOLE] shows on `varbit.plaguecity_dug_mud_pile`. */
        const val MUD_PATCH = 0
        const val MUD_HOLE = 1
        const val MUD_FILLED = 2

        /** Values of `varbit.plaguecity_pipe`. */
        const val PIPE_BLOCKED = 0
        const val PIPE_ROPE_TIED = 1
        const val PIPE_OPEN = 2

        const val DWELLBERRIES = "obj.dwellberries"
        const val PICTURE = "obj.elena_picture"
        const val BOOK = "obj.turnip_book"
        const val SCRUFFY_NOTE = "obj.scruffy_note"
        const val HANGOVER_CURE = "obj.hangover_cure"
        const val WARRANT = "obj.warrant"
        const val SMALL_KEY = "obj.elenakey"
        const val TELEPORT_SCROLL = "obj.ardougnescroll"
    }
}
