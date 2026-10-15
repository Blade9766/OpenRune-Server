package org.rsmod.content.skills.construction.data

/**
 * The combat room's ring, racks and dummies.
 *
 * The ring space is nineteen hotspots, each named for the rings that build on it: the ring walls
 * round the outside ("combat"), the barriers boxing in the two ranging pedestals ("ranging"), the
 * rails along the balance beam ("agility"), the walls every ring shares ("everything"), the four
 * corners and the floor's corner, side and middle tiles. Levels, materials and experience are the
 * cache rows'; the rules come from the Old School wiki.
 */
object Combat {
    private const val HOTSPOT = "loc.poh_gr_1_"

    private val WALLS =
        listOf(
            "wall_cobat_corner",
            "wall_bluecorner",
            "wall_redcorner",
            "wall_combat_agility_corner",
            "wall_combat",
            "wall_ranging_combat",
            "wall_combat_agility",
            "wall_everything",
            "wall_ranging",
            "wall_ranging_agility",
            "wall_agility",
        )

    private val FLOORS =
        listOf("floor_sw", "floor_se", "floor_nw", "floor_ne", "floor_s", "floor_n", "floor_side", "floor_middle")

    /** Every ring space hotspot, in the order [Ring.built] lists its pieces. */
    val RING_HOTSPOTS: List<String> = (WALLS + FLOORS).map { HOTSPOT + it }

    /** The floor hotspots, whose built pieces are where a ring's fighters stand. */
    val RING_FLOORS: Set<String> = FLOORS.mapTo(HashSet()) { HOTSPOT + it }

    /**
     * What a ring puts on one hotspot, given the hotspot's name without its prefix, or null to leave
     * it empty.
     */
    private fun interface Pieces {
        fun on(hotspot: String): String?
    }

    /** A boxing, fencing or combat ring: ring walls all round the outside and a mat over the floor. */
    private fun ring(corner: (String) -> String, wall: String, mat: String) =
        Pieces { hotspot ->
            when {
                hotspot.endsWith("corner") -> corner(hotspot)
                "combat" in hotspot || hotspot == "wall_everything" -> wall
                hotspot.startsWith("wall") -> null
                hotspot in setOf("floor_sw", "floor_se", "floor_nw", "floor_ne") -> "${mat}_corner"
                hotspot == "floor_middle" -> "${mat}_middle"
                else -> "${mat}_side"
            }
        }

    /** A rule a ring holds its fighters to. */
    enum class Style {
        MELEE,
        RANGED_OR_MAGIC,
        ANY,
    }

    /**
     * A ring option. [weapons] is what it allows in the hand - null for anything - and [armour]
     * whether anything but a weapon and shield may be worn; [style] is the kinds of attack allowed.
     */
    enum class Ring(
        val label: String,
        private val pieces: Pieces,
        val style: Style,
        val armour: Boolean,
        val weapons: Set<String>?,
    ) {
        BOXING(
            "boxing ring",
            ring(
                { if (it == "wall_bluecorner") "loc.poh_boxing_ringwall_blue" else if (it == "wall_redcorner") "loc.poh_boxing_ringwall_red" else "loc.poh_boxing_ringwall_corner" },
                "loc.poh_boxing_ringwall_white",
                "loc.poh_boxing_ring_mat",
            ),
            Style.MELEE,
            armour = false,
            weapons = setOf("obj.poh_boxing_gloves_red", "obj.poh_boxing_gloves_blue"),
        ),
        FENCING(
            "fencing ring",
            ring({ "loc.poh_fencing_ringwall" }, "loc.poh_fencing_ringwall", "loc.poh_fencing_ring_mat"),
            Style.MELEE,
            armour = false,
            weapons = null,
        ),
        COMBAT(
            "combat ring",
            ring({ "loc.poh_combat_ringwall" }, "loc.poh_combat_ringwall", "loc.poh_combat_mat"),
            Style.ANY,
            armour = true,
            weapons = null,
        ),
        PEDESTALS(
            "ranging pedestals",
            Pieces { hotspot ->
                when {
                    hotspot in setOf("wall_ranging", "wall_ranging_combat", "wall_ranging_agility", "wall_everything") ->
                        MAGIC_BARRIER
                    hotspot == "floor_se" || hotspot == "floor_nw" -> "loc.poh_magic_circle_mat"
                    else -> null
                }
            },
            Style.RANGED_OR_MAGIC,
            armour = true,
            weapons = null,
        ),
        BEAM(
            "balance beam",
            Pieces { hotspot ->
                when (hotspot) {
                    "wall_agility", "wall_ranging_agility", "wall_combat_agility", "wall_combat_agility_corner", "wall_everything" ->
                        "loc.poh_agility_rail"
                    "floor_nw" -> BEAM_LEFT
                    "floor_n" -> BEAM_MIDDLE
                    "floor_ne" -> BEAM_RIGHT
                    else -> null
                }
            },
            Style.MELEE,
            armour = true,
            weapons = null,
        );

        /** The piece this ring builds on each of [RING_HOTSPOTS], or the hotspot itself where it builds nothing. */
        val built: List<String>
            get() = (WALLS + FLOORS).map { pieces.on(it) ?: (HOTSPOT + it) }
    }

