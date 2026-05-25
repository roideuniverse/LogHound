plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    api(project(":core-api"))
    api(compose.runtime)
    api(compose.ui)
    api(compose.foundation)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
