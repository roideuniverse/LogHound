plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    api(libs.kotlinx.coroutinesCore)
    api(compose.runtime)
    api(compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
