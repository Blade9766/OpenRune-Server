plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

dependencies {
    implementation(projects.api.pluginCommons)
    implementation(projects.api.attr)
    implementation(projects.content.interfaces.omnishop)
    implementation(projects.content.other.consumables)
    testImplementation(libs.fastutil)
}
