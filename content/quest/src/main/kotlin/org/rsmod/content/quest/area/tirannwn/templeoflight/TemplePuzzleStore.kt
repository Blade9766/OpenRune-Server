package org.rsmod.content.quest.area.tirannwn.templeoflight

import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.game.entity.Player

/**
 * Saves what each adjustable pillar holds on five server varps, seven pillars of four bits per
 * varp, in [TempleGeometry.adjustable] order. A nibble is 0 for an empty pillar (or a preset
 * mirror facing its starting way), 1-6 for a mirror facing north, east, south, west, up or down,
 * and 7-11 for the yellow, cyan and blue crystals and the two fractured crystals.
 *
 * Schema 1 is the only version so far; `varbit.mourning2_puzzle_version` stays 0 until the first
 * save, and an all-zero record reads as the untouched temple, so existing characters need no
 * migration. A nibble that names nothing (12-15), or a crystal in a preset mirror's pillar, reads
 * as the pillar's default rather than failing; the dispenser makes good any piece that loses.
 */
object TemplePuzzleStore {
    const val SCHEMA_VERSION = 1
    const val VERSION_VARBIT = "varbit.mourning2_puzzle_version"
    val VARPS = (1..5).map { "varp.mourning2_pillars_$it" }
    private const val PER_VARP = 7
    private const val BITS = 4
    private const val MASK = (1 shl BITS) - 1

    private val facings = listOf(Facing.NORTH, Facing.EAST, Facing.SOUTH, Facing.WEST, Facing.UP, Facing.DOWN)
    private val crystals =
        listOf(
            TempleItem.YELLOW,
            TempleItem.CYAN,
            TempleItem.BLUE,
            TempleItem.FRACTURED_HORIZONTAL,
            TempleItem.FRACTURED_VERTICAL,
        )

    init {
        check(TempleGeometry.adjustable.size <= VARPS.size * PER_VARP)
    }

    fun load(player: Player): PuzzleState = decode(IntArray(VARPS.size) { player.vars[VARPS[it]] })

    fun save(player: Player, state: PuzzleState) {
        val words = encode(state)
        for ((index, varp) in VARPS.withIndex()) {
            if (player.vars[varp] != words[index]) {
                VarPlayerIntMapSetter.set(player, varp, words[index])
            }
        }
        if (player.vars[VERSION_VARBIT] != SCHEMA_VERSION) {
            VarPlayerIntMapSetter.set(player, VERSION_VARBIT, SCHEMA_VERSION)
        }
    }

    fun encode(state: PuzzleState): IntArray {
        val words = IntArray(VARPS.size)
        for ((index, pillar) in TempleGeometry.adjustable.withIndex()) {
            val code = code(pillar, state.contentOf(pillar))
            val word = index / PER_VARP
            val shift = (index % PER_VARP) * BITS
            words[word] = words[word] or (code shl shift)
        }
        return words
    }

    fun decode(words: IntArray): PuzzleState {
        val contents = HashMap<String, PillarContent>()
        for ((index, pillar) in TempleGeometry.adjustable.withIndex()) {
            val word = words.getOrElse(index / PER_VARP) { 0 }
            val code = (word ushr ((index % PER_VARP) * BITS)) and MASK
            val content = content(pillar, code) ?: continue
            contents[pillar.id] = content
        }
        return PuzzleState(contents)
    }

    private fun code(pillar: Pillar, content: PillarContent): Int =
        when (content) {
            PillarContent.Empty -> 0
            is PillarContent.Mirror ->
                if (pillar.kind == PillarKind.PRESET_MIRROR && content.facing == pillar.presetFacing) 0 else facings.indexOf(content.facing) + 1
            is PillarContent.Crystal -> crystals.indexOf(content.item) + 1 + facings.size
        }

    private fun content(pillar: Pillar, code: Int): PillarContent? {
        val mirror = facings.getOrNull(code - 1)?.let { PillarContent.Mirror(it) }
        if (pillar.kind == PillarKind.PRESET_MIRROR) {
            return mirror
        }
        if (code == 0) {
            return null
        }
        return mirror ?: crystals.getOrNull(code - 1 - facings.size)?.let { PillarContent.Crystal(it) }
    }
}
