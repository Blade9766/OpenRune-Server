plugins {
    id("base-conventions")
    id("game-cache-test-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(projects.api.config)
    implementation(projects.engine.game)
    implementation(projects.engine.plugin)
    implementation(projects.engine.utilsBits)
}
