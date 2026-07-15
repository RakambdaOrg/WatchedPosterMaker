plugins {
    id("org.jetbrains.kotlin.jvm") version "2.4.10"
    id("org.jetbrains.kotlin.plugin.serialization") version "2.4.10"
    id("io.ktor.plugin") version "3.5.1"
}

group = "fr.rakambda"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation(platform(libs.jacksonBom))
    implementation(platform(libs.log4j2Bom))

    implementation(libs.dotenvKotlin)
    implementation(libs.bundles.logging)
    implementation(libs.bundles.jackson)

    implementation(libs.mariadb)
    implementation(libs.sqlite)
    implementation(libs.bundles.exposed)

    implementation("io.ktor:ktor-client-auth")
    implementation("io.ktor:ktor-client-cio")
    implementation("io.ktor:ktor-client-core")
    implementation("io.ktor:ktor-client-logging")
    implementation("io.ktor:ktor-client-content-negotiation")
    implementation("io.ktor:ktor-serialization-jackson")

    testImplementation(kotlin("test-junit5"))
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
        jvmTarget = org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_26
    }
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "26"
    targetCompatibility = "26"
}

tasks.withType<Test> {
    useJUnitPlatform()
}

application {
    mainClass.set("fr.rakambda.watchedpostermaker.MainKt")
}

ktor {
    docker {
        jreVersion.set(JavaVersion.VERSION_26)
        imageTag.set(providers.gradleProperty("ktor.docker.tag").orElse("latest"))
        externalRegistry.set(
            io.ktor.plugin.features.DockerImageRegistry.externalRegistry(
                hostname = providers.gradleProperty("ktor.docker.host"),
                username = providers.gradleProperty("ktor.docker.username"),
                password = providers.gradleProperty("ktor.docker.password"),
                project = providers.gradleProperty("ktor.docker.image"),
            )
        )
    }
}
