package org.rsmod.content.interfaces.settings.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.StructType
import dev.openrune.types.enums.enum

data class SettingCategory(private val struct: StructType) {

    val id: Int = struct.getParamAsInt("param.settings_category_id")
    val name: String = struct.getParamAsString("param.settings_category_name")
    val settings: List<Setting> = populateSettings()

    /**
     * The client draws one clickzone child per enum entry, blocked or not, so the settings must
     * stay in enum key order for `comsub` to address the right one.
     */
    private fun populateSettings(): List<Setting> {
        val enumId = struct.params?.get(SETTINGS_CATEGORY_SETTINGS_LIST_PARAM) as? Int ?: return emptyList()
        return enum<Int, Int>(enumId)
            .filterValuesNotNull()
            .backing
            .entries
            .sortedBy { it.key }
            .mapNotNull { (_, structId) -> ServerCacheManager.getStruct(structId)?.let(::Setting) }
    }

    override fun toString(): String =
        "SettingCategory(id=$id, name='$name', settings=$settings)"

    companion object {
        private const val SETTINGS_CATEGORY_SETTINGS_LIST_PARAM = 745
    }
}
