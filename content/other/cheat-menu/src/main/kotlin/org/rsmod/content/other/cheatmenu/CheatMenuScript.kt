package org.rsmod.content.other.cheatmenu

import dev.openrune.ServerCacheManager
import dev.openrune.definition.type.widget.IfEvent
import dev.openrune.rscm.RSCM
import dev.openrune.rscm.RSCM.asRSCM
import dev.openrune.rscm.RSCMType
import dev.openrune.types.ItemServerType
import dev.openrune.types.StatType
import dev.or2.central.account.Rights
import jakarta.inject.Inject
import kotlin.math.max
import org.rsmod.api.combat.commons.magic.Spellbook
import org.rsmod.api.config.Constants
import org.rsmod.api.invtx.invAdd
import org.rsmod.api.player.cheat.adminGodMode
import org.rsmod.api.player.cheat.adminInfiniteRunes
import org.rsmod.api.player.cheat.adminMaxHit
import org.rsmod.api.player.cheat.adminNoClip
import org.rsmod.api.player.cheat.adminOneHitKill
import org.rsmod.api.player.hook.TeleportType
import org.rsmod.api.player.output.UpdateRun
import org.rsmod.api.player.output.mes
import org.rsmod.api.player.output.runClientScript
import org.rsmod.api.player.protect.ProtectedAccess
import org.rsmod.api.player.protect.ProtectedAccessLauncher
import org.rsmod.api.player.stat.PlayerSkillXP
import org.rsmod.api.player.stat.stat
import org.rsmod.api.player.stat.statAdvance
import org.rsmod.api.player.stat.statBase
import org.rsmod.api.player.stat.statRestore
import org.rsmod.api.player.stat.statSub
import org.rsmod.api.player.ui.PlayerInterfaceUpdates
import org.rsmod.api.script.onCommand
import org.rsmod.api.script.onIfModalButton
import org.rsmod.api.spells.autocast.MagicSpellbookManager
import org.rsmod.game.cheat.Cheat
import org.rsmod.game.entity.Player
import org.rsmod.game.entity.player.Appearance
import org.rsmod.game.stat.PlayerSkillXPTable
import org.rsmod.map.CoordGrid
import org.rsmod.plugin.scripts.PluginScript
import org.rsmod.plugin.scripts.ScriptContext

/**
 * An administrator cheat menu: `::cheat` opens `interface.cheat_panel` in the side panel, laid out
 * like the house options panel - On/Off radio rows for the god mode / one-hit-kill / max hit /
 * no-rune-cost / no-clip cheats, and stone buttons for healing, teleporting, skills, spawning,
 * appearance and spellbook.
 *
 * The panel keeps the game view clear; the long lists behind its buttons (teleport destinations,
 * skills) open in the scrollable [ProtectedAccess.menu] list, and the short ones in the chatbox
 * option dialogue.
 *
 * Every toggle is backed by an attribute in `org.rsmod.api.player.cheat`, so the cheats stay active
 * until switched off (or until the player logs out) rather than only while the panel is open.
 */
