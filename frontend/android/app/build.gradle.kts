import java.security.KeyStore
import java.security.cert.X509Certificate
import java.util.Base64
import java.net.URI

plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.gms.google-services") apply false
}

// Explicit development-only no-Firebase build; startup presents safe configuration errors.
val firebaseDisabled = providers.environmentVariable("HYPED_DISABLE_FIREBASE").getOrElse("false") == "true"
if (!firebaseDisabled && file("google-services.json").isFile) {
    apply(plugin = "com.google.gms.google-services")
}

// One build value controls both the verified manifest host and the runtime allowlist.
val inviteHost = providers.gradleProperty("INVITE_HOST")
    .orElse(providers.environmentVariable("INVITE_HOST")).getOrElse("")
val hostLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
require(inviteHost.isEmpty() || (inviteHost.length in 3..253 && inviteHost.contains('.') &&
    inviteHost.split('.').all { hostLabel.matches(it) })) { "INVITE_HOST must be an exact lowercase ASCII DNS hostname" }
val integrityProject = providers.gradleProperty("PLAY_INTEGRITY_PROJECT_NUMBER")
    .orElse(providers.environmentVariable("PLAY_INTEGRITY_PROJECT_NUMBER")).getOrElse("0")
require(integrityProject.matches(Regex("0|[1-9][0-9]{0,17}"))) { "Invalid integrity project number" }
val releaseRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}
val dartDefines = providers.gradleProperty("dart-defines").getOrElse("").split(',').mapNotNull {
    runCatching { String(Base64.getDecoder().decode(it), Charsets.UTF_8) }.getOrNull()
}
val apiUrl = dartDefines.lastOrNull { it.startsWith("HYPED_API_BASE_URL=") }?.substringAfter('=')
fun productionHost(host: String?): Boolean {
    if (host == null || !host.contains('.') || host.length > 253 ||
        host.split('.').any { !hostLabel.matches(it) } || host.substringAfterLast('.').all { it.isDigit() }) return false
    return listOf("localhost", "local", "internal", "invalid", "test", "example",
        "example.com", "example.org", "example.net").none { host == it || host.endsWith(".$it") }
}
fun validateRelease() {
    val api = runCatching { URI(apiUrl ?: "") }.getOrNull()
    require(api != null && api.scheme == "https" && productionHost(api.host) &&
        api.rawAuthority == api.host && api.rawQuery == null && api.rawFragment == null &&
        api.path == "/api/v1") { "Release requires an HTTPS production HYPED_API_BASE_URL ending /api/v1" }
    require(productionHost(inviteHost)) { "Release requires an owned production INVITE_HOST" }
    require(System.getenv("HYPED_KEYSTORE_PATH")?.let { file(it).isFile } == true &&
        listOf("HYPED_KEYSTORE_PASSWORD", "HYPED_KEY_ALIAS", "HYPED_KEY_PASSWORD")
            .all { !System.getenv(it).isNullOrBlank() }) { "Release requires controlled release signing configuration" }
    val approvedSigning = runCatching {
        val store = KeyStore.getInstance(file(System.getenv("HYPED_KEYSTORE_PATH")),
            System.getenv("HYPED_KEYSTORE_PASSWORD").toCharArray())
        val alias = System.getenv("HYPED_KEY_ALIAS")
        val certificate = store.getCertificate(alias) as X509Certificate
        store.isKeyEntry(alias) && store.getKey(alias, System.getenv("HYPED_KEY_PASSWORD").toCharArray()) != null &&
            !certificate.subjectX500Principal.name.contains("CN=Android Debug", ignoreCase = true)
    }.getOrDefault(false)
    require(approvedSigning) { "Release signing must use a valid non-debug private key and certificate" }
    require(!firebaseDisabled && file("google-services.json").isFile) {
        "Release requires local Firebase Android client configuration"
    }
}
if (releaseRequested) validateRelease()
val validateReleaseConfiguration = tasks.register("validateReleaseConfiguration") { doLast { validateRelease() } }
// Also guard aggregate tasks (for example assemble) that schedule release work indirectly.
tasks.configureEach {
    if (name.contains("release", ignoreCase = true) && name != "validateReleaseConfiguration") {
        dependsOn(validateReleaseConfiguration)
    }
}
val hasReleaseSigning = !System.getenv("HYPED_KEYSTORE_PATH").isNullOrBlank() &&
    listOf("HYPED_KEYSTORE_PASSWORD", "HYPED_KEY_ALIAS", "HYPED_KEY_PASSWORD")
        .all { !System.getenv(it).isNullOrBlank() }

android {
    namespace = "com.hyped.hyped"
    buildFeatures { buildConfig = true }
    compileSdk = flutter.compileSdkVersion
    ndkVersion = flutter.ndkVersion

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = JavaVersion.VERSION_17.toString()
    }

    defaultConfig {
        // TODO: Specify your own unique Application ID (https://developer.android.com/studio/build/application-id.html).
        applicationId = "com.hyped.hyped"
        manifestPlaceholders["inviteHost"] = inviteHost.ifEmpty { "unconfigured.invalid" }
        buildConfigField("String", "INVITE_HOST", "\"$inviteHost\"")
        buildConfigField("long", "PLAY_INTEGRITY_PROJECT_NUMBER", "${integrityProject}L")
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    if (hasReleaseSigning) {
        signingConfigs.create("controlledRelease") {
            storeFile = file(System.getenv("HYPED_KEYSTORE_PATH"))
            storePassword = System.getenv("HYPED_KEYSTORE_PASSWORD")
            keyAlias = System.getenv("HYPED_KEY_ALIAS")
            keyPassword = System.getenv("HYPED_KEY_PASSWORD")
        }
    }
    buildTypes {
        release {
            if (hasReleaseSigning) signingConfig = signingConfigs.getByName("controlledRelease")
            isDebuggable = false
        }
    }
}

dependencies {
  implementation("com.google.android.play:integrity:1.6.0")
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  // Import the Firebase BoM
  implementation(platform("com.google.firebase:firebase-bom:34.19.0"))


  // TODO: Add the dependencies for Firebase products you want to use
  // When using the BoM, don't specify versions in Firebase dependencies
  implementation("com.google.firebase:firebase-messaging")


  // Add the dependencies for any other desired Firebase products
  // https://firebase.google.com/docs/android/setup#available-libraries
}

flutter {
    source = "../.."
}
