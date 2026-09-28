package org.rsmod.tools.combatanims

import com.github.michaelbull.logging.InlineLogger
import dev.openrune.OsrsCacheProvider
import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.ItemType
import dev.openrune.definition.type.NpcType
import dev.openrune.definition.type.SequenceType
import dev.openrune.filesystem.Cache
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.NpcServerType
import dev.openrune.types.dbcol.DbHelper
import dev.openrune.util.WeaponCategory
import dev.openrune.util.Wearpos
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.Paths
import org.rsmod.api.config.refs.params

/**
 * Writes `npc-combat-anims.toml` for the `npc-combat-anims` plugin.
 *
 * Reads the server cache for npc and item data and the client cache for the models each npc and
 * item is drawn with. A humanoid npc holding the same wear model as a weapon item is holding that
 * weapon, so it gets the weapon's player animations; every other attackable npc gets its
 * animation family from its ready animation. The decisions themselves live in
 * [NpcCombatAnimResolver] and [AnimationFamilies]; this file only gathers the facts and writes
 * the result.
 *
 * Run from the repository root (the Gradle task sets the working directory):
 * `./gradlew :tools:combat-anims:dumpNpcCombatAnims`. Options: `--out=<path>` to write elsewhere.
 */
fun main(args: Array<String>) {
    val root = Paths.get(System.getProperty("user.dir")).toAbsolutePath().normalize()
    val out =
        args.firstOrNull { it.startsWith("--out=") }?.substringAfter("=")?.let(root::resolve)
            ?: root.resolve(NpcCombatAnimDumper.DEFAULT_OUTPUT)
    NpcCombatAnimDumper(root).dump(out)
}

class NpcCombatAnimDumper(private val root: Path) {
    fun dump(out: Path) {
        val revision = readRevision(root.resolve("game.yml"))
        logger.info { "Loading server cache (revision $revision)..." }
        ServerCacheManager.init(revision)

        logger.info { "Loading client npc and item models..." }
        val live = Cache.load(root.resolve(".data/cache/LIVE"))
        val clientItems = HashMap<Int, ItemType>()
        val clientNpcs = HashMap<Int, NpcType>()
        OsrsCacheProvider.ItemDecoder(revision).load(live, clientItems)
        OsrsCacheProvider.NPCDecoder(revision).load(live, clientNpcs)

        val sequences = sequenceNames()
        val weaponsByModel = indexWeapons(clientItems)
        val shieldsByModel = indexShields(clientItems)
        logger.info {
            "Indexed ${weaponsByModel.size} weapon and ${shieldsByModel.size} shield wear models, " +
                "${sequences.size} sequences."
        }

        val sequenceFacts = sequenceFacts(live, revision)
        val synths = synths()
        val overrides = readOverrides(root.resolve(OVERRIDES))
        val lostCity =
            LostCityReference.match(
                LostCityReference.npcs(),
                clientNpcs.mapValues { it.value.name },
                clientNpcs.mapValues { it.value.models.orEmpty().toList() },
            )
        val femaleModels = LostCityReference.femaleBodyModels()
        val maleModels = LostCityReference.maleBodyModels()
        logger.info {
            "Indexed ${synths.size} synths, ${lostCity.size} LostCity npcs, " +
                "${overrides.size} overrides."
        }

        val generated = HashMap<String, NpcCombatAnims>()
        val attackable = mutableListOf<CompletionNpc>()
        for ((id, client) in clientNpcs.toSortedMap()) {
            val server = ServerCacheManager.getNpc(id) ?: continue
            val rscm = rscmOrNull(RSCMType.NPC, id) ?: continue
            if ((0 until 5).none { client.actions.getOpOrNull(it) == "Attack" }) {
                continue
            }
            val facts =
                NpcFacts(
                    rscm = rscm,
                    name = client.name,
                    readyAnim = bareName(RSCMType.SEQ, client.standAnim),
                    attack = client.attack,
                    ranged = client.ranged,
                    magic = client.magic,
                    attackable = true,
                )
            val models = client.models.orEmpty()
            val weapon =
                models.firstNotNullOfOrNull(weaponsByModel::get)
                    ?: models.firstNotNullOfOrNull(NpcCombatAnimResolver.npcOnlyWeapons::get)
            val shield = models.firstNotNullOfOrNull(shieldsByModel::get)
            NpcCombatAnimResolver.resolve(facts, weapon, shield, sequences)?.let {
                generated[rscm] = it
            }
            attackable +=
                CompletionNpc(
                    rscm = rscm,
                    name = client.name,
                    readyAnim = facts.readyAnim,
                    female = isFemale(client.name, rscm, models.toSet(), femaleModels, maleModels),
                    declared = declared(server),
                    reference = lostCity[id],
                )
        }

        val completion = NpcCombatCompletion(sequenceFacts, synths, overrides)
        val results = completion.complete(attackable, generated)
        val counts = sortedMapOf<String, Int>()
        for (result in results) {
            for (origin in result.source.split(" + ")) {
                counts.merge(origin.substringBefore(':'), 1, Int::plus)
            }
        }

        Files.createDirectories(out.parent)
        Files.writeString(out, NpcCombatAnimToml.render(results))
        logger.info { "Wrote ${results.size} npcs to $out" }
        for ((source, count) in counts) {
            logger.info { "  $source: $count" }
        }
    }

