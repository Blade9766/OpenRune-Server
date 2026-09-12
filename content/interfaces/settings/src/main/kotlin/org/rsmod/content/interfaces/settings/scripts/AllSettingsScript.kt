package org.rsmod.content.interfaces.settings.scripts

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import jakarta.inject.Inject
import org.rsmod.api.attr.AttributeKey
import org.rsmod.api.player.music.MusicPlayer
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.ui.ifCloseOverlay
import org.rsmod.api.player.ui.ifSetEvents
import org.rsmod.api.player.vars.VarPlayerIntMapSetter
import org.rsmod.api.player.vars.boolVarBit
import org.rsmod.api.player.vars.intVarBit
import org.rsmod.api.script.onDialogInput
import org.rsmod.api.script.onIfOpen
import org.rsmod.api.script.onIfOverlayButton
import org.rsmod.api.script.onPlayerLogin
import org.rsmod.api.table.SettingsConfigsRow
import org.rsmod.events.EventBus
import org.rsmod.game.entity.Player
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

val SettingsConfigsRow.varValue: String?
    get() =
        varbit?.let { RSCM.getReverseMapping(RSCMType.VARBIT, it) }
            ?: varp?.let { RSCM.getReverseMapping(RSCMType.VARP, it) }

/**
 * Drives the "All Settings" window (`interface.settings`).
 *
 * The client builds the list from the same category enums the server reads, creating exactly one
 * `settings_clickzone` child per enum entry (hidden entries included), so an `if_button` comsub
 * maps straight to the entry index of the browsed category, or of every searchable category
 * concatenated while searching. Dropdown entries take three children each, and volume sliders
 * report through a script trigger carrying `(setting id, volume)`.
 */
