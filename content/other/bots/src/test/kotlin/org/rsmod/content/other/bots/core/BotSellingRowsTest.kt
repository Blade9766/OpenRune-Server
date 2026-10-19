package org.rsmod.content.other.bots.core

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class BotSellingRowsTest {
    private val config = BotTestHarness.woodcuttingConfig()
    private val selling = SiteCapabilities(canSell = true, usesSupplies = true)
    private val products = listOf(SaleProduct("obj.logs", "Logs"), SaleProduct("obj.iron_ore", "Iron ore"))

    private fun rows(config: BotConfig, site: SiteCapabilities = selling, products: List<SaleProduct> = this.products) =
        BotSellingRows.of(config, site, products)

    private fun row(config: BotConfig, label: String, site: SiteCapabilities = selling) = rows(config, site).first { it.label == label }

    @Test
    fun `every step from the defaults gives a config the coordinator accepts`() {
        for (row in rows(config)) {
            when (val control = row.control) {
                is SettingControl.Stepper -> {
                    control.down?.let { assertTrue(it(config).validate().isEmpty(), "${row.label} down: ${it(config).validate()}") }
                    control.up?.let { assertTrue(it(config).validate().isEmpty(), "${row.label} up: ${it(config).validate()}") }
                }
                is SettingControl.Toggle -> control.change?.let { assertTrue(it(config).validate().isEmpty(), row.label) }
                is SettingControl.Input -> {}
            }
        }
    }

    @Test
    fun `switching the sale item keeps it on the allowed list`() {
        val next = (row(config, "Sells").control as SettingControl.Toggle).change!!(config)

        assertEquals("obj.iron_ore", next.sale.item)
        assertTrue("obj.iron_ore" in next.sale.allowedItems)
        assertTrue(next.validate().isEmpty())
    }

    @Test
    fun `a single-product site cannot switch its sale item`() {
        val sells = rows(config, products = products.take(1)).first { it.label == "Sells" }

        assertNotNull(sells.unavailable)
    }

    @Test
    fun `the batch size never grows past the sell at amount`() {
        val full = config.copy(sale = config.sale.copy(threshold = 1000, quantity = 1000))

        assertNull((row(full, "Batch size").control as SettingControl.Stepper).up)
    }

    @Test
    fun `a reference price of zero means the market price`() {
        val fixed = config.copy(pricing = config.pricing.copy(referencePrice = 250))
        val input = row(fixed, "Reference price").control as SettingControl.Input

        assertEquals(400, input.apply(fixed, 400).pricing.referencePrice)
        assertNull(input.apply(fixed, 0).pricing.referencePrice)
        assertEquals("market", row(input.apply(fixed, 0), "Reference price").value)
    }

    @Test
    fun `percent steps do not drift with rounding`() {
        var current = config
        repeat(7) { current = (row(current, "Price adjustment").control as SettingControl.Stepper).up!!(current) }

        assertEquals(config.pricing.adjustmentPercent + 3.5, current.pricing.adjustmentPercent)
    }

    @Test
    fun `pricing rows are unavailable while selling is off`() {
        val off = config.copy(sale = config.sale.copy(enabled = false))

        assertTrue(rows(off).filter { it.label != "Restock batch" }.all { it.unavailable != null })
    }

    @Test
    fun `restock batch only applies to work that uses supplies`() {
        val noSupplies = SiteCapabilities(canSell = true, usesSupplies = false)

        assertNotNull(row(config, "Restock batch", noSupplies).unavailable)
        assertNull(row(config, "Restock batch").unavailable)
    }
}
