plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
    id("org.jetbrains.kotlin.plugin.serialization")
}

android {
    namespace = "com.texasprogram.app"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.texasprogram.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 25
        versionName = "3.0.0"
    }

    // Постоянный ключ подписи из секретов CI. Без одного и того же ключа Android
    // не ставит новую версию поверх старой: приходилось удалять приложение,
    // а вместе с ним пропадали профили. Ключа нет (локальная сборка) — отладочный.
    val keystorePath = System.getenv("STRAIN_KEYSTORE_PATH")
    val keystorePassword = System.getenv("STRAIN_KEYSTORE_PASSWORD")
    val hasReleaseKey = !keystorePath.isNullOrBlank() && !keystorePassword.isNullOrBlank() && file(keystorePath).exists()
    signingConfigs {
        if (hasReleaseKey) {
            create("strain") {
                storeFile = file(keystorePath!!)
                storePassword = keystorePassword
                keyAlias = "strain"
                keyPassword = keystorePassword
            }
        }
    }

    buildTypes {
        // Ставим release: debug-сборка Compose заметно тормозит — без R8 код не
        // оптимизирован, а отладочный режим мешает ART компилировать заранее.
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = signingConfigs.getByName(if (hasReleaseKey) "strain" else "debug")
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
        // combinedClickable из Compose Foundation помечен экспериментальным.
        // Разрешаем его один раз здесь, чтобы не рассыпать аннотации по файлам.
        freeCompilerArgs += "-opt-in=androidx.compose.foundation.ExperimentalFoundationApi"
    }

    buildFeatures {
        compose = true
    }

    packaging {
        resources.excludes += "/META-INF/{AL2.0,LGPL2.1}"
    }
}

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.10.01")
    implementation(composeBom)
    androidTestImplementation(composeBom)

    implementation("androidx.core:core-ktx:1.15.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.7")
    implementation("androidx.activity:activity-compose:1.9.3")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.ui:ui-graphics")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.7.3")
    // Фото ленты: загрузка с кэшем на диске; поворот снимков по EXIF перед отправкой.
    implementation("io.coil-kt:coil-compose:2.7.0")
    implementation("androidx.exifinterface:exifinterface:1.3.7")
    // Готовые профили Compose компилируются при установке APK, а не во время
    // первых прокруток — иначе первые минуты интерфейс подтормаживает.
    implementation("androidx.profileinstaller:profileinstaller:1.4.1")
    implementation("androidx.core:core-splashscreen:1.0.1")

    testImplementation("junit:junit:4.13.2")
}
