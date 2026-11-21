plugins {
  id("com.android.library")
  id("com.google.devtools.ksp")
  id("com.google.dagger.hilt.android")
  alias(libs.plugins.kotlin.serialization)
}

android {
  namespace = "app.music.backend.sonari.dsp"
  compileSdk = 37
  defaultConfig {
    minSdk = 26
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin {
  jvmToolchain(21)
}

// Audio processors, the DSP controller, and the biquad/parametric EQ core they are built on.
dependencies {
  api(libs.media3)
  implementation(libs.timber)
  implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

  implementation(libs.hilt)
  ksp(libs.hilt.compiler)

  testImplementation(libs.junit)
}
