plugins {
  id("com.android.library")
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.compose.compiler)
}

android {
  namespace = "app.music.backend.sonari.canvas"
  compileSdk = 37

  defaultConfig { minSdk = 26 }

  compileOptions {
    isCoreLibraryDesugaringEnabled = true
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }

  buildFeatures { compose = true }
}

kotlin { jvmToolchain(21) }

// Artwork canvases: Tidal / Apple Music / Sonari providers, plus the artist video surface.
dependencies {
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.json)
  implementation(libs.ktor.client.encoding)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  implementation(libs.compose.runtime)
  implementation(libs.compose.foundation)
  implementation(libs.compose.ui)
  implementation(libs.compose.animation)
  implementation(libs.material3)

  implementation(libs.media3)
  implementation(libs.media3.ui)
  implementation(libs.media3.okhttp)

  coreLibraryDesugaring(libs.desugaring)

  testImplementation(libs.junit)
}
