package io.github.b88878393.vaultsample.database;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.zaxxer.hikari.HikariConfigMXBean;
import com.zaxxer.hikari.HikariDataSource;
import com.zaxxer.hikari.HikariPoolMXBean;
import java.time.Duration;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.vault.core.lease.SecretLeaseContainer;
import org.springframework.vault.core.lease.domain.Lease;
import org.springframework.vault.core.lease.domain.RequestedSecret;
import org.springframework.vault.core.lease.event.LeaseListener;
import org.springframework.vault.core.lease.event.SecretLeaseCreatedEvent;
import org.springframework.vault.core.lease.event.SecretLeaseExpiredEvent;

class DatabaseCredentialRotatorTest {

    private static final String PATH = "database/creds/app-readwrite";

    private final SecretLeaseContainer container = mock(SecretLeaseContainer.class);
    private final HikariDataSource dataSource = mock(HikariDataSource.class);
    private final HikariConfigMXBean config = mock(HikariConfigMXBean.class);
    private final HikariPoolMXBean pool = mock(HikariPoolMXBean.class);
    private DatabaseCredentialRotator rotator;

    @BeforeEach
    void setUp() throws Exception {
        when(dataSource.unwrap(HikariDataSource.class)).thenReturn(dataSource);
        when(dataSource.getHikariConfigMXBean()).thenReturn(config);
        when(dataSource.getHikariPoolMXBean()).thenReturn(pool);
        rotator = new DatabaseCredentialRotator(container, dataSource, PATH);
    }

    @Test
    void registersAndRemovesListener() {
        rotator.afterPropertiesSet();
        verify(container).addLeaseListener(any(LeaseListener.class));
        rotator.destroy();
        verify(container).removeLeaseListener(any(LeaseListener.class));
    }

    @Test
    void requestsRotatingSecretWhenRenewableLeaseExpires() {
        rotator.onLeaseEvent(new SecretLeaseExpiredEvent(RequestedSecret.renewable(PATH), lease()));

        verify(container).requestRotatingSecret(PATH);
    }

    @Test
    void appliesRotatedCredentialsToPool() {
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.rotating(PATH), lease(),
                Map.of("username", "v-approle-app-new", "password", "demo-password")));

        verify(config).setUsername("v-approle-app-new");
        verify(config).setPassword("demo-password");
        verify(pool).softEvictConnections();
    }

    @Test
    void ignoresInitialRenewableCredentials() {
        // The initial credentials are applied by Spring Boot's DataSource auto-configuration.
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.renewable(PATH), lease(),
                Map.of("username", "u", "password", "p")));

        verifyNoInteractions(config, pool);
    }

    @Test
    void ignoresOtherPaths() {
        rotator.onLeaseEvent(new SecretLeaseExpiredEvent(RequestedSecret.renewable("database/creds/other"), lease()));
        rotator.onLeaseEvent(new SecretLeaseCreatedEvent(RequestedSecret.rotating("database/creds/other"), lease(),
                Map.of("username", "u", "password", "p")));

        verify(container, never()).requestRotatingSecret(any(String.class));
        verifyNoInteractions(config, pool);
    }

    private static Lease lease() {
        return Lease.of("database/creds/app-readwrite/lease-id", Duration.ofMinutes(5), true);
    }
}
