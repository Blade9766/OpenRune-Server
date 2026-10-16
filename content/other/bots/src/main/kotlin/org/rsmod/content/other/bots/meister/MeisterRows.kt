package org.rsmod.content.other.bots.meister

typealias SettingsChange = ((MeisterSettings) -> MeisterSettings)?

sealed interface MeisterControl {
    data class Stepper(val down: SettingsChange, val up: SettingsChange) : MeisterControl

    data class Toggle(val label: String, val change: SettingsChange) : MeisterControl
}

data class MeisterRow(val label: String, val value: String, val help: String, val control: MeisterControl)

/** Master Meister's settings as pages of rows for the Bot Settings window. */
object MeisterRows {
    fun all(settings: MeisterSettings): List<MeisterRow> =
        MeisterKnobs.SWITCHES.map { switch(settings, it) } + MeisterKnobs.ALL.map { knob(settings, it) }

    fun pageCount(perPage: Int): Int = (MeisterKnobs.SWITCHES.size + MeisterKnobs.ALL.size + perPage - 1) / perPage

    fun page(settings: MeisterSettings, page: Int, perPage: Int): List<MeisterRow> = all(settings).drop(page * perPage).take(perPage)

    private fun switch(settings: MeisterSettings, switch: MeisterSwitch): MeisterRow {
        val on = switch.get(settings)
        return MeisterRow(
            label = switch.label,
            value = if (on) "on" else "off",
            help = switch.help,
            control = MeisterControl.Toggle(if (on) "Turn off" else "Turn on", { s -> switch.set(s, !on) }),
        )
    }

    private fun knob(settings: MeisterSettings, knob: MeisterKnob): MeisterRow {
        val value = knob.get(settings)
        val range = knob.range
        return MeisterRow(
            label = knob.label,
            value = knob.format(value),
            help = knob.help,
            control =
                MeisterControl.Stepper(
                    down = if (value > range.first) { s -> knob.set(s, maxOf(range.first, value - knob.step)) } else null,
                    up = if (value < range.last) { s -> knob.set(s, minOf(range.last, value + knob.step)) } else null,
                ),
        )
    }
}
