package org.rsmod.content.quest.area.ardougne.regicide

/**
 * The fractionalising still, one server tick at a time. Nothing here trusts the client: the
 * interface only sends "turn a valve" and "add coal", and every gauge reading, the progress and
 * the outcome are worked out here.
 *
 * Pressure and heat are drawn as needles on 13-step gauges ([needle], ten units a step), green
 * over needles 8-9 and orange from 10 to the end stop at 12, as on the real interface; going past
 * the end stop ([MAX]) is what burns the still out. The tar regulator feeds pressure (faster on its afterburner setting, which also keeps
 * the fire hotter); the pressure valve holds it in its middle position and vents it when opened
 * fully. Coal adds [COAL_HEAT] at once and the fire cools steadily. Distilling only happens while
 * both needles sit in or next to the green ([WORKING]), and fills the bar by one step every
 * [PROGRESS_TICKS] ticks, [TOTAL] steps in all. Either needle reaching its end stop burns the
 * still out: the gauges, valves and bar go back to the start, but the tar stays in the still.
 *
 * The OSRS formulas are not published; these values reproduce the published guides (two coal to
 * start, one more as the heat sags, four at once burns it out, roughly five coal a barrel, about
 * half a minute of distilling) and are collected here so they can be tuned in one place.
 */
class StillSimulation(
    var pressure: Int = 0,
    var heat: Int = 0,
    var pressureValve: PressureValve = PressureValve.CLOSED,
    var tar: TarRegulator = TarRegulator.CLOSED,
    var total: Int = 0,
) {
    private var progressTicks = 0

    enum class PressureValve { CLOSED, CONTROLLED, RELEASED }

    enum class TarRegulator { CLOSED, INTAKE, AFTERBURNER }

    enum class Outcome { NONE, BURNT_OUT, OVER_PRESSURE, DISTILLED }

    val isFinished: Boolean
        get() = total >= TOTAL

    fun addCoal(): Outcome {
        heat += COAL_HEAT
        return if (heat >= MAX) burnOut(Outcome.BURNT_OUT) else Outcome.NONE
    }

    fun turnPressureValve(delta: Int) {
        pressureValve = PressureValve.entries[(pressureValve.ordinal + delta).coerceIn(0, PressureValve.entries.lastIndex)]
    }

    fun turnTarRegulator(delta: Int) {
        tar = TarRegulator.entries[(tar.ordinal + delta).coerceIn(0, TarRegulator.entries.lastIndex)]
    }

    fun step(): Outcome {
        if (isFinished) {
            return Outcome.NONE
        }
        val feed =
            when (tar) {
                TarRegulator.CLOSED -> 0
                TarRegulator.INTAKE -> INTAKE_FEED
                TarRegulator.AFTERBURNER -> AFTERBURNER_FEED
            }
        pressure =
            when (pressureValve) {
                PressureValve.CLOSED -> pressure + feed
                PressureValve.CONTROLLED -> pressure
                PressureValve.RELEASED -> pressure - RELEASE_RATE
            }.coerceIn(0, MAX)
        if (pressure >= MAX) {
            return burnOut(Outcome.OVER_PRESSURE)
        }
        val cooling = if (tar == TarRegulator.AFTERBURNER) AFTERBURNER_COOLING else COOLING
        heat = (heat - cooling).coerceAtLeast(0)
        if (needle(pressure) in WORKING && needle(heat) in WORKING) {
            progressTicks++
            if (progressTicks >= PROGRESS_TICKS) {
                progressTicks = 0
                total++
            }
        }
        return if (isFinished) Outcome.DISTILLED else Outcome.NONE
    }

    private fun burnOut(outcome: Outcome): Outcome {
        pressure = 0
        heat = 0
        total = 0
        progressTicks = 0
        pressureValve = PressureValve.CLOSED
        tar = TarRegulator.CLOSED
        return outcome
    }

    /**
     * `varp.regicide_still_settings` as the interface reads it: the pressure needle on bits 0-12,
     * the heat needle on bits 13-25, the pressure valve on 26-28 and the tar regulator on 29-31,
     * one bit set in each group.
     */
    fun settings(): Int =
        (1 shl needle(pressure)) or
            (1 shl (HEAT_NEEDLE_BIT + needle(heat))) or
            (1 shl (PRESSURE_VALVE_BIT + pressureValve.ordinal)) or
            (1 shl (TAR_REGULATOR_BIT + tar.ordinal))

    companion object {
        const val MAX = 130
        const val NEEDLE_STEPS = 12
        const val UNITS_PER_STEP = 10
        const val TOTAL = 25
        const val COAL_HEAT = 40
        const val COOLING = 3
        const val AFTERBURNER_COOLING = 2
        const val INTAKE_FEED = 4
        const val AFTERBURNER_FEED = 8
        const val RELEASE_RATE = 20
        const val PROGRESS_TICKS = 2

        /** Needles in the green (8-9) or one step either side of it. */
        val WORKING = 7..10
        val GREEN = 8..9

        const val HEAT_NEEDLE_BIT = 13
        const val PRESSURE_VALVE_BIT = 26
        const val TAR_REGULATOR_BIT = 29

        fun needle(value: Int): Int = (value / UNITS_PER_STEP).coerceIn(0, NEEDLE_STEPS)
    }
}
