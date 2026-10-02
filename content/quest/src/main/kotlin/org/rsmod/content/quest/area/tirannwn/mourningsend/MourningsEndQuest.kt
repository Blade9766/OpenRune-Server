package org.rsmod.content.quest.area.tirannwn.mourningsend

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.util.Wearpos
import jakarta.inject.Singleton
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.content.quest.manager.ItemRewardDisplay
import org.rsmod.content.quest.manager.Quest
import org.rsmod.content.quest.manager.QuestItemDrops
import org.rsmod.content.quest.manager.QuestJournalBuilder
import org.rsmod.content.quest.manager.QuestRequirements
import org.rsmod.content.quest.manager.QuestScript
import org.rsmod.content.quest.manager.rewards
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.ScriptContext

internal var Player.tegidChat by intVarBit("varbit.mourning_tegid_chat")
internal var Player.firstSilk by intVarBit("varbit.mourning_silk_1")
internal var Player.secondSilk by intVarBit("varbit.mourning_silk_2")
internal var Player.furGiven by intVarBit("varbit.mourning_fur")
internal var Player.trousersShown by intVarBit("varbit.mourning_trousers_chat")
internal var Player.trousersFixed by intVarBit("varbit.mourning_trousers_fixed")
internal var Player.gnomeState by intVarBit("varbit.mourning_gnome")
internal var Player.gunAmmo by intVarBit("varbit.mourning_gun_ammo")
internal var Player.elenaState by intVarBit("varbit.mourning_elena")
internal var Player.elunedChants by intVarBit("varbit.mourning_eluned_chant")
internal var Player.crystalToggle by intVarBit("varbit.mourning_teleport_destination_toggle")
internal var Player.hqApple by intVarBit("varbit.mourning_hq_apple")

/**
 * Mourning's End Part I.
 *
 * The stage is the whole of the cache varp `varp.mourning_quest` (endstate 9 from
 * `dbrow.quest_mourningsendpart1`); the client only distinguishes 0, 1-8 and 9, so the in-progress
 * values are this server's: 1 once Eluned has brought the player to Lletya with a teleport
 * crystal, 2 after Arianwyn's briefing, 3 when Essyllt has taken the mourner letter, 4 once the
 * gnome has fixed the device, 5 when Essyllt has given the food-supply task, 6 once Elena has
 * handed over the sieve, 7 when two different food stores are spoiled, 8 after Essyllt's report
 * on the mines, and 9 on Arianwyn's debrief.
 *
 * Everything else lives on the cache's own `varp.mourning_quest_bits`: Tegid's chat, each silk and
 * the fur handed to Oronwen, the trousers shown and mended, the gnome's progress (the rack and the
 * freed gnome are multis on it), one flag per sheep flock (the field sheep are multis on them, so
 * a re-dyed flock only looks re-dyed to the player who did it), the toad loaded in the device,
 * Elena's help, one flag per food store and Eluned's recharge count. The only server-only flag is
 * whether the player is carrying the rotten apple from behind the Mourner Headquarters, on
 * `varp.mourning_end_state`.
 *
 * Holding an item is never progress: lost clothing comes back from the Arandar mourner, a lost
 * device or key from Essyllt, and a lost sieve from Elena.
 */
