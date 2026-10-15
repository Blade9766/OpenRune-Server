plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.achievementDiaryTasks)
    implementation(projects.api.generated)
    implementation(projects.content.skills.runecrafting)
    testImplementation(projects.api.invPlugin)
    testImplementation(projects.api.invStorage)
}
