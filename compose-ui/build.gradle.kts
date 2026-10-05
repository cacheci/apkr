plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.jetbrains.compose)
}

kotlin {
    jvmToolchain(17)
}

dependencies {
    implementation(project(":shared"))
    implementation(compose.desktop.currentOs)
    implementation("org.jetbrains.compose.components:components-resources:1.12.1")
    implementation(libs.coroutines.core)
    implementation("androidx.navigationevent:navigationevent-compose:1.1.2")
}

compose.resources {
    packageOfResClass = "com.cacheci.apkk.ui.resources"
}
