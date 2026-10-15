package org.rsmod.content.skills.farming

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test
import org.rsmod.content.skills.farming.data.Crops
import org.rsmod.content.skills.farming.scripts.GardenerScript
import org.rsmod.content.skills.farming.state.PatchState

class GardenerProtectionTest {
    private val crop = Crops.ALL.first { it.protection != null }

    private fun growing(stage: Int = 1) = PatchState(cropKey = crop.key, stage = stage)

    @Test
    fun `a healthy growing crop can be protected`() {
        assertNull(GardenerScript.protectionRefusal(growing()))
    }

    @Test
    fun `an empty patch is refused`() {
        assertEquals(GardenerScript.NOTHING_GROWING, GardenerScript.protectionRefusal(PatchState()))
    }

    @Test
    fun `a diseased crop is refused`() {
        val state = growing().apply { diseased = true }
        assertEquals(GardenerScript.DISEASED, GardenerScript.protectionRefusal(state))
    }

    @Test
    fun `a dead crop is refused`() {
        val state = growing().apply { dead = true }
        assertEquals(GardenerScript.ALREADY_DEAD, GardenerScript.protectionRefusal(state))
    }

    @Test
    fun `a fully grown crop is refused`() {
        assertEquals(
            GardenerScript.FULLY_GROWN,
            GardenerScript.protectionRefusal(growing(stage = crop.cycles)),
        )
    }

    @Test
    fun `an already protected crop is refused`() {
        val state = growing().apply { protectedByFarmer = true }
        assertEquals(GardenerScript.ALREADY_WATCHING, GardenerScript.protectionRefusal(state))
    }
}
