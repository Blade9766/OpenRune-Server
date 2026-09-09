package org.rsmod.content.quest.area.ardougne.biohazard

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Biohazard.
 *
 * The stage lives in `varp.biohazard` (endstate 16 from `dbrow.quest_biohazard`). Two locs are
 * varp-multilocs on it: the watchtower by the wall gets its `Investigate` option only at
 * [STAGE_MET_OMART], and the mourners' cauldron its `Inspect` option only at [STAGE_HQ_REFUSED],
 * so those two stage values are fixed by the cache.
 *
 * The handful of chat flags the cache keeps as varbits live on `varp.elenaquest_extra_bits`,
 * shared with Plague City; they are stored in quest attributes and mirrored by [syncVars].
 */
@Singleton
class BiohazardQuest : QuestScript(
    "quest_biohazard",
    "varp.biohazard",
    rewards {
        xp("stat.thieving", THIEVING_XP)
        extra("Access to the Combat Training Camp")
        extra("Free passage through the West Ardougne gate")
    },
    ItemRewardDisplay(DISTILLATOR),
) {
    /** Omart has explained the watchtower problem, so Jerico will lend his pigeons. */
    val metOmart = quest.attribute(name = "MET_OMART", default = false)

    /** Julie has said she wants her husband to see a priest. */
    val metJulie = quest.attribute(name = "MET_JULIE", default = false)

    /** Asyff has already handed over his free, tatty priest gown. */
    val freeClothes = quest.attribute(name = "FREE_CLOTHES", default = false)

    /** Elena has had the post-quest chat where the player keeps the king's secret. */
    val postQuestChat = quest.attribute(name = "POSTQUEST_CHAT", default = false)

    /** Bird feed has been scattered on the watchtower fence and the pigeons will go for it. */
    val birdFeedThrown = quest.attribute(name = "BIRD_FEED_THROWN", default = false)

    /** The vial each of the chemist's errand boys is carrying to Varrock, or empty. */
    val chancyVial = quest.attribute(name = "CHANCY_VIAL", default = "")
    val daVinciVial = quest.attribute(name = "DAVINCI_VIAL", default = "")
    val hopsVial = quest.attribute(name = "HOPS_VIAL", default = "")

    /** Hits landed on the Combat Training Camp dummies that still paid experience. */
    val dummyHits = quest.attribute(name = "DUMMY_HITS", default = 0)

    private var Player.metOmartVar by intVarBit("varbit.biohazard_met_omart")
    private var Player.metJulieVar by intVarBit("varbit.biohazard_met_julie")
    private var Player.freeClothesVar by intVarBit("varbit.biohazard_free_clothes")
    private var Player.postQuestChatVar by intVarBit("varbit.biohazard_postquest_chat")

    override fun ScriptContext.init() {
        onPlayerLogin { syncVars(player) }
    }

    fun syncVars(player: Player) {
        player.metOmartVar = if (metOmart.get(player)) 1 else 0
        player.metJulieVar = if (metJulie.get(player)) 1 else 0
        player.freeClothesVar = if (freeClothes.get(player)) 1 else 0
        player.postQuestChatVar = if (postQuestChat.get(player)) 1 else 0
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    /** Moves the quest forward to [stage] if it is not already there or past it. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /** Whether the player may still be let into West Ardougne over Omart's rope ladder. */
    fun ladderAvailable(player: Player): Boolean = stage(player) in STAGE_DISTRACTED until STAGE_GOT_SAMPLES

    override fun subTitle(): String =
        "talking to <col=800000>Elena</col> in her house in <col=800000>East Ardougne</col>, " +
            "just west of the log balance over the river. You must have completed " +
            "<col=800000>Plague City</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The mourners confiscated <red>Elena</red>'s <red>distillator</red>, which she " +
                    "needs to test her plague samples. It is being held in the " +
                    "<red>Mourner Headquarters</red> in West Ardougne, and the old tunnel " +
                    "has been filled in.",
            ) {}

            objective(
                "Elena's father's friend <red>Jerico</red>, in the house south of the northern " +
                    "bank, is in contact with West Ardougne and may know another way in.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }

            objective(
                "<red>Omart</red>, waiting by the wall at the south end of East Ardougne, has " +
                    "a rope ladder, but the guards on the <red>watchtower</red> would see. He " +
                    "suggested distracting them with <red>bird feed</red> and some of Jerico's " +
                    "<red>pigeons</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MET_OMART }
                hasItem("birdfeed", "I have some bird feed.").strike()
                hasItem("pigeons", "I have a cage of pigeons.").strike()
            }

            objective(
                "The guards are busy with the pigeons. I should get back to <red>Omart</red> " +
                    "quickly, with my <red>gas mask</red> on.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DISTRACTED }
            }

            objective(
                "I am over the wall. The <red>Mourner Headquarters</red> is the most " +
                    "north-eastern building in the city; <red>Kilron</red> by the south wall " +
                    "can get me back over.",
            ) {
                visibleWhen { stage(access.player) == STAGE_CROSSED_WALL }
            }

            objective(
                "Only mourners are allowed inside the headquarters. Perhaps there is another " +
                    "way in, or a way to make them need help from outside.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HQ_REFUSED }
            }

            objective(
                "I poisoned the mourners' stew with a rotten apple. Several of them are ill " +
                    "and they would let a <red>doctor</red> in. <red>Nurse Sarah</red> lives " +
                    "south-west of the chapel.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STEW_POISONED }
                hasItem("doctor_gown", "I have a medical gown.").strike()
            }

            objective(
                "The sickest mourner upstairs turned out to be guarding a caged storeroom. He " +
                    "dropped a <red>key</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_MOURNER_KILLED }
                hasItem("mournerkeytw", "I have the key.").strike()
            }

            objective(
                "I found Elena's <red>distillator</red>. I should take it back to her.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_DISTILLATOR }
                hasItem("distillator", "I have the distillator.").strike()
            }

            objective(
                "Elena's tests made no sense, so she wants her old mentor <red>Guidor</red> in " +
                    "south-east Varrock to look at a <red>plague sample</red> and three vials. " +
                    "First I need <red>touch paper</red> from the <red>chemist</red> in Rimmington.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_SAMPLES }
            }

            objective(
                "The guards search everyone going to Guidor's part of Varrock. The chemist's " +
                    "errand boys can carry the vials, but each will steal anything he can " +
                    "use: <red>Da Vinci</red> paints, <red>Chancy</red> gambles and " +
                    "<red>Hops</red> drinks.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GOT_TOUCH_PAPER }
                hasItem("touch_paper", "I have the touch paper.").strike()
                hasItem("plaguesample", "I have the plague sample.").strike()
            }

            objective(
                "Guidor found nothing in the sample. There is no plague; someone has been " +
                    "lying. I should tell <red>Elena</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GUIDOR_TESTED }
            }

            objective(
                "Elena thinks <red>King Lathas</red> must know what is really going on. He is " +
                    "in the throne room upstairs in East Ardougne castle.",
            ) {
                visibleWhen { stage(access.player) == STAGE_TOLD_ELENA }
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Elena needed her distillator back from the Mourner Headquarters. With bird " +
                    "feed and a cage of Jerico's pigeons I distracted the watchtower guards, " +
                    "went over the wall on Omart's rope ladder, poisoned the mourners' stew " +
                    "and walked in dressed as a doctor to take it back.",
            )
            line(
                "Her tests came back blank, so I smuggled her samples past the Varrock guards " +
                    "with the chemist's errand boys and, dressed as a priest, got Guidor to " +
                    "test them. There is no plague at all.",
            )
            line(
                "King Lathas admitted the plague is a hoax to keep his brother Tyras, corrupted " +
                    "by the Dark Lord in the west, sealed away. He asked me to keep the secret, " +
                    "opened his training camp to me and told the mourners to let me through " +
                    "the wall.",
            )
        }

    companion object {
        const val STAGE_STARTED = 1

        /** `loc.biowatchtower` shows its Investigate option at exactly this value. */
        const val STAGE_MET_OMART = 2
        const val STAGE_DISTRACTED = 3
        const val STAGE_CROSSED_WALL = 4

        /** `loc.mournercauldron` shows its Inspect option at exactly this value. */
        const val STAGE_HQ_REFUSED = 5
        const val STAGE_STEW_POISONED = 6
        const val STAGE_MOURNER_KILLED = 7
        const val STAGE_GOT_DISTILLATOR = 8
        const val STAGE_GOT_SAMPLES = 9
        const val STAGE_GOT_TOUCH_PAPER = 10
        const val STAGE_GUIDOR_TESTED = 11
        const val STAGE_TOLD_ELENA = 12

        const val THIEVING_XP = 1250.0
        const val PLAGUE_CITY = "quest_plaguecity"

        const val BIRD_FEED = "obj.birdfeed"
        const val PIGEON_CAGE_FULL = "obj.pigeons"
        const val PIGEON_CAGE_EMPTY = "obj.pigeoncage"
        const val ROTTEN_APPLE = "obj.rottenapples"
        const val MOURNER_KEY = "obj.mournerkeytw"
        const val DISTILLATOR = "obj.distillator"
        const val PLAGUE_SAMPLE = "obj.plaguesample"
        const val ETHENEA = "obj.ethenea"
        const val LIQUID_HONEY = "obj.liquid_honey"
        const val SULPHURIC_BROLINE = "obj.sulphuric_broline"
        const val TOUCH_PAPER = "obj.touch_paper"

        val VIALS = listOf(ETHENEA, LIQUID_HONEY, SULPHURIC_BROLINE)
    }
}