    /** Weapon wear model -> the weapon's combat facts. The lowest item id claims a shared model. */
    private fun indexWeapons(clientItems: Map<Int, ItemType>): Map<Int, WeaponFacts> {
        val stanceAttackAnim = "param.attack_anim_stance1".asRSCM(RSCMType.PARAM)
        val stanceAttackSound = "param.attack_sound_stance1".asRSCM(RSCMType.PARAM)
        val lungeAttackAnim = "param.attack_anim_stance3".asRSCM(RSCMType.PARAM)
        val lungeAttackSound = "param.attack_sound_stance3".asRSCM(RSCMType.PARAM)
        val index = HashMap<Int, WeaponFacts>()
        for ((id, server) in ServerCacheManager.getItems().toSortedMap()) {
            if (server.wearpos1 != Wearpos.RightHand.slot) {
                continue
            }
            if (server.weaponCategory == WeaponCategory.Unarmed) {
                continue
            }
            val attackAnim = paramName(server, stanceAttackAnim, RSCMType.SEQ) ?: continue
            val client = clientItems[id] ?: continue
            val facts =
                WeaponFacts(
                    rscm = rscmOrNull(RSCMType.OBJ, id) ?: continue,
                    category = server.weaponCategory,
                    attackAnim = attackAnim,
                    attackSound = paramInt(server, stanceAttackSound),
                    defendAnim = paramName(server, params.defend_anim.id, RSCMType.SEQ),
                    projTravel = paramName(server, params.proj_travel.id, RSCMType.SPOTANIM),
                    projType = paramName(server, params.proj_type.id, RSCMType.PROJANIM),
                    attackRange = paramInt(server, params.attackrange.id),
                    lungeAnim = paramName(server, lungeAttackAnim, RSCMType.SEQ),
                    lungeSound = paramInt(server, lungeAttackSound),
                )
            for (model in wearModels(client)) {
                index.putIfAbsent(model, facts)
            }
        }
        return index
    }

    private fun indexShields(clientItems: Map<Int, ItemType>): Map<Int, ShieldFacts> {
        val index = HashMap<Int, ShieldFacts>()
        for ((id, server) in ServerCacheManager.getItems().toSortedMap()) {
            if (server.wearpos1 != Wearpos.LeftHand.slot) {
                continue
            }
            val client = clientItems[id] ?: continue
            val rscm = rscmOrNull(RSCMType.OBJ, id) ?: continue
            val facts = ShieldFacts(rscm, server.name)
            for (model in wearModels(client)) {
                index.putIfAbsent(model, facts)
            }
        }
        return index
    }

    private fun wearModels(item: ItemType): List<Int> =
        listOf(
                item.maleModel0,
                item.maleModel1,
                item.maleModel2,
                item.femaleModel0,
                item.femaleModel1,
            )
            .filter { it > 0 }

    private fun sequenceNames(): Set<String> =
        ServerCacheManager.getAnims().keys.mapNotNullTo(HashSet()) { bareName(RSCMType.SEQ, it) }

    /** The skeleton (frame base) each sequence animates, read from its first frame. */
    private fun sequenceFacts(live: Cache, revision: Int): Map<String, SequenceFacts> {
        val types = HashMap<Int, SequenceType>()
        OsrsCacheProvider.SequenceDecoder(revision).load(live, types)
        val out = HashMap<String, SequenceFacts>()
        for ((id, type) in types) {
            val name = bareName(RSCMType.SEQ, id) ?: continue
            val frame = type.frameIDs?.firstOrNull()?.takeIf { it >= 0 }
            val data = frame?.let { runCatching { live.data(0, it ushr 16, it and 0xFFFF) }.getOrNull() }
            val skeleton =
                if (data == null || data.size < 2) {
                    null
                } else {
                    ((data[0].toInt() and 0xFF) shl 8) or (data[1].toInt() and 0xFF)
                }
            val hasSounds =
                !type.soundEffects.isNullOrEmpty() || !type.skeletalSounds.isNullOrEmpty()
            out[name] = SequenceFacts(skeleton, hasSounds)
        }
        return out
    }

