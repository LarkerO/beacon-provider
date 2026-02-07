import org.gradle.api.GradleException
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.toolchain.JavaLanguageVersion

plugins {
    `java-library`
}

val architecturyVersion: String by rootProject
val architecturyJarFile = rootProject.layout.projectDirectory.file("checkouts/architectury-$architecturyVersion.jar")
val downloadArchitecturyJar = rootProject.tasks.named("downloadArchitecturyJar")
val mtrCommonJar = rootProject.layout.projectDirectory.file("libs/mtr3/MTR-forge-1.20.1-3.2.2-hotfix-2-slim.jar").also {
    if (!it.asFile.exists()) {
        throw GradleException("请把 MTR-forge-1.20.1-3.2.2-hotfix-2-slim.jar 放到 libs/mtr3/ 以供 common 编译")
    }
}

repositories {
    mavenCentral()
}

dependencies {
    api("org.slf4j:slf4j-api:2.0.13")
    api("com.google.code.gson:gson:2.11.0")
    api("org.msgpack:msgpack-core:0.9.0")
    api("org.bouncycastle:bcprov-jdk15on:1.70")
    compileOnly("io.netty:netty-all:4.1.111.Final")
    compileOnly(files(architecturyJarFile).builtBy(downloadArchitecturyJar))
    compileOnly(files(mtrCommonJar))
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
    options.release.set(8)
}
