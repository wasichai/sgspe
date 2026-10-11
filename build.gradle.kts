import org.springframework.boot.gradle.tasks.bundling.BootJar

plugins {
    kotlin("jvm") version "2.4.20"
    kotlin("plugin.spring") version "2.4.20"
    id("org.springframework.boot") version "4.1.1"
    id("org.jlleitschuh.gradle.ktlint") version "14.2.0"
}

group = "sgspe"
description = "sgspe: gestión social para proyectos extractivos sobre wasichai (core, views, forms, pages, gis, workflow)"

val wasichaiVersion = "1.1.0"

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
    // F3: automatizaciones (AUT-NN, AutomationDrain), documentos emitidos (RQ-REP-029), campos FILE con su
    // almacenamiento (D1, WAS-ADR-0061) y notificaciones con su canal de correo (D2, WAS-ADR-0046 y 0060)
    implementation("wasichai:wasichai-spring-boot-starter-automation")
    implementation("wasichai:wasichai-spring-boot-starter-documents")
    implementation("wasichai:wasichai-spring-boot-starter-files")
    implementation("wasichai:wasichai-spring-boot-starter-notifications")
    // wasichai.files.store=s3 (MinIO en desarrollo, ADR-0014): el cliente asíncrono del AWS SDK v2, alineado por su BOM
    implementation(platform("software.amazon.awssdk:bom:2.55.10"))
    implementation("software.amazon.awssdk:s3")
    // el canal de correo de wasichai-notifications pide un MailSender (spring.mail.*, GAP-021)
    implementation("org.springframework.boot:spring-boot-starter-mail")

    // sgspe.territory lee hojas XLSX y sgspe.privacy las escribe (RQ-REP-022); 5.4 o superior por CVE-2025-31672
    // (platform-fit.md §6)
    implementation("org.apache.poi:poi-ooxml:5.5.1")

    // WasichaiIntegrationTest; brings spring-boot-starter-test, webflux-test and testcontainers
    testImplementation("wasichai:wasichai-test")
    // el contrato del almacenamiento S3 contra un MinIO de Testcontainers (FileStoreContractTest); versión del BOM de
    // Testcontainers que trae Spring Boot
    testImplementation("org.testcontainers:testcontainers-minio")
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

// el modelo, los roles y los workflows viajan dentro del jar como configuración (ModelCatalog): las guardas leen de ahí
// los campos derivados, los roles declarados y los roles de cada transición, nunca de literales en el código (ASM-070)
tasks.named<ProcessResources>("processResources") {
    from("model") {
        include("model.json", "roles.json", "workflows/*.json")
        into("model")
    }
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
    // la programación de los trabajos (DerivedFieldsSchedule) no corre en las pruebas: competiría por el candado de clúster
    // con runOnce y recorrería los tenants de la base compartida. las pruebas de un trabajo llaman a runOnce
    systemProperty("sgspe.jobs.enabled", "false")
    // un shell con develop/.env cargado trae estas variables: ProdStartupTest prueba el perfil prod con sus propios valores y
    // una variable de entorno gana a lo que él fija, así que el JVM de las pruebas no las ve
    val fromEnvFile =
        listOf("WASICHAI_JWT_SECRET", "WASICHAI_SEED_DEV", "WASICHAI_SECURITY_JWT_TTL", "WASICHAI_FILES_STORE", "WASICHAI_FILES_S3_BUCKET")
    for (name in fromEnvFile) {
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
    // sin programación de trabajos: una vuelta programada con el candado de clúster haría que runOnce devolviera null
    systemProperty("sgspe.jobs.enabled", "false")
    integrationDefaults()
    // -Dmatrix.roles=FIELD_AGENT,GIS_ANALYST recorta la matriz de permisos a esos roles (PermissionMatrixApiTest)
    System.getProperty("matrix.roles")?.let { systemProperty("matrix.roles", it) }
    // la app registra en stdout; stderr lleva solo lo que una prueba reporta
    testLogging {
        events("standard_error")
    }
    // SgspeApiTest aprovisiona con los scripts y los json de model/, incluidos los de model/workflows/: un cambio en ellos
    // vuelve a correr la suite, nunca un verde de caché
    inputs.files(fileTree("model") { include("**/*.py", "**/*.json") })
    shouldRunAfter(tasks.named("test"))
}

// lo que la integración fija para los módulos de F3, como propiedades del sistema, que ganan a las variables de
// develop/.env (en `test` no: ProdStartupTest necesita que application-prod.yml decida el almacenamiento): los archivos
// en una carpeta de build (sin MinIO: el almacenamiento S3 lo prueba FileStoreContractTest), sin la limpieza de archivos
// ni el bucle de notificaciones, que recorrerían todos los tenants de la base compartida, y sin correo
fun Test.integrationDefaults() {
    systemProperty("wasichai.files.store", "local")
    systemProperty(
        "wasichai.files.local.path",
        layout.buildDirectory
            .dir("wasichai-files")
            .get()
            .asFile.path
    )
    systemProperty("wasichai.files.cleanup.interval", "0s")
    systemProperty("wasichai.notifications.tick", "0s")
    systemProperty("wasichai.notifications.email.enabled", "false")
}

// un jar ejecutable con nombre fijo
tasks.named<BootJar>("bootJar") {
    archiveFileName.set("app.jar")
}

// una app, no una librería: sin el jar plano junto al boot jar
tasks.named<Jar>("jar") {
    enabled = false
}
