plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("bcu-plugin")
}

bytecodeUtil {
    loggerLevel = 1
    modifiers = arrayOf(
        com.ysj.lib.bcu.modifier.aspect.AspectModifier::class.java,
    )
    notNeed = { entryName ->
        !entryName.startsWith("com/ysj/") && !entryName.startsWith("com/example/")
    }
}

android {
    namespace = "com.ysj.demo.aspect"
    compileSdk = 33

    defaultConfig {
        applicationId = "com.ysj.demo.aspect"
        minSdk = 21
        targetSdk = 33
        versionCode = 1
        versionName = "1.0.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        getByName("debug") {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
        getByName("release") {
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
        viewBinding = true
    }
    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
    lint {
        checkReleaseBuilds = false
    }
}

dependencies {
    implementation(fileTree(mapOf("dir" to "libs", "include" to listOf("*.jar"))))
//    implementation("com.squareup.okhttp3:okhttp:4.9.2")

    implementation(project(":lib_modifier_aspect:aspect-api"))

}