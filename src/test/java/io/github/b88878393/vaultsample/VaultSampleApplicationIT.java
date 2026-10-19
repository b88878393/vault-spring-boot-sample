package io.github.b88878393.vaultsample;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.b88878393.vaultsample.database.DatabaseCredentialRotator;
import java.io.IOException;
import java.io.StringReader;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.ApplicationContext;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.testcontainers.containers.Container.ExecResult;
import org.testcontainers.containers.Network;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.lifecycle.Startables;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.utility.MountableFile;
import org.testcontainers.vault.VaultContainer;

/**
 * End-to-end test against real Vault and PostgreSQL containers.
 *
 * <p>Vault is configured with the very same {@code scripts/vault-init.sh} and policy files that the quickstart uses
 * (executed inside the Vault container), and the application authenticates with the AppRole credentials that the
 * script generates. This therefore also verifies that the least-privilege policy is sufficient.
 *
 * <p>The containers are started in a static initializer and their coordinates are passed as system properties,
 * because {@code spring.config.import=vault://} is resolved very early - before {@code @DynamicPropertySource}
 * values would be available to the Vault config data loader.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class VaultSampleApplicationIT {

    /** DEMO token for the throwaway test Vault. */
    private static final String ROOT_TOKEN = "it-root-token";
    private static final String PG_SUPERUSER_PASSWORD = "it-postgres-password";
    private static final String APP = "vault-spring-boot-sample";
    private static final String DB_CREDS_PATH = "database/creds/app-readwrite";

    private static final Network NETWORK = Network.newNetwork();

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>(
            DockerImageName.parse(System.getProperty("postgres.image", "postgres:17.11-alpine"))
                    .asCompatibleSubstituteFor("postgres"))
            .withDatabaseName("appdb")
            .withUsername("postgres")
            .withPassword(PG_SUPERUSER_PASSWORD)
            .withNetwork(NETWORK)
            .withNetworkAliases("postgres")
            .withCopyFileToContainer(MountableFile.forHostPath("postgres/init.sql"),
                    "/docker-entrypoint-initdb.d/01-init.sql");

    static final VaultContainer<?> VAULT = new VaultContainer<>(
            DockerImageName.parse(System.getProperty("vault.image", "hashicorp/vault:2.2.0"))
                    .asCompatibleSubstituteFor("hashicorp/vault"))
            .withVaultToken(ROOT_TOKEN)
            .withNetwork(NETWORK)
            .withCopyFileToContainer(MountableFile.forHostPath("scripts/vault-init.sh", 0755),
                    "/opt/sample/scripts/vault-init.sh")
            .withCopyFileToContainer(MountableFile.forHostPath("vault/policies"), "/opt/sample/vault/policies");

    private static final List<String> SYSTEM_PROPERTIES = List.of(
            "spring.cloud.vault.uri", "spring.cloud.vault.app-role.role-id",
            "spring.cloud.vault.app-role.secret-id", "spring.datasource.url");

    static {
        Startables.deepStart(POSTGRES, VAULT).join();
        vault("sh", "/opt/sample/scripts/vault-init.sh");
        Properties env = parseEnv(vault("cat", "/tmp/app.env"));

        System.setProperty("spring.cloud.vault.uri", VAULT.getHttpHostAddress());
        System.setProperty("spring.cloud.vault.app-role.role-id", env.getProperty("VAULT_ROLE_ID"));
        System.setProperty("spring.cloud.vault.app-role.secret-id", env.getProperty("VAULT_SECRET_ID"));
        System.setProperty("spring.datasource.url", POSTGRES.getJdbcUrl());
    }

    @AfterAll
    static void clearSystemProperties() {
        SYSTEM_PROPERTIES.forEach(System::clearProperty);
    }

    @Autowired
    private TestRestTemplate http;

    @Autowired
    private ApplicationContext context;

    @Test
    @Order(1)
    void readsStaticSecretsFromKv() {
        Map<String, Object> config = getJson("/api/config");

        assertThat(config).containsEntry("greeting", "Hello from Vault KV v2 (demo value)")
                .containsEntry("maxNoteLength", 2000);
        assertThat((String) config.get("partnerApiKeyMasked")).startsWith("demo").contains("***")
                .doesNotContain("NOT-A-REAL-SECRET");
    }

    @Test
    @Order(2)
    void connectsWithDynamicDatabaseCredentials() {
        Map<String, Object> identity = getJson("/api/db/identity");

        assertThat((String) identity.get("sessionUser")).startsWith("v-");
        assertThat(identity).containsEntry("currentUser", "app_owner").containsEntry("database", "appdb");
    }

    @Test
    @Order(3)
    void storesNotesEncryptedWithTransit() throws Exception {
        ResponseEntity<Map<String, Object>> created = http.exchange("/api/notes", HttpMethod.POST,
                jsonEntity(Map.of("title", "launch codes", "content", "demo plaintext 0000")), MAP);
        assertThat(created.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        Object id = created.getBody().get("id");

        assertThat(getJson("/api/notes/" + id)).containsEntry("content", "demo plaintext 0000");
        assertThat((String) getJson("/api/notes/" + id + "/stored").get("contentCiphertext")).startsWith("vault:v1:");

        // What the database really contains, read with the PostgreSQL superuser:
        try (Connection connection = DriverManager.getConnection(POSTGRES.getJdbcUrl(), "postgres",
                PG_SUPERUSER_PASSWORD);
             Statement statement = connection.createStatement();
             ResultSet rs = statement.executeQuery("select content_ciphertext from notes where id = " + id)) {
            assertThat(rs.next()).isTrue();
            assertThat(rs.getString(1)).startsWith("vault:v1:").doesNotContain("demo plaintext");
        }
    }

    @Test
    @Order(4)
    void transitEndpointsRoundTrip() {
        Map<String, Object> encrypted = postJson("/api/transit/encrypt", Map.of("plaintext", "hello"));
        assertThat((String) encrypted.get("ciphertext")).startsWith("vault:v1:");

        Map<String, Object> decrypted = postJson("/api/transit/decrypt", Map.of("ciphertext", encrypted.get("ciphertext")));
        assertThat(decrypted).containsEntry("plaintext", "hello");
    }

    @Test
    @Order(5)
    @SuppressWarnings("unchecked")
    void exposesVaultHealth() {
        Map<String, Object> health = getJson("/actuator/health");

        assertThat(health).containsEntry("status", "UP");
        Map<String, Object> components = (Map<String, Object>) health.get("components");
        assertThat((Map<String, Object>) components.get("vault")).containsEntry("status", "UP");
        assertThat((Map<String, Object>) components.get("transit")).containsEntry("status", "UP");
        assertThat((Map<String, Object>) components.get("db")).containsEntry("status", "UP");
    }

    @Test
    @Order(6)
    void refreshesRotatedKvSecretsAtRuntime() {
        String maskedBefore = (String) getJson("/api/config").get("partnerApiKeyMasked");
        vault("vault", "kv", "patch", "secret/" + APP,
                "app.greeting=Hello after rotation (demo value)", "partner.api-key=demo-rotated-api-key-0002");

        ResponseEntity<List<String>> refresh = http.exchange("/actuator/refresh", HttpMethod.POST,
                jsonEntity(Map.of()), new ParameterizedTypeReference<>() { });
        assertThat(refresh.getBody()).contains("app.greeting", "partner.api-key");

        Map<String, Object> config = getJson("/api/config");
        assertThat(config).containsEntry("greeting", "Hello after rotation (demo value)"); // @ConfigurationProperties
        assertThat((String) config.get("partnerApiKeyMasked")).isNotEqualTo(maskedBefore).endsWith("02"); // @RefreshScope
        // The refresh must not break the database connection pool.
        assertThat((String) getJson("/api/db/identity").get("sessionUser")).startsWith("v-");
    }

    @Test
    @Order(7)
    void rewrapsNotesAfterTransitKeyRotation() {
        vault("vault", "write", "-f", "transit/keys/notes/rotate");

        assertThat((Integer) postJson("/api/notes/rewrap", Map.of()).get("rewrapped")).isPositive();

        List<Map<String, Object>> notes = http.exchange("/api/notes", HttpMethod.GET, null,
                new ParameterizedTypeReference<List<Map<String, Object>>>() { }).getBody();
        assertThat(notes).isNotEmpty().allSatisfy(note -> assertThat(note).containsEntry("keyVersion", 2));
        Object id = notes.get(0).get("id");
        assertThat(getJson("/api/notes/" + id)).containsEntry("content", "demo plaintext 0000");
    }

    @Test
    @Order(8)
    void switchesPoolToRotatedDatabaseCredentials() {
        assertThat(context.getBean(DatabaseCredentialRotator.class)).isNotNull();
        String before = (String) getJson("/api/db/identity").get("sessionUser");

        // What happens when the lease hits max_ttl: DatabaseCredentialRotator requests a rotating secret,
        // receives new credentials and soft-evicts the pool.
        context.getBean(SecretLeaseContainer.class).requestRotatingSecret(DB_CREDS_PATH);

        await().atMost(Duration.ofSeconds(30)).untilAsserted(() -> {
            String after = (String) getJson("/api/db/identity").get("sessionUser");
            assertThat(after).startsWith("v-").isNotEqualTo(before);
        });
        assertThat(http.getForEntity("/api/notes", String.class).getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    // --- helpers ------------------------------------------------------------------------------------------------------

    private static final ParameterizedTypeReference<Map<String, Object>> MAP = new ParameterizedTypeReference<>() { };

    private Map<String, Object> getJson(String path) {
        ResponseEntity<Map<String, Object>> response = http.exchange(path, HttpMethod.GET, null, MAP);
        assertThat(response.getStatusCode()).as("GET %s", path).isEqualTo(HttpStatus.OK);
        return response.getBody();
    }

    private Map<String, Object> postJson(String path, Map<String, ?> body) {
        ResponseEntity<Map<String, Object>> response = http.exchange(path, HttpMethod.POST, jsonEntity(body), MAP);
        assertThat(response.getStatusCode().is2xxSuccessful()).as("POST %s -> %s", path, response).isTrue();
        return response.getBody();
    }

    private static HttpEntity<Map<String, ?>> jsonEntity(Map<String, ?> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }

    /** Runs a command in the Vault container with the root token, failing loudly on a non-zero exit code. */
    private static String vault(String... command) {
        String[] withEnv = new String[command.length + 7];
        withEnv[0] = "env";
        withEnv[1] = "VAULT_ADDR=http://127.0.0.1:8200";
        withEnv[2] = "VAULT_TOKEN=" + ROOT_TOKEN;
        withEnv[3] = "VAULT_CLI_MODE=local";
        withEnv[4] = "ENV_FILE=/tmp/app.env";
        withEnv[5] = "DB_CREDS_DEFAULT_TTL=10m";
        withEnv[6] = "DB_CREDS_MAX_TTL=1h";
        System.arraycopy(command, 0, withEnv, 7, command.length);
        try {
            ExecResult result = VAULT.execInContainer(withEnv);
            if (result.getExitCode() != 0) {
                throw new IllegalStateException("Command " + String.join(" ", command) + " failed: "
                        + result.getStdout() + result.getStderr());
            }
            return result.getStdout();
        } catch (Exception ex) {
            throw (ex instanceof RuntimeException rte) ? rte : new IllegalStateException(ex);
        }
    }

    private static Properties parseEnv(String content) {
        Properties properties = new Properties();
        try {
            properties.load(new StringReader(content));
        } catch (IOException ex) {
            throw new IllegalStateException(ex);
        }
        return properties;
    }
}
