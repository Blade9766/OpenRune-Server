package org.rsmod.content.skills.magic.arceuus

import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

class NoopSpellsScript : PluginScript() {
    override fun ScriptContext.startup() {
        for (spell in NoopSpell.entries) {
            onIfOverlayButton(spell.component) { refuse() }
        }
    }

    private fun ProtectedAccess.refuse() {
        mes("This spell has not been implemented yet.")
    }

    internal enum class NoopSpell(val component: String) {
        ResurrectCrops("component.magic_spellbook:resurrect_crops"),
        LesserGhost("component.magic_spellbook:resurrect_lesser_ghost"),
        LesserSkeleton("component.magic_spellbook:resurrect_lesser_skeleton"),
        LesserZombie("component.magic_spellbook:resurrect_lesser_zombie"),
        SuperiorGhost("component.magic_spellbook:resurrect_superior_ghost"),
        SuperiorSkeleton("component.magic_spellbook:resurrect_superior_skeleton"),
        SuperiorZombie("component.magic_spellbook:resurrect_superior_zombie"),
        GreaterGhost("component.magic_spellbook:resurrect_greater_ghost"),
        GreaterSkeleton("component.magic_spellbook:resurrect_greater_skeleton"),
        GreaterZombie("component.magic_spellbook:resurrect_greater_zombie"),
    }
}
