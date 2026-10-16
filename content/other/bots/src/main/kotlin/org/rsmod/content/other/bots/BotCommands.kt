package org.rsmod.content.other.bots

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.player.output.mes
import org.rsmod.api.utils.format.formatAmount
import org.rsmod.content.other.bots.core.BotConfig
import org.rsmod.content.other.bots.core.BotCoordinator
import org.rsmod.content.other.bots.core.BotRecord
import org.rsmod.content.other.bots.core.BotRoleType
import org.rsmod.content.other.bots.core.EssenceSource
import org.rsmod.content.other.bots.core.SaleConfig
import org.rsmod.content.other.bots.world.BotKit
import org.rsmod.content.other.bots.world.BotSites
import org.rsmod.content.other.bots.world.KitTarget
import org.rsmod.content.slayer.core.SlayerTaskManager
import org.rsmod.game.entity.Player

/** `::bot` sub-commands. Every change is logged with the administrator's name. */
class BotCommands(private val manager: BotManager, private val takenNames: () -> Set<String>) {
    private val audit = InlineLogger("bots.admin")

    fun run(player: Player, args: List<String>, openStatus: () -> Unit) {
        val coordinator = manager.coordinator
        if (coordinator == null) {
            player.mes("Bots are not loaded yet; check the server log.")
            return
        }
        val sub = args.firstOrNull() ?: return openStatus()
        val rest = args.drop(1)
        when (sub) {
            "status" -> if (rest.isEmpty()) openStatus() else details(player, coordinator, rest[0])
            "list" -> list(player, coordinator)
            "create" -> create(player, coordinator, rest)
            "enable" -> change(player, coordinator, rest[0], "enabled") { coordinator.enable(it.id) }
            "disable" -> change(player, coordinator, rest[0], "disabled") { coordinator.disable(it.id) }
            "remove" ->
                change(player, coordinator, rest[0], "removed") {
                    coordinator.remove(it.id, force = rest.getOrNull(1) == "force")
                }
            "clear" -> change(player, coordinator, rest[0], "error cleared") { coordinator.clearError(it.id) }
            "master" -> master(player, coordinator, rest[0])
            "max" -> report(player, "max active bots set to ${rest[0]}", coordinator.setMaxActive(rest[0].toInt()))
            "set" -> set(player, coordinator, rest)
            "give" -> give(player, coordinator, rest)
            "take" -> take(player, coordinator, rest)
            "level" -> level(player, coordinator, rest)
            "kit" -> change(player, coordinator, rest[0], "applied the starter kit to") { applyKit(coordinator, it) }
            "sites" -> sites(player, rest.firstOrNull() == "reload")
            "trace" -> trace(player, coordinator, rest[0])
            "here" -> player.mes("{ x = ${player.coords.x}, z = ${player.coords.z}, level = ${player.coords.level} }")
            else -> player.mes(USAGE)
        }
    }

    private fun create(player: Player, coordinator: BotCoordinator, args: List<String>) {
        val role = BotRoleType.of(args[0]) ?: return player.mes("Roles: ${BotRoleType.entries.joinToString { it.key }}.")
        val site = BotSites[args[1]] ?: return player.mes("Unknown site '${args[1]}'. Use ::bot sites.")
        if (site.roleType != role) {
            return player.mes("Site ${site.key} is a ${site.role} site.")
        }
        val product = site.products.firstOrNull() ?: return player.mes("Site ${site.key} lists no products.")
        val name = args.drop(2).takeIf { it.isNotEmpty() }?.joinToString(" ") { word ->
            word.replaceFirstChar(Char::uppercaseChar)
        }
        val config =
            BotConfig(
                role = role,
                site = site.key,
                sale = SaleConfig(enabled = site.exchangeRoute.isNotEmpty(), item = product, allowedItems = site.allProducts),
            )
        coordinator
            .create(config, name, takenNames())
            .onSuccess { bot ->
                audit(player, bot, "created ${role.key} bot at ${site.key}")
                val kit =
                    when {
                        site.kit == null -> "Give it its tools with ::bot give"
                        applyKit(coordinator, bot) == null -> "It has its starter kit"
                        else -> "Its starter kit did not fit; give it its tools with ::bot give"
                    }
                player.mes("Created bot #${bot.id} '${bot.name}'. $kit. Enable it from Manage or ::bot enable ${bot.id}.")
            }
            .onFailure { player.mes("Could not create bot: ${it.message}") }
    }

