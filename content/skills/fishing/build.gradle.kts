plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.scriptAdvanced)
    implementation(projects.content.interfaces.bank)
    implementation(projects.api.pluginCommons)
    testImplementation(projects.api.invStorage)
    implementation(projects.api.attr)
    implementation(projects.content.quest)
    implementation(projects.content.skills.utils)
}
