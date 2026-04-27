plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}