    fun applyKit(coordinator: BotCoordinator, bot: BotRecord): String? {
        val kit = BotSites[bot.config.site]?.kit ?: return "site ${bot.config.site} has no starter kit"
        return coordinator.editSave(bot.id) { BotKit.applyKit(it, kit, manager::itemId) }
    }

    private fun master(player: Player, coordinator: BotCoordinator, value: String) {
        val enabled =
            when (value) {
                "on" -> true
                "off" -> false
                else -> return player.mes("Use ::bot master on|off")
            }
        coordinator.setMaster(enabled)
        audit.info { "${player.username} turned the bot master toggle ${if (enabled) "on" else "off"}" }
        player.mes("Bot master toggle is now ${if (enabled) "ON" else "OFF"}.")
    }

    private fun set(player: Player, coordinator: BotCoordinator, args: List<String>) {
        val bot = coordinator.find(args[0]) ?: return player.mes("No bot '${args[0]}'.")
        val key = args[1]
        val value = args.drop(2).joinToString("_")
        val products = BotSites[bot.config.site]?.allProducts.orEmpty()
        val error =
            try {
                coordinator.updateConfig(bot.id) { applySetting(it, key, value, products) }
            } catch (e: IllegalArgumentException) {
                e.message ?: "invalid value"
            }
        report(player, "set $key=$value on ${bot.name}", error, bot)
    }

    private fun applySetting(
        config: BotConfig,
        key: String,
        value: String,
        products: Set<String>,
    ): BotConfig {
        val sale = config.sale
        val pricing = config.pricing
        return when (key) {
            "threshold" -> config.copy(sale = sale.copy(threshold = value.toInt()))
            "quantity" -> config.copy(sale = sale.copy(quantity = value.toInt()))
            "selling" -> config.copy(sale = sale.copy(enabled = value == "on"))
            "item" -> config.copy(sale = sale.copy(item = productOrThrow(value, products)))
            "allow" -> {
                val allowed = sale.allowedItems + if (value == "all") products else setOf(productOrThrow(value, products))
                config.copy(sale = sale.copy(allowedItems = allowed))
            }
            "disallow" -> config.copy(sale = sale.copy(allowedItems = sale.allowedItems - objName(value)))
            "refprice" -> {
                val reference = value.toIntOrNull()?.takeIf { it > 0 }
                config.copy(pricing = pricing.copy(referencePrice = reference))
            }
            "adjust" -> config.copy(pricing = pricing.copy(adjustmentPercent = value.toDouble()))
            "minprice" -> config.copy(pricing = pricing.copy(minPrice = value.toInt()))
            "deviation" -> config.copy(pricing = pricing.copy(maxDeviationPercent = value.toDouble()))
            "cooldown" -> config.copy(pricing = pricing.copy(cooldownMinutes = value.toInt()))
            "timeout" -> config.copy(pricing = pricing.copy(listingTimeoutMinutes = value.toInt()))
            "retries" -> config.copy(pricing = pricing.copy(maxRetries = value.toInt()))
            "step" -> config.copy(pricing = pricing.copy(retryStepPercent = value.toDouble()))
            "window" -> config.copy(pricing = pricing.copy(windowMinutes = value.toInt()))
            "mintrades" -> config.copy(pricing = pricing.copy(minTrades = value.toInt()))
            "minvolume" -> config.copy(pricing = pricing.copy(minVolume = value.toLong()))
            "interval" -> config.copy(actionIntervalTicks = value.toInt())
            "reaction" -> {
                val (min, max) = value.split("-").map(String::toInt).let { it.first() to it.last() }
                config.copy(reactionMinTicks = min, reactionMaxTicks = max)
            }
            "hourlimit" -> config.copy(maxGatheredPerHour = value.toInt())
            "restock" -> config.copy(restock = config.restock.copy(enabled = value == "on"))
            "restockqty" -> config.copy(restock = config.restock.copy(quantity = value.toInt()))
            "essence" -> {
                val source = EssenceSource.entries.firstOrNull { it.name.equals(value, true) }
                requireNotNull(source) { "essence must be bank or mine" }
                config.copy(essenceSource = source)
            }
            else -> throw IllegalArgumentException("unknown setting '$key'")
        }
    }

