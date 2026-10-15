package org.rsmod.content.quest.area.burthorpe.deathplateau

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Death Plateau.
 *
 * The stage lives in `varp.death_equiproom` (314), which runs 0..80 from
 * `dbrow.quest_deathplateau`. The values in between are the ones RuneLite's quest helper reads
 * off the live game: 10 started, 20 sent to Harold, 30 Harold refused, 40 told to buy him a
 * drink, 50 ale given, 55 holding the IOU, 60 combination read, 70 equipment room open.
 *
 * Everything after the equipment room happens at stage 70 and is remembered in varbits on the
 * server-only `varp.deathplateau_state`: who has been asked, the certificate, the map, the
 * scouted path and what has been handed to Denulth.
 */
@Singleton
class DeathPlateauQuest : QuestScript(
    QUEST_KEY,
    "varp.death_equiproom",
    rewards {
        xp("stat.attack", ATTACK_XP)
        item(STEEL_CLAWS)
        extra("The ability to make claws")
    },
    ItemRewardDisplay(STEEL_CLAWS, zoom = 110),
    completionJingle = Quest.QUEST_COMPLETE_1_JINGLE,
) {
    override fun ScriptContext.init() {
        quest.onVarSync(::resetFlags)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    private fun resetFlags(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.dpSabaAsked = false
        player.dpTenzingAsked = false
        player.dpDunstanAsked = false
        player.dpCertificateIssued = false
        player.dpCertificateHanded = false
        player.dpMapDrawn = false
        player.dpPathScouted = false
        player.dpMapHanded = false
        player.dpCombinationHanded = false
        player.dpHaroldDrunk = false
        player.dpHaroldPurse = 0
    }

    override fun subTitle(): String =
        "talking to <col=800000>Denulth</col> in his tent in <col=800000>Burthorpe</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "<red>Denulth</red> needs another way up <red>Death Plateau</red> so the " +
                    "<red>Imperial Guard</red> can reach the troll camp, and the combination to " +
                    "the equipment room in <red>Burthorpe Castle</red>, which the guard on duty " +
                    "last night lost.",
            ) {
                visibleWhen { stage(access.player) in STAGE_STARTED until STAGE_ROOM_OPEN }
            }
            objective("<red>Eohric</red>, the head servant, might know who the guard was.") {
                visibleWhen { stage(access.player) == STAGE_STARTED }
            }
            objective(
                "The guard was <red>Harold</red>, who is staying at the <red>Toad and " +
                    "Chicken</red> in Burthorpe.",
            ) {
                visibleWhen { stage(access.player) == STAGE_FIND_HAROLD }
            }
            objective(
                "Harold won't talk about it. <red>Eohric</red> might know how to loosen his " +
                    "tongue.",
            ) {
                visibleWhen { stage(access.player) == STAGE_HAROLD_REFUSED }
            }
            objective(
                "Eohric says Harold has a weakness for drink and for gambling. I should buy " +
                    "him an <red>Asgarnian ale</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_BUY_DRINK }
            }
            objective(
                "Harold has lost the combination and won't say more. Perhaps I can win " +
                    "something off him at <red>dice</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GAMBLE }
            }
            objective("Harold paid me with an <red>IOU</red>. I should read it.") {
                visibleWhen { stage(access.player) == STAGE_IOU }
            }
            objective(
                "The IOU was written on the back of the <red>combination</red>. I need to put " +
                    "the <red>stone balls</red> on the mechanism outside the equipment room in " +
                    "the right order.",
            ) {
                visibleWhen { stage(access.player) == STAGE_COMBINATION }
            }
            objective(
                "I have opened the equipment room. Now I need a way up <red>Death Plateau</red> " +
                    "that the trolls aren't guarding.",
            ) {
                visibleWhen { stage(access.player) == STAGE_ROOM_OPEN }
                custom(
                    access.player.dpSabaAsked,
                    "<red>Saba</red>, the hermit in the cave north-west of Burthorpe, says a " +
                        "<red>Sherpa</red> nearby might know a way.",
                )
                custom(
                    access.player.dpTenzingAsked,
                    "<red>Tenzing</red> will show me the secret way for ten loaves of " +
                        "<red>bread</red>, ten cooked <red>trout</red> and his climbing boots " +
                        "back with new spikes from <red>Dunstan</red> in Burthorpe.",
                )
                custom(
                    access.player.dpDunstanAsked,
                    "Dunstan will add the spikes for an <red>iron bar</red> if his son is " +
                        "taken into the <red>Imperial Guard</red>.",
                )
                custom(
                    access.player.dpCertificateIssued,
                    "<red>Denulth</red> gave me a <red>certificate</red> for Dunstan's son.",
                )
                    .strike()
                custom(
                    access.player.dpCertificateHanded,
                    "Dunstan has his certificate.",
                )
                    .strike()
                custom(
                    access.player.dpMapDrawn,
                    "Tenzing gave me a <red>map</red> of the secret way. I should check the " +
                        "path behind his hut before telling <red>Denulth</red> about it.",
                )
                custom(
                    access.player.dpPathScouted,
                    "The trolls haven't found the secret way. I should tell <red>Denulth</red>.",
                )
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "Denulth of the Imperial Guard needed a way to sneak up Death Plateau and the " +
                    "combination to Burthorpe Castle's equipment room.",
            )
            line(
                "Harold, the guard who lost the combination, gambled it away to me at dice on " +
                    "the back of an IOU, and the stone balls opened the equipment room.",
            )
            line(
                "Saba sent me to Tenzing the Sherpa, who drew me a map of a secret path in " +
                    "exchange for his supplies and a pair of spiked boots from Dunstan, whose son " +
                    "Denulth took into the Imperial Guard.",
            )
            line("Denulth made me an honorary member of the Imperial Guard.")
        }

    companion object {
        const val QUEST_KEY = "quest_deathplateau"

        const val STAGE_STARTED = 10
        const val STAGE_FIND_HAROLD = 20
        const val STAGE_HAROLD_REFUSED = 30
        const val STAGE_BUY_DRINK = 40
        const val STAGE_GAMBLE = 50
        const val STAGE_IOU = 55
        const val STAGE_COMBINATION = 60
        const val STAGE_ROOM_OPEN = 70
        const val STAGE_COMPLETE = 80

        const val ATTACK_XP = 3000.0

        const val DENULTH = "npc.death_ig_commander"
        const val EOHRIC = "npc.death_headservant"
        const val HAROLD = "npc.death_guard_equiproom"
        const val SABA = "npc.death_hermit"
        const val TENZING = "npc.death_sherpa"
        const val DUNSTAN = "npc.death_smithy"
        const val TRAPPED_ARCHER = "npc.death_archer_trapped"

        const val IOU = "obj.death_iou"
        const val COMBINATION = "obj.death_combination"
        const val SECRET_WAY_MAP = "obj.death_secretwaymap"
        const val CLIMBING_BOOTS = "obj.death_climbingboots"
        const val SPIKED_BOOTS = "obj.death_spikedboots"
        const val CERTIFICATE = "obj.death_entrancecert"
        const val ASGARNIAN_ALE = "obj.asgarnian_ale"
        const val BLURBERRY_SPECIAL = "obj.blurberry_special"
        const val PREMADE_BLURBERRY_SPECIAL = "obj.premade_blurberry_special"
        const val BREAD = "obj.bread"
        const val TROUT = "obj.trout"
        const val IRON_BAR = "obj.iron_bar"
        const val COINS = "obj.coins"
        const val STEEL_CLAWS = "obj.steel_claws"

        const val SUPPLY_COUNT = 10
    }
}