class AllSettingsScript
@Inject
constructor(private val musicPlayer: MusicPlayer, private val eventBus: EventBus) : PluginScript() {

    private class ConfirmationSetting(
        val settingId: Int,
        val title: String,
        val action: Player.() -> Unit,
    )

    private var Player.settingsCategory by intVarBit("varbit.settings_category")
    private var Player.selectedSetting by intVarBit("varbit.settings_selected_setting")
    private var Player.isSearching by intVarBit("varbit.floater_is_searching")
    private var Player.searchListenForKeyboard by intVarBit("varbit.floater_search_listen_for_keyboard")
    private var Player.chatboxOpened by intVarBit("varbit.floater_chatbox_opened")
    private var Player.settingsColourModalOpened by intVarBit("varbit.settings_colour_modal_opened")
    private val Player.newAccount by boolVarBit("varbit.new_player_account")

    private var Player.selectedCategory: Int?
        get() = attr[SELECTED_CATEGORY]
        set(value) {
            if (value == null) {
                attr.remove(SELECTED_CATEGORY)
            } else {
                attr.put(SELECTED_CATEGORY, value)
            }
        }

    override fun ScriptContext.startup() {
        onPlayerLogin { player.setDefaultOptions() }

        onDialogInput {
            if (player.settingsColourModalOpened == 1) {
                player.closeColourPicker(count)
            }
        }

        onIfOpen("interface.settings") {
            player.updateIfEvents()
            player.settingsColourModalOpened = 0
            player.chatboxOpened = 0
        }

        onIfOpen("interface.colour_pallet") { player.updateColourPickerEvents() }

        onIfOverlayButton("component.settings:close") {
            player.ifCloseOverlay("interface.settings", eventBus)
            runClientScript(CHATDEFAULT_RESTOREINPUT_CLIENTSCRIPT)
            player.settingsColourModalOpened = 0
            player.chatboxOpened = 0
        }

        onIfOverlayButton("component.settings:searchbar_image") {
            player.selectedCategory = SEARCH_CATEGORY
            player.beginSearch()
        }

        onIfOverlayButton("component.settings:categories_clickzone") {
            player.selectedCategory = it.comsub
            player.settingsCategory = it.comsub
            player.endSearch()
            runClientScript(CHATDEFAULT_RESTOREINPUT_CLIENTSCRIPT)
        }

        onIfOverlayButton("component.settings:tooltip_inside_if_clickzone") {
            VarPlayerIntMapSetter.toggle(player, "varbit.settings_disable_tooltip_in_interface")
        }

        onIfOverlayButton("component.settings:declutter_button_clickzone") {
            VarPlayerIntMapSetter.toggle(player, "varbit.option_settings_declutter")
        }

        onIfOverlayButton("component.settings_side:settings_open") {
            ifOpenOverlay("interface.settings")
            player.selectedCategory = 0
            player.settingsCategory = 0
            player.endSearch()
        }

        onIfOverlayButton("component.settings:dropdown_buttons") {
            val setting = Settings.findSetting(player.selectedSetting) ?: return@onIfOverlayButton
            selectDropdownOption(setting, it.comsub / DROPDOWN_CHILDREN_PER_ENTRY)
        }

        onIfOverlayButton("component.settings:settings_clickzone") {
            val settings =
                player.selectedCategory?.let { index -> Settings.getCategory(index)?.settings }
                    ?: Settings.allSettings
            val setting = settings.getOrNull(it.comsub) ?: return@onIfOverlayButton
            selectSetting(setting)
        }
    }

    private fun Player.beginSearch() {
        isSearching = 1
        searchListenForKeyboard = 1
    }

    private fun Player.endSearch() {
        isSearching = 0
        searchListenForKeyboard = 0
    }

    private fun Player.updateIfEvents() {
        ifSetEvents("component.settings:close", -1..-1, IfEvent.Op1, IfEvent.Op2)
        ifSetEvents("component.settings:searchbar_image", -1..-1, IfEvent.Op1)
        ifSetEvents("component.settings:tooltip_inside_if_clickzone", -1..-1, IfEvent.Op1)
        ifSetEvents("component.settings:declutter_button_clickzone", -1..-1, IfEvent.Op1)
        ifSetEvents("component.settings:categories_clickzone", 0..MAX_CATEGORIES, IfEvent.Op1)
        ifSetEvents("component.settings:dropdown_buttons", 0..MAX_CHILDREN, IfEvent.Op1)
        ifSetEvents(
            "component.settings:settings_clickzone",
            0..MAX_CHILDREN,
            IfEvent.Op1,
            IfEvent.ScriptTrigger,
        )
    }

    private fun Player.updateColourPickerEvents() {
        ifSetEvents("component.colour_pallet:pallet_colours", 0..MAX_CHILDREN, IfEvent.Op1)
        ifSetEvents("component.colour_pallet:pallet_custom_clickzone", 0..4, IfEvent.Op1)
        ifSetEvents("component.colour_pallet:cancel", -1..-1, IfEvent.Op1)
        ifSetEvents("component.colour_pallet:submit", -1..-1, IfEvent.Op1)
    }

    private suspend fun ProtectedAccess.selectSetting(setting: Setting) {
        player.chatboxOpened = 0
        if (setting.id < 0) {
            return
        }
        player.selectedSetting = setting.id

        when (setting.type) {
            SettingType.CHECKBOX -> toggleSetting(setting)
            SettingType.INPUT -> setNumberSetting(setting)
            SettingType.COLOUR_PICKER -> openColourPicker(setting)
            SettingType.BUTTON -> clickButton(setting)
            // Sliders, dropdowns and keybinds report through their own components.
            else -> Unit
        }
    }

    private fun ProtectedAccess.toggleSetting(setting: Setting) {
        val varValue = setting.row?.varValue ?: return
        when (setting.id) {
            COLLECTION_NEW_ITEM_CHAT_SETTING_ID -> toggleSharedVarbitFlag(varValue, bit = 0)
            COLLECTION_NEW_ITEM_POPUP_SETTING_ID -> toggleSharedVarbitFlag(varValue, bit = 1)
            else -> VarPlayerIntMapSetter.toggle(player, varValue)
        }
    }

    private fun ProtectedAccess.toggleSharedVarbitFlag(varbitName: String, bit: Int) {
        val varbit = ServerCacheManager.getVarbit(varbitName.asRSCM(RSCMType.VARBIT)) ?: return
        VarPlayerIntMapSetter.toggleBit(player, varbit, bit)
    }

    private fun ProtectedAccess.selectDropdownOption(setting: Setting, option: Int) {
        if (setting.id == ClientLayout.SETTING_ID) {
            val layout = ClientLayout.fromDropdownOption(option) ?: return
            player.selectClientLayout(layout, eventBus)
            return
        }
        SettingUtils.setDropdown(player, option, setting)
    }

    private suspend fun ProtectedAccess.setNumberSetting(setting: Setting) {
        player.chatboxOpened = 1
        player.isSearching = 0
        val value = countDialog(setting.row?.prompt ?: "Enter amount:")
        player.chatboxOpened = 0
        SettingUtils.setNumber(player, value, setting)
    }

    private fun ProtectedAccess.openColourPicker(setting: Setting) {
        if (setting.row?.varValue == null) {
            return
        }
        player.settingsColourModalOpened = 1
        ifOpenOverlay("interface.colour_pallet", COLOUR_PALLET_TARGET)
        runClientScript(
            COLOUR_PALLET_OPEN_CLIENTSCRIPT,
            COLOUR_PALLET_TARGET.asRSCM(),
            SettingUtils.getColour(player, setting),
        )
        runClientScript(CHATDEFAULT_STOPINPUT_CLIENTSCRIPT)
    }

    /** The picker's Save button resumes a count dialog with the colour; Cancel sends max int. */
    private fun Player.closeColourPicker(input: Int) {
        settingsColourModalOpened = 0
        ifCloseOverlay("interface.colour_pallet", eventBus)
        if (input == COLOUR_PALLET_CANCELLED) {
            return
        }
        val setting = Settings.findSetting(selectedSetting) ?: return
        SettingUtils.setColour(this, input, setting)
    }

    private suspend fun ProtectedAccess.clickButton(setting: Setting) {
        when (setting.id) {
            ENABLE_WILDERNESS_TELEPORT_WARNINGS_SETTING_ID ->
                player.setSettings(wildernessTeleportWarningSettingIds, value = 1)
            DISABLE_WILDERNESS_TELEPORT_WARNINGS_SETTING_ID ->
                player.setSettings(wildernessTeleportWarningSettingIds, value = 0)
            // Tablet warning vars are inverted: 0 shows the warning.
            ENABLE_TABLET_WARNINGS_SETTING_ID -> player.setSettings(tabletWarningSettingIds, value = 0)
            DISABLE_TABLET_WARNINGS_SETTING_ID -> player.setSettings(tabletWarningSettingIds, value = 1)
            else -> confirmSetting(setting)
        }
    }

    private suspend fun ProtectedAccess.confirmSetting(setting: Setting) {
        val confirmation = confirmationSettings.find { it.settingId == setting.id } ?: return
        val confirmed = choice2("Yes.", true, "No.", false, title = confirmation.title)
        if (confirmed) {
            confirmation.action(player)
        }
    }

    private fun Player.setSettings(settingIds: Iterable<Int>, value: Int) {
        for (id in settingIds) {
            val varValue = Settings.findSetting(id)?.row?.varValue ?: continue
            VarPlayerIntMapSetter.set(this, varValue, value)
        }
    }

    private fun Player.resetVolumeSliders() {
        setVolume(
            "varp.option_master_volume",
            "varbit.option_master_volume_desktop",
            MASTER_VOLUME_DEFAULT,
        )
        setVolume("varp.option_music", "varbit.option_music_desktop", MUSIC_VOLUME_DEFAULT)
        setVolume("varp.option_sounds", "varbit.option_sounds_desktop", SOUND_VOLUME_DEFAULT)
        setVolume(
            "varp.option_areasounds",
            "varbit.option_areasounds_desktop",
            AREA_SOUND_VOLUME_DEFAULT,
        )

        setSavedVolume("varbit.option_master_volume_saved", "varbit.option_master_volume_saved_desktop")
        setSavedVolume("varbit.option_music_saved", "varbit.option_music_saved_desktop")
        setSavedVolume("varbit.option_sounds_saved", "varbit.option_sounds_saved_desktop")
        setSavedVolume("varbit.option_areasounds_saved", "varbit.option_areasounds_saved_desktop")
    }

    private fun Player.setDefaultOptions() {
        if (!newAccount) return

        resetDefaultKeybinds()
        resetVolumeSliders()

        VarPlayerIntMapSetter.set(this, "varbit.keybinding_esc_to_close", 1)
        VarPlayerIntMapSetter.set(this, "varbit.option_collection_new_item", 3)
        VarPlayerIntMapSetter.set(this, "varp.option_attackpriority", 2)
        VarPlayerIntMapSetter.set(this, "varp.option_attackpriority_npc", 2)
        VarPlayerIntMapSetter.set(this, "varbit.bank_hidedepositinv", 1)

        setSettings(wildernessTeleportWarningSettingIds, value = 1)
    }

    private fun Player.setVolume(legacy: String, desktop: String, value: Int) {
        VarPlayerIntMapSetter.set(this, legacy, value)
        VarPlayerIntMapSetter.set(this, desktop, value)
    }

    private fun Player.setSavedVolume(legacy: String, desktop: String) {
        VarPlayerIntMapSetter.set(this, legacy, UNMUTE_VOLUME)
        VarPlayerIntMapSetter.set(this, desktop, UNMUTE_VOLUME)
    }

    private fun Player.resetDefaultKeybinds() {
        for ((setting, value) in Settings.getDefaultKeybinds()) {
            val varValue = setting.row?.varValue ?: continue
            VarPlayerIntMapSetter.set(this, varValue, value)
        }
    }

    private fun Player.resetColours(settingIds: Iterable<Int>) {
        for (id in settingIds) {
            val setting = Settings.findSetting(id) ?: continue
            SettingUtils.setColour(this, setting.defaultColour, setting)
        }
    }

    private val confirmationSettings by lazy {
        listOf(
            ConfirmationSetting(
                settingId = RESET_OPAQUE_CHAT_COLOURS_SETTING_ID,
                title = "Are you sure you want to reset your opaque chatbox colours?",
            ) {
                resetColours(opaqueChatColourSettingIds)
                mes("Default opaque colours restored.")
            },
            ConfirmationSetting(
                settingId = RESET_TRANSPARENT_CHAT_COLOURS_SETTING_ID,
                title = "Are you sure you want to reset your transparent chatbox colours?",
            ) {
                resetColours(transparentChatColourSettingIds)
                mes("Default transparent colours restored.")
            },
            ConfirmationSetting(
                settingId = RESET_SPLIT_CHAT_COLOURS_SETTING_ID,
                title = "Are you sure you want to reset your split chat colours?",
            ) {
                resetColours(splitChatColourSettingIds)
                mes("Default split chat colours restored.")
            },
            ConfirmationSetting(
                settingId = RESET_QUEST_LIST_COLOURS_SETTING_ID,
                title = "Are you sure you want to reset your quest list text colours?",
            ) {
                resetColours(questListColourSettingIds)
                mes("Default quest list text colours restored.")
            },
            ConfirmationSetting(
                settingId = RESET_MUSIC_PLAYER_COLOURS_SETTING_ID,
                title = "Are you sure you want to reset your music player text colours?",
            ) {
                resetColours(musicPlayerColourSettingIds)
                mes("Default music player text colours restored.")
            },
            ConfirmationSetting(
                settingId = RESET_VOLUME_SLIDERS_SETTING_ID,
                title = "Are you sure you want to reset your volume sliders?",
            ) {
                resetVolumeSliders()
                mes("Your volume sliders have been reset to their default values.")
            },
            ConfirmationSetting(
                settingId = RESET_KEYBINDS_SETTING_ID,
                title = "Are you sure you want to reset your keybinds?",
            ) {
                resetDefaultKeybinds()
                mes("Your keybinds have been reset to their default values.")
            },
        ) +
            (1..MusicPlayer.PLAYLIST_COUNT).map { playlist ->
                ConfirmationSetting(
                    settingId = WIPE_PLAYLIST_SETTING_IDS.getValue(playlist),
                    title = "Are you sure you want to wipe playlist $playlist?",
                ) {
                    musicPlayer.clearPlaylist(this, playlist)
                    mes("Playlist $playlist has been wiped.")
                }
            }
    }

    private companion object {
        private val SELECTED_CATEGORY = AttributeKey<Int>()

        private const val SEARCH_CATEGORY = -1
        private const val MAX_CATEGORIES = 31
        private const val MAX_CHILDREN = 512
        private const val DROPDOWN_CHILDREN_PER_ENTRY = 3

        private const val COLOUR_PALLET_TARGET = "component.settings:popup"
        private const val COLOUR_PALLET_OPEN_CLIENTSCRIPT = 4185
        private const val COLOUR_PALLET_CANCELLED = Int.MAX_VALUE
        private const val CHATDEFAULT_RESTOREINPUT_CLIENTSCRIPT = 2158
        private const val CHATDEFAULT_STOPINPUT_CLIENTSCRIPT = 4020

        private const val COLLECTION_NEW_ITEM_CHAT_SETTING_ID = 85
        private const val COLLECTION_NEW_ITEM_POPUP_SETTING_ID = 171

        private const val RESET_KEYBINDS_SETTING_ID = 58
        private const val DISABLE_TABLET_WARNINGS_SETTING_ID = 74
        private const val ENABLE_TABLET_WARNINGS_SETTING_ID = 75
        private const val DISABLE_WILDERNESS_TELEPORT_WARNINGS_SETTING_ID = 76
        private const val ENABLE_WILDERNESS_TELEPORT_WARNINGS_SETTING_ID = 77
        private const val RESET_OPAQUE_CHAT_COLOURS_SETTING_ID = 107
        private const val RESET_TRANSPARENT_CHAT_COLOURS_SETTING_ID = 108
        private const val RESET_SPLIT_CHAT_COLOURS_SETTING_ID = 109
        private const val RESET_QUEST_LIST_COLOURS_SETTING_ID = 228
        private const val RESET_VOLUME_SLIDERS_SETTING_ID = 468
        private const val RESET_MUSIC_PLAYER_COLOURS_SETTING_ID = 489
        private val WIPE_PLAYLIST_SETTING_IDS = mapOf(1 to 490, 2 to 491, 3 to 492)

        private val wildernessTeleportWarningSettingIds = listOf(60, 61, 62, 63, 64)
        private val tabletWarningSettingIds = listOf(67, 68, 69, 70, 71, 72, 73)
        private val opaqueChatColourSettingIds =
            listOf(87, 89, 92, 94, 97, 99, 101, 103, 105, 193, 196, 198, 434)
        private val transparentChatColourSettingIds =
            listOf(88, 90, 93, 95, 98, 100, 102, 104, 106, 194, 197, 199, 435)
        private val splitChatColourSettingIds = listOf(91, 96)
        private val questListColourSettingIds = listOf(224, 225, 226, 227)
        private val musicPlayerColourSettingIds = listOf(485, 486, 487, 488)

        private const val MASTER_VOLUME_DEFAULT = 100
        private const val MUSIC_VOLUME_DEFAULT = 20
        private const val SOUND_VOLUME_DEFAULT = 45
        private const val AREA_SOUND_VOLUME_DEFAULT = 30
        private const val UNMUTE_VOLUME = 5
    }
}
