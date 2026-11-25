import com.google.gms.googleservices.GoogleServicesPlugin.MissingGoogleServicesStrategy
import java.io.File
import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Properties

plugins {
  alias(libs.plugins.android.application)
  alias(libs.plugins.kotlin.compose)
  alias(libs.plugins.kotlin.serialization)
  alias(libs.plugins.google.devtools.ksp)
  alias(libs.plugins.hilt.android)
  alias(libs.plugins.secrets)
  alias(libs.plugins.google.services)
}

val versionPropsFile = rootProject.file("version.properties")
val versionProps = Properties().apply {
  if (versionPropsFile.exists()) {
    versionPropsFile.inputStream().use { load(it) }
  }
}
val baseMajor = versionProps.getProperty("VERSION_MAJOR", "1").toIntOrNull() ?: 1
val baseMinor = versionProps.getProperty("VERSION_MINOR", "0").toIntOrNull() ?: 0
val basePatch = versionProps.getProperty("VERSION_PATCH", "0").toIntOrNull() ?: 0
val baseCode = versionProps.getProperty("VERSION_CODE", "1").toIntOrNull() ?: 1

val resolvedVersionCode = project.findProperty("versionCode")?.toString()?.toIntOrNull()
  ?: System.getenv("VERSION_CODE")?.toIntOrNull()
  ?: baseCode

val resolvedVersionName = project.findProperty("versionName")?.toString()?.takeIf { it.isNotBlank() }
  ?: System.getenv("VERSION_NAME")?.takeIf { it.isNotBlank() }
  ?: "$baseMajor.$baseMinor.$basePatch"

val configuredGithubRepo = project.findProperty("githubRepo")?.toString()?.takeIf { it.isNotBlank() }
  ?: System.getenv("GITHUB_REPOSITORY")?.takeIf { it.isNotBlank() }
  ?: versionProps.getProperty("GITHUB_REPO", "").trim()

android {
  namespace = "com.example"
  compileSdk { version = release(36) { minorApiLevel = 1 } }

  defaultConfig {
    applicationId = "com.aistudio.pantrymaster.vrkqzm"
    minSdk = 24
    targetSdk = 36
    versionCode = resolvedVersionCode
    versionName = resolvedVersionName

    buildConfigField("String", "GITHUB_REPOSITORY", "\"${configuredGithubRepo.replace("\"", "\\\"")}\"")

    testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
  }

  // Do not remove or modify these signingConfigs. They are necessary for building, installing,
  // and updating Android apps in AI Studio.
  signingConfigs {
    create("release") {
      val keystorePath = System.getenv("KEYSTORE_PATH") ?: "${rootDir}/my-upload-key.jks"
      storeFile = file(keystorePath)
      storePassword = System.getenv("STORE_PASSWORD")
      keyAlias = "upload"
      keyPassword = System.getenv("KEY_PASSWORD")
    }
    create("debugConfig") {
      storeFile = file("${rootDir}/debug.keystore")
      storePassword = "android"
      keyAlias = "androiddebugkey"
      keyPassword = "android"
    }
  }

  buildTypes {
    release {
      isCrunchPngs = true
      isMinifyEnabled = true
      isShrinkResources = true
      ndk {
        abiFilters += listOf("arm64-v8a", "armeabi-v7a")
      }
      proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("release")
    }
    debug {
      // Do not remove or modify this signingConfig assignment. It is necessary for Android apps in
      // AI Studio.
      signingConfig = signingConfigs.getByName("debugConfig")
    }
  }
  compileOptions {
    sourceCompatibility = JavaVersion.VERSION_11
    targetCompatibility = JavaVersion.VERSION_11
    isCoreLibraryDesugaringEnabled = false
  }
  buildFeatures {
    compose = true
    buildConfig = true
  }
  testOptions { unitTests { isIncludeAndroidResources = true } }
  androidResources {
    localeFilters += listOf("it", "en")
  }
  packaging {
    resources {
      excludes += setOf(
        "DebugProbesKt.bin",
        "META-INF/AL2.0",
        "META-INF/LGPL2.1",
        "META-INF/LICENSE*",
        "META-INF/NOTICE*",
        "META-INF/CHANGES",
        "META-INF/README*"
      )
    }
  }
  dependenciesInfo {
    includeInApk = false
    includeInBundle = true
  }
}

secrets {
  propertiesFileName = ".env"
  defaultPropertiesFileName = ".env.example"
  ignoreList.add("FIREBASE_APPCHECK_DEBUG_TOKEN")
}

googleServices { missingGoogleServicesStrategy = MissingGoogleServicesStrategy.WARN }

