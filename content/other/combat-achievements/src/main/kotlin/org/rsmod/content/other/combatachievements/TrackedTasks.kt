package org.rsmod.content.other.combatachievements

import dev.openrune.rscm.RSCM.asRSCM
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.NpcList
import org.rsmod.game.entity.Player
import org.rsmod.game.hit.HitType

internal class KillContext(
    val player: Player,
    val npc: Npc,
    val fight: CombatFights.Fight?,
    val clock: Int,
    val streak: Int,
    val fights: CombatFights,
    val npcList: NpcList,
) {
    val ticks: Int
        get() = fight?.let { clock - it.startClock } ?: Int.MAX_VALUE

    val noDamageTaken: Boolean
        get() = fight != null && fight.damageTaken == 0

    fun only(style: HitType): Boolean = fight != null && fight.styles == setOf(style)

    fun onlyWeapons(objs: Set<Int>): Boolean =
        fight != null && fight.weapons.isNotEmpty() && objs.containsAll(fight.weapons)

    fun praying(varbit: String): Boolean = player.vars[varbit] != 0

    fun recentKills(ticks: Int): Int = fights.recentKills(player, npc.name.lowercase(), clock, ticks)

    fun bodyguardsDead(names: Set<String>): Boolean =
        npcList.none {
            it !== npc &&
                it.isVisible &&
                it.hitpoints > 0 &&
                it.name.lowercase() in names &&
                it.coords.level == npc.coords.level &&
                it.coords.chebyshevDistance(npc.coords) <= BODYGUARD_RANGE
        }

    private companion object {
        const val BODYGUARD_RANGE = 24
    }
}

/**
 * A Combat Achievement task checked when the player kills one of [npcNames]. Only tasks whose
 * whole condition can be read from the fight itself are listed; the rest wait on their boss's
 * own mechanics.
 */
internal class TrackedTask(
    val task: String,
    val npcNames: Set<String>,
    val condition: KillContext.() -> Boolean,
)

internal object TrackedTasks {
    private const val PROTECT_MELEE = "varbit.prayer_protectfrommelee"
    private const val PROTECT_MAGIC = "varbit.prayer_protectfrommagic"
    private const val ARCEUUS_SPELLBOOK = 3

    private val barrowsBrothers =
        setOf(
            "ahrim the blighted",
            "dharok the wretched",
            "guthan the infested",
            "karil the tainted",
            "torag the corrupted",
            "verac the defiled",
        )

    private val veracsFlails: Set<Int> by lazy {
        setOf(
                "obj.barrows_verac_weapon",
                "obj.barrows_verac_weapon_100",
                "obj.barrows_verac_weapon_75",
                "obj.barrows_verac_weapon_50",
                "obj.barrows_verac_weapon_25",
            )
            .map { it.asRSCM() }
            .toSet()
    }

    private val salamanders: Set<Int> by lazy {
        setOf(
                "obj.orange_salamander",
                "obj.red_salamander",
                "obj.black_salamander",
                "obj.green_salamander",
                "obj.mountain_salamander",
            )
            .map { it.asRSCM() }
            .toSet()
    }

    private val glacialTemotli: Set<Int> by lazy { setOf("obj.glacial_temotli".asRSCM()) }

    private fun task(task: String, vararg npcs: String, condition: KillContext.() -> Boolean) =
        TrackedTask(task, npcs.map { it.lowercase() }.toSet(), condition)

    private fun showdown(task: String, boss: String, vararg guards: String) =
        task(task, boss) { bodyguardsDead(guards.map { it.lowercase() }.toSet()) }

