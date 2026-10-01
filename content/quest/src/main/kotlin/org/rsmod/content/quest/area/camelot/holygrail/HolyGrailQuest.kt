package org.rsmod.content.quest.area.camelot.holygrail

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Holy Grail.
 *
 * The stage is the whole cache varp `varp.grail`, endstate 10 from `dbrow.quest_holygrail`:
 * - [STAGE_STARTED]: King Arthur has sent the player after the Grail, by way of Merlin.
 * - [STAGE_MERLIN]: Merlin has pointed the player at Entrana and at Galahad.
 * - [STAGE_ENTRANA]: the High Priest and the Crone have told of the Fisher King, the whistles and
 *   where to blow them.
 * - [STAGE_REALM_ENTERED]: the player has crossed into the dying Fisher Realm.
 * - [STAGE_HEIR_NEEDED]: the Fisher King has told of his failing health and his lost son.
 * - [STAGE_FEATHER]: King Arthur has named Sir Percival and handed over the magic gold feather.
 * - [STAGE_PERCIVAL_SENT]: Percival has been found and given a whistle home.
 * - [STAGE_REALM_RESTORED]: the player has seen the realm healed under King Percival.
 * - [STAGE_GRAIL_TAKEN]: the Grail has been lifted from the top of the castle's east tower.
 * - [STAGE_COMPLETE]: King Arthur has the Grail.
 *
 * The milestones that can happen in either order or that the stage cannot express live on
 * server-only varbits of `varp.holygrail_state` (see `HolyGrailVars`).
 */