@Singleton
class MourningsEndQuest :
    QuestScript(
        QUEST_KEY,
        "varp.mourning_quest",
        rewards {
            xp("stat.thieving", THIEVING_XP)
            xp("stat.hitpoints", HITPOINTS_XP)
            extra("Access to Lletya")
        },
        ItemRewardDisplay(GAS_MASK, zoom = 400),
        completionJingle = Quest.QUEST_COMPLETE_2_JINGLE,
    ) {
    override fun ScriptContext.init() {
        quest.onVarSync(::syncVars)
        QuestItemDrops.register(BLOODY_TOP) { needsTopDrop(it) }
        QuestItemDrops.register(LETTER) { needsLetterDrop(it) }
    }

    fun stage(player: Player): Int = quest.getQuestStage(player)

    fun isComplete(player: Player): Boolean = quest.isQuestCompleted(player)

    fun isStarted(player: Player): Boolean = stage(player) > 0

    /** Moves the stage forward to [stage]; never back, so a replayed step cannot undo progress. */
    fun advanceTo(access: ProtectedAccess, stage: Int) {
        val remaining = stage - stage(access.player)
        if (remaining > 0) {
            quest.advanceQuestStage(access, remaining)
        }
    }

    /**
     * Why [player] cannot start yet, or null. The quests go through the server's quest
     * requirement policy; Ranged and Thieving are checked unboosted, as the quest list marks them.
     */
    fun startProblem(player: Player): String? {
        val quests = listOf(ROVING_ELVES, BIG_CHOMPY, SHEEP_HERDER)
        if (quests.any { !QuestRequirements.hasCompleted(player, it) }) {
            return "You do not meet all of the requirements to start the Mourning's End Part I quest."
        }
        if (player.statBase(RANGED) < RANGED_REQ || player.statBase(THIEVING) < THIEVING_REQ) {
            return "You do not meet all of the requirements to start the Mourning's End Part I quest."
        }
        return null
    }

    /**
     * The quest counts as done for what it unlocks: really completed, or not started at all while
     * the quest requirement policy treats it as complete. A player part-way through always goes
     * by their own stage.
     */
    fun unlocked(player: Player): Boolean =
        isComplete(player) || (stage(player) == 0 && QuestRequirements.hasCompleted(player, QUEST_KEY))

    /** Lletya opens once the quest has started. */
    fun mayEnterLletya(player: Player): Boolean = isStarted(player) || unlocked(player)

    /** Infiltrating, or done with it: the Headquarters basement accepts a disguised player. */
    fun mayUseBasement(player: Player): Boolean = stage(player) >= STAGE_BRIEFED || unlocked(player)

    fun needsTopDrop(player: Player): Boolean =
        stage(player) in STAGE_BRIEFED until STAGE_ADMITTED &&
            !player.ownsAnywhere(BLOODY_TOP) && !player.ownsAnywhere(MOURNER_TOP)

    fun needsLetterDrop(player: Player): Boolean =
        stage(player) in STAGE_BRIEFED until STAGE_ADMITTED && !player.ownsAnywhere(LETTER)

    fun sheepDone(player: Player, flock: Flock): Boolean = player.vars[flock.varbit] == 1

    fun allSheepDone(player: Player): Boolean = Flock.entries.all { sheepDone(player, it) }

    fun storePoisoned(player: Player, store: FoodStore): Boolean = player.vars[store.varbit] == 1

    fun storesPoisoned(player: Player): Int = FoodStore.entries.count { storePoisoned(player, it) }

    fun syncVars(player: Player) {
        if (player.vars[ELUNED_VARBIT] != SOTE_SCOUT) {
            setVarBit(player, ELUNED_VARBIT, if (isStarted(player)) 1 else 0)
        }
        if (stage(player) != 0) {
            return
        }
        for (varbit in OWN_VARBITS) {
            setVarBit(player, varbit, 0)
        }
    }

    override fun subTitle(): String =
        "talking to <col=800000>Eluned</col> in the forest of <col=800000>Isafdar</col>, once " +
            "<col=800000>Roving Elves</col> is complete."

    override fun questLog(player: ProtectedAccess): String =
        questJournal(player) {
            val p = access.player
            val stage = stage(p)
            step(stage > STAGE_STARTED, "Eluned brought me to the elven village of Lletya, where Arianwyn wanted to speak with me.")
            if (stage >= STAGE_BRIEFED) {
                step(
                    stage > STAGE_BRIEFED,
                    "Arianwyn believes the mourners of West Ardougne are Lord Iorwerth's elves. He wants me to infiltrate them, perhaps by ambushing one crossing the Arandar pass.",
                )
            }
            if (stage == STAGE_BRIEFED) {
                disguiseLines(access)
            }
            if (stage >= STAGE_ADMITTED) {
                step(
                    stage > STAGE_ADMITTED,
                    "Essyllt, the head mourner, accepted my letter. He wants Farmer Brumty's sheep re-dyed with a broken gnomish device, so I should see the captive gnome.",
                )
            }
            if (stage == STAGE_ADMITTED) {
                gnomeLines(p)
            }
            if (stage >= STAGE_DEVICE_FIXED) {
                step(stage > STAGE_DEVICE_FIXED, "The gnome fixed the device. It fires dye-filled toads, which I can make with dyed ogre bellows.")
            }
            if (stage == STAGE_DEVICE_FIXED) {
                for (flock in Flock.entries) {
                    step(sheepDone(p, flock), "The ${flock.label} sheep need re-dyeing ${flock.label}.")
                }
                if (allSheepDone(p)) {
                    line("All four flocks are dyed. I should report to Essyllt.")
                }
            }
            if (stage >= STAGE_FOOD_TASK) {
                step(
                    stage > STAGE_SIEVE,
                    "Essyllt wants two of the three food stores in West Ardougne spoiled so that people seem to fall ill with the plague. The stew someone spoiled at the Headquarters may be the key.",
                )
            }
            if (stage == STAGE_FOOD_TASK) {
                line(if (p.elenaState == ELENA_AGREED) "Elena wants a rotten apple from behind the Mourner Headquarters." else "Elena may know what spoiled the stew.")
            }
            if (stage == STAGE_SIEVE) {
                line("Elena gave me a sieve. I must press rotten apples from the orchard north of the city, mix them with naphtha, sieve the mixture and dry it on a range, never a fire.")
                line("Food stores spoiled: ${storesPoisoned(p)} of 2.")
            }
            if (stage >= STAGE_STORES_DONE) {
                step(stage > STAGE_STORES_DONE, "I spoiled two food stores and should report to Essyllt.")
            }
            if (stage >= STAGE_REVEALED) {
                line("Essyllt told me the mourners are digging for a temple beneath the city. I should tell Arianwyn in Lletya.")
            }
        }

    private fun QuestJournalBuilder.disguiseLines(access: ProtectedAccess) {
        val p = access.player
        val top = if (access.ownsAnywhere(MOURNER_TOP)) "clean" else if (access.ownsAnywhere(BLOODY_TOP)) "still bloody" else "missing"
        val legs = if (access.ownsAnywhere(MOURNER_LEGS)) "mended" else if (access.ownsAnywhere(RIPPED_LEGS)) "still ripped" else "missing"
        line("Mourner top: $top. Mourner trousers: $legs.")
        if (p.tegidChat == 1 && !access.ownsAnywhere(MOURNER_TOP)) {
            line("Tegid the druid in Taverley won't share his soap.")
        }
        if (p.trousersShown == 1 && !access.ownsAnywhere(MOURNER_LEGS)) {
            line("Oronwen in Lletya needs two pieces of silk and some bear fur to mend the trousers.")
        }
        if (!access.ownsAnywhere(LETTER)) {
            line("I will need the mourner's letter of recommendation too.")
        }
    }

    private fun QuestJournalBuilder.gnomeLines(p: Player) {
        when {
            p.gnomeState >= GNOME_AGREED -> line("The gnome will help if I bring soft leather and magic logs, and let him have his toad crunchies.")
            p.gnomeState >= GNOME_SLIPPED -> line("The gnome let slip that he craves toad crunchies and hates having his feet tickled.")
            else -> line("The gnome is on a rack in the next room.")
        }
    }

    private fun QuestJournalBuilder.step(done: Boolean, text: String) {
        if (done) strike(text) else line(text)
    }

    override fun completedLog(player: ProtectedAccess): String =
        completionJournal(player) {
            line("I joined the mourners of West Ardougne in disguise and learnt that they are Lord Iorwerth's elves, keeping the plague alive with dyed sheep and spoiled food.")
            line("They are tunnelling beneath the city towards an ancient temple. Arianwyn fears it is the Temple of Light.")
        }

    companion object {
        const val QUEST_KEY = "quest_mourningsendpart1"
        const val ROVING_ELVES = "quest_rovingelves"
        const val BIG_CHOMPY = "quest_bigchompybirdhunting"
        const val SHEEP_HERDER = "quest_sheepherder"
        const val PLAGUE_CITY = "quest_plaguecity"
        const val REGICIDE = "quest_regicide"
        const val SONG_OF_THE_ELVES = "quest_songoftheelves"

        const val STAGE_STARTED = 1
        const val STAGE_BRIEFED = 2
        const val STAGE_ADMITTED = 3
        const val STAGE_DEVICE_FIXED = 4
        const val STAGE_FOOD_TASK = 5
        const val STAGE_SIEVE = 6
        const val STAGE_STORES_DONE = 7
        const val STAGE_REVEALED = 8
        const val STAGE_COMPLETE = 9

        const val RANGED = "stat.ranged"
        const val THIEVING = "stat.thieving"
        const val RANGED_REQ = 60
        const val THIEVING_REQ = 50
        const val THIEVING_XP = 40_000.0
        const val HITPOINTS_XP = 25_000.0

        const val GNOME_SLIPPED = 1
        const val GNOME_AGREED = 2
        const val GNOME_FREED = 7
        const val GNOME_ASKED_AMMO = 8

        const val ELENA_AGREED = 1
        const val ELENA_SIEVE = 2

        const val SOTE_SCOUT = 2
        const val ELUNED_VARBIT = "varbit.roving_female_woodelf"

        const val GAS_MASK = "obj.gasmask"
        const val BLOODY_TOP = "obj.mourning_bloody_mourner_top"
        const val MOURNER_TOP = "obj.mourning_mourner_top"
        const val RIPPED_LEGS = "obj.mourning_ripped_mourner_legs"
        const val MOURNER_LEGS = "obj.mourning_mourner_legs"
        const val GLOVES = "obj.mourning_mourner_gloves"
        const val BOOTS = "obj.mourning_mourner_boots"
        const val CLOAK = "obj.mourning_mourner_cloak"
        const val LETTER = "obj.mourning_mourner_message"
        const val SOAP = "obj.mourning_soap"
        const val BROKEN_DEVICE = "obj.mourning_paint_gun_broken"
        const val FIXED_DEVICE = "obj.mourning_paint_gun"
        const val GNOME_KEY = "obj.mourning_gnome_key"
        const val ROTTEN_APPLE = "obj.rottenapples"
        const val BARREL = "obj.regicide_barrel_empty"
        const val BARREL_OF_ROTTEN_APPLES = "obj.applebarrel_full"
        const val APPLE_BARREL = "obj.mourning_applebarrel_mush"
        const val NAPHTHA_APPLE_MIX = "obj.mourning_applebarrel_naphtha_mush"
        const val TOXIC_NAPHTHA = "obj.mourning_toxic_naphtha"
        const val SIEVE = "obj.mourning_sieve"
        const val TOXIC_POWDER = "obj.mourning_apple_toxin"
        const val BARREL_OF_NAPHTHA = "obj.regicide_barrel_naphtha"
        const val CRYSTAL_SEED = "obj.elf_crystal_tiny"
        const val CRYSTAL_4 = "obj.mourning_teleport_crystal_4"
        const val CRYSTAL_3 = "obj.mourning_teleport_crystal_3"
        const val CRYSTAL_2 = "obj.mourning_teleport_crystal_2"
        const val CRYSTAL_1 = "obj.mourning_teleport_crystal_1"
        const val BELLOWS = "obj.empty_ogre_bellows"
        const val SILK = "obj.silk"
        const val BEAR_FUR = "obj.fur"
        const val LEATHER = "obj.leather"
        const val MAGIC_LOGS = "obj.magic_logs"
        const val FEATHER = "obj.feather"
        const val TOAD_CRUNCHIES = "obj.toad_crunchies"
        const val PREMADE_TOAD_CRUNCHIES = "obj.premade_toad_crunchies"
        const val BUCKET_OF_WATER = "obj.bucket_water"
        const val BUCKET = "obj.bucket_empty"
        const val COINS = "obj.coins"

        val CRUNCHIES = listOf(TOAD_CRUNCHIES, PREMADE_TOAD_CRUNCHIES)
        val CRYSTALS = listOf(CRYSTAL_4, CRYSTAL_3, CRYSTAL_2, CRYSTAL_1)

        /** The six pieces of the mourner disguise, by the worn slot each occupies. */
        val DISGUISE =
            listOf(
                Wearpos.Hat to GAS_MASK,
                Wearpos.Torso to MOURNER_TOP,
                Wearpos.Legs to MOURNER_LEGS,
                Wearpos.Hands to GLOVES,
                Wearpos.Feet to BOOTS,
                Wearpos.Back to CLOAK,
            )

        val LLETYA_ARRIVAL = CoordGrid(2351, 3172, 0)
        val LLETYA_TELEPORT = CoordGrid(2328, 3170, 0)

        val OWN_VARBITS =
            listOf(
                "varbit.mourning_tegid_chat",
                "varbit.mourning_silk_1",
                "varbit.mourning_silk_2",
                "varbit.mourning_fur",
                "varbit.mourning_trousers_chat",
                "varbit.mourning_trousers_fixed",
                "varbit.mourning_gnome",
                "varbit.mourning_sheep_red",
                "varbit.mourning_sheep_green",
                "varbit.mourning_sheep_blue",
                "varbit.mourning_sheep_yellow",
                "varbit.mourning_gun_ammo",
                "varbit.mourning_elena",
                "varbit.mourning_food_poison1",
                "varbit.mourning_food_poison2",
                "varbit.mourning_food_poison3",
                "varbit.mourning_hq_apple",
            )

        fun setVarBit(player: Player, varbit: String, value: Int) {
            if (player.vars[varbit] != value) {
                VarPlayerIntMapSetter.set(player, varbit, value)
            }
        }
    }
}

