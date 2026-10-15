package org.rsmod.content.skills.magic.spell.attacks

import org.rsmod.api.spells.attack.SpellAttackMap
import org.rsmod.content.skills.magic.spell.attacks.ancient.AncientSpells
import org.rsmod.content.skills.magic.spell.attacks.arceuus.DarkLureSpell
import org.rsmod.content.skills.magic.spell.attacks.arceuus.DemonbaneSpells
import org.rsmod.content.skills.magic.spell.attacks.arceuus.GraspSpells
import org.rsmod.content.skills.magic.spell.attacks.lunar.VengeanceOtherSpell
import org.rsmod.content.skills.magic.spell.attacks.standard.CurseSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.ElementalSpells
import org.rsmod.content.skills.magic.spell.attacks.standard.StandardCombatSpells
import org.rsmod.plugin.module.PluginModule

class SpellAttacksModule : PluginModule() {
    override fun bind() {
        addSetBinding<SpellAttackMap>(ElementalSpells::class.java)
        addSetBinding<SpellAttackMap>(AncientSpells::class.java)
        addSetBinding<SpellAttackMap>(CurseSpells::class.java)
        addSetBinding<SpellAttackMap>(StandardCombatSpells::class.java)
        addSetBinding<SpellAttackMap>(VengeanceOtherSpell::class.java)
        addSetBinding<SpellAttackMap>(DarkLureSpell::class.java)
        addSetBinding<SpellAttackMap>(DemonbaneSpells::class.java)
        addSetBinding<SpellAttackMap>(GraspSpells::class.java)
    }
}
