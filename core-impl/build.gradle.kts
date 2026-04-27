plugins {
    alias(libs.plugins.kotlinJvm)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":database"))
    implementation(libs.kotlinx.coroutinesCore)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}
