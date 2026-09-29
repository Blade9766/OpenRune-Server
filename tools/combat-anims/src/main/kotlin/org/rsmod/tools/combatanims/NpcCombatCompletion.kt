package org.rsmod.tools.combatanims

import java.util.TreeMap

/** What the client cache knows about a sequence: the skeleton it animates and whether it sounds. */
data class SequenceFacts(val skeleton: Int?, val hasSounds: Boolean)

/** An npc's combat anims (bare sequence names) and sounds (synth ids). */
data class CombatValues(
    val attackAnim: String? = null,
    val defendAnim: String? = null,
    val deathAnim: String? = null,
    val attackSound: Int? = null,
    val defendSound: Int? = null,
    val deathSound: Int? = null,
) {
    fun anim(slot: Slot): String? =
        when (slot) {
            Slot.Attack -> attackAnim
            Slot.Defend -> defendAnim
            Slot.Death -> deathAnim
        }

    fun sound(slot: Slot): Int? =
        when (slot) {
            Slot.Attack -> attackSound
            Slot.Defend -> defendSound
            Slot.Death -> deathSound
        }

    /** Fills each field this leaves empty from [other]. */
    fun or(other: CombatValues?): CombatValues =
        if (other == null) {
            this
        } else {
            CombatValues(
                attackAnim ?: other.attackAnim,
                defendAnim ?: other.defendAnim,
                deathAnim ?: other.deathAnim,
                attackSound ?: other.attackSound,
                defendSound ?: other.defendSound,
                deathSound ?: other.deathSound,
            )
        }
}

enum class Slot {
    Attack,
    Defend,
    Death,
}

/** An attackable npc as the completion pass sees it. */
data class CompletionNpc(
    val rscm: String,
    val name: String,
    val readyAnim: String?,
    val female: Boolean,
    /** The npc's own params in the cache. The plugin never replaces these. */
    val declared: CombatValues,
    /** The npc's LostCity entry, when it has one. */
    val reference: LostCityNpc? = null,
)

/**
 * Fills in the combat anims and sounds [NpcCombatAnimResolver] leaves out, so that every attackable
 * npc attacks, blocks and dies with an animation made for its own skeleton, and is heard doing so.
 *
 * Each missing field is taken from the first source that has it (after the cache params of siblings
 * sharing the ready animation, which are Jagex's own values for the same rig):
 * 1. LostCity's 2004 configs ([LostCityReference]) - Jagex's own values, used for an anim only when
 *    it animates the npc's skeleton (many monsters have since been remodelled).
 * 2. The resolver's weapon or family result, unless it animates another skeleton.
 * 3. Siblings: the npcs sharing the ready animation whose cache, override or LostCity data names
 *    the field.
 * 4. The skeleton: the npc's ready animation's skeleton has one plainly named death (or attack, or
 *    block) animation closest in name to the ready animation.
 * 5. Humans (npcs on the player skeleton) punch, block and die as players do, with the male or
 *    female hit and death voice.
 * 6. Sounds by name: the synth jukebox (`dbtable.synth`) names monster sounds after the monster -
 *    `wall_beast_attack`, `wall_beast_hit`, `wall_beast_death`.
 *
 * An attack or death sound is skipped when the chosen animation carries its own sound effects,
 * as the newer bosses' skeletal animations do, so nothing plays twice.
 */