class CheatMenuScript
@Inject
constructor(
    private val protectedAccess: ProtectedAccessLauncher,
    private val spellbooks: MagicSpellbookManager,
) : PluginScript() {
    override fun ScriptContext.startup() {
        adminCommand("cheat", "Open the admin cheat menu", ::openMenu)
        adminCommand("cheatmenu", "Open the admin cheat menu", ::openMenu)
        adminCommand("spellbook", "Switch spellbook: standard, ancient, lunar or arceuus") {
            switchSpellbook(this)
        }
        adminCommand("ohk", "Toggle one-hit-kill on npcs", ::toggleOneHitKill)
        // Command names must not start with an emote name ("no", "run", "sit"...): the client
        // plays that emote for "::<emote>..." and never sends the command.
        adminCommand("freerunes", "Toggle casting spells without runes", ::toggleInfiniteRunes)
        adminCommand("ghost", "Toggle walking through walls and objects", ::toggleNoClip)
        adminCommand("heal", "Fully restore stats, hitpoints, prayer and run energy", ::fullHeal)

        onIfModalButton("component.cheat_panel:close") { ifClose() }
        for (toggle in CheatToggle.entries) {
            onIfModalButton("component.cheat_panel:${toggle.key}_on") {
                if (isAdmin()) setToggle(toggle, true)
            }
            onIfModalButton("component.cheat_panel:${toggle.key}_off") {
                if (isAdmin()) setToggle(toggle, false)
            }
        }
        onIfModalButton("component.cheat_panel:heal") {
            if (isAdmin()) {
                player.fullRestore()
                mes("Stats, hitpoints, prayer points and run energy restored.")
            }
        }
        onIfModalButton("component.cheat_panel:alloff") {
            if (isAdmin()) {
                CheatToggle.entries.forEach { it.set(player, false) }
                player.drawPanel()
                mes("Every cheat has been switched off.")
            }
        }
        onIfModalButton("component.cheat_panel:teleport") { if (isAdmin()) teleportMenu() }
        onIfModalButton("component.cheat_panel:skills") { if (isAdmin()) skillsMenu() }
        onIfModalButton("component.cheat_panel:spawn") { if (isAdmin()) spawnItems() }
        onIfModalButton("component.cheat_panel:looks") { if (isAdmin()) openDesign() }
        onIfModalButton("component.cheat_panel:spellbook") { if (isAdmin()) spellbookMenu() }

        onIfModalButton("component.cheat_teleport:list") {
            val destination = teleportRows().getOrNull(it.comsub)?.second
            if (isAdmin() && destination != null) {
                ifCloseSub(TELEPORT)
                goTo(destination)
            }
        }
        onIfModalButton("component.cheat_teleport:search") {
            if (isAdmin()) {
                ifCloseSub(TELEPORT)
                searchTeleport()
            }
        }
        onIfModalButton("component.cheat_teleport:coords") {
            if (isAdmin()) {
                ifCloseSub(TELEPORT)
                customTeleport()
            }
        }

        for ((key, part) in DESIGN_PARTS) {
            onIfModalButton("component.player_design:${key}_left") {
                if (isAdmin()) player.stepStyle(part, -1)
            }
            onIfModalButton("component.player_design:${key}_right") {
                if (isAdmin()) player.stepStyle(part, 1)
            }
        }
        for ((key, colour) in DESIGN_COLOURS) {
            onIfModalButton("component.player_design:${key}_left") {
                if (isAdmin()) player.stepColour(colour, -1)
            }
            onIfModalButton("component.player_design:${key}_right") {
                if (isAdmin()) player.stepColour(colour, 1)
            }
        }
        onIfModalButton("component.player_design:gender_male") {
            if (isAdmin()) chooseBodyType(Appearance.BODY_TYPE_A)
        }
        onIfModalButton("component.player_design:gender_female") {
            if (isAdmin()) chooseBodyType(Appearance.BODY_TYPE_B)
        }
        onIfModalButton(PRONOUN_BUTTONS) {
            val pronoun = it.comsub - PRONOUN_ROW_OFFSET
            if (isAdmin() && pronoun in Appearance.PRONOUN_HE..Appearance.PRONOUN_THEY) {
                player.appearance.pronoun = pronoun
                vars[PRONOUN_SETTING] = pronoun
            }
        }
        onIfModalButton("component.player_design:confirm") {
            if (isAdmin()) {
                ifCloseSub(DESIGN)
                mes("Appearance updated.")
            }
        }
    }

    private fun ProtectedAccess.isAdmin(): Boolean {
        if (player.modLevel.isAtLeast(Rights.ADMINISTRATOR)) {
            return true
        }
        ifClose()
        return false
    }

    private fun ScriptContext.adminCommand(
        command: String,
        desc: String,
        action: Cheat.() -> Unit,
    ) = onCommand(command) {
        this.desc = desc
        this.requiredRights = Rights.ADMINISTRATOR
        this.cheat(action)
    }

    /* Commands */

    private fun openMenu(cheat: Cheat) =
        with(cheat) { protectedAccess.launch(player) { openPanel() } }

    private fun toggleOneHitKill(cheat: Cheat) =
        with(cheat) {
            player.adminOneHitKill = !player.adminOneHitKill
            player.mes("One-hit-kill ${enabledText(player.adminOneHitKill)}.")
            player.drawPanel()
        }

    private fun toggleInfiniteRunes(cheat: Cheat) =
        with(cheat) {
            player.adminInfiniteRunes = !player.adminInfiniteRunes
            player.mes("Magic rune cost ${runeCostText(player.adminInfiniteRunes)}.")
            player.drawPanel()
        }

    private fun toggleNoClip(cheat: Cheat) =
        with(cheat) {
            player.adminNoClip = !player.adminNoClip
            player.mes("No clip ${enabledText(player.adminNoClip)}.")
            player.drawPanel()
        }

    private fun fullHeal(cheat: Cheat) =
        with(cheat) {
            player.fullRestore()
            player.mes("Stats, hitpoints, prayer points and run energy restored.")
        }

    private fun switchSpellbook(cheat: Cheat) =
        with(cheat) {
            val query = args.firstOrNull().orEmpty()
            val spellbook = findSpellbook(query)
            if (spellbook == null) {
                player.mes("Usage: ::spellbook standard|ancient|lunar|arceuus")
                return@with
            }
            player.setSpellbook(spellbook)
        }

    /* Chatbox dialogue plumbing */

    /**
     * Asks a single chatbox option dialogue and returns the zero-based index of the chosen label.
     *
     * The chatbox dialogue is fixed at two to five options, which is why [select] pages rather than
     * listing everything at once.
     */
    private suspend fun ProtectedAccess.ask(title: String, labels: List<String>): Int =
        when (labels.size) {
            2 -> choice2(labels[0], 0, labels[1], 1, title = title)
            3 -> choice3(labels[0], 0, labels[1], 1, labels[2], 2, title = title)
            4 ->
                choice4(labels[0], 0, labels[1], 1, labels[2], 2, labels[3], 3, title = title)
            5 ->
                choice5(
                    labels[0],
                    0,
                    labels[1],
                    1,
                    labels[2],
                    2,
                    labels[3],
                    3,
                    labels[4],
                    4,
                    title = title,
                )
            else -> error("Chatbox dialogues hold 2-5 options. (size=${labels.size})")
        }

    /**
     * Presents [choices] through the chatbox dialogue, four at a time. The fifth slot advances to
     * the next page, or shows [exitLabel] on the final page.
     *
     * @return the value behind the chosen entry, or `null` if the player picked [exitLabel].
     */
    private suspend fun <T> ProtectedAccess.select(
        title: String,
        choices: List<Choice<T>>,
        exitLabel: String = BACK,
    ): T? {
        require(choices.isNotEmpty()) { "`choices` must not be empty." }
        val pages = choices.chunked(PAGE_SIZE)
        var pageIndex = 0
        while (true) {
            val page = pages[pageIndex]
            val finalPage = pageIndex == pages.lastIndex
            val labels = page.map(Choice<T>::label) + if (finalPage) exitLabel else MORE
            val picked = ask(title, labels)
            if (picked < page.size) {
                return page[picked].value
            }
            if (finalPage) {
                return null
            }
            pageIndex++
        }
    }

    /** Asks a two-option confirmation, returning `true` for "Yes". */
    private suspend fun ProtectedAccess.confirm(title: String): Boolean =
        ask(title, listOf("Yes", "No")) == 0

    /* Side panel */

    private fun ProtectedAccess.openPanel() {
        ifOpenSide(PANEL)
        for (component in panelButtons()) {
            ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        player.drawPanel()
    }

    private fun ProtectedAccess.setToggle(toggle: CheatToggle, enabled: Boolean) {
        if (toggle.get(player) == enabled) {
            return
        }
        toggle.set(player, enabled)
        player.drawPanel()
        val text = if (toggle == CheatToggle.Runes) runeCostText(enabled) else enabledText(enabled)
        mes("${toggle.message} $text.")
    }

    private fun Player.drawPanel() {
        if (!ui.containsModal(PANEL)) {
            return
        }
        runClientScript(
            "clientscript.cheat_panel_draw".asRSCM(RSCMType.CLIENTSCRIPT),
            toggleFlags(this),
        )
    }

    /**
     * Picks from the scrollable list modal, then closes it here: the client closes its own copy on
     * a pick, but the server would otherwise still hold it open and a dialogue suspended after the
     * pick would lose protected access the moment that close arrives.
     */
    private suspend fun ProtectedAccess.pickFromList(title: String, choices: List<String>): Int {
        val picked = menu(title, hotkeys = false, choices = choices)
        ifCloseSub(MENU_INTERFACE)
        return picked
    }

    /* Teleport */

    /**
     * Opens the teleport window: every destination in one scrolling list under its region's
     * heading. A row's index is its position in [teleportRows], so a click needs no other state.
     */
    private fun ProtectedAccess.teleportMenu() {
        val rows = teleportRows()
        ifOpenMainModal(TELEPORT)
        ifSetEvents("component.cheat_teleport:list", rows.indices, IfEvent.Op1)
        ifSetEvents("component.cheat_teleport:search", -1..-1, IfEvent.Op1)
        ifSetEvents("component.cheat_teleport:coords", -1..-1, IfEvent.Op1)
        val encoded =
            rows.joinToString("") { (label, destination) ->
                "${if (destination == null) "H:" else "D:"}$label|"
            }
        player.runClientScript(
            "clientscript.cheat_teleport_draw".asRSCM(RSCMType.CLIENTSCRIPT),
            encoded,
        )
    }

    /** @return `true` if the player teleported. */
    private suspend fun ProtectedAccess.searchTeleport(): Boolean {
        val query = stringDialog("Enter a destination name:")
        val destination = findDestination(query)
        if (destination == null) {
            mes("No teleport destination matching '${query.trim()}'.")
            return false
        }
        goTo(destination)
        return true
    }

    /** @return `true` if the player teleported. */
    private suspend fun ProtectedAccess.customTeleport(): Boolean {
        val x = countDialog("Enter the destination x coordinate:")
        val z = countDialog("Enter the destination z coordinate:")
        val level = countDialog("Enter the destination level (0-3):")
        if (x !in 0..CoordGrid.X_BIT_MASK || z !in 0..CoordGrid.Z_BIT_MASK) {
            mes("Those coordinates are out of bounds.")
            return false
        }
        val dest = CoordGrid(x, z, level.coerceIn(0, CoordGrid.LEVEL_BIT_MASK))
        telejump(dest, TeleportType.Exempt)
        mes("Teleported to $dest.")
        return true
    }

    private fun ProtectedAccess.goTo(destination: TeleportDestination) {
        telejump(destination.coords, TeleportType.Exempt)
        mes("Teleported to ${destination.name} (${destination.coords}).")
    }

    /* Skills */

    private suspend fun ProtectedAccess.skillsMenu() {
        val options = SkillOption.entries
        val picked = pickFromList("Skills and experience", options.map { it.label })
        when (options.getOrNull(picked) ?: return) {
            SkillOption.GiveXp -> giveXp()
            SkillOption.SetLevel -> setLevel()
            SkillOption.ResetOne -> resetSkill()
            SkillOption.MaxAll -> {
                if (confirm("Max every skill?")) {
                    player.setAllStatLevels(MAX_LEVEL)
                    mes("Every skill has been maxed.")
                }
            }
            SkillOption.ResetAll -> {
                if (confirm("Reset every skill?")) {
                    player.setAllStatLevels(1)
                    mes("Every skill has been reset.")
                }
            }
        }
    }

    private suspend fun ProtectedAccess.giveXp() {
        val stat = pickStat() ?: return
        val internal = stat.internal()
        val amount = countDialog("Enter the amount of experience to add:")
        if (amount <= 0) {
            return
        }
        val added = player.statAdvance(internal, amount.toDouble(), rate = 1.0, globalRate = 1.0)
        mes("Added $added ${skillName(stat)} xp (now level ${player.statBase(internal)}).")
    }

    private suspend fun ProtectedAccess.setLevel() {
        val stat = pickStat() ?: return
        val requested = countDialog("Enter the level (${stat.minLevel}-${stat.maxLevel}):")
        val level = requested.coerceIn(stat.minLevel, stat.maxLevel)
        player.setStatLevel(stat, level)
        mes("${skillName(stat)} set to level $level.")
    }

    private suspend fun ProtectedAccess.resetSkill() {
        val stat = pickStat() ?: return
        player.setStatLevel(stat, stat.minLevel)
        mes("${skillName(stat)} reset to level ${stat.minLevel}.")
    }

    private suspend fun ProtectedAccess.pickStat(): StatType? {
        val stats = releasedStats()
        val picked = pickFromList("Which skill?", stats.map { skillName(it) })
        return stats.getOrNull(picked)
    }

    /* Items and appearance */

    private suspend fun ProtectedAccess.spawnItems() {
        while (true) {
            val item =
                objDialog(
                    title = "Search for an item to spawn:",
                    stockMarketRestriction = false,
                    showLastSearched = true,
                )
            val amount = countDialog("Enter spawn quantity:")
            if (amount <= 0) {
                return
            }
            give(item, amount)
            if (!confirm("Spawn another item?")) {
                return
            }
        }
    }

    private fun ProtectedAccess.give(item: ItemServerType, count: Int) {
        val spawned = player.invAdd(player.inv, item.id, count, strict = false).completed()
        if (spawned <= 0) {
            mes("You don't have enough inventory space.")
            return
        }
        mes("Spawned '${item.name}' x $spawned.")
    }

    private fun ProtectedAccess.openDesign() {
        ifOpenMainModal(DESIGN)
        for (component in designButtons()) {
            ifSetEvents(component, -1..-1, IfEvent.Op1)
        }
        ifSetEvents(
            PRONOUN_BUTTONS,
            PRONOUN_ROW_OFFSET + Appearance.PRONOUN_HE..PRONOUN_ROW_OFFSET + Appearance.PRONOUN_THEY,
            IfEvent.Op1,
        )
        vars[DESIGN_BODY_TYPE] = player.appearance.bodyType
        vars[PRONOUN_SETTING] = player.appearance.pronoun
    }

    private fun ProtectedAccess.chooseBodyType(bodyType: Int) {
        if (player.appearance.bodyType != bodyType) {
            player.setBodyType(bodyType)
        }
        vars[DESIGN_BODY_TYPE] = bodyType
    }

    /** Steps a slot through its styles like the designer's arrows; facial hair can also be none. */
    private fun Player.stepStyle(part: AppearancePart, step: Int) {
        val bodyType = appearance.bodyType
        val styles =
            if (part.optional) {
                part.stylesFor(bodyType) + Appearance.NO_IDENT_KIT
            } else {
                part.stylesFor(bodyType)
            }
        val current =
            appearance.identKitSnapshot().getOrNull(part.slot)?.toInt() ?: Appearance.NO_IDENT_KIT
        val index = styles.indexOf(current)
        val next = if (index < 0) styles.first() else styles[(index + step).mod(styles.size)]
        appearance.setIdentKit(part.slot, next)
    }

    private fun Player.stepColour(colour: AppearanceColour, step: Int) {
        val current = appearance.coloursSnapshot().getOrNull(colour.index)?.toInt() ?: 0
        appearance.setColour(colour.index, (current + step).mod(colour.paletteSize))
    }

    /* Spellbook */

    /**
     * Switches the player's active spellbook. [MagicSpellbookManager] also clears any autocast
     * selection, since the autocast spell belongs to the previous book.
     */
    private suspend fun ProtectedAccess.spellbookMenu() {
        val current = spellbooks.activeSpellbook(player)
        val choices =
            Spellbook.entries.map { book ->
                val label = if (book == current) "${spellbookName(book)} (current)" else spellbookName(book)
                Choice(label, book)
            }
        val chosen = select("Which spellbook?", choices) ?: return
        player.setSpellbook(chosen)
    }

    private fun Player.setSpellbook(spellbook: Spellbook) {
        when (val result = spellbooks.setSpellbook(this, spellbook)) {
            is MagicSpellbookManager.ChangeResult.Changed ->
                mes("Spellbook switched to ${spellbookName(result.current)}.")
            is MagicSpellbookManager.ChangeResult.Unchanged ->
                mes("Your spellbook is already ${spellbookName(result.current)}.")
        }
    }

    /* Player helpers */

    private fun Player.fullRestore() {
        restoreAllStats()
        restoreRunEnergy()
    }

    private fun Player.restoreAllStats() {
        for (stat in releasedStats()) {
            statRestore(stat.internal())
        }
    }

    private fun Player.restoreRunEnergy() {
        runEnergy = Constants.run_max_energy
        UpdateRun.energy(this, runEnergy)
    }

    private fun Player.setAllStatLevels(level: Int) {
        for (stat in releasedStats()) {
            setStatLevel(stat, level)
        }
    }

    private fun Player.setStatLevel(stat: StatType, level: Int) {
        val internal = stat.internal()
        val target = level.coerceIn(stat.minLevel, stat.maxLevel)
        val targetXp = PlayerSkillXPTable.getXPFromLevel(target)
        if (statBase(internal) > target) {
            statRevert(internal, target, targetXp)
            return
        }
        val xpDelta = targetXp - statMap.getXP(internal)
        statMap.setCurrentLevel(internal, target.toByte())
        statAdvance(internal, xpDelta.toDouble(), rate = 1.0, globalRate = 1.0)
    }

    /**
     * Lowers [stat] to [targetLevel] and [targetXp]. There is deliberately no shared helper for
     * this: xp reduction is not a standard gameplay operation and only exists for admin tooling.
     */
    private fun Player.statRevert(stat: String, targetLevel: Int, targetXp: Int) {
        statMap.setCurrentLevel(stat, statBase(stat).toByte())
        val levelDelta = stat(stat) - targetLevel
        statMap.setXP(stat, targetXp)
        statMap.setBaseLevel(stat, targetLevel.toByte())
        statSub(stat, constant = max(0, levelDelta), percent = 0)
        appearance.combatLevel = PlayerSkillXP.calculateCombatLevel(this)
        PlayerInterfaceUpdates.updateCombatLevel(this)
    }

    /**
     * Switches body type and swaps every ident-kit slot to that body type's styles.
     *
     * The appearance block sends explicit model ids (see `RspCycle.syncAppearance`), so setting
     * `Appearance.bodyType` alone flips the flag while the character keeps wearing the old body's
     * models - which reads in-game as the switch having done nothing.
     */
    private fun Player.setBodyType(bodyType: Int) {
        appearance.bodyType = bodyType
        val styles = DefaultAppearance.stylesFor(bodyType)
        for (part in AppearancePart.entries) {
            appearance.setIdentKit(part.slot, styles[part.slot])
        }
    }

    private data class Choice<out T>(val label: String, val value: T)

    private enum class SkillOption(val label: String) {
        GiveXp("Give experience"),
        SetLevel("Set a skill level"),
        ResetOne("Reset a skill"),
        MaxAll("Max every skill"),
        ResetAll("Reset every skill"),
    }

    /** The panel's radio rows, in the bit order `cheat_panel_draw` reads them. */
    internal enum class CheatToggle(
        val key: String,
        val message: String,
        val get: (Player) -> Boolean,
        val set: (Player, Boolean) -> Unit,
    ) {
        God("god", "God mode", { it.adminGodMode }, { p, on -> p.adminGodMode = on }),
        OneHitKill("ohk", "One-hit-kill", { it.adminOneHitKill }, { p, on -> p.adminOneHitKill = on }),
        MaxHit("maxhit", "Always max hit", { it.adminMaxHit }, { p, on -> p.adminMaxHit = on }),
        Runes(
            "runes",
            "Magic rune cost",
            { it.adminInfiniteRunes },
            { p, on -> p.adminInfiniteRunes = on },
        ),
        NoClip("noclip", "No clip", { it.adminNoClip }, { p, on -> p.adminNoClip = on }),
    }

    internal companion object {
        /** The chatbox dialogue holds five options; the fifth is reserved for navigation. */
        const val PAGE_SIZE = 4
        const val MORE = "More options..."
        const val BACK = "Back"
        const val MAX_LEVEL = 99

        const val PANEL = "interface.cheat_panel"
        const val TELEPORT = "interface.cheat_teleport"
        const val MENU_INTERFACE = "interface.menu"
        const val DESIGN = "interface.player_design"
        const val DESIGN_BODY_TYPE = "varbit.player_design_bodytype"
        const val PRONOUN_BUTTONS = "component.player_design:pronouns_buttons"
        const val PRONOUN_SETTING = "varbit.settings_transmit_pronouns"

        /** The pronoun dropdown's first row is its highlight, so pronoun rows start at 1. */
        const val PRONOUN_ROW_OFFSET = 1

        /** The designer's style rows by component prefix. */
        private val DESIGN_PARTS =
            mapOf(
                "head" to AppearancePart.Hair,
                "jaw" to AppearancePart.Jaw,
                "torso" to AppearancePart.Torso,
                "arms" to AppearancePart.Arms,
                "hands" to AppearancePart.Hands,
                "legs" to AppearancePart.Legs,
                "feet" to AppearancePart.Feet,
            )

        /** The designer's colour rows by component prefix. */
        private val DESIGN_COLOURS =
            mapOf(
                "hair" to AppearanceColour.Hair,
                "torso_col" to AppearanceColour.Torso,
                "legs_col" to AppearanceColour.Legs,
                "feet_col" to AppearanceColour.Feet,
                "skin" to AppearanceColour.Skin,
            )

        fun designButtons(): List<String> {
            val arrows =
                (DESIGN_PARTS.keys + DESIGN_COLOURS.keys).flatMap { listOf("${it}_left", "${it}_right") }
            return (arrows + listOf("gender_male", "gender_female", "confirm"))
                .map { "component.player_design:$it" }
        }

        /** Region headings (with no destination) followed by their destinations, in list order. */
        fun teleportRows(): List<Pair<String, TeleportDestination?>> =
            TeleportRegion.entries.flatMap { region ->
                listOf(region.label to null) + region.destinations.map { it.name to it }
            }

        private val PANEL_BUTTONS =
            listOf("close", "heal", "alloff", "teleport", "skills", "spawn", "looks", "spellbook")

        fun panelButtons(): List<String> {
            val radios = CheatToggle.entries.flatMap { listOf("${it.key}_on", "${it.key}_off") }
            return (PANEL_BUTTONS + radios).map { "component.cheat_panel:$it" }
        }

        fun toggleFlags(player: Player): Int =
            CheatToggle.entries.foldIndexed(0) { bit, flags, toggle ->
                if (toggle.get(player)) flags or (1 shl bit) else flags
            }

        const val GREEN = "0dc10d"
        const val RED = "ff0000"

        fun releasedStats(): List<StatType> =
            ServerCacheManager.getStats()
                .values
                .filterNot(StatType::unreleased)
                .sortedBy(StatType::displayName)

        fun StatType.internal(): String = RSCM.getReverseMapping(RSCMType.STAT, id)

        /** Matches a typed teleport name: exact, then prefix, then substring. */
        fun findDestination(input: String): TeleportDestination? {
            val query = input.trim().lowercase()
            if (query.isEmpty()) {
                return null
            }
            val all = TeleportRegion.entries.flatMap(TeleportRegion::destinations)
            return all.firstOrNull { it.name.lowercase() == query }
                ?: all.firstOrNull { it.name.lowercase().startsWith(query) }
                ?: all.firstOrNull { it.name.lowercase().contains(query) }
        }

        /**
         * Colours the toggle state, matching the confirm/cancel labels the bank interface uses for
         * its own option text.
         */
        fun state(enabled: Boolean): String =
            if (enabled) "<col=$GREEN>ON</col>" else "<col=$RED>OFF</col>"

        fun skillName(stat: StatType): String = stat.displayName.replaceFirstChar(Char::uppercase)
        fun enabledText(enabled: Boolean): String = if (enabled) "enabled" else "disabled"

        fun runeCostText(infinite: Boolean): String = if (infinite) "removed" else "restored"

        fun spellbookName(spellbook: Spellbook): String =
            when (spellbook) {
                Spellbook.Standard -> "Standard"
                Spellbook.Ancients -> "Ancient"
                Spellbook.Lunars -> "Lunar"
                Spellbook.Arceuus -> "Arceuus"
            }

        /** Matches a typed spellbook name: `ancient`, `ancients`, `lunar`, `arc`... */
        fun findSpellbook(input: String): Spellbook? {
            val query = input.trim().lowercase()
            if (query.isEmpty()) {
                return null
            }
            return Spellbook.entries.firstOrNull { spellbookName(it).lowercase() == query }
                ?: Spellbook.entries.firstOrNull { it.name.lowercase() == query }
                ?: Spellbook.entries.firstOrNull { spellbookName(it).lowercase().startsWith(query) }
        }
    }
}