/**
 * A flock of Farmer Brumty's sheep. [field] is the grazing multinpc on [varbit]: 0 shows the
 * sickly Sheep Herder sheep, 1 the freshly dyed one. [ammo] is the `varbit.mourning_gun_ammo`
 * value of the matching toad, in the order of the toad objs and of the client's toad models.
 */
enum class Flock(
    val label: String,
    val varbit: String,
    val field: String,
    val ammo: Int,
    val dye: String,
    val bellows: String,
    val toad: String,
    val travel: String,
    val impact: String,
) {
    BLUE("blue", "varbit.mourning_sheep_blue", "npc.plaguesheep_3", 1, "obj.bluedye", "obj.mourning_ogre_bellows_blue", "obj.mourning_bloated_toad_blue", "spotanim.toad_cannon_travel_blue", "spotanim.toad_cannon_impact_blue"),
    RED("red", "varbit.mourning_sheep_red", "npc.plaguesheep_1", 2, "obj.reddye", "obj.mourning_ogre_bellows_red", "obj.mourning_bloated_toad_red", "spotanim.toad_cannon_travel_red", "spotanim.toad_cannon_impact_red"),
    YELLOW("yellow", "varbit.mourning_sheep_yellow", "npc.plaguesheep_4", 3, "obj.yellowdye", "obj.mourning_ogre_bellows_yellow", "obj.mourning_bloated_toad_yellow", "spotanim.toad_cannon_travel_yellow", "spotanim.toad_cannon_impact_yellow"),
    GREEN("green", "varbit.mourning_sheep_green", "npc.plaguesheep_2", 4, "obj.greendye", "obj.mourning_ogre_bellows_green", "obj.mourning_bloated_toad_green", "spotanim.toad_cannon_travel_green", "spotanim.toad_cannon_impact_green"),
    ;

    companion object {
        fun ofAmmo(ammo: Int): Flock? = entries.firstOrNull { it.ammo == ammo }

        fun ofToad(obj: String): Flock? = entries.firstOrNull { it.toad == obj }

        fun ofField(npcId: Int): Flock? = entries.firstOrNull { it.field.asRSCM(RSCMType.NPC) == npcId }
    }
}

