plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
}

dependencies {
    implementation(libs.kotlinx.coroutinesCore)
    implementation(compose.runtime)
    implementation(compose.ui)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
