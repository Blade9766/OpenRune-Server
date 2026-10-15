package org.rsmod.content.quest.area.burthorpe.trollstronghold

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Troll Stronghold.
 *
 * The stage lives in `varp.troll_quest` (317), 0..50 from `dbrow.quest_trollstronghold`: 10
 * started, 20 Dad beaten, 30 prison door unlocked, 40 Godric freed, 50 complete (the values
 * RuneLite's quest helper reads). The rest is the cache's own quest varbits on
 * `varp.troll_varbit`: Eadgar freed, the secret exit opened from inside, the stronghold entered,
 * Dad's challenge accepted, and whether Dad was finished off rather than spared.
 */
@Singleton
class TrollStrongholdQuest : QuestScript(
    QUEST_KEY,
    "varp.troll_quest",
    rewards {
        item(LAW_TALISMAN)
        extra("Access to Trollheim")
    },
    ItemRewardDisplay(LAW_TALISMAN, zoom = 110),
    completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
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

    /** Whether Dad has been beaten, by this quest's stage or because the quest is behind them. */
    fun hasBeatenDad(player: Player): Boolean = isComplete(player) || stage(player) >= STAGE_DAD_BEATEN

    private fun resetFlags(player: Player) {
        if (stage(player) != 0) {
            return
        }
        player.tsFreedEadgar = false
        player.tsOpenedBackExit = false
        player.tsEnteredStronghold = false
        player.tsAcceptedChallenge = false
        player.tsKilledDad = false
    }

    override fun subTitle(): String =
        "talking to <col=800000>Denulth</col> in his tent in <col=800000>Burthorpe</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            objective(
                "The trolls ambushed the Imperial Guard's raid and carried off <red>Godric</red>, " +
                    "Dunstan's son. I have to find a way into the <red>Troll Stronghold</red> " +
                    "and rescue him. The way up is past <red>Tenzing</red>'s hut and over the " +
                    "rocks, for which I will need <red>climbing boots</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_STARTED }
                custom(
                    access.player.tsAcceptedChallenge,
                    "<red>Dad</red>, the troll champion, won't let me through his arena until " +
                        "I beat him.",
                )
            }
            objective(
                "I have beaten <red>Dad</red>. The stronghold is north of his arena, past the " +
                    "cave and round <red>Trollheim</red>. The <red>troll generals</red> inside " +
                    "should carry the key to the prison.",
            ) {
                visibleWhen { stage(access.player) == STAGE_DAD_BEATEN }
            }
            objective(
                "I have unlocked the prison. The two sleeping guards, <red>Berry</red> and " +
                    "<red>Twig</red>, carry the keys to the cells of <red>Godric</red> and " +
                    "<red>Eadgar</red>.",
            ) {
                visibleWhen { stage(access.player) == STAGE_PRISON_OPEN }
                custom(access.player.tsFreedEadgar, "I have freed Eadgar.").strike()
            }
            objective(
                "<red>Godric</red> is free. I should tell his father <red>Dunstan</red>, the " +
                    "smith in north-east Burthorpe.",
            ) {
                visibleWhen { stage(access.player) == STAGE_GODRIC_FREED }
                custom(!access.player.tsFreedEadgar, "Eadgar is still locked in his cell.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "The trolls ambushed the Imperial Guard's raid on Death Plateau and captured " +
                    "Godric, Dunstan's son.",
            )
            if (access.player.tsKilledDad) {
                line("I climbed past Death Plateau and killed Dad, the troll champion, in his arena.")
            } else {
                line("I climbed past Death Plateau and beat Dad, the troll champion, in his arena.")
            }
            line(
                "A troll general gave up the prison key, the sleeping guards Berry and Twig the " +
                    "cell keys, and I freed Godric and Eadgar from the Troll Stronghold.",
            )
            line("Dunstan gave me his family heirloom, a law talisman.")
        }

    companion object {
        const val QUEST_KEY = "quest_trollstronghold"

        const val STAGE_STARTED = 10
        const val STAGE_DAD_BEATEN = 20
        const val STAGE_PRISON_OPEN = 30
        const val STAGE_GODRIC_FREED = 40
        const val STAGE_COMPLETE = 50

        const val AGILITY_REQ = 15
        const val THIEVING_REQ = 30

        const val DAD = "npc.troll_champion"
        const val EADGAR = "npc.troll_eadgar"
        const val GODRIC = "npc.troll_godric"
        const val TWIG = "npc.troll_prison_guard1"
        const val BERRY = "npc.troll_prison_guard2"
        const val TWIG_AWAKE = "npc.troll_prison_guard1_awake"
        const val BERRY_AWAKE = "npc.troll_prison_guard2_awake"

        const val PRISON_KEY = "obj.troll_key_prison"
        const val GODRIC_KEY = "obj.troll_key_godric"
        const val EADGAR_KEY = "obj.troll_key_eadgar"
        const val LAW_TALISMAN = "obj.law_talisman"
        const val CLIMBING_BOOTS = "obj.death_climbingboots"
    }
}

var Player.tsFreedEadgar by boolVarBit("varbit.troll_freed_eadgar")

/** Set once the secret exit behind the prison has been opened from inside; it then works both ways. */
var Player.tsOpenedBackExit by boolVarBit("varbit.troll_opened_back_exit")

var Player.tsEnteredStronghold by boolVarBit("varbit.troll_entered_stronghold")

/** Set while the player has taken up Dad's challenge and the fight is theirs to finish. */
var Player.tsAcceptedChallenge by boolVarBit("varbit.troll_accepted_challenge")

/** Set when the player chose to finish Dad off rather than let him go. */
var Player.tsKilledDad by boolVarBit("varbit.troll_to_the_death")
