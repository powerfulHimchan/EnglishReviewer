plugins {
    kotlin("jvm"); kotlin("plugin.spring")
    id("org.springframework.boot"); id("io.spring.dependency-management")
}
kotlin { jvmToolchain(21) }
dependencies {
    implementation(project(":core"))
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.8.0")
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation(kotlin("test"))
}
tasks.test { useJUnitPlatform() }
tasks.bootJar { archiveFileName.set("server.jar") }
