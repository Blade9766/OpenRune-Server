plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.attr)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.scriptAdvanced)
    implementation(projects.api.specials)
    implementation(projects.api.spells)
    implementation(projects.content.other.poison)
}
