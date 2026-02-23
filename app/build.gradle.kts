import java.io.BufferedReader
import java.io.FileInputStream
import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("com.google.devtools.ksp")
    id("com.google.gms.google-services")
    id("com.google.firebase.crashlytics")
}

android {
    namespace = "com.codesrahul.exclusivetv"
    compileSdk = 34
    ndkVersion = "27.1.12297006"
    
    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "3.22.1"
        }
    }

    defaultConfig {
        applicationId = "com.codesrahul.exclusivetv"
        minSdk = 23
        targetSdk = 34
        multiDexEnabled = true
        versionCode = if (project.hasProperty("versionCodeOverride")) {
            project.property("versionCodeOverride").toString().toInt()
        } else {
            getVersionCode()
        }
        versionName = if (project.hasProperty("versionNameOverride")) {
            project.property("versionNameOverride").toString()
        } else {
            getVersionName()
        }
        println("Building with VersionCode: $versionCode, VersionName: $versionName")
    }

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    signingConfigs {
        create("release") {
            val keystorePropertiesFile = rootProject.file("keystore.properties")
            if (keystorePropertiesFile.exists()) {
                val keystoreProperties = Properties()
                keystoreProperties.load(FileInputStream(keystorePropertiesFile))
                keyAlias = keystoreProperties.getProperty("keyAlias")
                keyPassword = keystoreProperties.getProperty("keyPassword")
                storeFile = rootProject.file(keystoreProperties.getProperty("storeFile"))
                storePassword = keystoreProperties.getProperty("storePassword")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            isShrinkResources = false
            signingConfig = signingConfigs.getByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
    kotlinOptions {
        jvmTarget = "1.8"
    }

    lintOptions {
        isAbortOnError = false
    }
}

fun getVersionCode(): Int {
    return try {
        val versionFile = File(rootProject.projectDir, "version.json")
        val versionJson = versionFile.readText()
        val versionCode = versionJson.substringAfter("\"version_code\": ")
            .substringBefore(",")
            .trim()
            .toInt()
        println("Version Code from version.json: $versionCode")
        versionCode
    } catch (e: Exception) {
        println("Error reading version.json: ${e.message}")
        // Fallback to git tags if version.json is not available
        try {
            val process = Runtime.getRuntime().exec("git describe --tags --always")
            process.waitFor()
            val arr = (process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
                .replace("v", "").replace(".", " ").replace("-", " ") + " 0").split(" ")
            val versionCode =
                arr[0].toInt() * 16777216 + arr[1].toInt() * 65536 + arr[2].toInt() * 256 + arr[3].toInt()
            println("Version Code from git: $versionCode")
            versionCode
        } catch (ignored: Exception) {
            1
        }
    }
}

fun getVersionName(): String {
    return try {
        val versionFile = File(rootProject.projectDir, "version.json")
        val versionJson = versionFile.readText()
        val versionName = versionJson.substringAfter("\"version_name\": \"")
            .substringBefore("\"")
            .trim()
        println("Version Name from version.json: $versionName")
        versionName
    } catch (e: Exception) {
        println("Error reading version.json: ${e.message}")
        // Fallback to git tags if version.json is not available
        try {
            val process = Runtime.getRuntime().exec("git describe --tags --always")
            process.waitFor()
            val versionName = process.inputStream.bufferedReader().use(BufferedReader::readText).trim()
                .removePrefix("v")
            versionName.ifEmpty {
                "1.0.0"
            }
        } catch (ignored: Exception) {
            "1.0.0"
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.multidex:multidex:2.0.1")
    implementation("androidx.recyclerview:recyclerview:1.3.2")
    implementation("com.github.bumptech.glide:glide:4.16.0")

    // 21:2.11.0 17:2.6.4
    val retrofit2Version = "2.6.4"
    // Gson 2.10.1 and older: API level 19
    implementation("com.google.code.gson:gson:2.10.1")
    implementation("com.squareup.retrofit2:converter-gson:$retrofit2Version") {
        exclude(group = "com.google.code.gson", module = "gson")
    }
    implementation("com.squareup.retrofit2:retrofit:$retrofit2Version")

    implementation("androidx.constraintlayout:constraintlayout:2.1.4")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")

    implementation("io.github.lizongying:gua64:1.4.5")
    implementation("org.nanohttpd:nanohttpd:2.3.1")

    implementation("com.google.zxing:core:3.5.3")

    implementation("androidx.leanback:leanback:1.0.0")

    val media3Version = "1.4.1"
    implementation("androidx.media3:media3-exoplayer:$media3Version")
    implementation("androidx.media3:media3-ui:$media3Version")
    implementation("androidx.media3:media3-exoplayer-hls:$media3Version")
    implementation("androidx.media3:media3-exoplayer-rtsp:$media3Version")
    implementation("androidx.media3:media3-exoplayer-dash:$media3Version")
    implementation("androidx.media3:media3-datasource-okhttp:$media3Version")
    implementation("androidx.media3:media3-datasource-rtmp:$media3Version")
    implementation("androidx.media3:media3-extractor:$media3Version")

    implementation(platform("com.google.firebase:firebase-bom:33.1.2"))
    implementation("com.google.firebase:firebase-analytics")
    implementation("com.google.firebase:firebase-crashlytics")
    implementation("com.google.firebase:firebase-config-ktx")
    implementation("com.google.firebase:firebase-auth")
    implementation("com.google.firebase:firebase-firestore")


    implementation("androidx.cardview:cardview:1.0.0")
    implementation("androidx.tvprovider:tvprovider:1.0.0")

    // Room Database
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version")
    ksp("androidx.room:room-compiler:$room_version")

    // Security & Integrity
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.google.android.play:integrity:1.3.0")

    testImplementation("junit:junit:4.13.2")
}

configurations.configureEach {
    resolutionStrategy {
        force("com.google.code.gson:gson:2.10.1")
    }
}

tasks.withType<Test> {
    testLogging {
        showStandardStreams = true
    }
}