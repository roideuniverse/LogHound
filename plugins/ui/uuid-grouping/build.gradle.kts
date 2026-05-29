plugins {
    alias(libs.plugins.kotlinJvm)
    alias(libs.plugins.composeMultiplatform)
    alias(libs.plugins.composeCompiler)
    alias(libs.plugins.sqldelight)
    alias(libs.plugins.metro)
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
        create("UuidGroupingDb") {
            packageName.set("com.roideuniverse.loghound.plugins.uuidgrouping.sqldelight")
            dialect(libs.sqldelight.sqliteDialect324)
        }
    }
}
