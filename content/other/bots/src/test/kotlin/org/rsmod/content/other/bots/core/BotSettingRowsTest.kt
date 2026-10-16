package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotSettingRowsTest {
    private val config = BotTestHarness.woodcuttingConfig()
    private val sellingSite = SiteCapabilities(canSell = true, usesSupplies = true)

    private fun row(config: BotConfig, label: String, site: SiteCapabilities = sellingSite): SettingRow =
        BotSettingRows.of(config, site).first { it.label == label }

    private fun SettingRow.down(config: BotConfig): BotConfig = (control as SettingControl.Stepper).down!!.invoke(config)

    private fun SettingRow.up(config: BotConfig): BotConfig = (control as SettingControl.Stepper).up!!.invoke(config)

    private fun SettingRow.toggle(config: BotConfig): BotConfig = (control as SettingControl.Toggle).change!!.invoke(config)

    @Test
    fun `a new bot plays at the Normal preset`() {
        assertEquals(BotPace.Normal, BotPace.of(config))
        assertEquals("Normal", row(config, "Difficulty").value)
    }

    @Test
    fun `the difficulty button cycles through every preset and every result is valid`() {
        var current = config
        val seen = mutableListOf<BotPace?>()
        repeat(BotPace.entries.size) {
            current = row(current, "Difficulty").toggle(current)
            assertTrue(current.validate().isEmpty())
            seen += BotPace.of(current)
        }
        assertEquals(listOf(BotPace.Hard, BotPace.Easy, BotPace.Normal), seen)
    }

    @Test
    fun `changing reaction by hand makes the bot Custom and the next preset is Easy`() {
        val custom = row(config, "Reaction").up(config)

        assertNull(BotPace.of(custom))
        assertEquals("Custom", row(custom, "Difficulty").value)
        assertEquals(BotPace.Easy, BotPace.of(row(custom, "Difficulty").toggle(custom)))
    }

    @Test
    fun `reaction and speed stop at their limits`() {
        val fastest = config.copy(reactionMinTicks = 0, reactionMaxTicks = 0, actionIntervalTicks = 1)
        val slowest = config.copy(reactionMinTicks = 20, reactionMaxTicks = 20, actionIntervalTicks = BotSettingRows.MAX_INTERVAL)

        assertNull((row(fastest, "Reaction").control as SettingControl.Stepper).down)
        assertNull((row(fastest, "Speed").control as SettingControl.Stepper).down)
        assertNull((row(slowest, "Reaction").control as SettingControl.Stepper).up)
        assertNull((row(slowest, "Speed").control as SettingControl.Stepper).up)
    }

    @Test
    fun `lowering the sale threshold never leaves a batch bigger than it`() {
        val big = config.copy(sale = config.sale.copy(threshold = 1500, quantity = 1500))

        val lowered = row(big, "Sell at").down(big)

        assertEquals(1400, lowered.sale.threshold)
        assertEquals(1400, lowered.sale.quantity)
        assertTrue(lowered.validate().isEmpty())
    }

    @Test
    fun `the hourly limit steps down to none and not below`() {
        val limited = config.copy(maxGatheredPerHour = 100)

        val lowered = row(limited, "Hourly limit").down(limited)

        assertEquals(0, lowered.maxGatheredPerHour)
        assertEquals("none", row(lowered, "Hourly limit").value)
        assertNull((row(lowered, "Hourly limit").control as SettingControl.Stepper).down)
    }

    @Test
    fun `selling settings are unavailable at a site with no exchange route`() {
        val noExchange = SiteCapabilities(canSell = false, usesSupplies = true)

        assertNotNull(row(config, "Selling", noExchange).unavailable)
        assertNotNull(row(config, "Sell at", noExchange).unavailable)
        assertNotNull(row(config, "Restock", noExchange).unavailable)
    }

    @Test
    fun `restock is unavailable for work that uses no supplies`() {
        assertNotNull(row(config, "Restock", SiteCapabilities(canSell = true, usesSupplies = false)).unavailable)
    }

    @Test
    fun `sell at is unavailable while selling is off`() {
        val off = config.copy(sale = config.sale.copy(enabled = false))

        assertNotNull(row(off, "Sell at").unavailable)
        assertNull(row(row(off, "Selling").toggle(off), "Sell at").unavailable)
    }

    @Test
    fun `reset keeps what the bot sells and restores everything else`() {
        val changed =
            config.copy(
                reactionMinTicks = 7,
                reactionMaxTicks = 9,
                actionIntervalTicks = 3,
                maxGatheredPerHour = 500,
                pricing = config.pricing.copy(minPrice = 999),
                sale = config.sale.copy(threshold = 2000, enabled = false),
            )

        val reset = changed.withDefaults()

        assertEquals(BotPace.Normal, BotPace.of(reset))
        assertEquals(0, reset.maxGatheredPerHour)
        assertEquals(PricingConfig(), reset.pricing)
        assertEquals(changed.sale.item, reset.sale.item)
        assertEquals(changed.sale.allowedItems, reset.sale.allowedItems)
        assertEquals(false, reset.sale.enabled)
        assertEquals(SaleConfig().threshold, reset.sale.threshold)
    }

    @Test
    fun `copying behaviour leaves the other bot's site and sale item alone`() {
        val source = BotPace.Hard.applyTo(config).copy(maxGatheredPerHour = 750)
        val other = config.copy(site = "other_trees", sale = config.sale.copy(item = "obj.iron_ore", allowedItems = setOf("obj.iron_ore")))

        val copied = source.copyBehaviourTo(other)

        assertEquals(BotPace.Hard, BotPace.of(copied))
        assertEquals(750, copied.maxGatheredPerHour)
        assertEquals("other_trees", copied.site)
        assertEquals("obj.iron_ore", copied.sale.item)
    }
}