var Player.dpSabaAsked by boolVarBit("varbit.deathplateau_saba_asked")
var Player.dpTenzingAsked by boolVarBit("varbit.deathplateau_tenzing_asked")
var Player.dpDunstanAsked by boolVarBit("varbit.deathplateau_dunstan_asked")
var Player.dpCertificateIssued by boolVarBit("varbit.deathplateau_certificate_issued")
var Player.dpCertificateHanded by boolVarBit("varbit.deathplateau_certificate_handed")
var Player.dpMapDrawn by boolVarBit("varbit.deathplateau_map_drawn")
var Player.dpPathScouted by boolVarBit("varbit.deathplateau_path_scouted")
var Player.dpMapHanded by boolVarBit("varbit.deathplateau_map_handed")
var Player.dpCombinationHanded by boolVarBit("varbit.deathplateau_combination_handed")

/** Set once Harold has had a Blurberry special: he can no longer count his dice. */
var Player.dpHaroldDrunk by boolVarBit("varbit.deathplateau_harold_drunk")

/** The coins Harold still has to lose before a large win bankrupts him into an IOU. */
var Player.dpHaroldPurse by intVarBit("varbit.deathplateau_harold_purse")

/** The coins on the dice table while a game is in play; lost if the player walks away. */
var Player.dpDiceStake by intVarBit("varbit.deathplateau_dice_stake")

/** The total of Harold's two dice in the game in play. */
var Player.dpDiceHarold by intVarBit("varbit.deathplateau_dice_harold")

/** Whether the player carries, wears or has banked [obj]. */
internal fun ProtectedAccess.owns(obj: String): Boolean =
    obj in player.inv || obj in player.worn || obj in bank
