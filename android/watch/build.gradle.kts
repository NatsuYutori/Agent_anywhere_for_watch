import java.util.Properties
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}

// The official Cloud service. A self-hosted address can be entered on the login screen.
val officialServerUrl = "https://web.agents-anywhere.com"

// Optional local release signing. Never commit keystore.properties or the keystore itself.
val keystorePropertiesFile = rootProject.file("watch/keystore.properties")
val keystoreProperties = Properties().apply {
    keystorePropertiesFile.takeIf { it.isFile }?.inputStream()?.use { load(it) }
}
val hasReleaseSigning = keystorePropertiesFile.isFile &&
    !keystoreProperties.getProperty("storeFile").isNullOrBlank()

fun String.asBuildConfigString(): String = "\"" + replace("\\", "\\\\")
    .replace("\"", "\\\"")
    .replace("\n", "\\n")
    .replace("\r", "\\r") + "\""

// ---------------------------------------------------------------------------
// Shared upstream sources
//
// AGP 8.7's AndroidSourceDirectorySet exposes only srcDir/srcDirs — there is no
// include/exclude filter, and a bare `include(...)` inside that block silently
// binds to Project.include instead of filtering files. So the whitelist is
// materialised by a Sync task into the module build directory. Upstream stays the
// single source of truth: nothing is moved, forked into git, or edited.
// ---------------------------------------------------------------------------
val sharedSourceRoot = layout.projectDirectory.dir("../app/src/main/java")
val sharedSourceWhitelist = listOf(
    "com/agentsanywhere/app/api/**",
    "com/agentsanywhere/app/model/**",
    "com/agentsanywhere/app/config/**",
    "com/agentsanywhere/app/feature/auth/**",
    "com/agentsanywhere/app/feature/devices/**",
    "com/agentsanywhere/app/feature/realtime/**",
    "com/agentsanywhere/app/feature/sessiondetail/**",
    "com/agentsanywhere/app/feature/sessions/**",
    "com/agentsanywhere/app/ui/designsystem/Theme.kt",
)
// Deliberately excluded: MainActivity/app (phone nav), ui/screens (phone UI),
// feature/terminal (needs Termux), feature/update (phone self-update).

val sharedSourcesDir = layout.buildDirectory.dir("generated/sharedSources")

val syncSharedSources by tasks.registering(Sync::class) {
    group = "build"
    description = "Materialises the whitelisted upstream non-UI sources for the watch client."
    from(sharedSourceRoot) {
        sharedSourceWhitelist.forEach { pattern -> include(pattern) }
    }
    into(sharedSourcesDir)
}

android {
    // Shared sources reference com.agentsanywhere.app.BuildConfig, so the watch module
    // generates BuildConfig in the same package while shipping a distinct applicationId.
    namespace = "com.agentsanywhere.app"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.agentsanywhere.app.watch"
        minSdk = 26
        targetSdk = 36
        versionCode = 2010001
        versionName = "2.1.0-watch.1"
        buildConfigField("String", "OFFICIAL_SERVER_URL", officialServerUrl.asBuildConfigString())

        vectorDrawables {
            useSupportLibrary = true
        }
    }

    signingConfigs {
        if (hasReleaseSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                // The APK ends up v2-signed only, which is what AGP does for
                // minSdk >= 24: v1 (JAR) signing is skipped no matter what
                // enableV1Signing says. v2 is supported by every device that can
                // satisfy minSdk 26, so this is fine. Verify with:
                //   apksigner verify --verbose --print-certs watch-release.apk
            }
        }
    }

    buildTypes {
        debug {
            // Debug installs keep the same applicationId so a release build can replace
            // a debug build without uninstalling.
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro",
            )
            if (hasReleaseSigning) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        isCoreLibraryDesugaringEnabled = true
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        buildConfig = true
        compose = true
    }

    sourceSets["main"].java.srcDir(sharedSourcesDir)

    lint {
        // The watch APK is sideloaded, so the release build does not need lintVital.
        // This also stops `assembleRelease` from resolving com.android.tools.lint.
        checkReleaseBuilds = false
    }
}

tasks.withType<KotlinCompile>().configureEach {
    dependsOn(syncSharedSources)
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.okhttp)
    coreLibraryDesugaring(libs.desugar.jdk.libs)

    testImplementation(libs.junit)
    // android.jar stubs org.json and throws "not mocked" in unit tests, so the real
    // implementation goes on the test classpath (the :app module does the same).
    testImplementation("org.json:json:20240303")
    // Serves the real HTTP responses the auth wire-format test asserts against.
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    debugImplementation(libs.androidx.compose.ui.tooling)
}
