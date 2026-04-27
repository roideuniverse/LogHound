plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.sqldelight)
}

dependencies {
    implementation(project(":core-api"))
    implementation(libs.kotlinx.coroutinesCore)
    implementation(libs.sqldelight.sqliteDriver)
    implementation(libs.sqldelight.coroutinesExtensions)
    testImplementation(libs.kotlin.test)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutinesTest)
}

sqldelight {
    databases {
        create("LogHoundDb") {
            packageName.set("com.roideuniverse.loghound.database.sqldelight")
        }
    }
}
