import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "sgspe"
description = "sgspe: gestión social para proyectos extractivos sobre wasichai (core, views, forms, pages, gis, workflow)"

val wasichaiVersion = "0.3.2"

dependencies {
    implementation(platform("wasichai:wasichai-bom:$wasichaiVersion"))
    implementation("wasichai:wasichai-spring-boot-starter")
    implementation("wasichai:wasichai-spring-boot-starter-views")
    implementation("wasichai:wasichai-spring-boot-starter-forms")
    implementation("wasichai:wasichai-spring-boot-starter-pages")
    // F2: campos GEOMETRY en SRID 4326, filtro bbox, features y el componente MAP (gis.md §10); workflows por objeto
    // (workflows.md)
    implementation("wasichai:wasichai-spring-boot-starter-gis")
    implementation("wasichai:wasichai-spring-boot-starter-workflow")

    // WasichaiIntegrationTest; brings spring-boot-starter-test, webflux-test and testcontainers
    testImplementation("wasichai:wasichai-test")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

kotlin {
    jvmToolchain(25)
    compilerOptions {
        freeCompilerArgs.add("-Xjsr305=strict")
    }
}

// ktlint lee .editorconfig; la misma versión que wasichai y caja
ktlint {
    version.set("1.7.1")
}

// las pruebas unitarias corren siempre; las que necesitan base van por integrationTest
tasks.named<Test>("test") {
    useJUnitPlatform {
        excludeTags("integration")
    }
    // ArchitectureTest lee las fuentes y model/roles.json: un cambio en ellos vuelve a correr las pruebas
    inputs.dir("src/main/kotlin")
    // files, no file: tolera que roles.json no exista; file falla en Gradle 9.8 si el archivo falta
    inputs.files("model/roles.json")
    // un shell con develop/.env cargado trae estas variables: ProdStartupTest prueba el perfil prod con sus propios valores y
    // una variable de entorno gana a lo que él fija, así que el JVM de las pruebas no las ve
    for (name in listOf("WASICHAI_JWT_SECRET", "WASICHAI_SEED_DEV", "WASICHAI_SECURITY_JWT_TTL")) {
        environment.remove(name)
    }
}

// WASICHAI_TEST_DB_* (si están) apuntan la suite a una base externa en lugar de Testcontainers
tasks.register<Test>("integrationTest") {
    group = "verification"
    description = "pruebas de integración contra PostgreSQL 18 con PostGIS 3.6"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    useJUnitPlatform {
        includeTags("integration")
    }
    // la imagen del compose desde F1 (architecture.md §5.1); una base externa (WASICHAI_TEST_DB_*) ya la tiene
    systemProperty("wasichai.test.db.image", "postgis/postgis:18-3.6")
    // la app registra en stdout; stderr lleva solo lo que una prueba reporta
    testLogging {
        events("standard_error")
    }
    // SgspeApiTest aprovisiona con los scripts y los json de model/: un cambio en ellos vuelve a correr la suite, nunca un
    // verde de caché
    inputs.files(fileTree("model") { include("*.py", "*.json") })
    shouldRunAfter(tasks.named("test"))
}

// un jar ejecutable con nombre fijo
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

// una app, no una librería: sin el jar plano junto al boot jar
tasks.named<Jar>("jar") {
    enabled = false
}
