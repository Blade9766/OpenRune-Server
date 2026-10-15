plugins {
    id("base-conventions")
}

kotlin {
    explicitApi()
}

dependencies {
    implementation(libs.guice)
    implementation(libs.kotlin.inline.logger)
    implementation(libs.kotlin.coroutines.core)
    implementation(projects.api.db)
}
