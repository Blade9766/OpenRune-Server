plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    api(projects.api.generated)
    api(projects.api.player)
    implementation(projects.api.playerOutput)
    implementation(projects.engine.game)
    implementation(projects.engine.objtx)
}
