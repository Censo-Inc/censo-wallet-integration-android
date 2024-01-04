plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    kotlin("plugin.serialization")
    id("maven-publish")
    id("signing")
}

android {
    namespace = "co.censo.walletintegration"
    compileSdk = 34

    defaultConfig {
        minSdk = 26

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        consumerProguardFiles("consumer-rules.pro")

        aarMetadata {
            minCompileSdk = 26
        }
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
        }
    }

    testFixtures {
        enable = true
    }
}

afterEvaluate {
    publishing {
        publications {
            create<MavenPublication>("release") {
                groupId = "co.censo.walletintegration"
                artifactId = "censowalletintegration"
                version = "0.1.0"
                from(components["release"])
                pom.withXml {
                    asNode().let {
                        it.appendNode("name", "Censo Wallet Integration")
                        it.appendNode("description", "SDK to allow Android wallets to integration with the Censo seed phrase manager")
                        it.appendNode("url", "https://github.com/Censo-Inc/censo-wallet-integration-android")
                        it.appendNode("scm").appendNode("url", "https://github.com/Censo-Inc/censo-wallet-integration-android.git")
                        it.appendNode("licenses").appendNode("license").let {
                            it.appendNode("name", "MIT")
                            it.appendNode("url", "https://opensource.org/license/mit/")
                        }
                        it.appendNode("developers").appendNode("developer").let {
                            it.appendNode("id", "censo")
                            it.appendNode("name", "Censo, Inc.")
                            it.appendNode("email", "developers@censo.co")
                        }
                    }
                }
            }
        }
        repositories {
            maven {
                url = uri("https://s01.oss.sonatype.org/service/local/staging/deploy/maven2/")
                credentials {
                    username = properties["ossrhUsername"].toString()
                    password = properties["ossrhPassword"].toString()
                }
            }
        }
    }

    signing {
        sign(publishing.publications["release"])
    }
}


dependencies {

    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.appcompat:appcompat:1.6.1")
    implementation("com.google.android.material:material:1.11.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.1")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.2")
    implementation("org.jetbrains.kotlinx:kotlinx-datetime:0.5.0")
    api("org.bouncycastle:bcprov-jdk15to18:1.77")
    api("io.github.novacrypto:Base58:2022.01.17")
    api("com.squareup.retrofit2:retrofit:2.9.0")
    api("com.jakewharton.retrofit:retrofit2-kotlinx-serialization-converter:1.0.0")
    api("com.squareup.okhttp3:okhttp:5.0.0-alpha.2")
    api("com.squareup.okhttp3:logging-interceptor:5.0.0-alpha.2")
    testImplementation("org.awaitility:awaitility-kotlin:4.2.0")
    testImplementation("junit:junit:4.13.2")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.9.0")
    androidTestImplementation("androidx.test.ext:junit:1.1.5")
    androidTestImplementation("androidx.test.espresso:espresso-core:3.5.1")
    testImplementation("io.mockk:mockk:1.13.8")
}