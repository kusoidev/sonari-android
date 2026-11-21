plugins {
  id("com.android.library")
  id("com.google.devtools.ksp")
}

android {
  namespace = "app.music.backend.sonari.lyrics"
  compileSdk = 37
  defaultConfig { minSdk = 26 }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_21
    targetCompatibility = JavaVersion.VERSION_21
  }
}

kotlin { jvmToolchain(21) }

dependencies {
  implementation(project(":Core"))
  implementation(project(":Innertube"))
  implementation(project(":LyricsProviders"))

  implementation(libs.kuromoji.ipadic)
  implementation(libs.tinypinyin)
  implementation("androidx.core:core-ktx:1.13.1")
  implementation(libs.hilt)
  ksp(libs.hilt.compiler)
}

dependencies {
  implementation(libs.ktor.client.core)
  implementation(libs.ktor.client.okhttp)
  implementation(libs.ktor.client.content.negotiation)
  implementation(libs.ktor.serialization.json)
  testImplementation(libs.junit)
  testImplementation(libs.json)
}
