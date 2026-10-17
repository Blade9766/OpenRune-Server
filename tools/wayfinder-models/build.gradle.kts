plugins {
    id("base-conventions")
}

dependencies {
    implementation(libs.or2.all.cache)
    implementation(libs.netty.buffer)
}

tasks.register<JavaExec>("buildWayfinderModels") {
    group = "application"
    description =
        "Builds the wayfinder mages' custom models from cache parts into " +
            "content/travel/wayfinder/pack/src/main/resources/pack/models, with preview renders in " +
            "build/wayfinder-models. Example: ./gradlew :tools:wayfinder-models:buildWayfinderModels"
    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("org.rsmod.tools.wayfindermodels.WayfinderModelsKt")
    workingDir = rootProject.projectDir
}
