package io.github.b88878393.vaultsample.database;

import com.zaxxer.hikari.HikariDataSource;
import java.sql.SQLException;
import java.util.Map;
import javax.sql.DataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

/**
 * Keeps the connection pool working when the dynamic database credentials reach their maximum TTL.
 *
 * <p>Spring Cloud Vault requests credentials from {@code database/creds/<role>} at startup and renews the lease
 * in the background ({@code spring.cloud.vault.config.lifecycle}). A lease cannot be renewed beyond the role's
 * {@code max_ttl}, though. When that point is reached Spring Cloud Vault publishes a
 * {@link SecretLeaseExpiredEvent}; this component then:
 * <ol>
 *   <li>asks the {@link SecretLeaseContainer} for a <em>rotating</em> secret on the same path, which issues a new
 *       username/password pair (and keeps rotating it on every future expiry), and</li>
 *   <li>on the resulting {@link SecretLeaseCreatedEvent}, hands the new credentials to HikariCP and soft-evicts
 *       existing connections, so new connections use the new user while in-flight work finishes normally.</li>
 * </ol>
 */
@Component
@ConditionalOnProperty(prefix = "spring.cloud.vault.database", name = "enabled", havingValue = "true")
public class DatabaseCredentialRotator implements InitializingBean, DisposableBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseCredentialRotator.class);

    private final SecretLeaseContainer leaseContainer;
    private final HikariDataSource dataSource;
    private final String credentialsPath;
    private final LeaseListener listener = this::onLeaseEvent;

    public DatabaseCredentialRotator(
            SecretLeaseContainer leaseContainer,
            DataSource dataSource,
            @Value("${spring.cloud.vault.database.backend:database}/creds/${spring.cloud.vault.database.role}")
            String credentialsPath) throws SQLException {
        this.leaseContainer = leaseContainer;
        this.dataSource = dataSource.unwrap(HikariDataSource.class);
        this.credentialsPath = credentialsPath;
    }

    @Override
    public void afterPropertiesSet() {
        leaseContainer.addLeaseListener(listener);
        log.info("Watching Vault lease for dynamic database credentials at '{}'", credentialsPath);
    }

    @Override
    public void destroy() {
        leaseContainer.removeLeaseListener(listener);
    }

    String credentialsPath() {
        return credentialsPath;
    }

    void onLeaseEvent(SecretLeaseEvent event) {
        RequestedSecret secret = event.getSource();
        if (!credentialsPath.equals(secret.getPath())) {
            return;
        }
        if (event instanceof SecretLeaseExpiredEvent && secret.getMode() == RequestedSecret.Mode.RENEW) {
            log.info("Database credential lease reached its max TTL; requesting rotating credentials");
            leaseContainer.requestRotatingSecret(credentialsPath);
        } else if (event instanceof SecretLeaseCreatedEvent created
                && secret.getMode() == RequestedSecret.Mode.ROTATE) {
            applyCredentials(created.getSecrets());
        }
    }

    private void applyCredentials(Map<String, Object> secrets) {
        Object username = secrets.get("username");
        Object password = secrets.get("password");
        if (username == null || password == null) {
            log.warn("Rotated database secret at '{}' did not contain username/password", credentialsPath);
            return;
        }
        dataSource.getHikariConfigMXBean().setUsername(username.toString());
        dataSource.getHikariConfigMXBean().setPassword(password.toString());
        if (dataSource.getHikariPoolMXBean() != null) {
            dataSource.getHikariPoolMXBean().softEvictConnections();
        }
        // Log the (non-secret) generated user name to make rotation observable, never the password.
        log.info("Switched connection pool to rotated database user '{}'", username);
    }
}
