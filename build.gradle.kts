plugins {
   alias(libs.plugins.kotlin.jvm)
   alias(libs.plugins.kotlinx.serialization)
}

group = "com.github.kantis"
version = "0.1-SNAPSHOT"

repositories {
   mavenCentral()
   maven("https://oss.sonatype.org/content/repositories/snapshots") { mavenContent { snapshotsOnly() } }
   maven("https://s01.oss.sonatype.org/content/repositories/snapshots") { mavenContent { snapshotsOnly() } }
   mavenLocal()
}

kotlin {
   jvmToolchain(17)
   explicitApi()
}

dependencies {
   implementation(libs.kotlinx.serialization.json)
   implementation(libs.kotlinx.coroutines.core)
   implementation(libs.kotlinx.coroutines.slf4j)
   implementation(libs.ktor.client.core)
   testImplementation(libs.kotest.assertions.json)
   testImplementation(libs.kotest.runner.junit5)
   testImplementation(libs.kotest.extensions.wiremock)
   testImplementation(libs.ktor.client.cio)
   testImplementation(libs.mockk)
   testImplementation(libs.logback)
}

tasks.withType<Test>().configureEach {
   useJUnitPlatform()
   systemProperty("log4j2.configurationFile", "log4j2-local.properties")
}
