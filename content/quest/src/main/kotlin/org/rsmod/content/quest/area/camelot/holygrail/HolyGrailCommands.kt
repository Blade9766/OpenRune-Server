package org.rsmod.content.quest.area.camelot.holygrail

import dev.or2.central.account.Rights
import jakarta.inject.Inject
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.baseAttackLvl
import org.rsmod.api.script.onCommand
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.EXCALIBUR
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.MERLINS_CRYSTAL
import org.rsmod.content.quest.area.camelot.holygrail.HolyGrailQuest.Companion.REQUIRED_ATTACK
import org.rsmod.content.quest.area.karamja.shilovillage.readScroll
import org.rsmod.content.quest.manager.Quest
import org.rsmod.game.entity.Player
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * `::holygrail hint` names the current obstacle and nothing beyond it, and `::holygrail map`
 * opens a travel map of the quest's places. Both are open to everyone.
 *
 * Testing shortcuts for administrators:
 * - `::holygrail prototype` makes a fresh character quest-ready: Merlin's Crystal done (through
 *   its own completion, so Excalibur and the knighthood are real), 20 Attack, and Excalibur.
 * - `::holygrail kit` packs food, armour and a spare melee weapon for the Titan.
 * - `::holygrail goto <place>` jumps to a quest location (see [PLACES]).
 */
class HolyGrailCommands
@Inject
constructor(private val quest: HolyGrailQuest, private val launcher: ProtectedAccessLauncher) :
    PluginScript() {

    override fun ScriptContext.startup() {
        onCommand("holygrail") {
            desc = "Holy Grail helpers (ex: ::holygrail hint | map | prototype | kit | goto camelot)"
            invalidArgs = USAGE
            cheat {
                when (args.firstOrNull()?.lowercase()) {
                    "hint" -> player.mes("Hint: ${quest.hint(player)}")
                    "map" -> launcher.launch(player) { readScroll("Holy Grail: travel map", TRAVEL_MAP) }
                    "prototype" -> if (isAdmin(player)) prototype(player)
                    "kit" -> if (isAdmin(player)) kit(player)
                    "goto" -> {
                        if (!isAdmin(player)) return@cheat
                        val dest = PLACES[args.getOrNull(1)?.lowercase()]
                        if (dest == null) {
                            player.mes("Places: ${PLACES.keys.joinToString()}")
                            return@cheat
                        }
                        launcher.launch(player) { telejump(dest, TeleportType.Exempt) }
                    }
                    else -> player.mes(USAGE)
                }
            }
        }
    }

    private fun isAdmin(player: Player): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        player.mes("Only administrators can use that.")
        return false
    }

    private fun prototype(player: Player) {
        val merlin = Quest.get(MERLINS_CRYSTAL) ?: error("Missing quest: $MERLINS_CRYSTAL")
        if (!merlin.isQuestCompleted(player)) {
            launcher.launch(player) { merlin.completeQuest(this) }
        }
        if (player.baseAttackLvl < REQUIRED_ATTACK) {
            player.mes("Use ::setlevel attack $REQUIRED_ATTACK; Holy Grail needs it.")
        }
        val hasSword = player.inv.contains(EXCALIBUR) || player.worn.contains(EXCALIBUR)
        if (!hasSword && player.inv.freeSpace() > 0) {
            player.invAdd(player.inv, EXCALIBUR, 1)
        }
        player.mes(
            "You once freed Merlin from Morgan Le Faye's crystal, and the Lady of the Lake gave " +
                "you Excalibur. King Arthur has a new quest for you.",
        )
    }

    private fun kit(player: Player) {
        if (player.inv.freeSpace() < KIT.size) {
            player.mes("You need ${KIT.size} free inventory slots for the kit.")
            return
        }
        for (obj in KIT) {
            player.invAdd(player.inv, obj, 1)
        }
        player.mes("Added swordfish, rune armour and a spare rune longsword for the Titan.")
    }

    private companion object {
        const val USAGE = "Use as ::holygrail hint, map, prototype, kit or goto <place>"

        val KIT: List<String> =
            List(8) { "obj.swordfish" } +
                listOf("obj.rune_full_helm", "obj.rune_platebody", "obj.rune_platelegs", "obj.rune_kiteshield", "obj.rune_longsword")

        val PLACES =
            linkedMapOf(
                "camelot" to CoordGrid(2758, 3496, 0),
                "merlin" to CoordGrid(2766, 3502, 1),
                "sarim" to CoordGrid(3046, 3235, 0),
                "entrana" to CoordGrid(2850, 3346, 0),
                "galahad" to CoordGrid(2612, 3475, 0),
                "draynor" to CoordGrid(3106, 3362, 2),
                "tower" to HolyGrailQuest.TOWER,
                "realm" to HolyGrailQuest.DEAD_ARRIVAL,
                "titan" to FisherRealm.BRIDGE_EAST,
                "castle" to FisherRealm.BELL_SPOT,
                "goblins" to CoordGrid(2962, 3504, 0),
                "restored" to HolyGrailQuest.RESTORED_ARRIVAL,
            )

        val TRAVEL_MAP =
            """
            Camelot Castle - north-west of Seers' Village (Camelot Teleport).
              King Arthur: the throne room. Merlin: first floor, east door.
            Port Sarim dock - south of Falador. Monks sail to Entrana and
              send your weapons and armour to your bank before you board.
            Entrana - the chapel in the middle of the island: the High Priest.
            Galahad - his house west of McGrubor's Wood, west of Seers'.
            Draynor Manor - north of Draynor Village. Top floor, the room at
              the southern end. Bring the holy table napkin.
            Brimhaven watchtower - the peninsula north-west of Brimhaven,
              Karamja (boat from Ardougne). Blow a magic whistle beneath it.
            Fisher Realm - arrive east of the Titan's bridge. The river
              runs south to the fisherman; the castle lies south-west.
            Goblin Village - north of Falador. The house on its east side.
            """.trimIndent()
    }
}
