plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.maven.publish)
}

import com.vanniktech.maven.publish.SonatypeHost

android {
    namespace = "io.github.sslpinning"
    compileSdk {
        version = release(36)
    }

    defaultConfig {
        minSdk = 24

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }

    kotlinOptions {
        jvmTarget = "11"
    }
}

dependencies {
    api(libs.okhttp)
    api(libs.kotlinxCoroutinesCore)
    implementation(libs.okhttpLogging)
    implementation(libs.jsonCanonicalization)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

mavenPublishing {
    publishToMavenCentral(SonatypeHost.CENTRAL_PORTAL)
    signAllPublications()

    pom {
        name.set("sslpinning")
        description.set("Android SDK for SSL pinning with remotely signed pins registry.")
        inceptionYear.set("2025")
        url.set("https://github.com/ssl-pinning/android-sdk/")

        licenses {
            license {
                name.set("BSD-3-Clause")
                url.set("https://opensource.org/licenses/BSD-3-Clause")
                distribution.set("repo")
            }
        }

        developers {
            developer {
                id.set("ssl-pinning")
                name.set("ssl-pinning")
                url.set("https://github.com/ssl-pinning/")
            }
        }

        scm {
            url.set("https://github.com/ssl-pinning/android-sdk/")
            connection.set("scm:git:git://github.com/ssl-pinning/android-sdk.git")
            developerConnection.set("scm:git:ssh://git@github.com/ssl-pinning/android-sdk.git")
        }
    }
}
