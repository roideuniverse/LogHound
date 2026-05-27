plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core-api"))
    implementation(project(":design"))
    implementation(compose.runtime)
    implementation(compose.foundation)
    implementation(compose.material3)
    implementation(compose.ui)
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.sqldelight.sqliteDriver)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
}

sqldelight {
    databases {
        create("SessionsDb") {
            packageName.set("com.roideuniverse.loghound.plugins.sessions.sqldelight")
            dialect(libs.sqldelight.sqliteDialect324)
        }
    }
}
