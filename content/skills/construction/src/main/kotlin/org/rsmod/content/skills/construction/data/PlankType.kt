package org.rsmod.content.skills.construction.data

/** A log the sawmill will cut into a plank, and what it charges for the work. */
enum class PlankType(val logs: String, val plank: String, val label: String, val cost: Int) {
    NORMAL("obj.logs", "obj.woodplank", "Plank", 100),
    OAK("obj.oak_logs", "obj.plank_oak", "Oak plank", 250),
    TEAK("obj.teak_logs", "obj.plank_teak", "Teak plank", 500),
    MAHOGANY("obj.mahogany_logs", "obj.plank_mahogany", "Mahogany plank", 1500);

    companion object {
        fun forLogs(obj: String): PlankType? = entries.firstOrNull { it.logs == obj }
    }
}
