package org.rsmod.tools.combatanims

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class NpcCombatCompletionTest {
    private val sequences =
        mapOf(
            "human_ready" to SequenceFacts(skeleton = 0, hasSounds = false),
            "human_unarmedpunch" to SequenceFacts(0, false),
            "human_unarmed_def" to SequenceFacts(0, false),
            "human_death" to SequenceFacts(0, false),
            "cow_ready" to SequenceFacts(1, false),
            "cow_attack" to SequenceFacts(1, false),
            "cow_update_ready" to SequenceFacts(2, false),
            "cow_update_attack" to SequenceFacts(2, false),
            "cow_update_defend" to SequenceFacts(2, false),
            "cow_update_death" to SequenceFacts(2, false),
            "wall_beast_combat_ready" to SequenceFacts(3, false),
            "wall_beast_specialattack_defend" to SequenceFacts(3, false),
            "wall_beast_specialattack_death" to SequenceFacts(3, false),
            "boss_idle" to SequenceFacts(null, false),
            "boss_attack" to SequenceFacts(null, true),
        )

    private val synths =
        mapOf(
            "cow_attack" to 369,
            "cow_hit" to 371,
            "cow_death" to 370,
            "human_hit" to 513,
            "human_death" to 512,
            "female_hit" to 509,
            "female_death" to 508,
            "unarmed_punch" to 2566,
            "wall_beast_attack" to 893,
            "wall_beast_hit" to 896,
            "wall_beast_death" to 894,
            "boss_attack" to 1,
            "boss_hit" to 2,
            "boss_death" to 3,
        )

    private val completion = NpcCombatCompletion(sequences, synths)

    private fun npc(
        rscm: String,
        name: String,
        ready: String?,
        declared: CombatValues = CombatValues(),
        reference: LostCityNpc? = null,
        female: Boolean = false,
    ) = CompletionNpc(rscm, name, ready, female, declared, reference)

    @Test
    fun `lostcity anims on another skeleton are rejected but their sounds are kept`() {
        val cow =
            npc(
                "npc.cow",
                "Cow",
                "cow_update_ready",
                reference =
                    LostCityNpc(
                        "cow",
                        "Cow",
                        emptyList(),
                        emptyList(),
                        CombatNames(attackAnim = "cow_attack", attackSound = "cow_attack"),
                    ),
            )
        val result = completion.complete(listOf(cow), emptyMap()).single()
        assertEquals("cow_update_attack", result.attackAnim)
        assertEquals(369, result.attackSound)
        assertEquals("cow_update_death", result.deathAnim)
    }

    @Test
    fun `siblings sharing a ready anim copy trusted values`() {
        val cow = npc("npc.cow", "Cow", "cow_update_ready", declared = CombatValues(deathSound = 370))
        val calf = npc("npc.cow_beef", "Beef", "cow_update_ready")
        val results = completion.complete(listOf(cow, calf), emptyMap()).associateBy { it.npc }
        assertEquals(370, results.getValue("npc.cow_beef").deathSound)
        assertNull(results.getValue("npc.cow").deathSound)
    }

    @Test
    fun `an override's voice does not outrank a sibling's own family sounds`() {
        val overrides = mapOf("npc.cow_special" to CombatValues(deathSound = 999))
        val special = npc("npc.cow_special", "Special cow", "cow_update_ready")
        val cow = npc("npc.cow", "Cow", "cow_update_ready")
        val calf = npc("npc.cow_beef", "Beef", "cow_update_ready")
        val generated =
            mapOf("npc.cow" to NpcCombatAnims("npc.cow", source = "family", deathSound = 370))
        val results =
            NpcCombatCompletion(sequences, synths, overrides)
                .complete(listOf(special, cow, calf), generated)
                .associateBy { it.npc }
        assertEquals(370, results.getValue("npc.cow").deathSound)
        assertEquals(999, results.getValue("npc.cow_beef").deathSound)
    }

    @Test
    fun `humans punch block and die with the voice of their gender`() {
        val woman = npc("npc.woman", "Woman", "human_ready", female = true)
        val result = completion.complete(listOf(woman), emptyMap()).single()
        assertEquals(2566, result.attackSound)
        assertEquals("human_unarmed_def", result.defendAnim)
        assertEquals(509, result.defendSound)
        assertEquals("human_death", result.deathAnim)
        assertEquals(508, result.deathSound)
    }

    @Test
    fun `monster sounds are found under the monster name`() {
        val beast = npc("npc.swamp_wallbeast_combat", "Wall beast", "wall_beast_combat_ready")
        val result = completion.complete(listOf(beast), emptyMap()).single()
        assertEquals(893, result.attackSound)
        assertEquals(896, result.defendSound)
        assertEquals(894, result.deathSound)
        assertEquals("wall_beast_specialattack_death", result.deathAnim)
        assertEquals("wall_beast_specialattack_defend", result.defendAnim)
    }

    @Test
    fun `an attack anim with its own sound effects gets no attack sound`() {
        val boss = npc("npc.boss", "Boss", "boss_idle", declared = CombatValues(attackAnim = "boss_attack"))
        val result = completion.complete(listOf(boss), emptyMap()).single()
        assertNull(result.attackSound)
        assertEquals(2, result.defendSound)
    }

    @Test
    fun `cache values are never repeated in the output`() {
        val declared =
            CombatValues("cow_update_attack", "cow_update_defend", "cow_update_death", 369, 371, 370)
        val cow = npc("npc.cow", "Cow", "cow_update_ready", declared = declared)
        assertEquals(emptyList<NpcCombatAnims>(), completion.complete(listOf(cow), emptyMap()))
    }
}
