plugins {
    id("base-conventions")
    id("integration-test-suite")
}

dependencies {
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.api.shops)
    implementation(projects.content.generic.genericLocs)
    implementation(projects.content.interfaces.bank)
    implementation(projects.content.quest)
}
