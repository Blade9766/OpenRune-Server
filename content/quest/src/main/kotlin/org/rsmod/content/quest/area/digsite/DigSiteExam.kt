package org.rsmod.content.quest.area.digsite

import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.ANIMAL_SKULL
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.SPECIAL_CUP
import org.rsmod.content.quest.area.digsite.TheDigSiteQuest.Companion.TEDDY

/** The three students on the digsite, each holding one answer per exam. */
enum class DigSiteStudent(
    val npc: String,
    val lostItem: String,
    val journalName: String,
) {
    GreenTop("npc.student1", ANIMAL_SKULL, "The student in the green top"),
    PurpleSkirt("npc.student2", TEDDY, "The student in the purple skirt"),
    OrangeShirt("npc.student3", SPECIAL_CUP, "The student in the orange shirt"),
}

/** When an exam option is offered. The correct answer only appears once a student has taught it. */
enum class ExamOptionVisibility {
    Always,
    WhenKnown,
    WhenUnknown,
}

data class ExamOption(val text: String, val visibility: ExamOptionVisibility)

/**
 * One exam question: what the examiner asks, the three options the player is offered, the student
 * who teaches the answer and the line they teach it with.
 *
 * Each question carries four options but only ever shows three - the correct answer takes the
 * place of a decoy once its student has handed it over, which is why answering before asking
 * around is hopeless.
 */
data class ExamQuestion(
    val ask: String,
    val student: DigSiteStudent,
    val notes: String,
    val options: List<ExamOption>,
) {
    val answer: String
        get() = options.first { it.visibility == ExamOptionVisibility.WhenKnown }.text

    fun offered(knowsAnswer: Boolean): List<String> =
        options
            .filter {
                when (it.visibility) {
                    ExamOptionVisibility.Always -> true
                    ExamOptionVisibility.WhenKnown -> knowsAnswer
                    ExamOptionVisibility.WhenUnknown -> !knowsAnswer
                }
            }
            .map { it.text }
}

object DigSiteExams {
    private fun known(text: String) = ExamOption(text, ExamOptionVisibility.WhenKnown)

    private fun unknown(text: String) = ExamOption(text, ExamOptionVisibility.WhenUnknown)

    private fun decoy(text: String) = ExamOption(text, ExamOptionVisibility.Always)

    val LEVEL_1 =
        listOf(
            ExamQuestion(
                ask = "Question 1 - Earth Sciences overview. Can you tell me what Earth Sciences is?",
                student = DigSiteStudent.GreenTop,
                notes =
                    "The study of Earth Sciences is: The study of the earth, its contents and " +
                        "history.",
                options =
                    listOf(
                        known("The study of the earth, its contents and history."),
                        unknown("The study of gardening, planting and fruiting vegetation."),
                        decoy("The study of planets and the history of the worlds."),
                        decoy("The combination of archaeology and vegetarianism."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 1, question 2 - Eligibility. Can you tell me which " +
                        "people are allowed to use the digsite?",
                student = DigSiteStudent.OrangeShirt,
                notes =
                    "The people eligible to use the digsite are: All that have passed the " +
                        "appropriate Earth Sciences exams.",
                options =
                    listOf(
                        unknown("Magic users, miners and their escorts."),
                        decoy("Professors, students and workmen only."),
                        decoy("Local residents, contractors and small pink fish."),
                        known("All that have passed the appropriate Earth Sciences exam."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 1, question 3 - Health and safety. Can you tell me the " +
                        "proper safety points when working on a digsite?",
                student = DigSiteStudent.PurpleSkirt,
                notes =
                    "The proper health and safety points are: Leather gloves and boots to be worn " +
                        "at all times; proper tools must be used.",
                options =
                    listOf(
                        unknown("Heat-resistant clothing to be worn at all times."),
                        decoy("Rubber chickens to be worn on the head at all times."),
                        known("Gloves and boots to be worn at all times; proper tools must be used."),
                        decoy("Protective clothing to be worn; tools kept away from site."),
                    ),
            ),
        )

    val LEVEL_2 =
        listOf(
            ExamQuestion(
                ask =
                    "Question 1 - Sample transportation. Can you tell me how we transport samples?",
                student = DigSiteStudent.OrangeShirt,
                notes =
                    "Correct sample transportation: Samples taken in rough form; kept only in " +
                        "sealed containers.",
                options =
                    listOf(
                        unknown("Samples cut and cleaned before transportation."),
                        decoy("Samples ground and suspended in an acid solution."),
                        decoy("Samples to be given to the melon-collecting monkey."),
                        known("Samples taken in rough form; kept only in sealed containers."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 2, question 2 - Handling of finds. What is the proper " +
                        "way to handle finds?",
                student = DigSiteStudent.PurpleSkirt,
                notes = "Finds handling: Finds must be carefully handled, and gloves worn.",
                options =
                    listOf(
                        unknown("Finds must not be handled by anyone."),
                        known("Finds must be carefully handled, and gloves worn."),
                        decoy("Finds to be given to the site workmen."),
                        decoy("Drop them on the floor and jump on them."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 2, question 3 - Rock pick usage. Can you tell me the " +
                        "proper use for a rock pick?",
                student = DigSiteStudent.GreenTop,
                notes =
                    "Correct rock pick usage: Always handle with care; strike the rock cleanly on " +
                        "its cleaving point.",
                options =
                    listOf(
                        unknown("Strike rock repeatedly until powdered."),
                        decoy("Rock pick must be used flat and with a strong force."),
                        known("Always handle with care; strike cleanly on its cleaving point."),
                        decoy("Rock picks are to be used to milk cows on a rainy morning."),
                    ),
            ),
        )

    val LEVEL_3 =
        listOf(
            ExamQuestion(
                ask = "Question 1 - Sample preparation. Can you tell me how we prepare samples?",
                student = DigSiteStudent.PurpleSkirt,
                notes = "Sample preparation: Samples cleaned, and carried only in specimen jars.",
                options =
                    listOf(
                        unknown("Samples may be mixed together safely."),
                        known("Samples cleaned, and carried only in specimen jars."),
                        decoy("Sample types catalogued and carried by hand only."),
                        decoy("Samples to be spread thickly with mashed banana."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 3, question 2 - Specimen brush use. What is the proper " +
                        "way to use a specimen brush?",
                student = DigSiteStudent.GreenTop,
                notes = "Specimen brush use: Brush carefully and slowly using short strokes.",
                options =
                    listOf(
                        unknown("Brush quickly using a wet brush."),
                        known("Brush carefully and slowly using short strokes."),
                        decoy("Dipped in glue and stuck to a sheep's back."),
                        decoy("Brush quickly and with force."),
                    ),
            ),
            ExamQuestion(
                ask =
                    "Earth Sciences level 3, question 3 - Advanced techniques. Can you describe " +
                        "the technique for handling bones?",
                student = DigSiteStudent.OrangeShirt,
                notes =
                    "The proper technique for handling bones is: Handle bones carefully and keep " +
                        "them away from other samples.",
                options =
                    listOf(
                        unknown("Bones must not be taken from the digsite."),
                        decoy("Feed to hungry dogs."),
                        decoy("Bones to be ground and tested for mineral content."),
                        known("Handle bones very carefully and keep them away from other samples."),
                    ),
            ),
        )

    fun questions(level: Int): List<ExamQuestion> =
        when (level) {
            1 -> LEVEL_1
            2 -> LEVEL_2
            else -> LEVEL_3
        }

    /** The question [student] teaches for exam [level]. */
    fun question(level: Int, student: DigSiteStudent): ExamQuestion =
        questions(level).first { it.student == student }
}
