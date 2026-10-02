plugins {
    alias(libs.plugins.kotlin.multiplatform)
}

kotlin {
    jvm("desktop")

    sourceSets {
        commonMain.dependencies {
            implementation(libs.coroutines.core)
        }
        named("desktopMain") {
            dependencies {
                implementation(libs.batik.codec)
                implementation(libs.batik.transcoder)
            }
        }
        commonTest.dependencies {
            implementation(kotlin("test"))
        }
    }
}
