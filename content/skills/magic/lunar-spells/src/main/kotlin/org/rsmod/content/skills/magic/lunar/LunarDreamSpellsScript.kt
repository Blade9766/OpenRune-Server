package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.NpcServerType
import jakarta.inject.Inject
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.combat.manager.MagicRuneManager
import org.rsmod.api.combat.manager.MagicRuneManager.Companion.isFailure
import org.rsmod.api.config.refs.params
import org.rsmod.api.enums.DreamMentorHumidifyEnums
import org.rsmod.api.enums.NamedEnums
import org.rsmod.api.invtx.invTransaction
import org.rsmod.api.invtx.select
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.baseHitpointsLvl
import org.rsmod.api.player.stat.hitpoints
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.script.onApNpcT
import org.rsmod.api.script.onApPlayerT
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onOpHeld1
import org.rsmod.api.script.onOpNpcT
import org.rsmod.api.script.onOpPlayerT
import org.rsmod.api.spells.MagicSpellRegistry
import org.rsmod.game.entity.Npc
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Monster Examine, Humidify, Hunter Kit, Stat Spy and Dream. Every cast goes through
 * [MagicRuneManager.attemptCast], which enforces the spellbook, the Magic level, Dream Mentor
 * and the runes; anything that could fail afterwards is checked before the runes are taken.
 */