    val all: List<TrackedTask> =
        listOf(
            task("Claw Clipper", "King Black Dragon") { praying(PROTECT_MELEE) },
            task("Protection from Moss", "Bryophyta") { praying(PROTECT_MAGIC) },
            TrackedTask("Defence? What Defence?", barrowsBrothers) { only(HitType.Magic) },
            task("Mage of the Ruins", "Crazy archaeologist") { only(HitType.Magic) },
            task("Mage of the Swamp", "Deranged archaeologist") { only(HitType.Magic) },
            task("The Flincher", "Chaos Elemental") { noDamageTaken },
            task("I Can't Reach That", "Scorpia") { noDamageTaken },
            task("Demon Evasion", "Skotizo") { noDamageTaken },
            task("Avoiding Those Little Arms", "Giant Mole") { noDamageTaken },
            task("Hard Hitter", "Giant Mole") { fight != null && fight.damagingHits <= 4 },
            task("Inspect Repellent", "Sarachnis") { noDamageTaken },
            task("Prayer Smasher", "Kalphite Queen") { onlyWeapons(veracsFlails) },
            task("Nagua Negation", "Amoxliatl") { noDamageTaken },
            task("Amoxliatl Speed-Trialist", "Amoxliatl") { ticks < 100 },
            task("Amoxliatl Speed-Chaser", "Amoxliatl") { ticks < 50 },
            task("Without Ralos' Light", "Amoxliatl") { fight != null && !fight.prayerLost },
            task("Temotli Triumph", "Amoxliatl") { onlyWeapons(glacialTemotli) },
            task("Kemo Makti", "Amoxliatl") { streak >= 10 },
            task("Plant-Based Diet", "Hespori") { fight != null && !fight.prayerLost },
            task("Hespori Speed-Trialist", "Hespori") { ticks < 80 },
            task("Hespori Speed-Chaser", "Hespori") { ticks < 60 },
            task("Vardorvis Speed-Trialist", "Vardorvis") { ticks < 125 },
            task("Vardorvis Speed-Chaser", "Vardorvis") { ticks < 109 },
            task("Vardorvis Speed-Runner", "Vardorvis") { ticks < 92 },
            task("Whisperer Speed-Trialist", "The Whisperer") { ticks < 300 },
            task("Whisperer Speed-Chaser", "The Whisperer") { ticks < 242 },
            task("Whisperer Speed-Runner", "The Whisperer") { ticks < 209 },
            task("Tentacular", "The Whisperer") {
                player.vars["varbit.spellbook"] == ARCEUUS_SPELLBOOK
            },
            task("Phantom Muspah Speed-Trialist", "Phantom Muspah") { ticks < 300 },
            task("Phantom Muspah Speed-Chaser", "Phantom Muspah") { ticks < 200 },
            task("Phantom Muspah Speed-Runner", "Phantom Muspah") { ticks < 150 },
            task("Essence Farmer", "Phantom Muspah") { streak >= 10 },
            task("More than just a ranged weapon", "Phantom Muspah") { onlyWeapons(salamanders) },
            task("The Worst Ranged Weapon", "Kree'arra") { onlyWeapons(salamanders) },
            task("Two Times the Torment", "Tormented Demon") { recentKills(3) >= 2 },
            task("Three Times the Thrashing", "Tormented Demon") { recentKills(5) >= 3 },
            showdown(
                "General Showdown",
                "General Graardor",
                "Sergeant Strongstack",
                "Sergeant Steelwill",
                "Sergeant Grimspike",
            ),
            showdown(
                "Airborne Showdown",
                "Kree'arra",
                "Flight Kilisa",
                "Flockleader Geerin",
                "Wingman Skree",
            ),
            showdown("Commander Showdown", "Commander Zilyana", "Starlight", "Growler", "Bree"),
            showdown(
                "Demonic Showdown",
                "K'ril Tsutsaroth",
                "Tstanon Karlak",
                "Zakl'n Gritch",
                "Balfrug Kreeyath",
            ),
        )

    val byNpcName: Map<String, List<TrackedTask>> by lazy {
        buildMap<String, MutableList<TrackedTask>> {
            for (task in all) {
                for (name in task.npcNames) {
                    getOrPut(name) { mutableListOf() } += task
                }
            }
        }
    }
}
