package org.rsmod.content.skills.construction.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.stat.constructionLvl
import org.rsmod.api.repo.obj.ObjRepository
import org.rsmod.api.script.onOpLoc1
import org.rsmod.api.stats.xpmod.XpModifiers
import org.rsmod.content.skills.construction.Construction
import org.rsmod.content.skills.construction.data.Buildable
import org.rsmod.content.skills.construction.data.Flatpacks
import org.rsmod.content.skills.construction.data.FurnitureRows
import org.rsmod.content.skills.construction.data.RoomType
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * Packing furniture at a workbench. Old School lists the categories in its own menu, drawn by a
 * clientscript that is not in the dumps, so the categories are a plain list here; the pieces in one
 * are shown in the regular furniture creation menu.
 */
class WorkbenchScript
@Inject
constructor(
    private val xpMods: XpModifiers,
    private val objRepo: ObjRepository,
) : PluginScript() {
    override fun ScriptContext.startup() {
        for ((bench, maxLevel) in Flatpacks.WORKBENCHES) {
            onOpLoc1(bench) { workAt(maxLevel) }
        }
    }

    private suspend fun ProtectedAccess.workAt(maxLevel: Int) {
        val categories = CATEGORIES.filter { category -> category.options.any { it.level <= maxLevel } }
        val choice = menu("Furniture Creation Menu", hotkeys = false, choices = categories.map { it.label })
        val category = categories.getOrNull(choice) ?: return
        ifClose()

        val options = category.options.filter { it.level <= maxLevel }
        val entries =
            options.map { option ->
                PohInterfaces.FurnitureEntry(
                    row = FurnitureRows.of(option)!!.rowId,
                    level = option.level,
                    materials = option.materials.map { "${objName(it.obj)}: ${it.count}" },
                    buildable = player.constructionLvl >= option.level && hasMaterials(option),
                )
            }
        val slot = PohInterfaces.selectFurniture(this, entries) ?: return
        pack(options[slot])
    }

    private suspend fun ProtectedAccess.pack(option: Buildable) {
        val flatpack = Flatpacks.of(option) ?: return
        if (player.constructionLvl < option.level) {
            mes("You need a Construction level of ${option.level} to make that.")
            return
        }
        if (Construction.HAMMER !in inv || Construction.SAW !in inv) {
            mes("You need a hammer and a saw to make a flatpack.")
            return
        }
        if (!hasMaterials(option)) {
            mes("You do not have the materials to make that.")
            return
        }
        anim(Construction.BUILD_ANIM)
        delay(PACK_CYCLE)
        resetAnim()
        if (!hasMaterials(option)) {
            mes("You do not have the materials to make that.")
            return
        }
        for (material in option.materials) {
            invDel(inv, material.obj, material.count)
        }
        invAddOrDrop(objRepo, flatpack)
        statAdvance(Construction.STAT, option.xp * xpMods.get(player, Construction.STAT))
        mes("You make a flatpack of the ${option.label.lowercase()}.")
    }

    private fun ProtectedAccess.hasMaterials(option: Buildable): Boolean =
        option.materials.all { invTotal(inv, it.obj) >= it.count }

    private fun objName(obj: String): String =
        ServerCacheManager.getItem(obj.asRSCM(RSCMType.OBJ))?.name
            ?: obj.removePrefix("obj.").replace('_', ' ')

    private class Category(val label: String, val options: List<Buildable>)

    private companion object {
        const val PACK_CYCLE = 8

        /**
         * One category per kind of hotspot, in room order. The same hotspot turns up in more than
         * one room - every room has a rug space - so categories are told apart by what they hold.
         */
        val CATEGORIES: List<Category> by lazy {
            RoomType.entries
                .flatMap { it.hotspots }
                .map { group ->
                    Category(
                        group.label.removeSuffix(" space"),
                        group.options.filter { Flatpacks.of(it) != null },
                    )
                }
                .filter { it.options.isNotEmpty() }
                .distinctBy { category -> category.options.map { it.label } }
        }
    }
}
