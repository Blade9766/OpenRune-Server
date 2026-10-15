package org.rsmod.content.skills.magic.lunar

import org.rsmod.api.combat.commons.magic.SpellCastHook
import org.rsmod.plugin.module.PluginModule

class LunarSpellsModule : PluginModule() {
    override fun bind() {
        bindInstance<SpellbookSwap>()
        addSetBinding<SpellCastHook>(SpellbookSwap::class.java)
    }
}