class LunarDreamSpellsScript
@Inject
constructor(private val spells: MagicSpellRegistry, private val runes: MagicRuneManager) : PluginScript() {
    override fun ScriptContext.startup() {
        val examine = component(LunarDreamSpell.MonsterExamine.component)
        onOpNpcT(LunarDreamSpell.MonsterExamine.component) { monsterExamine(it.npc) }
        onApNpcT(examine) { monsterExamine(it.npc) }
        onIfModalButton(MONSTER_CLOSE) { ifClose() }

        onOpPlayerT(LunarDreamSpell.StatSpy.component) { statSpy(it.target) }
        onApPlayerT(component(LunarDreamSpell.StatSpy.component)) { statSpy(it.target) }
        onIfModalButton(PLAYER_STATS_CLOSE) { ifClose() }

        onIfOverlayButton(LunarDreamSpell.Humidify.component) { humidify() }
        onIfOverlayButton(LunarDreamSpell.HunterKit.component) { hunterKit() }
        onOpHeld1(HUNTER_KIT) { openKit(it.slot) }
        onIfOverlayButton(LunarDreamSpell.Dream.component) { dream() }
    }

    private fun spell(spell: LunarDreamSpell): MagicSpell? = spell.resolve(spells)

    private fun ProtectedAccess.cast(dream: LunarDreamSpell, spell: MagicSpell): Boolean {
        if (runes.attemptCast(player, spell).isFailure()) {
            return false
        }
        anim(dream.seq)
        spotanim(dream.spotanim)
        statAdvance("stat.magic", spell.castXp)
        return true
    }

    /* Monster Examine */

    private fun ProtectedAccess.monsterExamine(npc: Npc) {
        val spell = spell(LunarDreamSpell.MonsterExamine) ?: return
        val type = npc.visType
        if (type.combatLevel <= 0 || type.actions.getOpOrNull(1) != "Attack") {
            mes("You can only cast Monster Examine on monsters you can fight.")
            return
        }
        faceEntitySquare(npc)
        if (!cast(LunarDreamSpell.MonsterExamine, spell)) {
            return
        }
        npc.spotanim(LunarDreamSpell.MonsterExamine.spotanim)
        ifOpenSide(MONSTER_INTERFACE)
        ifSetEvents(MONSTER_CLOSE, -1..-1, IfEvent.Op1)
        runClientScript(MONSTER_POPULATE.asRSCM(RSCMType.CLIENTSCRIPT), monsterReport(type))
    }

    /* Stat Spy */

    private fun ProtectedAccess.statSpy(target: Player) {
        val spell = spell(LunarDreamSpell.StatSpy) ?: return
        faceEntitySquare(target)
        if (!cast(LunarDreamSpell.StatSpy, spell)) {
            return
        }
        target.spotanim(LunarDreamSpell.StatSpy.spotanim)
        target.mes("${player.displayName} is spying on your stats!")
        ifOpenSide(PLAYER_STATS_INTERFACE)
        ifSetEvents(PLAYER_STATS_CLOSE, -1..-1, IfEvent.Op1)
        ifSetText(PLAYER_STATS_NAME, target.displayName)
        for ((component, stat) in SPY_STATS) {
            ifSetText("component.dream_player_stats:stat_$component", target.stat(stat).toString())
            ifSetText("component.dream_player_stats:statbase_$component", target.statBase(stat).toString())
        }
    }

    /* Humidify */

    private fun ProtectedAccess.humidify() {
        val spell = spell(LunarDreamSpell.Humidify) ?: return
        val vessels = humidifiable()
        if (vessels.isEmpty()) {
            mes("You have nothing in your inventory that this spell can humidify.")
            return
        }
        if (!cast(LunarDreamSpell.Humidify, spell)) {
            return
        }
        for ((from, to) in vessels) {
            val count = inv.count(from)
            if (count > 0) {
                invReplace(inv, from, count, to)
            }
        }
    }

    private fun ProtectedAccess.humidifiable(): List<Pair<String, String>> =
        DreamMentorHumidifyEnums.dream_mentor_humidify_from.keys.mapNotNull { index ->
            val from = DreamMentorHumidifyEnums.dream_mentor_humidify_from[index]?.internalName ?: return@mapNotNull null
            val to = DreamMentorHumidifyEnums.dream_mentor_humidify_to[index]?.internalName ?: return@mapNotNull null
            (from to to).takeIf { from in inv }
        }

    /* Hunter Kit */

    private fun ProtectedAccess.hunterKit() {
        val spell = spell(LunarDreamSpell.HunterKit) ?: return
        val contents = kitContents()
        if (HUNTER_KIT in inv || contents.all { it in inv }) {
            mes("Why make a kit? You already have all the Hunter equipment it holds.")
            return
        }
        if (inv.isFull()) {
            mes("You don't have enough inventory space to hold a hunter kit.")
            return
        }
        if (!cast(LunarDreamSpell.HunterKit, spell)) {
            return
        }
        invAdd(inv, HUNTER_KIT)
    }

    private fun ProtectedAccess.openKit(slot: Int) {
        val kit = HUNTER_KIT.asRSCM(RSCMType.OBJ)
        val contents = kitContents()
        val transaction =
            player.invTransaction(inv) {
                val pack = select(inv)
                delete {
                    from = pack
                    obj = kit
                    strictCount = 1
                    strictSlot = slot
                }
                for (item in contents) {
                    insert {
                        into = pack
                        obj = item.asRSCM(RSCMType.OBJ)
                        strictCount = 1
                    }
                }
            }
        if (!transaction.success) {
            mes("You need ${contents.size - 1} free inventory spaces to open the hunter kit.")
            return
        }
        mes("You open the hunter kit.")
    }

    private fun kitContents(): List<String> =
        NamedEnums.dream_mentor_hunter_kit.keys.sorted().mapNotNull {
            NamedEnums.dream_mentor_hunter_kit[it]?.internalName
        }

    /* Dream */

    private suspend fun ProtectedAccess.dream() {
        val spell = spell(LunarDreamSpell.Dream) ?: return
        if (player.hitpoints >= player.baseHitpointsLvl) {
            mes("You have no need to rest; your Hitpoints are already full.")
            return
        }
        if (!cast(LunarDreamSpell.Dream, spell)) {
            return
        }
        delay(DREAM_SETTLE_TICKS)
        while (player.hitpoints < player.baseHitpointsLvl) {
            anim(DREAM_LOOP_SEQ)
            spotanim(LunarDreamSpell.Dream.spotanim)
            delay(DREAM_HEAL_TICKS)
            statHeal("stat.hitpoints", 1, 0)
        }
        anim(DREAM_END_SEQ)
        mes("You wake up feeling refreshed.")
    }

    private fun component(name: String) =
        checkNotNull(ServerCacheManager.fromComponent(name.asRSCM(RSCMType.COMPONENT))) { "Missing component: $name" }

    companion object {
        const val HUNTER_KIT = "obj.dream_hunter_box"

        const val MONSTER_INTERFACE = "interface.dream_monster_stat"
        const val MONSTER_CLOSE = "component.dream_monster_stat:moster_stat_close"
        const val MONSTER_POPULATE = "clientscript.[clientscript,dream_monster_populate]"
        const val PLAYER_STATS_INTERFACE = "interface.dream_player_stats"
        const val PLAYER_STATS_CLOSE = "component.dream_player_stats:dream_playerstat_close"
        const val PLAYER_STATS_NAME = "component.dream_player_stats:stat_name"

        /** A Hitpoint every 50 ticks on top of the natural one per 100 heals three times as fast. */
        const val DREAM_HEAL_TICKS = 50
        const val DREAM_SETTLE_TICKS = 2
        const val DREAM_LOOP_SEQ = "seq.dream_zeds"
        const val DREAM_END_SEQ = "seq.dream_spell_end"

        val SPY_STATS =
            listOf(
                "attack" to "stat.attack", "hitpoints" to "stat.hitpoints", "mining" to "stat.mining",
                "strength" to "stat.strength", "agility" to "stat.agility", "smithing" to "stat.smithing",
                "defence" to "stat.defence", "herblore" to "stat.herblore", "fishing" to "stat.fishing",
                "ranged" to "stat.ranged", "thieving" to "stat.thieving", "cooking" to "stat.cooking",
                "prayer" to "stat.prayer", "crafting" to "stat.crafting", "firemaking" to "stat.firemaking",
                "magic" to "stat.magic", "fletching" to "stat.fletching", "woodcutting" to "stat.woodcutting",
                "runecraft" to "stat.runecrafting", "slayer" to "stat.slayer", "farming" to "stat.farming",
                "construction" to "stat.construction", "hunter" to "stat.hunter", "sailing" to "stat.sailing",
            )

        /**
         * The text `dream_monster_populate` splits on `|`: stats, offence, defence and other
         * properties, then the monster's name.
         */
        fun monsterReport(type: NpcServerType): String {
            fun bonus(param: org.rsmod.api.config.aliases.ParamInt): Int = type.paramOrNull(param) ?: 0
            val stats =
                listOf(
                    "Combat level: ${type.combatLevel}",
                    "Hitpoints: ${type.hitpoints}",
                    "Attack: ${type.attack}",
                    "Strength: ${type.strength}",
                    "Defence: ${type.defence}",
                    "Magic: ${type.magic}",
                    "Ranged: ${type.ranged}",
                )
            val offence =
                listOf(
                    "Stab: ${bonus(params.attack_stab)}",
                    "Slash: ${bonus(params.attack_slash)}",
                    "Crush: ${bonus(params.attack_crush)}",
                    "Magic: ${bonus(params.attack_magic)}",
                    "Ranged: ${bonus(params.attack_ranged)}",
                    "Strength: ${bonus(params.melee_strength)}",
                    "Ranged strength: ${bonus(params.ranged_strength)}",
                    "Magic damage: ${bonus(params.magic_damage)}%",
                )
            val defence =
                listOf(
                    "Stab: ${bonus(params.defence_stab)}",
                    "Slash: ${bonus(params.defence_slash)}",
                    "Crush: ${bonus(params.defence_crush)}",
                    "Magic: ${bonus(params.defence_magic)}",
                    "Ranged: ${bonus(params.defence_ranged)}",
                )
            val other = listOf("Size: ${type.size}")
            return listOf(stats, offence, defence, other).joinToString("|") { it.joinToString("<br>") } +
                "|" + type.name
        }
    }
}
