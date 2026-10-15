package pohui

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.stat.baseConstructionLvl
import org.rsmod.api.player.ui.ifClose
import org.rsmod.api.player.ui.ifOpenMain
import org.rsmod.api.script.onCommand
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class PohUiPlugin @Inject constructor(private val eventBus: EventBus) :
    PluginScript() {
    override fun ScriptContext.startup() {
        onCommand("cs2") {
            desc = "Run a clientscript: ::cs2 nameOrId [args] (ints, iface:child, or text_with_underscores)"
            cheat {
                val script = args.firstOrNull()?.let(::resolveScript)
                if (script == null) {
                    player.mes("Use as ::cs2 nameOrId [args] (ex: ::cs2 poh_jewellery_box_init 3 Box 0)")
                    return@cheat
                }
                player.runClientScript(script, args.drop(1).map(::parseArg))
                player.mes("Ran clientscript $script with ${args.size - 1} arg(s).")
            }
        }
        onCommand("pohui") {
            desc = "Preview a POH interface: ::pohui jewellery|costumes|furniture|addroom [variant]"
            cheat { openPreview(player, args) }
        }
    }

    private fun openPreview(player: Player, args: List<String>) {
        val variant = args.getOrNull(1)
        when (args.firstOrNull()) {
            "jewellery" -> open(player, "interface.poh_jewellery_box") {
                val tier = variant?.toIntOrNull() ?: 3
                runClientScript(script("poh_jewellery_box_init"), tier, JEWELLERY_TITLES[tier] ?: "", 0)
            }
            "costumes" -> {
                val enum = COSTUME_ENUMS[variant ?: "cape"]
                if (enum == null) {
                    player.mes("Variants: ${COSTUME_ENUMS.keys.joinToString()}")
                    return
                }
                open(player, "interface.poh_costumes") {
                    runClientScript(script("poh_costumes_init"), enum, 0, 1)
                }
            }
            "furniture" -> open(player, "interface.poh_furniture_creation") {
                val level = player.baseConstructionLvl
                for (slot in 1..MAX_FURNITURE_SLOTS) {
                    val entry = ARMCHAIRS.getOrNull(slot - 1)
                    if (entry == null) {
                        runClientScript(script("poh_furniture_creation_entry"), slot, -1, -1, "", 0)
                        continue
                    }
                    val canBuild = if (level >= entry.level) 1 else 0
                    runClientScript(
                        script("poh_furniture_creation_entry"),
                        slot,
                        entry.dbrow,
                        entry.level,
                        entry.materials,
                        canBuild,
                    )
                }
                runClientScript(script("script1406"), ARMCHAIRS.size, 0)
            }
            "addroom" -> open(player, "interface.poh_add_room") {}
            else -> player.mes("Use as ::pohui jewellery [1-3] | costumes [${COSTUME_ENUMS.keys.joinToString("|")}] | furniture | addroom")
        }
    }

    private fun open(player: Player, interf: String, init: Player.() -> Unit) {
        player.ifClose(eventBus)
        player.ifOpenMain(interf, eventBus)
        player.init()
    }

    private fun resolveScript(arg: String): Int? =
        arg.toIntOrNull()
            ?: runCatching { "clientscript.[clientscript,$arg]".asRSCM(RSCMType.CLIENTSCRIPT) }
                .getOrNull()

    private fun parseArg(arg: String): Any {
        arg.toIntOrNull()?.let {
            return it
        }
        val component = COMPONENT_ARG.matchEntire(arg)
        if (component != null) {
            val (interf, child) = component.destructured
            return (interf.toInt() shl 16) or child.toInt()
        }
        return arg.replace('_', ' ')
    }

    private fun script(name: String): Int = "clientscript.[clientscript,$name]".asRSCM(RSCMType.CLIENTSCRIPT)

    private data class FurnitureEntry(val dbrow: Int, val level: Int, val materials: String)

    private companion object {
        const val MAX_FURNITURE_SLOTS = 31
        val COMPONENT_ARG = Regex("""(\d+):(\d+)""")

        val JEWELLERY_TITLES =
            mapOf(1 to "Basic Jewellery Box", 2 to "Fancy Jewellery Box", 3 to "Ornate Jewellery Box")

        val COSTUME_ENUMS =
            linkedMapOf("magic" to 3289, "armour" to 3290, "fancy" to 3291, "cape" to 3292)

        val ARMCHAIRS =
            listOf(
                FurnitureEntry(5517, 1, "Plank: 2|Nails: 2"),
                FurnitureEntry(5518, 8, "Plank: 3|Nails: 3"),
                FurnitureEntry(5519, 14, "Plank: 3|Nails: 3"),
                FurnitureEntry(5520, 19, "Oak plank: 2"),
                FurnitureEntry(5521, 26, "Oak plank: 3"),
                FurnitureEntry(5522, 35, "Teak plank: 2"),
                FurnitureEntry(5523, 50, "Mahogany plank: 2"),
            )
    }
}
