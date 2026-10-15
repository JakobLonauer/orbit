import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.serialization)
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions { jvmTarget = JvmTarget.JVM_17 }
}

dependencies {
    api(project(":core:model"))
    implementation(libs.kotlinx.serialization.json)
    // PLAN 9 B1: number kind and place offline (brings libphonenumber with it).
    implementation(libs.libphonenumber.geocoder)
    testImplementation(libs.junit)
}
