plugins {
    id("com.android.application")
    id("kotlin-android")
    // The Flutter Gradle Plugin must be applied after the Android and Kotlin Gradle plugins.
    id("dev.flutter.flutter-gradle-plugin")
    id("com.google.gms.google-services")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

if (file("google-services.json").exists()) {
    apply(plugin = "com.google.gms.google-services")
}

// One build value controls both the verified manifest host and the runtime allowlist.
val inviteHost = providers.gradleProperty("INVITE_HOST")
    .orElse(providers.environmentVariable("INVITE_HOST")).getOrElse("")
val hostLabel = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
require(inviteHost.isEmpty() || (inviteHost.length in 3..253 && inviteHost.contains('.') &&
    inviteHost.split('.').all { hostLabel.matches(it) })) { "INVITE_HOST must be an exact lowercase ASCII DNS hostname" }
if (gradle.startParameter.taskNames.any { it.contains("release", ignoreCase = true) } && inviteHost.isEmpty()) {
    throw GradleException("Release App Links require INVITE_HOST and a verified owned domain; see docs/android-app-links.md")
}

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
        // You can update the following values to match your application needs.
        // For more information, see: https://flutter.dev/to/review-gradle-config.
        minSdk = flutter.minSdkVersion
        targetSdk = flutter.targetSdkVersion
        versionCode = flutter.versionCode
        versionName = flutter.versionName
    }

    buildTypes {
        release {
            // Release signing is injected by the controlled local/CI environment.
            // No signing material or debug-key fallback is committed.
        }
    }
}

dependencies {
  testImplementation("junit:junit:4.13.2")
  testImplementation("org.json:json:20240303")
  // Import the Firebase BoM
  implementation(platform("com.google.firebase:firebase-bom:34.19.0"))


  // TODO: Add the dependencies for Firebase products you want to use
  // When using the BoM, don't specify versions in Firebase dependencies
  implementation("com.google.firebase:firebase-analytics")
  implementation("com.google.firebase:firebase-messaging")


  // Add the dependencies for any other desired Firebase products
  // https://firebase.google.com/docs/android/setup#available-libraries
}

flutter {
    source = "../.."
}
