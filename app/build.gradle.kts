import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.kapt)
    alias(libs.plugins.hilt)
}

// Подписание release-сборки. Секреты (пароли, keystore) НЕ хранятся в репозитории:
// файл keystore.properties лежит рядом с корнем проекта и добавлен в .gitignore.
// Шаблон и инструкция генерации keystore — keystore.properties.example.
// Если файла нет (CI, чужая машина) — release собирается неподписанным.
val keystorePropertiesFile = rootProject.file("keystore.properties")
val keystoreProperties = Properties().apply {
    if (keystorePropertiesFile.exists()) {
        keystorePropertiesFile.inputStream().use { load(it) }
    }
}

android {
    // namespace — внутренний package для R/BuildConfig, в сторы не попадает.
    // Оставлен com.example.smarttracker, чтобы не переименовывать все Kotlin-пакеты.
    namespace = "com.example.smarttracker"
    compileSdk = 35

    defaultConfig {
        // Идентификатор приложения в сторах (RuStore/Play). com.example.* сторы
        // не принимают. После первой публикации менять НЕЛЬЗЯ.
        applicationId = "com.smarttracker.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.2"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        vectorDrawables {
            useSupportLibrary = true
        }

    }

    signingConfigs {
        if (keystorePropertiesFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProperties["storeFile"] as String)
                storePassword = keystoreProperties["storePassword"] as String
                keyAlias = keystoreProperties["keyAlias"] as String
                keyPassword = keystoreProperties["keyPassword"] as String
            }
        }
    }

    buildTypes {
        debug {
            buildConfigField("String", "BASE_URL", "\"https://runtastic.gottland.ru/\"")
        }
        release {
            // R8: минификация + обфускация. Правила — proguard-rules.pro.
            // После любого изменения правил обязателен smoke-test release-сборки
            // на устройстве: логин → тренировка → финиш → история (R8 ломает
            // рефлексию Gson/Retrofit молча, без ошибок компиляции).
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
            buildConfigField("String", "BASE_URL", "\"https://runtastic.gottland.ru/\"")
            // null если keystore.properties отсутствует → неподписанный APK
            signingConfig = signingConfigs.findByName("release")
        }
    }

    testOptions {
        unitTests {
            // android.util.Log и другие Android-классы возвращают дефолтные значения
            // в JVM unit-тестах, а не бросают исключения (RuntimeException: Stub!).
            isReturnDefaultValues = true
            // Robolectric требует доступ к скомпилированным ресурсам (R-классы, assets).
            // Без этого ApplicationProvider.getApplicationContext() возвращает null.
            isIncludeAndroidResources = true
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

    // WorkManager + Hilt-интеграция — фоновая доставка офлайн-операций
    implementation(libs.androidx.work.runtime.ktx)
    implementation(libs.hilt.work)
    kapt(libs.hilt.work.compiler)

    // Debug
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)

    // Tests
    testImplementation(libs.junit)
    testImplementation(libs.mockito.kotlin)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Robolectric — Android-окружение для JVM-тестов (нужен для WorkManager-воркеров)
    testImplementation(libs.robolectric)
    // work-testing — TestListenableWorkerBuilder для синхронного запуска воркеров в тестах
    testImplementation(libs.androidx.work.testing)
    // androidx.test.core нужен явно при работе Robolectric в testImplementation
    // (ApplicationProvider.getApplicationContext() транзитивно не попадает в test classpath)
    testImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    androidTestImplementation(libs.room.testing)
}

// Hilt требует kapt
kapt {
    correctErrorTypes = true
}
