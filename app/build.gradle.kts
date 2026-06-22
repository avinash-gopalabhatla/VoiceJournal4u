// app/build.gradle.kts
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.hilt)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.carmind.voicejournal"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.carmind.voicejournal"
        minSdk = 29          // Android 10+ (MediaPipe LLM requirement)
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        // Override at build time:
        // ./gradlew assembleDebug -PANTHROPICKEY=sk-ant-xxx -POLLAMAURL=http://192.168.x.x:11434
        buildConfigField("String", "ANTHROPIC_API_KEY", "\"${project.findProperty("ANTHROPICKEY") ?: ""}\"")
        buildConfigField("String", "OLLAMA_BASE_URL", "\"${project.findProperty("OLLAMAURL") ?: "http://192.168.43.1:11434"}\"")
        // 192.168.43.1 = Pi 4 IP when S25+ hotspot is active (CarMind setup)

        externalNativeBuild {
            cmake {
                cppFlags("-fno-finite-math-only")
                arguments(
                    "-DCMAKE_SHARED_LINKER_FLAGS=-Wl,-z,max-page-size=16384",
                    "-DCMAKE_BUILD_TYPE=Release"
                )
            }
        }

        ndk {
            abiFilters.add("arm64-v8a")
            abiFilters.add("armeabi-v7a")
        }
    }

    buildTypes {
        debug {
            // Native debug symbols are kept by default in debug builds.
            // If stripping is desired, it should be configured in the packaging block.
        }
        release {
            isMinifyEnabled = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    packaging {
        jniLibs {
            useLegacyPackaging = false
        }
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }
}

dependencies {
    // Core
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime)
    implementation(libs.androidx.lifecycle.viewmodel)
    implementation(libs.coroutines.android)

    // Compose
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.ui.graphics)
    implementation(libs.compose.ui.tooling.preview)
    implementation(libs.compose.material3)
    implementation(libs.compose.material.icons)
    implementation(libs.compose.activity)
    implementation(libs.compose.navigation)
    debugImplementation(libs.compose.ui.tooling)

    // Hilt
    implementation(libs.hilt.android)
    implementation(libs.hilt.navigation.compose)
    implementation(libs.hilt.work)
    ksp(libs.hilt.compiler)

    // Room
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)

    // Network
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)
    implementation(libs.okhttp.logging)

    // MediaPipe on-device LLM (Snapdragon 8 Elite GPU)
    implementation(libs.mediapipe.tasks.genai)

    // DataStore
    implementation(libs.datastore.preferences)

    // WorkManager (background Pi 4 sync)
    implementation(libs.work.runtime)

    // Permissions
    implementation(libs.accompanist.permissions)

    // Whisper.cpp (integrated directly from source)
    // implementation(libs.whisper)

    // Media
    implementation(libs.media3.exoplayer)

    // Markdown
    implementation(libs.markdown.renderer)
}
