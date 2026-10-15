plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.areaChecker)
    implementation(projects.api.bosses)
    implementation(projects.api.combatAchievementTasks)
    implementation(projects.api.dropTable)
    implementation(projects.api.dropTablePlugin)
    implementation(projects.content.drops)
    implementation(projects.content.interfaces.collectionLog)
    implementation(projects.content.quest)
    testImplementation(libs.fastutil)
    testImplementation(libs.or2.all.cache)
}
