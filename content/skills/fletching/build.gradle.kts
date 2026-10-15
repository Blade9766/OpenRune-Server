plugins {
    id("base-conventions")
}

dependencies {
    implementation(projects.api.player)
    implementation(projects.api.pluginCommons)
    implementation(projects.api.script)
    implementation(projects.api.shops)
    implementation(projects.content.skills.utils)
    implementation(projects.engine.game)
}
