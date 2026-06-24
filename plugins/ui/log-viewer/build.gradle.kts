plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.metro)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":design"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