    /** Synth name -> id, from the sound jukebox table. */
    private fun synths(): Map<String, Int> {
        val out = HashMap<String, Int>()
        for (row in DbHelper.table("dbtable.synth")) {
            val values =
                runCatching { row.getColumn("dbcol.synth:synth").column.values }.getOrNull()
            val list = values?.toList() ?: continue
            for (i in 0 until list.size - 1 step 2) {
                val name = list[i] as? String ?: continue
                val id = list[i + 1] as? Int ?: continue
                out.putIfAbsent(name, id)
            }
        }
        return out
    }

    private fun declared(server: NpcServerType): CombatValues {
        val values = server.paramMap?.primitiveMap.orEmpty()
        fun seq(param: Int): String? = (values[param] as? Int)?.let { bareName(RSCMType.SEQ, it) }
        fun int(param: Int): Int? = values[param] as? Int
        return CombatValues(
            attackAnim = seq(params.attack_anim.id),
            defendAnim = seq(params.defend_anim.id),
            deathAnim = seq(params.death_anim.id),
            attackSound = int(params.attack_sound.id),
            defendSound = int(params.defend_sound.id),
            deathSound = int(params.death_sound.id),
        )
    }

    private fun isFemale(
        name: String,
        rscm: String,
        models: Set<Int>,
        femaleModels: Set<Int>,
        maleModels: Set<Int>,
    ): Boolean {
        if (models.any { it in maleModels }) {
            return false
        }
        if (models.any { it in femaleModels }) {
            return true
        }
        return femaleNames.containsMatchIn("${name.lowercase()} ${rscm.lowercase()}")
    }

    /** The anims and sounds of the hand overrides, which siblings may copy. */
    private fun readOverrides(file: Path): Map<String, CombatValues> {
        if (!Files.exists(file)) {
            return emptyMap()
        }
        val out = HashMap<String, CombatValues>()
        for (line in Files.readAllLines(file)) {
            if (!line.trimStart().startsWith("{ id =")) {
                continue
            }
            val values =
                overrideField.findAll(line).associate {
                    it.groupValues[1] to it.groupValues[3].ifEmpty { it.groupValues[4] }
                }
            val id = values["id"] ?: continue
            out[id] =
                CombatValues(
                    attackAnim = values["attack_anim"]?.removePrefix("seq."),
                    defendAnim = values["defend_anim"]?.removePrefix("seq."),
                    deathAnim = values["death_anim"]?.removePrefix("seq."),
                    attackSound = values["attack_sound"]?.toIntOrNull(),
                    defendSound = values["defend_sound"]?.toIntOrNull(),
                    deathSound = values["death_sound"]?.toIntOrNull(),
                )
        }
        return out
    }

    private fun paramInt(item: ItemServerType, param: Int): Int? =
        item.paramMap?.primitiveMap?.get(param) as? Int

    private fun paramName(item: ItemServerType, param: Int, type: RSCMType): String? =
        paramInt(item, param)?.let { bareName(type, it) }

    /** `seq.human_ready` -> `human_ready`; `null` for an unnamed or unset id. */
    private fun bareName(type: RSCMType, id: Int): String? =
        rscmOrNull(type, id)?.substringAfter('.')

    private fun rscmOrNull(type: RSCMType, id: Int): String? {
        if (id < 0) {
            return null
        }
        return runCatching { RSCM.getReverseMapping(type, id) }.getOrNull()
    }

    private fun readRevision(gameYml: Path): Int {
        val line =
            Files.readAllLines(gameYml).firstOrNull { it.trimStart().startsWith("revision:") }
                ?: error("No revision line in $gameYml")
        return line.substringAfter("revision:").trim().substringBefore('.').toInt()
    }

    companion object {
        const val DEFAULT_OUTPUT: String =
            "content/other/npc-combat-anims/src/main/resources/npc-combat-anims.toml"

        private const val OVERRIDES: String =
            "content/other/npc-combat-anims/src/main/resources/npc-combat-anims-overrides.toml"

        private val overrideField = Regex("""(\w+) = ("([^"]*)"|(\d+))""")

        private val femaleNames =
            Regex(
                "woman|female|\\blady|queen|princess|_f\\b|_f_|\\bgirl|sister|mother|witch|priestess"
            )

        private val logger = InlineLogger()
    }
}
