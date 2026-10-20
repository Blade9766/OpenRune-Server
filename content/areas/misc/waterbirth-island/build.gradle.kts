plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.content.quest)
    implementation(projects.content.skills.slayer)
    testImplementation(libs.fastutil)
}
