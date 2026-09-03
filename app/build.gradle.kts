import org.gradle.api.GradleException

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
    alias(libs.plugins.hilt)
    id("kotlin-kapt")
    alias(libs.plugins.serialization)
}

val releaseStoreFile = providers.gradleProperty("RELEASE_STORE_FILE").orNull
val releaseStorePassword = providers.gradleProperty("RELEASE_STORE_PASSWORD").orNull
val releaseKeyAlias = providers.gradleProperty("RELEASE_KEY_ALIAS").orNull
val releaseKeyPassword = providers.gradleProperty("RELEASE_KEY_PASSWORD").orNull
val releaseTaskRequested = gradle.startParameter.taskNames.any {
    it.contains("release", ignoreCase = true)
}

android {
    namespace = "com.arktools.xiao"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.arktools.xiao"
        minSdk = 24
        targetSdk = 35
        versionCode = 215
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    signingConfigs {
        create("release") {
            if (releaseTaskRequested) {
                if (
                    releaseStoreFile == null || releaseStorePassword == null ||
                    releaseKeyAlias == null || releaseKeyPassword == null
                ) {
                    throw GradleException(
                        "Release signing is not configured. Set RELEASE_STORE_FILE, " +
                            "RELEASE_STORE_PASSWORD, RELEASE_KEY_ALIAS and RELEASE_KEY_PASSWORD."
                    )
                }
                storeFile = file(releaseStoreFile)
                storePassword = releaseStorePassword
                keyAlias = releaseKeyAlias
                keyPassword = releaseKeyPassword
                storeType = "PKCS12"
            }
        }
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions {
        jvmTarget = "17"
    }
    buildFeatures {
        compose = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    implementation(libs.androidx.material.icons.extended)

    // Navigation
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.hilt.navigation.compose)

    // Room
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)

    // Hilt
    implementation(libs.hilt.android)
    kapt(libs.hilt.compiler)

    // DataStore
    implementation(libs.androidx.datastore.preferences)

    // Coroutines
    implementation(libs.kotlinx.coroutines.android)

    // Serialization
    implementation(libs.kotlinx.serialization.json)

    // AppCompat and Material (required by ad SDKs)
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("androidx.cardview:cardview:1.0.0")

    // Tosin 广告 SDK Core（新版）
    implementation(files("libs/tosin-ad-Y260817.aar"))
    implementation(files("libs/tosin-adx-2.9.65.aar"))

    // 广告 SDK 必须依赖
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")
    implementation("com.github.bumptech.glide:glide:4.16.0")
    implementation("com.squareup.retrofit2:adapter-rxjava2:2.2.0")
    implementation("io.reactivex.rxjava2:rxjava:2.2.21")

    // OAID（国内广告归因）
    implementation(files("libs/oaid_sdk_1.0.25.aar"))

    // CSJ 穿山甲
    implementation(files("libs/tosin-csj-adapter-7.6.1.1.aar"))
    // GDT 优量汇
    implementation(files("libs/tosin-gdt-adapter-4.690.1560.aar"))
    // KS 快手
    implementation(files("libs/tosin-ks-adapter-5.1.20.1.aar"))
    // 百度
    implementation(files("libs/tosin-baidu-adapter-9.450.aar"))

    // sigmob
    implementation(files("libs/sigmob/tosin-sigmob_common-adapter-1.9.4.aar"))
    implementation(files("libs/sigmob/tosin-sigmob_windsdk-adapter-4.25.11.aar"))

    // topon
    implementation(files("libs/topon/tosin-anythink_banner-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_china_core.aar"))
    implementation(files("libs/topon/tosin-anythink_core-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_interstitial-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_native-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_rewardvideo-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_splash-adapter.aar"))
    implementation(files("libs/topon/tosin-anythink_adx_sdk_kuying_necessary-adapter-6.5.48.aar"))
    implementation(files("libs/topon/tosin-anythink_network_adx_kuying_sdk_necessary-adapter.aar"))

    // yout
    implementation(files("libs/yout/tosin-adalliance-adapter-4.7.7.aar"))

    // taptap
    implementation(files("libs/taptap/tosin-taptap-adapter-4.2.4.8.aar"))

    // adgain
    implementation(files("libs/adgain/tosin-adgainsdk-adapter-4.2.7.2.aar"))
    implementation(files("libs/adgain/tosin-adgainbeizi-adapter-4.2.5.4.aar"))
    implementation(files("libs/adgain/tosin-adgaingromore-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-adgainjiguang-adapter-4.2.2.1.aar"))
    implementation(files("libs/adgain/tosin-adgaintaku-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-adgaintobid-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-admate-adapter-4.2.7.aar"))
    implementation(files("libs/adgain/tosin-mediatom-adapter-4.2.5.2.aar"))

    // adview
    implementation(files("libs/adview/tosin-adview-adapter-5.0.5.aar"))

    // beizi
    implementation(files("libs/beizi/tosin-beizi-adapter-5.3.0.3.aar"))

    // dm 点媒
    implementation(files("libs/dm/tosin-domob-adapter-3.8.2.aar"))

    // funlink
    implementation(files("libs/funlink/tosin-funlink-adapter-2.9.0_77390768.aar"))
    implementation(files("libs/funlink/tosin-funlink_gromore-adapter-2.9.0_77328722.aar"))
    implementation(files("libs/funlink/tosin-funlink_taku-adapter-2.9.0_77328722.aar"))
    implementation(files("libs/funlink/tosin-funlink_tobid-adapter-2.9.0_77328722.aar"))

    // hx 鸿兴
    implementation(files("libs/hx/tosin-hx-sdk-1.6.17.aar"))
    implementation(files("libs/hx/tosin-hx-gromore-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-mediatom-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-taku-adapter.aar"))
    implementation(files("libs/hx/tosin-hx-tobid-adapter.aar"))

    // jiatou
    implementation(files("libs/jiatou/tosin-advista-adapter-1.9.2.aar"))

    // jutui
    implementation(files("libs/jutui/tosin-jutui-adapter-4.2.3.1.aar"))

    // maimeng
    implementation(files("libs/maimeng/tosin-wm-adapter-7.9.19.25.aar"))

    // ms
    implementation(files("libs/ms/tosin-ms-adapter-3.0.4.1.aar"))

    // tianxuan
    implementation(files("libs/tianxuan/tosin-UBiX-adapter-2.10.1.11.aar"))

    // zhongchen
    implementation(files("libs/zhongchen/tosin-starsads-adapter-1.3.04.aar"))

    // oaid
    implementation(files("libs/oaid_sdk_1.0.25.aar"))

    // TapTap SDK
    implementation("com.taptap.sdk:tap-core:4.10.3")
    implementation("com.taptap.sdk:tap-login:4.10.3")
    implementation("com.taptap.sdk:tap-compliance:4.10.3")  // 防沉迷

    // Bugly 崩溃上报
    implementation("com.tencent.bugly:crashreport:4.1.9.3")


    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
}
