plugins {
    id("org.jetbrains.kotlin.jvm") version "1.5.31"
    application
}

buildscript {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/central")
    }
}

repositories {
    maven("https://maven.aliyun.com/repository/central")
    maven("https://maven.aliyun.com/repository/apache-snapshots")
}

dependencies {
    implementation(platform("org.jetbrains.kotlin:kotlin-bom"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.google.guava:guava:30.1.1-jre")
    implementation("info.picocli:picocli:4.6.2")
    implementation("org.ow2.asm:asm:9.2")
    implementation("org.javassist:javassist:3.28.0-GA")
    implementation("com.h2database:h2:2.0.204")
    implementation("org.rocksdb:rocksdbjni:6.27.3")

    testImplementation("org.jetbrains.kotlin:kotlin-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit")
}

application {
    mainClass.set("com.wybosys.jar_refactor_package.AppKt")
}
