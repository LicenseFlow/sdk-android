plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
    `maven-publish`
    signing
}

val groupId: String = (project.findProperty("GROUP") as String?) ?: "dev.licenseflow"
val artifactId: String = (project.findProperty("ARTIFACT_ID") as String?) ?: "licenseflow-android"
val versionName: String = (project.findProperty("VERSION_NAME") as String?) ?: "2.2.0"

group = groupId
version = versionName

android {
    namespace = "dev.licenseflow.sdk"
    compileSdk = 34

    defaultConfig {
        minSdk = 24
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        buildConfigField("String", "SDK_VERSION", "\"$versionName\"")
    }

    buildFeatures {
        buildConfig = true
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    testOptions {
        unitTests.isReturnDefaultValues = true
    }

    publishing {
        singleVariant("release") {
            withSourcesJar()
            withJavadocJar()
        }
    }
}

dependencies {
    implementation("androidx.core:core-ktx:1.12.0")
    implementation("androidx.security:security-crypto:1.1.0-alpha06")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("com.google.code.gson:gson:2.10.1")

    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
}

publishing {
    publications {
        register<MavenPublication>("release") {
            afterEvaluate { from(components["release"]) }

            this.groupId = groupId
            this.artifactId = artifactId
            this.version = versionName

            pom {
                name.set("LicenseFlow Android SDK")
                description.set(
                    "Official LicenseFlow Android (Kotlin) SDK — hardware-bound activations, " +
                        "identity-based entitlement resolution, floating seat leases and offline " +
                        "entitlement caching."
                )
                url.set("https://licenseflow.dev")
                inceptionYear.set("2026")

                licenses {
                    license {
                        name.set("MIT License")
                        url.set("https://opensource.org/licenses/MIT")
                    }
                }
                developers {
                    developer {
                        id.set("licenseflow")
                        name.set("LicenseFlow")
                        email.set("support@licenseflow.dev")
                        organization.set("LicenseFlow")
                        organizationUrl.set("https://licenseflow.dev")
                    }
                }
                scm {
                    url.set("https://github.com/LicenseFlow/sdk-android")
                    connection.set("scm:git:https://github.com/LicenseFlow/sdk-android.git")
                    developerConnection.set("scm:git:ssh://git@github.com/LicenseFlow/sdk-android.git")
                }
            }
        }
    }

    repositories {
        // Sonatype Central Portal (Maven Central). Credentials are provided by CI
        // via MAVEN_USERNAME / MAVEN_PASSWORD and are never committed.
        maven {
            name = "mavenCentral"
            url = uri("https://ossrh-staging-api.central.sonatype.com/service/local/staging/deploy/maven2/")
            credentials {
                username = System.getenv("MAVEN_USERNAME") ?: ""
                password = System.getenv("MAVEN_PASSWORD") ?: ""
            }
        }
        // Local verification target for `./gradlew publishReleasePublicationToLocalRepoRepository`
        maven {
            name = "localRepo"
            url = uri(layout.buildDirectory.dir("repo"))
        }
    }
}

signing {
    val signingKey = System.getenv("MAVEN_GPG_PRIVATE_KEY")
    val signingPassphrase = System.getenv("MAVEN_GPG_PASSPHRASE")
    if (!signingKey.isNullOrBlank()) {
        useInMemoryPgpKeys(signingKey, signingPassphrase ?: "")
        sign(publishing.publications)
    }
}
