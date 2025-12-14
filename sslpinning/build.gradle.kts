plugins {
    alias(libs.plugins.android.library)
    alias(libs.plugins.kotlin.android)

    id("maven-publish")
    id("signing")
}

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

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation(libs.jsonCanonicalization)
    implementation(libs.okhttp)
    implementation(libs.okhttpLogging)
    implementation(libs.kotlinxCoroutinesCore)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.appcompat)
    implementation(libs.material)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
}

group = "io.github.sslpinning"
version = "0.1.0"

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                from(components["release"])

                artifactId = "sslpinning"

                pom {
                    name.set("sslpinning")
                    description.set("Android SDK for SSL pinning with remotely signed pins registry.")
                    url.set("https://github.com/ssl-pinning/ssl-pinning-android")

                    licenses {
                        license {
                            name.set("BSD 3-Clause")
                            url.set("https://opensource.org/licenses/BSD-3-Clause")
                        }
                    }

                    scm {
                        url.set("https://github.com/ssl-pinning/ssl-pinning-android")
                        connection.set("scm:git:https://github.com/ssl-pinning/ssl-pinning-android.git")
                        developerConnection.set("scm:git:ssh://git@github.com:ssl-pinning/ssl-pinning-android.git")
                    }

                    developers {
                        developer {
                            id.set("ssl-pinning")
                            name.set("ssl-pinning")
                        }
                    }
                }
            }
        }

        repositories {
            maven {
                name = "central"

                // Central Portal staging endpoint
                url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")

                credentials {
                    username = System.getenv("CENTRAL_USERNAME")
                    password = System.getenv("CENTRAL_PASSWORD")
                }
            }
        }
    }

    signing {
        val key = System.getenv("GPG_PRIVATE_KEY")
        val pass = System.getenv("GPG_PASSPHRASE")

        useInMemoryPgpKeys(key, pass)
        sign(publishing.publications["release"])
    }
}