    /** The beam's three pieces, which a player stands on to fight with a pugel. */
    const val BEAM_LEFT: String = "loc.poh_balancebeam_endl"
    const val BEAM_MIDDLE: String = "loc.poh_balancebeam_middle"
    const val BEAM_RIGHT: String = "loc.poh_balancebeam_endr"
    val BEAM: List<String> = listOf(BEAM_LEFT, BEAM_MIDDLE, BEAM_RIGHT)

    const val MAGIC_BARRIER: String = "loc.poh_magic_circle_wall"

    /** The ring walls a player climbs over to get in or out. */
    val ROPES: List<String> =
        listOf(
            "loc.poh_boxing_ringwall_white",
            "loc.poh_boxing_ringwall_corner",
            "loc.poh_boxing_ringwall_red",
            "loc.poh_boxing_ringwall_blue",
            "loc.poh_fencing_ringwall",
            "loc.poh_combat_ringwall",
        )

    const val PUGEL: String = "obj.poh_pugel"

    /** A storage rack and what can be taken from it. */
    /** Everything the racks hand out, which stays in the house like its teas and drinks. */
    val RACK_ITEMS: List<String> by lazy { Rack.entries.flatMap { it.items }.distinct() }

    enum class Rack(val loc: String, val items: List<String>) {
        GLOVES("loc.poh_weapons_rack_gloves", listOf("obj.poh_boxing_gloves_red", "obj.poh_boxing_gloves_blue")),
        WEAPONS(
            "loc.poh_weapons_rack_gloves+woodenstuff",
            listOf("obj.poh_boxing_gloves_red", "obj.poh_boxing_gloves_blue", "obj.poh_wooden_sword", "obj.poh_wooden_shield"),
        ),
        EXTRA(
            "loc.poh_weapons_rack_gloves+woodenstuff+pugels",
            listOf(
                "obj.poh_boxing_gloves_red",
                "obj.poh_boxing_gloves_blue",
                "obj.poh_wooden_sword",
                "obj.poh_wooden_shield",
                PUGEL,
            ),
        ),
    }

