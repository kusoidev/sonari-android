// AGP 9 ships built-in Kotlin, so only the compose compiler plugin is declared here.
plugins {
    id("com.android.application") version "9.0.1" apply false
    id("org.jetbrains.kotlin.plugin.compose") version "2.2.10" apply false
}
