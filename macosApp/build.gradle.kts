import org.jetbrains.compose.desktop.application.dsl.TargetFormat

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
    implementation(project(":compose-ui"))
    implementation(compose.desktop.currentOs)
}

compose.desktop {
    application {
        mainClass = "com.cacheci.apkk.macos.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Dmg, TargetFormat.Pkg)
            modules("java.prefs")
            packageName = "APK Viewer"
            packageVersion = "0.2.0"
            description = "APK basic information viewer"
            vendor = "apkk"
            macOS {
                iconFile.set(rootProject.file("packaging/icons/icon_droid.icns"))
                bundleID = "com.cacheci.apk-info-viewer"
            }
            fileAssociation(
                mimeType = "application/vnd.android.package-archive",
                extension = "apk",
                description = "Android application package",
                macOSIconFile = rootProject.file("packaging/icons/icon_droid.icns"),
            )
        }
    }
}
