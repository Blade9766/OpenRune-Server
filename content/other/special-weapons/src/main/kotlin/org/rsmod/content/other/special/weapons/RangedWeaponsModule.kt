package org.rsmod.content.other.special.weapons

import org.rsmod.api.weapons.WeaponMap
import org.rsmod.content.other.special.weapons.ranged.CrystalBowWeapons
import org.rsmod.content.other.special.weapons.ranged.DarkBowWeapons
import org.rsmod.content.other.special.weapons.ranged.RevenantBowWeapons
import org.rsmod.plugin.module.PluginModule

class RangedWeaponsModule : PluginModule() {
    override fun bind() {
        addSetBinding<WeaponMap>(DarkBowWeapons::class.java)
        addSetBinding<WeaponMap>(CrystalBowWeapons::class.java)
        addSetBinding<WeaponMap>(RevenantBowWeapons::class.java)
    }
}