    private fun give(player: Player, coordinator: BotCoordinator, args: List<String>) {
        val bot = coordinator.find(args[0]) ?: return player.mes("No bot '${args[0]}'.")
        val (itemName, count, target) = parseItemArgs(args.drop(1))
        val itemId = manager.itemId(itemName) ?: return player.mes("No item $itemName.")
        val error = coordinator.editSave(bot.id) { BotKit.give(it, itemId, count, target) }
        report(player, "gave ${count.formatAmount} x $itemName to ${bot.name} (${target.name.lowercase()})", error, bot)
    }

    private fun take(player: Player, coordinator: BotCoordinator, args: List<String>) {
        val bot = coordinator.find(args[0]) ?: return player.mes("No bot '${args[0]}'.")
        val (itemName, count, target) = parseItemArgs(args.drop(1))
        val itemId = manager.itemId(itemName) ?: return player.mes("No item $itemName.")
        var removed = 0
        val error =
            coordinator.editSave(bot.id) {
                val (save, taken) = BotKit.take(it, itemId, count, target)
                removed = taken
                save
            }
        report(player, "removed ${removed.formatAmount} x $itemName from ${bot.name}", error, bot)
    }

    private fun level(player: Player, coordinator: BotCoordinator, args: List<String>) {
        val bot = coordinator.find(args[0]) ?: return player.mes("No bot '${args[0]}'.")
        val stat = "stat.${args[1]}"
        val level = args[2].toInt()
        val error = coordinator.editSave(bot.id) { BotKit.setLevel(it, stat, level) }
        report(player, "set ${args[1]} to $level on ${bot.name}", error, bot)
    }

    private fun details(player: Player, coordinator: BotCoordinator, idOrName: String) {
        val view = coordinator.status().firstOrNull { it.record.id.toString() == idOrName || it.record.name.equals(idOrName, true) }
            ?: return player.mes("No bot '$idOrName'.")
        val r = view.record
        val c = r.config
        player.mes("#${r.id} ${r.name}: ${c.role.key} at ${c.site}, ${r.state.label} - ${r.activity}")
        player.mes(
            "Inventory ${view.inventoryUsed}/${view.inventoryCapacity}, banked ${c.sale.item.removePrefix("obj.")}: " +
                "${view.bankedSaleItems.formatAmount}, listings ${view.activeListings}, coins ${view.coins.formatAmount}"
        )
        player.mes(
            "Sale: ${if (c.sale.enabled) "on" else "off"}, threshold ${c.sale.threshold}, qty ${c.sale.quantity}, " +
                "allowed ${c.sale.allowedItems.joinToString { it.removePrefix("obj.") }}"
        )
        player.mes(
            "Pricing: ref ${c.pricing.referencePrice ?: "market"}, adjust ${c.pricing.adjustmentPercent}%, " +
                "min ${c.pricing.minPrice}, retries ${r.saleRetries}/${c.pricing.maxRetries}, " +
                "cooldown ${c.pricing.cooldownMinutes}m"
        )
        player.mes(
            "Totals: gathered ${r.stats.gathered.formatAmount}, deposited ${r.stats.deposited.formatAmount}, " +
                "listed ${r.stats.listed.formatAmount}, sold ${r.stats.sold.formatAmount} for ${r.stats.coinsEarned.formatAmount} gp"
        )
        player.mes(
            "Restock: ${if (c.restock.enabled) "on" else "off"}, qty ${c.restock.quantity}, " +
                "bought ${r.stats.suppliesBought.formatAmount} supplies"
        )
        r.pendingListing?.let {
            val kind = if (it.buy) "buy" else "listing"
            player.mes("Pending $kind: ${it.quantity} x ${it.itemId} at ${it.price} in slot ${it.slot}")
        }
        if (r.pendingCollects.isNotEmpty()) {
            player.mes("Pending collections: slots ${r.pendingCollects.joinToString { it.slot.toString() }}")
        }
        if (c.role == BotRoleType.Slayer) {
            player.mes(slayerTask(r))
        }
        r.lastError?.let { player.mes("Last error: $it") }
    }

