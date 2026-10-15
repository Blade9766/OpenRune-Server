package org.rsmod.content.other.commands

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.util.Wearpos
import org.rsmod.api.player.output.mes
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Cosmetic obj transmogrification for admins. `::itemtransmog <obj>` makes the slot that obj
 * belongs to look like it, whatever is worn there; the worn objs, their bonuses and their combat
 * behaviour are untouched. Overrides last until they are cleared or the player logs out.
 */
class AdminItemTransmogCommands : PluginScript() {
    override fun ScriptContext.startup() {
        onCommand(
            "itemtransmog",
            "Make a worn slot look like another obj (ex: ::itemtransmog santa_hat)",
            ::itemTransmog,
            aliases = listOf("transmogitem"),
        ) {
            invalidArgs =
                "Use as ::itemtransmog objNameOrId, ::itemtransmog hide [slot] " +
                    "or ::itemtransmog clear [slot]"
        }
    }

    private fun itemTransmog(cheat: Cheat) =
        with(cheat) {
            val input = args.getOrNull(0)
            if (input == null) {
                player.mesUsage()
                return
            }
            if (input.equals("clear", ignoreCase = true)) {
                player.clearOverrides(args.getOrNull(1))
                return
            }
            if (input.equals("hide", ignoreCase = true)) {
                player.hideSlot(args.getOrNull(1))
                return
            }

            val type = resolveObj(input)
            if (type == null) {
                player.mes("There is no obj mapped to: '$input'")
                return
            }
            val wearpos = Wearpos[type.wearpos1]
            if (wearpos == null || wearpos !in Wearpos.visibleWearpos) {
                player.mes("`${type.nameOrId(input)}` is not worn in a visible slot.")
                return
            }

            player.appearance.setWornOverride(wearpos.slot, type.id)
            player.rebuildAppearance()
            player.mes("Your ${wearpos.name.lowercase()} slot now looks like ${type.nameOrId(input)}.")
        }

    private fun Player.hideSlot(slotArg: String?) {
        val wearpos = resolveWearpos(slotArg ?: "hat")
        if (wearpos == null) {
            mesUnknownSlot(slotArg)
            return
        }
        appearance.setWornOverride(wearpos.slot, Appearance.HIDDEN_WORN_OVERRIDE)
        rebuildAppearance()
        mes("Your ${wearpos.name.lowercase()} slot is now hidden.")
    }

    private fun Player.clearOverrides(slotArg: String?) {
        if (slotArg == null) {
            appearance.clearWornOverrides()
            rebuildAppearance()
            mes("Cleared all obj transmogs.")
            return
        }
        val wearpos = resolveWearpos(slotArg)
        if (wearpos == null) {
            mesUnknownSlot(slotArg)
            return
        }
        appearance.clearWornOverride(wearpos.slot)
        rebuildAppearance()
        mes("Cleared the ${wearpos.name.lowercase()} slot transmog.")
    }

    private fun Player.mesUsage() {
        mes("Use as ::itemtransmog objNameOrId (ex: ::itemtransmog santa_hat)")
        mes("Hide a slot with ::itemtransmog hide hat.")
        mes("Clear with ::itemtransmog clear, or ::itemtransmog clear hat for one slot.")
    }

    private fun Player.mesUnknownSlot(slotArg: String?) {
        val slots = Wearpos.visibleWearpos.joinToString { it.name.lowercase() }
        mes("Unknown slot: '$slotArg'. Slots: $slots")
    }

    private fun resolveWearpos(slotArg: String): Wearpos? =
        Wearpos.visibleWearpos.firstOrNull { it.name.equals(slotArg, ignoreCase = true) }
            ?: slotArg.toIntOrNull()?.let(Wearpos::get)?.takeIf { it in Wearpos.visibleWearpos }

    private fun resolveObj(input: String): ItemServerType? {
        val id = input.toIntOrNull()
        if (id != null) {
            return ServerCacheManager.getItem(id)
        }
        val gameval = if (input.startsWith("obj.")) input else "obj.$input"
        val mapped = runCatching { gameval.asRSCM(RSCMType.OBJ) }.getOrNull()
        if (mapped != null) {
            ServerCacheManager.getItem(mapped)?.let {
                return it
            }
        }
        val name = input.replace('_', ' ')
        return ServerCacheManager.getItems().values.firstOrNull { it.name.equals(name, true) }
    }

    private fun ItemServerType.nameOrId(input: String): String = name.ifEmpty { input }
}