dependencies {
  implementation(platform(libs.androidx.compose.bom))
  // Firebase disabled per PantryMaster offline-only specification
  // implementation(platform(libs.firebase.bom))
  // implementation(libs.firebase.ai)
  // implementation(libs.firebase.appcheck.recaptcha)
  // implementation(libs.firebase.appcheck.debug)
  // implementation(libs.firebase.firestore)
  // implementation(libs.firebase.auth)
  // implementation(libs.androidx.credentials)
  // implementation(libs.androidx.credentials.play.services)
  // implementation(libs.googleid)

  implementation(libs.androidx.activity.compose)
  implementation(libs.androidx.camera.camera2)
  implementation(libs.androidx.camera.core)
  implementation(libs.androidx.camera.lifecycle)
  implementation(libs.androidx.camera.view)
  implementation(libs.mlkit.barcode.scanning)
  implementation(libs.mlkit.text.recognition)

  implementation(libs.androidx.compose.material.icons.core)
  implementation(libs.androidx.compose.material.icons.extended)
  implementation(libs.androidx.compose.material3)
  implementation(libs.androidx.compose.material3.window.size)
  implementation(libs.androidx.compose.ui)
  implementation(libs.androidx.compose.ui.graphics)
  implementation(libs.androidx.compose.ui.tooling.preview)
  implementation(libs.androidx.core.ktx)
  implementation(libs.androidx.datastore.preferences)
  implementation(libs.androidx.lifecycle.runtime.compose)
  implementation(libs.androidx.lifecycle.runtime.ktx)
  implementation(libs.androidx.lifecycle.viewmodel.compose)
  implementation(libs.androidx.navigation.compose)
  implementation(libs.androidx.room.ktx)
  implementation(libs.androidx.room.runtime)
  implementation(libs.androidx.work.runtime.ktx)
  implementation(libs.coil.compose)
  implementation(libs.converter.moshi)
  implementation(libs.hilt.android)
  implementation(libs.androidx.hilt.navigation.compose)
  implementation(libs.androidx.hilt.work)
  implementation(libs.kotlinx.coroutines.android)
  implementation(libs.kotlinx.coroutines.core)
  implementation(libs.kotlinx.serialization.json)
  implementation(libs.logging.interceptor)
  implementation(libs.moshi)
  implementation(libs.okhttp)
  implementation(libs.retrofit)

  testImplementation(libs.androidx.compose.ui.test.junit4)
  testImplementation(libs.androidx.core)
  testImplementation(libs.androidx.junit)
  testImplementation(libs.androidx.room.testing)
  testImplementation(libs.androidx.work.testing)
  testImplementation(libs.junit)
  testImplementation(libs.kotlinx.coroutines.test)
  testImplementation(libs.robolectric)

  androidTestImplementation(platform(libs.androidx.compose.bom))
  androidTestImplementation(libs.androidx.compose.ui.test.junit4)
  androidTestImplementation(libs.androidx.espresso.core)
  androidTestImplementation(libs.androidx.junit)
  androidTestImplementation(libs.androidx.runner)

  debugImplementation(libs.androidx.compose.ui.test.manifest)
  debugImplementation(libs.androidx.compose.ui.tooling)

  "ksp"(libs.androidx.room.compiler)
  "ksp"(libs.moshi.kotlin.codegen)
  "ksp"(libs.hilt.compiler)
  "ksp"(libs.androidx.hilt.compiler)
}

val releaseKeystorePathProvider = providers.environmentVariable("KEYSTORE_PATH")
  .orElse("${rootDir.absolutePath}/my-upload-key.jks")
val releaseStorePasswordProvider = providers.environmentVariable("STORE_PASSWORD")
val releaseKeyPasswordProvider = providers.environmentVariable("KEY_PASSWORD")

val verifyReleaseSigning = tasks.register("verifyReleaseSigning") {
  group = "verification"
  description = "Verifica presenza, alias e credenziali del keystore Release locale prima del packaging."
  val ksPath = releaseKeystorePathProvider
  val storePass = releaseStorePasswordProvider
  val keyPass = releaseKeyPasswordProvider

  doLast {
    val path = ksPath.get()
    val ksFile = File(path)
    if (!ksFile.exists() || !ksFile.isFile) {
      throw GradleException(
        "Keystore Release non trovato in: $path. " +
          "Esegui PantryMaster-Release-Manager.ps1 (oppure imposta KEYSTORE_PATH, STORE_PASSWORD e KEY_PASSWORD). " +
          "La build Release non utilizza mai la firma Debug come fallback."
      )
    }
    val sp = storePass.orNull
    val kp = keyPass.orNull
    if (sp.isNullOrBlank() || kp.isNullOrBlank()) {
      throw GradleException(
        "Credenziali STORE_PASSWORD e KEY_PASSWORD mancanti. " +
          "Avvia PantryMaster-Release-Manager.ps1 per compilare in modo sicuro."
      )
    }

    val ks = KeyStore.getInstance("JKS")
    ksFile.inputStream().use { input ->
      try {
        ks.load(input, sp.toCharArray())
      } catch (e: Exception) {
        throw GradleException("Password del keystore (STORE_PASSWORD) errata o file JKS corrotto: $path", e)
      }
    }
    if (!ks.containsAlias("upload") || !ks.isKeyEntry("upload")) {
      throw GradleException("Alias 'upload' mancante o non valido nel keystore: $path")
    }
    try {
      ks.getKey("upload", kp.toCharArray())
        ?: throw GradleException("Chiave privata associata ad alias 'upload' non trovata.")
    } catch (e: Exception) {
      throw GradleException("Password della chiave (KEY_PASSWORD) errata per l'alias 'upload'.", e)
    }
    val cert = ks.getCertificate("upload") as? X509Certificate
      ?: throw GradleException("Certificato X.509 mancante per l'alias 'upload'.")
    if (cert.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)) {
      throw GradleException("Il keystore Release punta a un certificato Android Debug: operazione bloccata.")
    }
  }
}

tasks.matching {
  it.name == "validateSigningRelease" ||
    it.name == "packageRelease" ||
    it.name == "signReleaseBundle"
}.configureEach {
  dependsOn(verifyReleaseSigning)
}

