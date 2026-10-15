package org.rsmod.content.skills.magic.lunar

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import org.rsmod.api.combat.commons.magic.MagicSpell
import org.rsmod.api.spells.MagicSpellRegistry

/**
 * The seven Lunar spells Dream Mentor teaches. Their level, runes and the quest itself are
 * required by the cache's spell params, which `MagicRuneManager.attemptCast` checks on every cast.
 */
enum class LunarDreamSpell(val obj: String, val component: String, val seq: String, val spotanim: String) {
    MonsterExamine(
        "obj.66_monster_examine",
        "component.magic_spellbook:monster_examine",
        "seq.dream_player_monsterexam_statspy",
        "spotanim.dream_spell_monsterexam_statspy_spotanim",
    ),
    Humidify(
        "obj.68_humidify",
        "component.magic_spellbook:humidify",
        "seq.dream_player_humidify_spell",
        "spotanim.dream_humidify_spell_spotanim",
    ),
    HunterKit(
        "obj.71_hunter_kit",
        "component.magic_spellbook:hunter_kit",
        "seq.dream_player_hunter_box_spell",
        "spotanim.dream_hunter_spell_spotanim",
    ),
    StatSpy(
        "obj.75_stat_spy",
        "component.magic_spellbook:stat_spy",
        "seq.dream_player_monsterexam_statspy",
        "spotanim.dream_spell_playerexam_statspy_spotanim",
    ),
    Dream(
        "obj.79_dream",
        "component.magic_spellbook:dream",
        "seq.dream_spell_start",
        "spotanim.dream_zeds_spotanim",
    ),
    PlankMake(
        "obj.86_plank_make",
        "component.magic_spellbook:plank_make",
        "seq.dream_player_make_plank_spell",
        "spotanim.dream_plank_make_spell_spotanim",
    ),
    SpellbookSwap(
        "obj.96_spellbook_swap",
        "component.magic_spellbook:spellbook_swap",
        "seq.dream_player_spellbook_swap",
        "spotanim.dream_spellbook_spotanim",
    );

    fun resolve(spells: MagicSpellRegistry): MagicSpell? {
        val type = ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ)) ?: return null
        return spells.getObjSpell(type)
    }
}
