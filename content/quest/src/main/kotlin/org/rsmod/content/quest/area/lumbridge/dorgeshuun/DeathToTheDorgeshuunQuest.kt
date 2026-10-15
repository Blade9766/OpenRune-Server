package org.rsmod.content.quest.area.lumbridge.dorgeshuun

import jakarta.inject.Singleton
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Death to the Dorgeshuun.
 *
 * The stage is `varbit.dttd_main` (bits 0-10 of `varp.dttd_base`, which also carries the tour
 * flags and the hideout/corpse/tears state), endstate [STAGE_COMPLETE]. The values are pinned by
 * the cache multis: the mill crates fill and the mill trapdoor opens at [STAGE_TEARS], the
 * delivery dwarf and the crate carriers stand at the mill for [STAGE_TEARS]..[STAGE_MILL], Juna's
 * copy of Zanik's body lies before her at [STAGE_TEARS] and Dartog and both ends of the mill
 * tunnel appear at [STAGE_COMPLETE]. The storeroom guard flags live on the transient
 * `varp.dttd_temp`, so a relog puts every guard back.
 *
 * Finishing the quest also moves `varbit.lost_tribe_quest` to 12, which is what gives Mistag and
 * Kazgar their Watermill travel option.
 */
@Singleton
class DeathToTheDorgeshuunQuest :
    QuestScript(
        QUEST_KEY,
        "varp.dttd_base",
        rewards {
            xp("stat.thieving", THIEVING_XP)
            xp("stat.ranged", RANGED_XP)
            scroll(
                "2,000 Thieving XP",
                "2,000 Ranged XP",
                "Access to Dorgesh-Kaan",
                "Use of the bone dagger and Dorgeshuun",
                "crossbow special attacks",
                "Access to the H.A.M. store room",
            )
        },
        ItemRewardDisplay(BONE_CROSSBOW, zoom = 250),
        questVarbit = "varbit.dttd_main",
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun canStart(player: Player): Boolean =
        QuestRequirements.hasCompleted(player, LOST_TRIBE) &&
            QuestRequirements.hasCompleted(player, GOBLIN_DIPLOMACY) &&
            QuestRequirements.hasCompleted(player, RUNE_MYSTERIES)

    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    fun complete(access: ProtectedAccess) {
        val player = access.player
        player.dttdZanikInCellar = false
        player.dttdHamTrapdoor = HAM_TRAPDOOR_OPEN
        player.dttdMillGuardsDead = 0
        quest.completeQuest(access)
        VarPlayerIntMapSetter.set(player, LOST_TRIBE_VARBIT, LOST_TRIBE_AFTER_DTTD)
    }

    /**
     * Keeps the multis in step with the stage after a jump or reset: Zanik leaves the cellar once
     * she is no longer due to follow the player, and nothing of the quest is left in the world
     * before it starts. Putting her back in the cellar is [ZanikFollower]'s job, since only it
     * knows whether she is out with the player.
     */
    fun syncVars(player: Player) {
        val stage = stage(player)
        if (stage == 0) {
            for (varbit in SUB_STATE_VARBITS) {
                if (player.vars[varbit] != 0) {
                    VarPlayerIntMapSetter.set(player, varbit, 0)
                }
            }
            return
        }
        if (stage !in CELLAR_STAGES && player.dttdZanikInCellar) {
            player.dttdZanikInCellar = false
        }
        if (stage != STAGE_ZANIK_DEAD && player.dttdZanikCorpse) {
            player.dttdZanikCorpse = false
        }
    }

    fun hasToured(player: Player): Boolean =
        player.dttdTourDuke &&
            player.dttdTourCitizens &&
            player.dttdTourSun &&
            player.dttdTourPriest &&
            player.dttdTourGoblins &&
            player.dttdTourShop

    fun wearsHamSet(player: Player): Boolean = HAM_SET.all { it in player.worn }

    /** How many whole H.A.M. outfits the player carries, counting what they wear. */
    fun hamSets(player: Player): Int =
        HAM_SET.minOf { piece -> player.inv.count(piece) + player.worn.count(piece) }

    fun handsFree(player: Player): Boolean =
        player.worn[WEAPON_SLOT] == null && player.worn[SHIELD_SLOT] == null

    override fun subTitle(): String =
        "talking to <col=800000>Mistag</col> in the <col=800000>Dorgeshuun mines</col>."

    override fun questLog(player: ProtectedAccess) =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            objective(
                "<red>Mistag</red> asked me to guide a Dorgeshuun agent around the surface. I need two " +
                    "full sets of <red>H.A.M. robes</red> - shirt, robe, hood, cloak, gloves, boots and " +
                    "logo - so we can visit the H.A.M. hideout in disguise. The agent will meet me in " +
                    "the <red>Lumbridge Castle cellar</red>.",
            ) {
                visibleWhen { stage in STAGE_STARTED..STAGE_MET_ZANIK }
                custom(hamSets(p) >= 2, "I have two sets of H.A.M. robes.")
            }
            objective(
                "The agent is <red>Zanik</red>. I should show her around <red>Lumbridge</red>: the " +
                    "<red>Duke</red>, the townsfolk, the church, the shops and the sky itself.",
            ) {
                visibleWhen { stage == STAGE_TOUR }
                custom(p.dttdTourDuke, "Zanik has met the Duke.")
                custom(p.dttdTourSun, "Zanik has seen the sun.")
                custom(p.dttdTourCitizens, "Zanik has met some of the townsfolk.")
                custom(p.dttdTourPriest, "Zanik has talked about the gods with Father Aereck.")
                custom(p.dttdTourGoblins, "Zanik has met the surface goblins.")
                custom(p.dttdTourShop, "Zanik has bought a souvenir in the general store.")
                custom(hasToured(p), "I should ask Zanik if she has seen enough of Lumbridge.")
            }
            objective(
                "Zanik told me about the mark on her forehead. Now we should put on our disguises and " +
                    "find out what is going on in the <red>H.A.M. hideout</red> west of Lumbridge.",
            ) {
                visibleWhen { stage == STAGE_HAM_HIDEOUT }
                custom(p.dttdHamJohanhus, "Johanhus Ulsbrecht said the cave goblins will be dealt with soon.")
                custom(p.dttdHamTrapdoor >= 1, "Zanik spotted a hidden trapdoor south of the stage.")
            }
            objective(
                "Below the hideout is a storeroom where the H.A.M. leaders make their plans. Zanik can " +
                    "shoot the guards if I make them turn their backs on her.",
            ) {
                visibleWhen { stage == STAGE_STOREROOMS }
            }
            objective(
                "The guards caught us listening at the meeting room door. Zanik is dead, but the mark on " +
                    "her forehead is glowing. I should take her body to <red>Juna</red> in the " +
                    "<red>Tears of Guthix</red> cave.",
            ) {
                visibleWhen { stage == STAGE_ZANIK_DEAD }
                hasItem(DEAD_ZANIK, "I am carrying Zanik's body.")
            }
            objective(
                "Juna says the Tears of Guthix can bring Zanik back. I must collect <red>twenty</red> " +
                    "blue tears with both hands free.",
            ) {
                visibleWhen { stage == STAGE_TEARS }
            }
            objective(
                "Zanik is alive again! She overheard the H.A.M. planning to flood Dorgesh-Kaan from the " +
                    "<red>Lumbridge water mill</red>. She will wait for me in the castle cellar.",
            ) {
                visibleWhen { stage in STAGE_REVIVED..STAGE_MILL }
            }
            objective(
                "We got into the water mill cellar. I have to stop <red>Sigmund</red> and his guards.",
            ) {
                visibleWhen { stage == STAGE_SHOWDOWN }
            }
            objective(
                "Sigmund escaped with a ring of life. I should smash the <red>drilling machine</red> " +
                    "and follow the tunnel south to the Dorgeshuun mines.",
            ) {
                visibleWhen { stage in STAGE_SIGMUND_FLED..STAGE_MACHINE_SMASHED }
                stageAtLeast(STAGE_MACHINE_SMASHED, "The machine is in pieces.")
            }
        }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line(
                "I guided Zanik, the first Dorgeshuun to visit the surface, around Lumbridge and into " +
                    "the H.A.M. hideout, where the guards caught us spying on their leaders and killed her.",
            )
            line(
                "Juna brought her back with the Tears of Guthix, and the mark on her forehead says she " +
                    "has a destiny to fulfil.",
            )
            line(
                "Together we smashed Sigmund's drilling machine before it could flood Dorgesh-Kaan, and " +
                    "the Dorgeshuun Council has opened the city to the people of Lumbridge.",
            )
        }

    companion object {
        const val QUEST_KEY = "quest_deathtothedorgeshuun"
        const val LOST_TRIBE = "quest_losttribe"
        const val GOBLIN_DIPLOMACY = "quest_goblindiplomacy"
        const val RUNE_MYSTERIES = "quest_runemysteries"
        const val TEARS_OF_GUTHIX = "quest_tearsofguthix"

        const val STAGE_STARTED = 1
        const val STAGE_MET_ZANIK = 2
        const val STAGE_TOUR = 3
        const val STAGE_HAM_HIDEOUT = 4
        const val STAGE_STOREROOMS = 5
        const val STAGE_ZANIK_DEAD = 6
        const val STAGE_TEARS = 7
        const val STAGE_REVIVED = 8
        const val STAGE_MILL = 9
        const val STAGE_SHOWDOWN = 10
        const val STAGE_SIGMUND_FLED = 11
        const val STAGE_MACHINE_SMASHED = 12
        const val STAGE_COMPLETE = 13

        /** The stages at which Zanik waits in the castle cellar whenever she isn't following. */
        val CELLAR_STAGES = setOf(STAGE_STARTED, STAGE_MET_ZANIK, STAGE_TOUR, STAGE_HAM_HIDEOUT, STAGE_STOREROOMS, STAGE_MILL)

        const val AGILITY_REQ = 23
        const val THIEVING_REQ = 23
        const val THIEVING_XP = 2000.0
        const val RANGED_XP = 2000.0

        const val LOST_TRIBE_VARBIT = "varbit.lost_tribe_quest"
        const val LOST_TRIBE_AFTER_DTTD = 12

        /** `varbit.dttd_ham_trapdoor_state` once the store room is open to the player for good. */
        const val HAM_TRAPDOOR_OPEN = 2

        const val WEAPON_SLOT = 3
        const val SHIELD_SLOT = 5

        const val ZANIK_CELLAR = "npc.dttd_zanik_cellar"
        const val ZANIK_FOLLOWER = "npc.dttd_zanik_follower"
        const val ZANIK_FOLLOWER_HAM = "npc.dttd_zanik_follower_ham"
        const val ZANIK_SHOWDOWN = "npc.dttd_zanik_follower_showdown"
        const val ZANIK_CHATHEAD = "npc.dttd_zanik_follower"
        const val ZANIK_HAM_CHATHEAD = "npc.dttd_zanik_follower_ham"
        const val JUNA_CHATHEAD = "npc.tog_juna_dummy"
        const val DARTOG = "npc.dttd_maze_guide"

        const val DEAD_ZANIK = "obj.dttd_dead_zanik"
        const val ZANIK_CRATE = "obj.dttd_zanik_crate"
        const val BONE_CROSSBOW = "obj.dttd_bone_crossbow"
        const val TORCH = "obj.torch_lit"

        val HAM_SET =
            listOf(
                "obj.ham_hood",
                "obj.ham_shirt",
                "obj.ham_robe",
                "obj.ham_badge",
                "obj.ham_cloak",
                "obj.ham_gloves",
                "obj.ham_boots",
            )

        /** Beside the ladder in the Lumbridge Castle cellar, where Zanik waits. */
        val CELLAR_WAIT = CoordGrid(3210, 9623, 0)

        /** Lumbridge and its fields, where Zanik is happy to be shown around. */
        fun inLumbridge(coords: CoordGrid): Boolean {
            val z = if (coords.z >= UNDERGROUND_OFFSET) coords.z - UNDERGROUND_OFFSET else coords.z
            return coords.x in 3136..3263 && z in 3136..3327
        }

        const val UNDERGROUND_OFFSET = 6400

        val SUB_STATE_VARBITS =
            listOf(
                "varbit.dttd_tour_duke",
                "varbit.dttd_tour_priest",
                "varbit.dttd_tour_goblins",
                "varbit.dttd_tour_citizens",
                "varbit.dttd_tour_sun",
                "varbit.dttd_zanik_in_cellar",
                "varbit.dttd_tour_shop",
                "varbit.dttd_tour_edge_warned",
                "varbit.dttd_tour_ham_civilians",
                "varbit.dttd_tour_ham_deacon",
                "varbit.dttd_tour_ham_johanhus",
                "varbit.dttd_ham_trapdoor_state",
                "varbit.dttd_zanik_corpse",
                "varbit.dttd_collecting_tears",
                "varbit.dttd_ham_sigmund_present",
                "varbit.dttd_mill_guards_dead",
            )
    }
}
