package org.rsmod.content.quest.area.wilderness.magearena

import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType

/**
 * The three gods of the Mage Arena and everything the arena content keys off them: the cape and
 * staff the chamber hands out, the god spell, the statue to chant at, and the Mage Arena II
 * follower with the remains it drops.
 */
enum class God(
    val displayName: String,
    val cape: String,
    val imbuedCape: String,
    val staff: String,
    /** Other weapons that count as the god's staff for the arena spells (and the Charge bonus). */
    val alternativeStaffs: List<String>,
    val spell: String,
    val spellName: String,
    /** How Kolodion refers to the spell when he reports training progress. */
    val spellNickname: String,
    val statue: String,
    val impact: String,
    val castSound: String,
    val remains: String,
    val remainsName: String,
    /** The player's line when handing the remains over. */
    val remainsDescription: String,
    val followerName: String,
    val follower: String,
    val followerSpawning: String,
    val followerSpawnAnim: String,
    /** How Kolodion names the creature when refusing to imbue without its remains. */
    val creatureName: String,
) {
    SARADOMIN(
        displayName = "Saradomin",
        cape = "obj.saradomin_cape",
        imbuedCape = "obj.ma2_saradomin_cape",
        staff = "obj.saradomin_staff",
        alternativeStaffs = listOf("obj.staff_of_light"),
        spell = "obj.60_saradomin_strike",
        spellName = "Saradomin Strike",
        spellNickname = "strike",
        statue = "loc.magearena_statue_saradomin",
        impact = "spotanim.saradomin_lightning",
        castSound = "synth.saradomin_strike_cast",
        remains = "obj.ma2_saradomin_heart",
        remainsName = "Justiciar's hand",
        remainsDescription = "the Saradominist justiciar",
        followerName = "Justiciar Zachariah",
        follower = "npc.ma2_boss_saradomin",
        followerSpawning = "npc.ma2_boss_saradomin_spawning",
        followerSpawnAnim = "seq.wild_zealot_spawn",
        creatureName = "Saradomin",
    ),
    GUTHIX(
        displayName = "Guthix",
        cape = "obj.guthix_cape",
        imbuedCape = "obj.ma2_guthix_cape",
        staff = "obj.guthix_staff",
        alternativeStaffs = listOf("obj.staff_of_balance", "obj.pest_void_knight_mace"),
        spell = "obj.60_claws_of_guthix",
        spellName = "Claws of Guthix",
        spellNickname = "claw",
        statue = "loc.magearena_statue_guthix",
        impact = "spotanim.guthix_claw_green",
        castSound = "synth.claws_of_guthix_cast",
        remains = "obj.ma2_guthix_heart",
        remainsName = "Ent's roots",
        remainsDescription = "the Guthixian Ent",
        followerName = "Derwen",
        follower = "npc.ma2_boss_guthix",
        followerSpawning = "npc.ma2_boss_guthix_spawning",
        followerSpawnAnim = "seq.ent_boss_spawn",
        creatureName = "Guthix",
    ),
    ZAMORAK(
        displayName = "Zamorak",
        cape = "obj.zamorak_cape",
        imbuedCape = "obj.ma2_zamorak_cape",
        staff = "obj.zamorak_staff",
        alternativeStaffs = listOf("obj.sotd", "obj.toxic_sotd_charged", "obj.toxic_sotd"),
        spell = "obj.60_flames_of_zamorak",
        spellName = "Flames of Zamorak",
        spellNickname = "flame",
        statue = "loc.magearena_statue_zamorak",
        impact = "spotanim.zamorak_flame",
        castSound = "synth.flames_of_zamorak_cast",
        remains = "obj.ma2_zamorak_heart",
        remainsName = "Demon's heart",
        remainsDescription = "the Zamorakian Demon",
        followerName = "Porazdir",
        follower = "npc.ma2_boss_zamorak",
        followerSpawning = "npc.ma2_boss_zamorak_spawning",
        followerSpawnAnim = "seq.zamorak_demon_boss_spawn",
        creatureName = "Zamorak",
    );

    val capeId: Int by lazy { cape.asRSCM(RSCMType.OBJ) }
    val imbuedCapeId: Int by lazy { imbuedCape.asRSCM(RSCMType.OBJ) }
    val spellId: Int by lazy { spell.asRSCM(RSCMType.OBJ) }
    val remainsId: Int by lazy { remains.asRSCM(RSCMType.OBJ) }
    val followerId: Int by lazy { follower.asRSCM(RSCMType.NPC) }
    val followerSpawningId: Int by lazy { followerSpawning.asRSCM(RSCMType.NPC) }
    val staffIds: Set<Int> by lazy { (listOf(staff) + alternativeStaffs).map { it.asRSCM(RSCMType.OBJ) }.toSet() }

    companion object {
        fun byCape(objId: Int): God? = entries.firstOrNull { it.capeId == objId }

        fun bySpell(objId: Int): God? = entries.firstOrNull { it.spellId == objId }

        fun byRemains(objId: Int): God? = entries.firstOrNull { it.remainsId == objId }

        fun byFollower(npcId: Int): God? = entries.firstOrNull { it.followerId == npcId }

        fun byStaff(objId: Int): God? = entries.firstOrNull { objId in it.staffIds }
    }
}
