package org.rsmod.content.quest.area.mortton.shades.catacombs

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.npc.interact.AiPlayerInteractions
import org.rsmod.api.npc.isInCombat
import org.rsmod.api.npc.opPlayer2
import org.rsmod.api.npc.owner.assignSpawnOwner
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.random.GameRandom
import org.rsmod.api.repo.loc.LocRepository
import org.rsmod.api.repo.npc.NpcRepository
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onAiTimer
import org.rsmod.api.script.onOpLoc1
import org.rsmod.game.MapClock
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.PlayerList
import org.rsmod.game.loc.BoundLocInfo
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * The chests of the Shade Catacombs. Each opens only for the key of its own metal and loop colour,
 * which crumbles in the lock. Every chest pays out swamp paste and coins, one roll on its metal's
 * table, and a 1/63 chance of that metal's coffin locks; gold chests may also hold a piece of
 * zealot's robes. One chest in twenty wakes an Undead Zealot.
 */
class ShadeChests
@Inject
constructor(
    private val random: GameRandom,
    private val locRepo: LocRepository,
    private val objRepo: ObjRepository,
    private val npcRepo: NpcRepository,
    private val aiInteractions: AiPlayerInteractions,
    private val playerList: PlayerList,
    private val clock: MapClock,
) : PluginScript() {

    override fun ScriptContext.startup() {
        for (metal in ShadeMetal.entries) {
            for (trim in ShadeTrim.entries) {
                onOpLoc1(chest(metal, trim)) { open(it.loc, metal, trim) }
            }
        }
        for (zealot in ZEALOTS) {
            onAiTimer(zealot) { npc.pursueOwner() }
        }
    }

    private suspend fun ProtectedAccess.open(chest: BoundLocInfo, metal: ShadeMetal, trim: ShadeTrim) {
        val key = ShadeKeys.key(metal, trim)
        if (key !in inv) {
            mes("This chest is locked. It needs a ${metal.key} key with a ${trim.colour} loop.")
            return
        }
        faceSquare(chest.adjustedCentre)
        anim(OPEN_SEQ)
        soundSynth(UNLOCK_SOUND)
        delay(1)
        invDel(inv, key)
        mes("You unlock the chest and the key crumbles to dust in the lock.")
        val table = LOOT.getValue(metal)
        give(SWAMP_PASTE, random.of(table.paste))
        give(COINS, random.of(1..table.maxCoins))
        val roll = pick(table.rolls)
        give(roll.obj, random.of(roll.min, roll.max), noted = roll.noted)
        if (random.of(LOCK_CHANCE) == 0) {
            give("obj.shades_lock_${metal.key}", 1)
        }
        if (metal == ShadeMetal.Gold && random.of(ZEALOT_PIECE_CHANCE) == 0) {
            give(random.pick(ZEALOT_ROBES), 1)
        }
        if (random.of(ZEALOT_CHANCE) == 0) {
            wakeZealot()
        }
        val opened = openChest(metal, trim)
        locRepo.change(chest, opened, OPEN_TICKS)
    }

    private fun ProtectedAccess.give(obj: String, count: Int, noted: Boolean = false) {
        if (count <= 0) {
            return
        }
        if (!invAdd(inv, obj, count, strict = false, cert = noted).success) {
            val type = if (noted) RSCM.getReverseMapping(RSCMType.OBJ, ocCert(obj).id) else obj
            objRepo.add(type, coords, GROUND_TICKS, player, count)
            mes("Your inventory is too full, so the reward falls to the ground.")
        }
    }

    private fun ProtectedAccess.wakeZealot() {
        val type = requireNotNull(ServerCacheManager.getNpc(random.pick(ZEALOTS).asRSCM(RSCMType.NPC)))
        val zealot = Npc(type, random.of(coords, 1))
        npcRepo.add(zealot, ZEALOT_TICKS)
        zealot.respawns = false
        zealot.assignSpawnOwner(player, clock.cycle)
        zealot.opPlayer2(player, aiInteractions)
        zealot.aiTimer(PURSUE_INTERVAL)
        mes("An Undead Zealot rises to defend the chest!")
    }

    /** In single-way combat the zealot waits out a fight the opener is already in, then joins. */
    private fun Npc.pursueOwner() {
        aiTimer(PURSUE_INTERVAL)
        if (isInCombat()) {
            return
        }
        val owner = spawnOwner.resolve(playerList) ?: return
        if (owner.coords.chebyshevDistance(coords) <= PURSUE_RANGE) {
            opPlayer2(owner, aiInteractions)
        }
    }

    private fun pick(rolls: List<Loot>): Loot {
        var roll = random.of(rolls.sumOf { it.weight })
        for (loot in rolls) {
            roll -= loot.weight
            if (roll < 0) return loot
        }
        return rolls.last()
    }

    private data class Loot(
        val obj: String,
        val weight: Int,
        val min: Int = 1,
        val max: Int = min,
        val noted: Boolean = false,
    )

    private data class ChestTable(val paste: IntRange, val maxCoins: Int, val rolls: List<Loot>)

    private companion object {
        const val OPEN_SEQ = "seq.human_openchest"
        const val UNLOCK_SOUND = "synth.chest_open"
        const val COINS = "obj.coins"
        const val SWAMP_PASTE = "obj.swamppaste"

        const val LOCK_CHANCE = 63
        const val ZEALOT_PIECE_CHANCE = 32
        const val ZEALOT_CHANCE = 20
        const val ZEALOT_TICKS = 200
        const val PURSUE_INTERVAL = 2
        const val PURSUE_RANGE = 10
        const val OPEN_TICKS = 5
        const val GROUND_TICKS = 200

        val ZEALOTS = listOf("npc.shades_undead_zealot_1", "npc.shades_undead_zealot_2")
        val ZEALOT_ROBES =
            listOf(
                "obj.shades_prayer_helm",
                "obj.shades_prayer_top",
                "obj.shades_prayer_bottom",
                "obj.shades_prayer_boots",
            )

        val ShadeTrim.colour: String
            get() = if (this == ShadeTrim.BloodRed) "blood red" else key

        fun chest(metal: ShadeMetal, trim: ShadeTrim): String = "loc.shadechest_${metal.key}_${trim.key}"

        /** The bronze blood red chest's open form predates the naming of the others. */
        fun openChest(metal: ShadeMetal, trim: ShadeTrim): String =
            if (metal == ShadeMetal.Bronze && trim == ShadeTrim.BloodRed) {
                "loc.shadelairchestopen"
            } else {
                "loc.shadechestopen_${metal.key}_${trim.key}"
            }

        /** Weights from the OSRS wiki drop logs of each metal's red-loop chest. */
        val LOOT =
            mapOf(
                ShadeMetal.Bronze to
                    ChestTable(
                        5..10,
                        270,
                        listOf(
                            Loot("obj.black_dagger_p", 5),
                            Loot("obj.steel_axe", 6),
                            Loot("obj.mithril_dagger_p", 4),
                            Loot("obj.magic_staff", 6),
                            Loot("obj.steel_spear", 4),
                            Loot("obj.steel_spear_p", 4),
                            Loot("obj.steel_mace", 3),
                            Loot("obj.steel_scimitar", 3),
                            Loot("obj.steel_sword", 4),
                            Loot("obj.black_axe", 1),
                            Loot("obj.black_dagger", 6),
                            Loot("obj.black_mace", 2),
                            Loot("obj.mithril_dagger", 5),
                            Loot("obj.steel_med_helm", 5),
                            Loot("obj.chaosrune", 14, 12, 29),
                            Loot("obj.amulet_of_defence", 7),
                            Loot("obj.sapphire_ring", 2),
                            Loot("obj.silver_bar", 6, 3),
                            Loot("obj.gold_bar", 5, 3),
                            Loot("obj.mithril_bar", 5, 3),
                        ),
                    ),
                ShadeMetal.Steel to
                    ChestTable(
                        15..30,
                        700,
                        listOf(
                            Loot("obj.steel_2h_sword", 1),
                            Loot("obj.steel_warhammer", 6),
                            Loot("obj.steel_battleaxe", 5),
                            Loot("obj.black_warhammer", 6),
                            Loot("obj.black_spear", 2),
                            Loot("obj.black_scimitar", 10),
                            Loot("obj.black_sword", 3),
                            Loot("obj.black_longsword", 2),
                            Loot("obj.mithril_mace", 7),
                            Loot("obj.mithril_spear", 4),
                            Loot("obj.mithril_spear_p", 4),
                            Loot("obj.mithril_sword", 3),
                            Loot("obj.adamant_dagger", 4),
                            Loot("obj.adamant_dagger_p", 1),
                            Loot("obj.studded_chaps", 5),
                            Loot("obj.steel_chainbody", 5),
                            Loot("obj.steel_kiteshield", 2),
                            Loot("obj.black_med_helm", 7),
                            Loot("obj.mithril_med_helm", 3),
                            Loot("obj.adamantite_bar", 6, 3),
                            Loot("obj.emerald_ring", 2),
                            Loot("obj.amulet_of_strength", 6),
                            Loot("obj.chaosrune", 11, 10, 29),
                            Loot("obj.naturerune", 15, 10, 29),
                            Loot("obj.willow_logs", 10, 5, 14, noted = true),
                        ),
                    ),
                ShadeMetal.Black to
                    ChestTable(
                        25..40,
                        1000,
                        listOf(
                            Loot("obj.staff_of_air", 1),
                            Loot("obj.staff_of_water", 1),
                            Loot("obj.staff_of_earth", 1),
                            Loot("obj.staff_of_fire", 1),
                            Loot("obj.mithril_2h_sword", 2),
                            Loot("obj.mithril_warhammer", 2),
                            Loot("obj.mithril_battleaxe", 2),
                            Loot("obj.mithril_longsword", 6),
                            Loot("obj.mithril_scimitar", 2),
                            Loot("obj.adamant_scimitar", 2),
                            Loot("obj.adamant_mace", 6),
                            Loot("obj.adamant_sword", 2),
                            Loot("obj.adamant_axe", 6),
                            Loot("obj.black_spear", 4),
                            Loot("obj.black_battleaxe", 3),
                            Loot("obj.black_2h_sword", 2),
                            Loot("obj.steel_platebody", 2),
                            Loot("obj.black_chainbody", 5),
                            Loot("obj.black_kiteshield", 6),
                            Loot("obj.black_sq_shield", 9),
                            Loot("obj.black_platelegs", 2),
                            Loot("obj.black_plateskirt", 2),
                            Loot("obj.black_full_helm", 3),
                            Loot("obj.mithril_kiteshield", 2),
                            Loot("obj.mithril_sq_shield", 4),
                            Loot("obj.mithril_full_helm", 6),
                            Loot("obj.mithril_platelegs", 2),
                            Loot("obj.mithril_chainbody", 2),
                            Loot("obj.adamant_med_helm", 2),
                            Loot("obj.fine_cloth", 7),
                            Loot("obj.ruby_ring", 7),
                            Loot("obj.amulet_of_magic", 2),
                            Loot("obj.naturerune", 7, 10, 29),
                            Loot("obj.deathrune", 4, 10, 29),
                            Loot("obj.willow_logs", 10, 5, 14, noted = true),
                            Loot("obj.yew_logs", 6, 5, 14, noted = true),
                            Loot("obj.flamtaer_hammer", 1),
                        ),
                    ),
                ShadeMetal.Silver to
                    ChestTable(
                        25..40,
                        2326,
                        listOf(
                            Loot("obj.adamant_spear", 11),
                            Loot("obj.adamant_spear_p", 11),
                            Loot("obj.black_spear", 6),
                            Loot("obj.rune_sword", 3),
                            Loot("obj.battlestaff", 9),
                            Loot("obj.adamnt_warhammer", 3),
                            Loot("obj.adamant_battleaxe", 3),
                            Loot("obj.adamant_2h_sword", 3),
                            Loot("obj.adamant_longsword", 3),
                            Loot("obj.rune_scimitar", 2),
                            Loot("obj.rune_longsword", 1),
                            Loot("obj.black_platebody", 4),
                            Loot("obj.mithril_plateskirt", 4),
                            Loot("obj.rune_med_helm", 3),
                            Loot("obj.adamant_chainbody", 3),
                            Loot("obj.adamant_platelegs", 3),
                            Loot("obj.adamant_plateskirt", 3),
                            Loot("obj.adamant_kiteshield", 3),
                            Loot("obj.adamant_platebody", 2),
                            Loot("obj.adamant_sq_shield", 2),
                            Loot("obj.mithril_platebody", 2),
                            Loot("obj.rune_chainbody", 1),
                            Loot("obj.fine_cloth", 16),
                            Loot("obj.damned_amulet", 10),
                            Loot("obj.diamond_ring", 6),
                            Loot("obj.amulet_of_power", 2),
                            Loot("obj.deathrune", 9, 10, 30),
                            Loot("obj.bloodrune", 6, 10, 30),
                            Loot("obj.flamtaer_hammer", 11),
                            Loot("obj.yew_logs", 3, 5, 10, noted = true),
                            Loot("obj.magic_logs", 2, 5, 10, noted = true),
                        ),
                    ),
                ShadeMetal.Gold to
                    ChestTable(
                        40..70,
                        3160,
                        listOf(
                            Loot("obj.adamant_spear", 7),
                            Loot("obj.adamant_spear_p", 6),
                            Loot("obj.rune_sword", 3),
                            Loot("obj.adamant_longsword", 3),
                            Loot("obj.battlestaff", 11, 3, noted = true),
                            Loot("obj.rune_scimitar", 3),
                            Loot("obj.rune_longsword", 5),
                            Loot("obj.dragon_dagger", 2),
                            Loot("obj.dragon_longsword", 1),
                            Loot("obj.dragon_mace", 1),
                            Loot("obj.adamant_full_helm", 8),
                            Loot("obj.mithril_plateskirt", 5),
                            Loot("obj.rune_med_helm", 3),
                            Loot("obj.adamant_chainbody", 4),
                            Loot("obj.adamant_platelegs", 3),
                            Loot("obj.adamant_plateskirt", 3),
                            Loot("obj.adamant_kiteshield", 2),
                            Loot("obj.adamant_platebody", 3),
                            Loot("obj.rune_chainbody", 2),
                            Loot("obj.rune_platebody", 2),
                            Loot("obj.rune_platelegs", 2),
                            Loot("obj.rune_plateskirt", 2),
                            Loot("obj.fine_cloth", 21),
                            Loot("obj.damned_amulet", 6),
                            Loot("obj.dragonstone", 2),
                            Loot("obj.dragonstone_ring", 3),
                            Loot("obj.deathrune", 9, 10, 29),
                            Loot("obj.soulrune", 6, 10, 29),
                            Loot("obj.flamtaer_hammer", 4),
                            Loot("obj.yew_logs", 4, 5, 14, noted = true),
                            Loot("obj.redwood_logs", 2, 5, 14, noted = true),
                        ),
                    ),
            )
    }
}
