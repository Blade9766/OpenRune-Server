package org.rsmod.api.spells.runes

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeAll
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.TestInstance
import org.rsmod.api.spells.runes.combo.ComboRuneRepository
import org.rsmod.api.spells.runes.compact.CompactRuneRepository
import org.rsmod.api.spells.runes.fake.FakeRuneRepository
import org.rsmod.api.spells.runes.staves.StaffSubstituteRepository
import org.rsmod.api.spells.runes.subs.RuneSubstituteRepository
import org.rsmod.api.spells.runes.unlimited.UnlimitedRuneRepository
import org.rsmod.game.inv.Inventory

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MagicRunesPouchTest {
    private val compact = CompactRuneRepository()
    private val combos = ComboRuneRepository()
    private val fakes = FakeRuneRepository()
    private val unlimited = UnlimitedRuneRepository()
    private val runeSubs = RuneSubstituteRepository()
    private val staffSubs = StaffSubstituteRepository()

    @BeforeAll
    fun loadCache() {
        ServerCacheManager.init(240).close()
        compact.init()
        combos.init()
        fakes.init()
        unlimited.init()
        runeSubs.init()
        staffSubs.init()
    }

    @Test
    fun `pouched combo runes are not counted twice`() {
        val validations = validate(pouchedLava = 2)
        assertTrue(validations.any { it.isInvalid }, "$validations")
    }

    @Test
    fun `pouched combo runes cover both requirements when there are enough`() {
        val validations = validate(pouchedLava = 3)
        assertTrue(validations.all { it.isValid }, "$validations")
        val taken =
            validations
                .filterIsInstance<MagicRunes.Validation.Valid.HasEnough>()
                .flatMap { it.sources }
                .filterIsInstance<MagicRunes.Source.VarBitSource>()
                .sumOf { it.count }
        assertEquals(3, taken)
    }

    private fun validate(pouchedLava: Int): List<MagicRunes.Validation> {
        val lavaCompact = checkNotNull(compact[obj("obj.lavarune")])
        val pouch =
            MagicRunes.RunePouch(
                compactId1 = lavaCompact,
                compactId2 = 0,
                compactId3 = 0,
                compactId4 = 0,
                countVarBit1 = "varbit.rune_pouch_quantity_1",
                countVarBit2 = "varbit.rune_pouch_quantity_2",
                countVarBit3 = "varbit.rune_pouch_quantity_3",
                countVarBit4 = null,
                count1 = pouchedLava,
                count2 = 0,
                count3 = 0,
                count4 = 0,
            )
        val requirements =
            MagicRunes.RequirementList(
                listOf(
                    MagicRunes.RequirementList.Requirement(obj("obj.firerune"), 3, null),
                    MagicRunes.RequirementList.Requirement(obj("obj.earthrune"), 2, null),
                )
            )
        return MagicRunes.validateRequirements(
            inv = Inventory.create("inv.inv"),
            worn = Inventory.create("inv.worn"),
            pouch = pouch,
            requirements = requirements,
            useFakeRunes = false,
            runeFountain = false,
            compact = compact,
            unlimited = unlimited,
            combos = combos,
            fakes = fakes,
            runeSubs = runeSubs,
            staffSubs = staffSubs,
        )
    }

    private fun obj(internal: String): ItemServerType =
        checkNotNull(ServerCacheManager.getItem(internal.asRSCM(RSCMType.OBJ)))
}
