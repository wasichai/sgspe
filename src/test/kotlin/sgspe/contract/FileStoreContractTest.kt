package sgspe.contract

import kotlinx.coroutines.reactive.awaitSingle
import kotlinx.coroutines.runBlocking
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Tag
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.springframework.core.io.buffer.DataBufferUtils
import org.testcontainers.containers.MinIOContainer
import org.testcontainers.utility.DockerImageName
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider
import software.amazon.awssdk.regions.Region
import software.amazon.awssdk.services.s3.S3AsyncClient
import software.amazon.awssdk.services.s3.model.S3Exception
import wasichai.files.FileStore
import wasichai.files.LocalFileStore
import wasichai.files.S3FileStore
import java.net.URI
import java.nio.file.Path
import java.util.UUID
import kotlin.random.Random

// ADR-0014 y D1: el contrato que SGSPE espera del almacenamiento de las evidencias (FileStore de wasichai-files,
// WAS-ADR-0061), con el almacén local de las pruebas y con el S3 de producción contra un MinIO: lo que se guarda bajo una
// clave <organización>/<archivo> vuelve igual byte a byte, una clave con otra forma se rechaza antes de tocar el almacén y
// borrar dos veces no falla. en la CI el MinIO es de Testcontainers; con Docker remoto, SGSPE_TEST_S3_ENDPOINT apunta al
// MinIO de compose.yml por el túnel (docs/develop/README.md §6)
@Tag("integration")
class FileStoreContractTest {
    @TempDir
    lateinit var folder: Path

    private fun key(): String = FileStore.keyOf(UUID.randomUUID(), UUID.randomUUID())

    private suspend fun read(
        store: FileStore,
        key: String
    ): ByteArray {
        val joined = DataBufferUtils.join(store.open(key)).awaitSingle()
        return ByteArray(joined.readableByteCount()).also {
            joined.read(it)
            DataBufferUtils.release(joined)
        }
    }

    private fun contract(store: FileStore) =
        runBlocking {
            val bytes = Random(7).nextBytes(256 * 1024)
            val key = key()
            store.put(key, bytes, "application/pdf")
            assertArrayEquals(bytes, read(store, key))
            // una clave que no es <uuid>/<uuid> no llega al almacén: ni rutas ni separadores propios
            for (bad in listOf("../$key", "otra/clave", key.replace("/", "\\"))) {
                assertThrows(IllegalArgumentException::class.java, { runBlocking { store.put(bad, bytes, "application/pdf") } }, bad)
            }
            store.delete(key)
            store.delete(key)
            assertThrows(Exception::class.java) { runBlocking { read(store, key) } }
        }

    @Test
    fun `el almacen local cumple el contrato`() = contract(LocalFileStore(folder))

    @Test
    fun `el almacen S3 cumple el contrato contra MinIO`() = contract(S3FileStore(Minio.client, Minio.bucket(), "pruebas-${UUID.randomUUID()}"))

    private object Minio {
        private val external = System.getenv("SGSPE_TEST_S3_ENDPOINT")

        // la misma imagen que compose.yml; solo se levanta sin un MinIO externo
        private val container: MinIOContainer by lazy {
            MinIOContainer(DockerImageName.parse("pgsty/minio:RELEASE.2026-08-04T00-00-00Z").asCompatibleSubstituteFor("minio/minio"))
                .also { it.start() }
        }

        val client: S3AsyncClient by lazy {
            val endpoint = external ?: container.s3URL
            val user = if (external != null) System.getenv("SGSPE_TEST_S3_ACCESS_KEY") ?: "sgspe" else container.userName
            val password = if (external != null) System.getenv("SGSPE_TEST_S3_SECRET_KEY") ?: "sgspe-minio" else container.password
            S3AsyncClient
                .builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.US_EAST_1)
                .forcePathStyle(true)
                .credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(user, password)))
                .build()
        }

        // el bucket de las pruebas, creado si falta (el cliente asíncrono envuelve el 404 en una CompletionException)
        @Synchronized
        fun bucket(): String {
            val name = "sgspe-pruebas"
            try {
                client.headBucket { it.bucket(name) }.join()
            } catch (e: Exception) {
                val cause = e.cause as? S3Exception ?: throw e
                if (cause.statusCode() != 404) throw e
                client.createBucket { it.bucket(name) }.join()
            }
            return name
        }
    }
}