    /**
     * A combat dummy form, by its built loc and the npc Attach stands up in its place. The ornate
     * dummy's forms are swapped between, once [unlock] has been paid for its form; [unlockAny] is a
     * list of items any one of which pays.
     */
    enum class Dummy(val loc: String, val npc: String, val unlock: Int = 0, val unlockAny: List<String> = emptyList()) {
        PLAIN("loc.poh_combat_dummy", "npc.poh_combat_dummy_npc"),
        UNDEAD("loc.poh_combat_dummy_undeadslayer", "npc.poh_combat_dummy_undeadslayer_npc"),
        ORNATE_UNDEAD("loc.poh_combat_dummy_upgraded_1_undead", "npc.poh_combat_dummy_upgraded_undeadslayer_npc"),
        ORNATE("loc.poh_combat_dummy_upgraded_1", "npc.poh_combat_dummy_upgraded_npc"),
        ORNATE_WILDERNESS(
            "loc.poh_combat_dummy_upgraded_1_ether",
            "npc.poh_combat_dummy_upgraded_ether_npc",
            500,
            listOf("obj.wild_cave_shard"),
        ),
        ORNATE_KALPHITE(
            "loc.poh_combat_dummy_upgraded_1_kq",
            "npc.poh_combat_dummy_upgraded_kq_npc",
            10,
            listOf("obj.arceuus_corpse_kalphite"),
        ),
        ORNATE_KURASK(
            "loc.poh_combat_dummy_upgraded_1_kurask",
            "npc.poh_combat_dummy_upgraded_kurask_npc",
            1,
            listOf("obj.poh_trophydrop_kurask", "obj.poh_trophydrop_kurask_stuffed"),
        ),
        ORNATE_VAMPYRE(
            "loc.poh_combat_dummy_upgraded_1_vampire",
            "npc.poh_combat_dummy_upgraded_vampire_npc",
            20,
            listOf("obj.vampire_dust"),
        ),
        ORNATE_DRAGON(
            "loc.poh_combat_dummy_upgraded_1_vorkath",
            "npc.poh_combat_dummy_upgraded_vorkath_npc",
            1,
            listOf("obj.vorkath_head", "obj.vorkath_head_stuffed"),
        );

        val ornate: Boolean
            get() = ordinal >= ORNATE_UNDEAD.ordinal

        /** The bit this form takes in [VARIANTS_VARP] once paid for; forms that cost nothing have none. */
        val unlockBit: Int?
            get() = if (unlock > 0) ordinal - ORNATE_WILDERNESS.ordinal else null

        val label: String
            get() = name.lowercase().replace('_', ' ')

        companion object {
            val ORNATE_FORMS: List<Dummy> = entries.filter { it.ornate }

            fun ofLoc(loc: String): Dummy? = entries.firstOrNull { it.loc == loc }

            fun ofNpc(npc: String): Dummy? = entries.firstOrNull { it.npc == npc }
        }
    }

    /**
     * Which ornate dummy forms the owner has paid for, a bit each from the bottom, and above
     * [FORM_SHIFT] the index into [Dummy.ORNATE_FORMS] of the form on show.
     */
    const val VARIANTS_VARP: String = "varp.poh_dummy_variants"

    private const val FORM_SHIFT = 8
    private const val FORM_MASK = 0xFF

    fun shownForm(variants: Int): Dummy = Dummy.ORNATE_FORMS.getOrElse((variants shr FORM_SHIFT) and FORM_MASK) { Dummy.ORNATE_UNDEAD }

    fun withForm(variants: Int, form: Dummy): Int =
        (variants and (FORM_MASK shl FORM_SHIFT).inv()) or (Dummy.ORNATE_FORMS.indexOf(form) shl FORM_SHIFT)

    fun unlocked(variants: Int, form: Dummy): Boolean {
        val bit = form.unlockBit ?: return true
        return variants and (1 shl bit) != 0
    }

    fun withUnlocked(variants: Int, form: Dummy): Int = form.unlockBit?.let { variants or (1 shl it) } ?: variants

    /** The ornate dummy form an owner's built ornate dummy shows as, or null when [built] is not one. */
    fun shownDummy(built: String, variants: Int): String? =
        if (built == Dummy.ORNATE_UNDEAD.loc) shownForm(variants).loc else null

    /** A dummy never runs out of hitpoints; it is topped back up to this after every hit. */
    const val DUMMY_HITPOINTS: Int = 10_000
}
