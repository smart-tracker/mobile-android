plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

android {
    namespace = "com.example.smarttracker"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.smarttracker"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://runtastic.gottland.ru/\"")
        }
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://runtastic.gottland.ru/\"")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
        // Для LocalDate на minSdk < 26 нужен desugaring.
        // minSdk = 26 — java.time доступен нативно, десюгаринг не нужен.
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }

    composeOptions {
        kotlinCompilerExtensionVersion = libs.versions.composeCompilerExtension.get()
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // Google Material — тема Theme.Material3.Light.NoActionBar нужна для enableEdgeToEdge()
    // Без неё android:Theme.Material.Light.NoActionBar не определяет R.attr.isLightTheme,
    // что вызывает "Invalid resource ID 0x00000000" в логах при запуске.
    implementation(libs.google.material)

    // Core AndroidX
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.activity.compose)

    // Compose BOM — единая версия для всех compose-библиотек
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)

    // Hilt — внедрение зависимостей
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)
    implementation(libs.hilt.navigation.compose)

    // Retrofit — сетевые запросы
    implementation(libs.retrofit)
    implementation(libs.retrofit.gson)

    // OkHttp — http клиент + логирование запросов
    implementation(platform(libs.okhttp.bom))
    implementation(libs.okhttp)
    implementation(libs.okhttp.logging)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Security Crypto — хранение JWT токенов
    implementation(libs.androidx.security.crypto)

    // DataStore — хранение настроек
    implementation(libs.androidx.datastore.preferences)

    // Coil — отображение изображений из File, URL и drawable через AsyncImage
    implementation(libs.coil.compose)

    // Room — локальная база данных GPS-точек тренировок
    implementation(libs.room.runtime)
    implementation(libs.room.ktx)
    kapt(libs.room.compiler)

    // MapLibre — карта с OpenFreeMap тайлами, без API-ключа
    implementation(libs.maplibre.android)

    // GPS мульти-рантайм: GMS (Google) + HMS (Huawei) в одном APK.
    // На устройстве без одного из SDK RuntimeDetector поймает NoClassDefFoundError
    // и использует оставшийся провайдер или AOSP-fallback.
    implementation(libs.gms.location)
    implementation(libs.hms.location)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
}

// Hilt требует kapt
kapt {
    correctErrorTypes = true
}