    private fun trace(player: Player, coordinator: BotCoordinator, idOrName: String) {
        val bot = coordinator.find(idOrName) ?: return player.mes("No bot '$idOrName'.")
        val trace = coordinator.trace(bot.id) ?: return player.mes("${bot.name} is not in the world.")
        val config = bot.config
        player.mes("${bot.name}: ${bot.state.label} - ${bot.activity}")
        player.mes("Reaction ${config.reactionMinTicks}-${config.reactionMaxTicks} ticks, interval ${config.actionIntervalTicks}")
        player.mes("Last target: ${trace.focus ?: "none yet"}")
        val now = System.currentTimeMillis()
        for (decision in trace.decisions.takeLast(TRACE_LINES)) {
            player.mes("${(now - decision.at) / 1000}s ago: ${decision.text}")
        }
    }

    private fun slayerTask(record: BotRecord): String {
        val live = manager.playerOf(record.id)?.vars
        fun value(varp: String): Int = live?.get(varp) ?: record.save.vars[varp.asRSCM(RSCMType.VARP)] ?: 0
        val left = value(SLAYER_COUNT)
        val name = SlayerTaskManager.slayerTargets.firstOrNull { it.id == value(SLAYER_TARGET) }?.nameLowercase
        return if (name == null || left <= 0) "Slayer: no task" else "Slayer: $left $name left"
    }

    private fun list(player: Player, coordinator: BotCoordinator) {
        val bots = coordinator.all()
        player.mes("Bots: ${bots.size}, master ${if (coordinator.settings.masterEnabled) "ON" else "OFF"}, max active ${coordinator.settings.maxActiveBots}")
        for (bot in bots) {
            player.mes("#${bot.id} ${bot.name} - ${bot.config.role.key} @ ${bot.config.site} - ${bot.state.label}${if (bot.enabled) "" else " (disabled)"}")
        }
    }

    private fun sites(player: Player, reload: Boolean) {
        val sites = if (reload) BotSites.reload() else BotSites.all()
        for (site in sites.values) {
            player.mes("${site.key} (${site.role}): ${site.description}")
        }
    }

    private fun change(player: Player, coordinator: BotCoordinator, idOrName: String, what: String, action: (BotRecord) -> String?) {
        val bot = coordinator.find(idOrName) ?: return player.mes("No bot '$idOrName'.")
        report(player, "$what ${bot.name}", action(bot), bot)
    }

    private fun report(player: Player, what: String, error: String?, bot: BotRecord? = null) {
        if (error != null) {
            player.mes("Could not apply: $error")
            return
        }
        if (bot != null) audit(player, bot, what) else audit.info { "${player.username}: $what" }
        player.mes("Done: $what.")
    }

    private fun audit(player: Player, bot: BotRecord, what: String) {
        audit.info { "${player.username} [bot ${bot.id} ${bot.name}] $what" }
    }

    private fun parseItemArgs(args: List<String>): Triple<String, Int, KitTarget> {
        var words = args
        val target =
            when (words.lastOrNull()) {
                "worn" -> KitTarget.Worn
                "bank" -> KitTarget.Bank
                "inv" -> KitTarget.Inv
                else -> null
            }
        if (target != null) words = words.dropLast(1)
        val count = words.lastOrNull()?.toIntOrNull()
        if (count != null) words = words.dropLast(1)
        return Triple(objName(words.joinToString("_")), count ?: 1, target ?: KitTarget.Inv)
    }

    private fun objName(value: String): String = if (value.startsWith("obj.")) value else "obj.$value"

    private fun productOrThrow(value: String, products: Set<String>): String {
        val name = objName(value)
        require(name in products) { "$name is not gathered at this bot's site (${products.joinToString()})" }
        require(ServerCacheManager.getItem(manager.itemId(name) ?: -1) != null) { "unknown item $name" }
        return name
    }

    companion object {
        private const val SLAYER_TARGET = "varp.slayer_target"
        private const val SLAYER_COUNT = "varp.slayer_count"

        val SETTING_KEYS: List<String> =
            listOf(
                "threshold", "quantity", "selling", "item", "allow", "disallow", "refprice", "adjust",
                "minprice", "deviation", "cooldown", "timeout", "retries", "step", "window",
                "mintrades", "minvolume", "interval", "reaction", "hourlimit", "essence", "restock", "restockqty",
            )

        private const val TRACE_LINES = 8
        const val USAGE: String =
            "::bot [status <bot>|list|create <role> <site> [name]|enable|disable|remove <bot> [force]|" +
                "clear <bot>|kit <bot>|master on|off|max <n>|set <bot> <key> <value>|give|take <bot> <item> [n] [inv|worn|bank]|" +
                "level <bot> <stat> <lvl>|trace <bot>|sites [reload]|here]"
    }
}
