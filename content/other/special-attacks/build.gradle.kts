plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.npc)
    implementation(projects.api.repo)
    implementation(projects.api.specials)
    implementation(projects.api.spells)
}