class NpcCombatCompletion(
    private val sequences: Map<String, SequenceFacts>,
    synths: Map<String, Int>,
    private val overrides: Map<String, CombatValues> = emptyMap(),
) {
    private val synths = TreeMap(synths)
    private val humanSkeleton = sequences[HUMAN_READY]?.skeleton

    fun complete(
        npcs: List<CompletionNpc>,
        generated: Map<String, NpcCombatAnims>,
    ): List<NpcCombatAnims> {
        val monsters = npcs.filter { it.readyAnim != null && !isHumanoid(it) }
        val cacheSiblings = monsters.groupBy({ it.readyAnim!! }, ::known)
        val siblings = monsters.groupBy({ it.readyAnim!! }, ::trusted)
        return npcs.mapNotNull { npc ->
            complete(
                npc,
                generated[npc.rscm],
                cacheSiblings[npc.readyAnim].orEmpty(),
                siblings[npc.readyAnim].orEmpty(),
            )
        }
    }

    fun isHumanoid(npc: CompletionNpc): Boolean =
        humanSkeleton != null && skeleton(npc.readyAnim) == humanSkeleton

    private fun complete(
        npc: CompletionNpc,
        generated: NpcCombatAnims?,
        cacheSiblings: List<CombatValues>,
        siblings: List<CombatValues>,
    ): NpcCombatAnims? {
        val origins = linkedSetOf<String>()
        generated?.let { origins += it.source }
        val reference = validReference(npc)
        val humanoid = isHumanoid(npc)
        val nameSounds = if (humanoid) null else soundsByName(npc)

        fun siblingAnim(values: List<CombatValues>, slot: Slot): String? =
            majority(values.mapNotNull { it.anim(slot) }.filter { compatible(npc.readyAnim, it) })

        fun anim(slot: Slot): String? {
            if (npc.declared.anim(slot) != null) {
                return null
            }
            val fromGenerator = generated?.anim(slot)
            // A humanoid swings the weapon it is drawn holding, which the resolver reads from its
            // models; 2004 often armed the same npc differently.
            if (humanoid && fromGenerator != null) {
                return fromGenerator
            }
            if (!humanoid) {
                siblingAnim(cacheSiblings, slot)?.let {
                    origins += "sibling"
                    return it
                }
            }
            reference?.anim(slot)?.let {
                origins += "lostcity:${npc.reference?.key}"
                return it
            }
            if (fromGenerator != null && compatible(npc.readyAnim, fromGenerator)) {
                return fromGenerator
            }
            if (!humanoid) {
                siblingAnim(siblings, slot)?.let {
                    origins += "sibling"
                    return it
                }
            }
            if (fromGenerator != null) {
                return fromGenerator
            }
            if (!humanoid) {
                skeletonSearch(npc.readyAnim, slot)?.let {
                    origins += "skeleton"
                    return it
                }
                return null
            }
            val attack = npc.declared.attackAnim ?: generated?.attackAnim
            val unarmed = attack == null || attack == HUMAN_PUNCH
            return when {
                slot == Slot.Death -> HUMAN_DEATH
                slot == Slot.Defend && unarmed -> HUMAN_BLOCK
                else -> null
            }.also { if (it != null) origins += "human" }
        }

        val attackAnim = anim(Slot.Attack)
        val defendAnim = anim(Slot.Defend)
        val deathAnim = anim(Slot.Death)

        fun siblingSound(values: List<CombatValues>, slot: Slot): Int? =
            if (humanoid) null else majority(values.mapNotNull { it.sound(slot) })

        fun sound(slot: Slot, playedAnim: String?): Int? {
            if (npc.declared.sound(slot) != null) {
                return null
            }
            if (slot != Slot.Defend && playedAnim != null && sequences[playedAnim]?.hasSounds == true) {
                return null
            }
            if (humanoid && slot == Slot.Attack) {
                generated?.attackSound?.let {
                    return it
                }
            }
            siblingSound(cacheSiblings, slot)?.let {
                origins += "sibling"
                return it
            }
            reference?.sound(slot)?.let {
                origins += "lostcity:${npc.reference?.key}"
                return it
            }
            generated?.sound(slot)?.let {
                return it
            }
            if (humanoid) {
                return humanSound(npc, slot, playedAnim)?.also { origins += "human" }
            }
            siblingSound(siblings, slot)?.let {
                origins += "sibling"
                return it
            }
            return nameSounds?.second?.sound(slot)?.also { origins += "synth:${nameSounds.first}" }
        }

        val attackSound = sound(Slot.Attack, attackAnim ?: npc.declared.attackAnim)
        val defendSound = sound(Slot.Defend, defendAnim ?: npc.declared.defendAnim)
        val deathSound = sound(Slot.Death, deathAnim ?: npc.declared.deathAnim)

        val result =
            (generated ?: NpcCombatAnims(npc.rscm, source = ""))
                .copy(
                    source = origins.joinToString(" + ").ifEmpty { "completion" },
                    attackAnim = attackAnim,
                    defendAnim = defendAnim,
                    deathAnim = deathAnim,
                    attackSound = attackSound,
                    defendSound = defendSound,
                    deathSound = deathSound,
                )
        return result.takeUnless { it.isEmpty }
    }

    private fun NpcCombatAnims.anim(slot: Slot): String? =
        when (slot) {
            Slot.Attack -> attackAnim
            Slot.Defend -> defendAnim
            Slot.Death -> deathAnim
        }

    private fun NpcCombatAnims.sound(slot: Slot): Int? =
        when (slot) {
            Slot.Attack -> attackSound
            Slot.Defend -> defendSound
            Slot.Death -> deathSound
        }

    /**
     * Cache params: the values siblings copy first. Hand overrides are left out because most give
     * one npc a voice or set its family does not share (the Dragon Slayer II ghosts, greater demons).
     */
    private fun known(npc: CompletionNpc): CombatValues = npc.declared

    /** Hand overrides, cache params, then LostCity's: the values siblings copy as a last resort. */
    private fun trusted(npc: CompletionNpc): CombatValues =
        (overrides[npc.rscm] ?: CombatValues()).or(known(npc)).or(validReference(npc))

    /** The npc's LostCity values that exist in the OSRS cache and fit its skeleton. */
    private fun validReference(npc: CompletionNpc): CombatValues? {
        val names = npc.reference?.combat ?: return null
        fun anim(name: String?): String? =
            name?.takeIf { it in sequences && compatible(npc.readyAnim, it) }
        return CombatValues(
            attackAnim = anim(names.attackAnim),
            defendAnim = anim(names.defendAnim),
            deathAnim = anim(names.deathAnim),
            attackSound = names.attackSound?.let(synths::get),
            defendSound = names.defendSound?.let(synths::get),
            deathSound = names.deathSound?.let(synths::get),
        )
    }

    private fun humanSound(npc: CompletionNpc, slot: Slot, playedAnim: String?): Int? =
        when (slot) {
            Slot.Attack ->
                if (playedAnim == null || playedAnim == HUMAN_PUNCH) synths["unarmed_punch"] else null
            Slot.Defend -> synths[if (npc.female) "female_hit" else "human_hit"]
            Slot.Death -> synths[if (npc.female) "female_death" else "human_death"]
        }

    private fun skeleton(seq: String?): Int? = seq?.let { sequences[it]?.skeleton }

    /** Anims on a skeleton the client cannot compare (skeletal rigs) are given the benefit of it. */
    fun compatible(ready: String?, seq: String): Boolean {
        val facts = sequences[seq] ?: return false
        val readySkeleton = skeleton(ready) ?: return true
        val seqSkeleton = facts.skeleton ?: return true
        return readySkeleton == seqSkeleton
    }

    /**
     * The one animation on the ready animation's skeleton whose name says it is the [slot] member,
     * preferring the name sharing the longest start with the ready animation's.
     */
    fun skeletonSearch(ready: String?, slot: Slot): String? {
        val readySkeleton = skeleton(ready) ?: return null
        val readyName = ready ?: return null
        val words = slotWords.getValue(slot)
        val candidates =
            sequences
                .filter { (name, facts) ->
                    facts.skeleton == readySkeleton &&
                        name.split('_').any { word -> word.trimEnd { it.isDigit() } in words } &&
                        (slot != Slot.Attack || !nonMeleeAttack.containsMatchIn(name))
                }
                .keys
        return candidates.minWithOrNull(
            compareBy<String>({ -commonPrefix(it, readyName) }, { it.length }, { it })
        )
    }

    /** The monster's sounds found under the monster's own name in the synth jukebox. */
    fun soundsByName(npc: CompletionNpc): Pair<String, CombatValues>? {
        for (key in nameKeys(npc)) {
            val found =
                CombatValues(
                    attackSound = synthStartingWith("${key}_attack"),
                    defendSound = soundSuffixes(key, "_hit", "_defend", "_block"),
                    deathSound = soundSuffixes(key, "_death", "_die", "_dead"),
                )
            val count = listOfNotNull(found.attackSound, found.defendSound, found.deathSound).size
            if (count >= 2) {
                return key to found
            }
        }
        return null
    }

    private fun soundSuffixes(key: String, vararg suffixes: String): Int? =
        suffixes.firstNotNullOfOrNull { synths["$key$it"] }

    private fun synthStartingWith(name: String): Int? =
        synths[name] ?: synths.ceilingEntry("${name}_")?.takeIf { it.key.startsWith("${name}_") }?.value

    /**
     * Candidate synth prefixes, most specific first: the display name, the gameval name, the ready
     * animation's family, and last the display name's final word ("Deadly red spider" -> spider).
     */
    fun nameKeys(npc: CompletionNpc): List<String> {
        val display =
            npc.name
                .lowercase()
                .replace(Regex("<[^>]*>"), "")
                .replace(Regex("\\(.*?\\)"), "")
                .replace(Regex("[ -]+"), "_")
                .replace(Regex("[^a-z0-9_]"), "")
                .trim('_')
        val keys = mutableListOf<String>()
        if (display.isNotEmpty()) {
            keys += display
        }
        keys += npc.rscm.removePrefix("npc.").trimEnd { it.isDigit() }.trimEnd('_')
        val ready = npc.readyAnim
        if (ready != null) {
            val marker = readyMarkers.firstOrNull { ready.indexOf(it) > 0 }
            if (marker != null) {
                val family = ready.substring(0, ready.indexOf(marker))
                keys += family
                keys += family.replace("_update", "").replace("_rework", "")
            }
        }
        val lastWord = display.substringAfterLast('_')
        if (lastWord != display && lastWord.length >= 3) {
            keys += lastWord
        }
        return keys.flatMap { listOfNotNull(it, synthAliases[it]) }.filter { it.length >= 3 }.distinct()
    }

    private fun commonPrefix(a: String, b: String): Int {
        var i = 0
        while (i < a.length && i < b.length && a[i] == b[i]) {
            i++
        }
        return i
    }

    private fun <T> majority(values: List<T>): T? =
        values.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key

    private companion object {
        const val HUMAN_READY = "human_ready"
        const val HUMAN_PUNCH = "human_unarmedpunch"
        const val HUMAN_BLOCK = "human_unarmed_def"
        const val HUMAN_DEATH = "human_death"

        val readyMarkers =
            listOf("_just_ready_update", "_ready_update", "_readyanim", "_ready", "_idle", "_stand")

        val slotWords: Map<Slot, Set<String>> =
            mapOf(
                Slot.Attack to setOf("attack", "melee", "punch", "slash", "stab", "crush", "swipe", "bite"),
                Slot.Defend to setOf("defend", "block", "def", "parry", "defence"),
                Slot.Death to setOf("death", "die", "dies", "dead"),
            )

        val nonMeleeAttack = Regex("magic|range|cast|spec|_proj|spot")

        /** Npcs whose synths are named after something other than their name or animations. */
        val synthAliases: Map<String, String> =
            mapOf(
                "nechryael" to "nechrayel",
                "greater_nechryael" to "nechrayel",
                "kalphite_soldier" to "kalthite_soldier",
                "kalphite_worker" to "kalthite_worker",
                "kalphite_guardian" to "kalthite_lord",
                "skeletal_wyvern" to "skwy",
                "ork" to "orc",
                "vyrewatch" to "vyrewatch_vampire",
                "vyrewatch_sentinel" to "vyrewatch_vampire",
                "lizardman_shaman" to "lizardman",
                "lizardman_brute" to "lizardman",
                "praying_mantis" to "lore_preying_mantis",
                "giant_lobster" to "lore_lobster",
                "bear_cub" to "bear",
                "grizzly_bear_cub" to "bear",
                "lava_beast" to "lavabeast",
                "nail_beast" to "ramble_nailbeast",
                "tormented_demon" to "luc2_demon",
                "werewolf" to "half_werewolf",
                "scarab_swarm" to "scarabs",
            )
    }
}
