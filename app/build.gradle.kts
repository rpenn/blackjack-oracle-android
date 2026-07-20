plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.blackjackoracle"
    compileSdk = 36

    defaultConfig {
        applicationId = "com.blackjackoracle.app"
        minSdk = 24
        targetSdk = 36
        versionCode = 7
        versionName = "1.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "ADVISOR_BASE_URL", "\"https://oracle-mobile-api.vercel.app\"")
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            ndk {
                debugSymbolLevel = "FULL"
            }
        }
        debug {
            isMinifyEnabled = false
            applicationIdSuffix = ".debug"
        }
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_11; targetCompatibility = JavaVersion.VERSION_11 }
    testOptions { unitTests.isReturnDefaultValues = true }
}

dependencies {
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.core.ktx)
    // Not used directly — pins the transitive androidx.fragment from RevenueCat's
    // play-services deps above 1.1.0, which Play Console flags as outdated.
    implementation(libs.androidx.fragment)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.okhttp)
    implementation(libs.play.review)
    implementation(libs.play.review.ktx)
    implementation(libs.revenuecat.purchases)
    implementation(libs.androidx.security.crypto)
    implementation(libs.androidx.datastore.preferences)
    testImplementation(libs.junit)
    testImplementation(libs.kotlinx.coroutines.test)
    testImplementation(libs.okhttp.mockwebserver)
    // Real org.json on the JVM test classpath so EntitlementStore's JWT-exp
    // parsing runs under unit tests (the android.jar org.json is a stub).
    testImplementation(libs.json)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.compose.ui.test.junit4)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    debugImplementation(libs.androidx.compose.ui.tooling)
    debugImplementation(libs.androidx.compose.ui.test.manifest)
}
