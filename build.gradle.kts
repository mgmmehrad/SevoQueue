buildscript {
    repositories {
        mavenCentral()
    }
    dependencies {
        classpath("com.guardsquare:proguard-gradle:7.4.2")
    }
}

plugins {
    java
}

group = "org.mgmmehrad"
version = "1.0.5"

val javaVersion = 17
val bungeecordVersion = "1.21-R0.3"
val velocityVersion = "3.3.0-SNAPSHOT"

java {
    sourceCompatibility = JavaVersion.toVersion(javaVersion)
    targetCompatibility = JavaVersion.toVersion(javaVersion)
}

repositories {
    mavenCentral()
    maven { url = uri("https://repo.opencollab.dev/maven-snapshots/") }
    maven { url = uri("https://repo.opencollab.dev/maven-releases/") }
    maven { url = uri("https://repo.papermc.io/repository/maven-public/") }
    maven { url = uri("https://repo.velocitypowered.com/snapshots/") }
}

dependencies {
    // کتابخانه‌هایی که در خود سرور موجود هستند و نباید پکیج شوند
    compileOnly("net.md-5:bungeecord-api:$bungeecordVersion")
    compileOnly("com.velocitypowered:velocity-api:$velocityVersion")
    compileOnly("net.kyori:adventure-api:4.17.0")
    compileOnly("com.google.inject:guice:5.1.0")
    compileOnly("org.slf4j:slf4j-api:2.0.9")
    compileOnly("io.papermc.paper:paper-api:1.20.4-R0.1-SNAPSHOT")
    compileOnly("org.geysermc.geyser:api:2.4.2-SNAPSHOT")

    // کتابخانه‌ای که باید حتماً به داخل پلاگین شما تزریق شود
    implementation("org.spongepowered:configurate-yaml:4.1.2")
    // کتابخانه bStats برای Metrics
    implementation("org.bstats:bstats-velocity:3.2.1")
}

// ساخت خودکار فایل Fat JAR (ترکیب فایل‌ها) بدون نیاز به افزونه Shadow
tasks.jar {
    archiveClassifier.set("") // اضافه کردن پسوند -all به فایل خروجی

    duplicatesStrategy = DuplicatesStrategy.EXCLUDE

    // کپی کردن محتویات کدهای کامپایل شده پروژه همراه با منابع داخل فایل جار
    from(sourceSets.main.get().output)

    // باز کردن و تزریق تمام متعلقات پکیج‌های کانتفیگوریشن به صورت بایت‌کد مستقیم
    from(configurations.runtimeClasspath.get().map { if (it.isDirectory) it else zipTree(it) }) {
        exclude("META-INF/*.SF", "META-INF/*.DSA", "META-INF/*.RSA")
    }
}

tasks.processResources {
    val props = mapOf(
        "version" to version,
        "java.version" to javaVersion,
        "bungeecord.version" to bungeecordVersion,
        "velocity.version" to velocityVersion
    )
    inputs.properties(props)
    filteringCharset = "UTF-8"

    filesNotMatching("**/*.png") {
        expand(props)
    }
}

// تسک پروگارد هماهنگ شده با فرآیند ساخت پروژه
val proguardTask = tasks.register<proguard.gradle.ProGuardTask>("proguard") {
    dependsOn(tasks.jar)

    // فایل ورودی همان جار ساخته شده است
    injars(tasks.jar.get().archiveFile)

    // خروجی در یک فایل موقت ذخیره می‌شود تا تداخل ایجاد نکند
    val tempOut = layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar")
    outjars(tempOut)

    // لایبرری‌های جاوا ۱۷ و وابستگی‌های پروژه
    libraryjars("${System.getProperty("java.home")}/jmods/java.base.jmod")
    libraryjars(configurations.compileClasspath.get().files)

    // خواندن تنظیمات از فایل proguard.pro
    configuration("proguard.pro")
}

// جایگزین کردن فایل اصلی با فایل ابفاسکیت شده پس از اجرای دستور build
tasks.build {
    dependsOn(proguardTask)
    doLast {
        val finalJar = tasks.jar.get().archiveFile.get().asFile
        val obfuscatedJar = layout.buildDirectory.file("libs/${project.name}-${project.version}-protected.jar").get().asFile

        if (obfuscatedJar.exists()) {
            finalJar.delete()
            obfuscatedJar.renameTo(finalJar)
            logger.lifecycle("ProGuard applied successfully to ${finalJar.name}!")
        }
    }
}