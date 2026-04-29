plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    api(project(":core-api"))
    api(libs.kotlinx.coroutinesCore)
    api(compose.runtime)
    api(compose.foundation)
    api(compose.material3)
    api(compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(compose.desktop.uiTestJUnit4)
    testImplementation(compose.desktop.currentOs)
}
