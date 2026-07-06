plugins {
    java
}

group = "org.mgmmehrad"
version = "1.0.4"

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