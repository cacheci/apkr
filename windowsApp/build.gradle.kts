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
        mainClass = "com.cacheci.apkk.windows.MainKt"
        nativeDistributions {
            targetFormats(TargetFormat.Msi, TargetFormat.Exe)
            modules("java.prefs")
            packageName = "APK Viewer"
            packageVersion = "0.2.0"
            description = "APK basic information viewer"
            vendor = "apkk"
            windows {
                iconFile.set(rootProject.file("packaging/icons/icon.ico"))
                menuGroup = "APK Viewer"
                upgradeUuid = "8ef6e907-d78c-4fc4-a9e0-e1d30d1ff501"
            }
            fileAssociation(
                mimeType = "application/vnd.android.package-archive",
                extension = "apk",
                description = "Android application package",
                windowsIconFile = rootProject.file("packaging/icons/icon.ico"),
            )
        }
    }
}