/**
 * The three food stores of West Ardougne. Each is a group of grain-sack multilocs (on Song of the
 * Elves' `sote_west_food` varbits, which only burn them later); [locs] are their base types and
 * [varbit] this quest's flag for the store.
 */
enum class FoodStore(val label: String, val varbit: String, val locs: List<String>) {
    CIVIC_OFFICE("civic office", "varbit.mourning_food_poison1", listOf("loc.mourning_sack_full1", "loc.mourning_sacks1")),
    CHURCH("church", "varbit.mourning_food_poison2", listOf("loc.mourning_sack_full2", "loc.mourning_sacks2")),
    GENERAL_STORE("general store", "varbit.mourning_food_poison3", listOf("loc.mourning_sack_full3", "loc.mourning_sacks3")),
    ;

    companion object {
        fun ofLoc(locId: Int): FoodStore? = entries.firstOrNull { store -> store.locs.any { it.asRSCM(RSCMType.LOC) == locId } }
    }
}

internal fun Player.ownsAnywhere(obj: String): Boolean =
    inv.contains(obj) || worn.contains(obj) || invMap.getOrPut("inv.bank").contains(obj)

internal fun ProtectedAccess.ownsAnywhere(obj: String): Boolean = player.ownsAnywhere(obj)

/** Every disguise piece worn in its own slot; whatever else is worn does not matter. */
internal fun Player.wearsDisguise(): Boolean =
    MourningsEndQuest.DISGUISE.all { (pos, obj) -> worn[pos.slot]?.id == obj.asRSCM(RSCMType.OBJ) }

internal fun Player.ownsCrystal(): Boolean =
    (MourningsEndQuest.CRYSTALS + MourningsEndQuest.CRYSTAL_SEED).any { ownsAnywhere(it) }

/**
 * Swaps [take] for [give] in the inventory as one transaction: either every item changes hands or
 * none does, so a full pack never eats an ingredient.
 */
internal fun ProtectedAccess.swap(take: List<Pair<String, Int>>, give: List<Pair<String, Int>>): Boolean =
    player.invTransaction(inv) {
        val pack = select(inv)
        for ((obj, count) in take) {
            delete {
                from = pack
                this.obj = obj.asRSCM()
                strictCount = count
            }
        }
        for ((obj, count) in give) {
            insert {
                into = pack
                this.obj = obj.asRSCM()
                strictCount = count
            }
        }
    }.success