@Singleton
class HolyGrailQuest :
    QuestScript(
        QUEST_KEY,
        "varp.grail",
        rewards {
            xp("stat.prayer", PRAYER_XP)
            xp("stat.defence", DEFENCE_XP)
            scroll("11,000 Prayer XP", "15,300 Defence XP", "Access to the Fisher Realm")
        },
        ItemRewardDisplay(HOLY_GRAIL, zoom = 180),
        completionJingle = Quest.QUEST_COMPLETE_3_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::clearWhenReset)
    }

    override fun subTitle(): String =
        "talking to <col=800000>King Arthur</col> in <col=800000>Camelot Castle</col>."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            val start =
                "King Arthur has asked me to find the Holy Grail. He says <red>Merlin</red> may " +
                    "know where to begin."
            if (stage == STAGE_STARTED) {
                line(start)
                line("Merlin keeps a workshop on the first floor of Camelot, east of the throne room.")
                return@questJournal
            }
            strike(start)
            val merlin =
                "Merlin says the monks of <red>Entrana</red> know more of the Grail, and that " +
                    "<red>Galahad</red>, who once saw it, lives west of McGrubor's Wood."
            if (stage == STAGE_MERLIN) {
                line(merlin)
                line("Entrana's monks sail from Port Sarim, and forbid weapons and armour on the island.")
                galahadLine(p)
                return@questJournal
            }
            strike(merlin)
            val entrana =
                "The High Priest named the <red>Fisher King</red>, whose sick land holds the Grail. " +
                    "A crone told me <red>magic whistles</red> are hidden in Draynor Manor, and " +
                    "must be blown beneath the tower on the peninsula north-west of " +
                    "<red>Brimhaven</red>."
            if (stage == STAGE_ENTRANA) {
                line(entrana)
                galahadLine(p)
                if (p.whistlesFound) {
                    line("I found the whistles. I should blow one beneath the Brimhaven tower.")
                } else {
                    line(
                        "The whistles lie in the top floor's southern room of Draynor Manor, but " +
                            "only those who carry something touched by the Grail can see them.",
                    )
                }
                return@questJournal
            }
            strike(entrana)
            val realm =
                "The whistle carried me into the <red>Fisher Realm</red>, a grey and dying land."
            if (stage == STAGE_REALM_ENTERED) {
                line(realm)
                when {
                    !p.titanDefeated ->
                        line(
                            "The <red>Black Knight Titan</red> blocks the bridge. He says only a " +
                                "king's sword can finish him: <red>Excalibur</red>.",
                        )
                    !p.castleEntered ->
                        line(
                            "The titan is beaten. A fisherman downriver may know how to reach the " +
                                "castle.",
                        )
                    else -> line("I am inside the castle. I should speak with the Fisher King upstairs.")
                }
                if (p.heardHealth != p.heardSon) {
                    line("The Fisher King has more to tell me. I should hear him out.")
                }
                return@questJournal
            }
            strike(realm)
            val heir =
                "The Fisher King is dying and his land with him. Only his lost <red>son</red>, " +
                    "brought home, can heal them both."
            if (stage == STAGE_HEIR_NEEDED) {
                line(heir)
                line("King Arthur knows every knight of note. He may know who the son is.")
                return@questJournal
            }
            strike(heir)
            val feather =
                "King Arthur named the son: <red>Sir Percival</red>, who rode off after the golden " +
                    "boots of Arkaneeses. Arthur gave me a <red>magic gold feather</red> that " +
                    "points towards them."
            if (stage == STAGE_FEATHER) {
                line(feather)
                when {
                    p.percivalFound ->
                        line(
                            "I found Percival in a sack in Goblin Village. He needs a magic " +
                                "whistle of his own, and I must keep one for myself.",
                        )
                    FEATHER !in p.inv -> line("I should keep the feather with me and blow on it.")
                    else -> line("Blowing on the feather shows which way to go.")
                }
                return@questJournal
            }
            strike(feather)
            val sent =
                "I found Percival in Goblin Village and gave him a whistle to take him home."
            if (stage == STAGE_PERCIVAL_SENT) {
                line(sent)
                line("I should blow my own whistle beneath the Brimhaven tower and follow him.")
                return@questJournal
            }
            strike(sent)
            val restored = "Percival is king, and the Fisher Realm is green and alive again."
            if (stage == STAGE_REALM_RESTORED) {
                line(restored)
                line("The Grail waits at the top of the castle's <red>eastern tower</red>.")
                return@questJournal
            }
            strike(restored)
            line("I have the <red>Holy Grail</red>. I should take it to King Arthur.")
            if (HOLY_GRAIL !in p.inv) {
                line("I no longer carry it. It can be found again in the Fisher King's tower.")
            }
        }

    private fun QuestJournalBuilder.galahadLine(p: Player) {
        if (p.napkinGiven) {
            strike("Galahad gave me a holy table napkin from the Grail's own table.")
        } else {
            line("I have not yet visited Galahad.")
        }
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "King Arthur sent me after the Holy Grail. Merlin, the monks of Entrana and " +
                    "Galahad set me on the trail of the Fisher King, and a holy napkin let me " +
                    "see the magic whistles hidden in Draynor Manor.",
            )
            line(
                "I defeated the Black Knight Titan with Excalibur and found the Fisher King dying " +
                    "for want of his son. That son was Sir Percival, whom I pulled from a sack in " +
                    "Goblin Village and sent home with a whistle.",
            )
            line(
                "With King Percival on the throne the realm bloomed again, and I brought the " +
                    "Holy Grail back to Camelot.",
            )
        }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isStarted(player: Player): Boolean = stage(player) >= STAGE_STARTED

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        if (stage > stage(access.player)) {
            quest.setQuestStage(access, stage)
        }
    }

    fun complete(access: ProtectedAccess) {
        quest.completeQuest(access)
    }

    fun meetsRequirements(player: Player, attack: Int): Boolean =
        attack >= REQUIRED_ATTACK && QuestRequirements.hasCompleted(player, MERLINS_CRYSTAL)

    /** Whistles the player still needs to own: one for themself and, until Percival has his, one for him. */
    fun whistlesNeeded(player: Player): Int =
        when {
            stage(player) == 0 -> 0
            stage(player) < STAGE_PERCIVAL_SENT -> 2
            else -> 1
        }

    /** Where blowing a whistle beneath the Brimhaven tower takes the player. */
    fun realmFor(player: Player): CoordGrid? =
        when {
            stage(player) < STAGE_ENTRANA -> null
            stage(player) >= STAGE_PERCIVAL_SENT -> RESTORED_ARRIVAL
            else -> DEAD_ARRIVAL
        }

    fun hint(player: Player): String {
        val stage = stage(player)
        return when {
            stage == 0 && !QuestRequirements.hasCompleted(player, MERLINS_CRYSTAL) ->
                "Holy Grail needs Merlin's Crystal completed first."
            stage == 0 -> "Speak to King Arthur in Camelot Castle about a new quest."
            stage == STAGE_STARTED ->
                "Find Merlin in his workshop: first floor of Camelot, through the door east of the throne room."
            stage == STAGE_MERLIN ->
                "Sail to Entrana from Port Sarim (the monks hold your weapons and armour) and speak to the High Priest."
            stage == STAGE_ENTRANA && !player.napkinGiven ->
                "Visit Galahad in his house west of McGrubor's Wood. He has something touched by the Grail."
            stage == STAGE_ENTRANA && !player.whistlesFound ->
                "Carry the holy table napkin into the southern room on the top floor of Draynor Manor."
            stage == STAGE_ENTRANA -> "Blow a magic whistle beneath the tower on the peninsula north-west of Brimhaven."
            stage == STAGE_REALM_ENTERED && !player.titanDefeated ->
                "Defeat the Black Knight Titan on the bridge. The last blow must come from Excalibur."
            stage == STAGE_REALM_ENTERED && !player.castleEntered ->
                "Follow the river south and ask the fisherman how to get into the castle."
            stage == STAGE_REALM_ENTERED -> "Climb the castle stairs and talk to the Fisher King about everything."
            stage == STAGE_HEIR_NEEDED -> "Tell King Arthur about the Fisher King's lost son."
            stage == STAGE_FEATHER && FEATHER !in player.inv -> "Ask King Arthur for another magic gold feather."
            stage == STAGE_FEATHER && !player.percivalFound ->
                "Blow on the feather. It points to Goblin Village; open the sacks in the house on its east side."
            stage == STAGE_FEATHER -> "You need two whistles: one to give Percival, one to keep. Then open the sacks again."
            stage == STAGE_PERCIVAL_SENT -> "Blow your whistle beneath the Brimhaven tower to follow Percival home."
            stage == STAGE_REALM_RESTORED -> "Climb to the top of the castle's eastern tower and take the Grail."
            stage == STAGE_GRAIL_TAKEN && HOLY_GRAIL !in player.inv ->
                "Fetch the Grail again from the top of the castle's eastern tower."
            stage == STAGE_GRAIL_TAKEN -> "Give the Holy Grail to King Arthur."
            else -> "You have completed this quest."
        }
    }

    private fun clearWhenReset(player: Player) {
        if (stage(player) != 0) {
            return
        }
        for (varbit in SUB_STATE_VARBITS) {
            if (player.vars[varbit] != 0) {
                VarPlayerIntMapSetter.set(player, varbit, 0)
            }
        }
    }

    companion object {
        const val QUEST_KEY = "quest_holygrail"
        const val MERLINS_CRYSTAL = "quest_merlinscrystal"

        const val STAGE_STARTED = 1
        const val STAGE_MERLIN = 2
        const val STAGE_ENTRANA = 3
        const val STAGE_REALM_ENTERED = 4
        const val STAGE_HEIR_NEEDED = 5
        const val STAGE_FEATHER = 6
        const val STAGE_PERCIVAL_SENT = 7
        const val STAGE_REALM_RESTORED = 8
        const val STAGE_GRAIL_TAKEN = 9
        const val STAGE_COMPLETE = 10

        const val REQUIRED_ATTACK = 20
        const val PRAYER_XP = 11_000.0
        const val DEFENCE_XP = 15_300.0

        const val KING_ARTHUR = "npc.king_arthur"
        const val MERLIN = "npc.merlin2"
        const val HIGH_PRIEST = "npc.high_priest_of_entrana"
        const val CRONE = "npc.grail_crone"
        const val GALAHAD = "npc.brother_galahad"
        const val TITAN = "npc.black_knight_titan"
        const val FISHERMAN = "npc.grail_fisherman"
        const val FISHER_KING = "npc.fisher_king"
        const val GRAIL_MAIDEN = "npc.grail_maiden"
        const val SIR_PERCIVAL = "npc.sir_percival"
        const val KING_PERCIVAL = "npc.king_percival"
        const val UNHAPPY_PEASANT = "npc.unhappy_peasant"
        const val HAPPY_PEASANT = "npc.happy_peasant"

        const val EXCALIBUR = "obj.excalibur"
        const val NAPKIN = "obj.holy_table_napkin"
        const val WHISTLE = "obj.magic_whistle"
        const val BELL = "obj.grail_bell"
        const val FEATHER = "obj.magic_golden_feather"
        const val HOLY_GRAIL = "obj.holy_grail"

        const val WORKSHOP_DOOR = "loc.merlinworkshop"
        const val WHISTLE_DOOR = "loc.whistledoor"
        const val PERCIVAL_SACKS = "loc.percy_sacks"

        /** Beneath the watchtower on the peninsula north-west of Brimhaven, where the realm is near. */
        val TOWER = CoordGrid(2741, 3234, 0)
        const val TOWER_RADIUS = 3

        /** East of the Titan's bridge in the dying realm. */
        val DEAD_ARRIVAL = CoordGrid(2802, 4716, 0)

        /** The same spot in the healed realm, which is its own copy of the map 128 tiles west. */
        val RESTORED_ARRIVAL = CoordGrid(2674, 4716, 0)

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.holygrail_napkin",
                "varbit.holygrail_whistles_found",
                "varbit.holygrail_titan_defeated",
                "varbit.holygrail_castle_entered",
                "varbit.holygrail_heard_health",
                "varbit.holygrail_heard_son",
                "varbit.holygrail_percival_found",
            )
    }
}
