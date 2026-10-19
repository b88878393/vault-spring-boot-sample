package io.github.b88878393.vaultsample.database;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationStartedEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.stereotype.Component;

/**
 * Prevents {@code POST /actuator/refresh} from requesting a <em>new</em> set of database credentials.
 *
 * <p>A refresh re-runs the complete {@code spring.config.import=vault://} processing. That is exactly what we want
 * for KV secrets, but for the database backend it would issue an additional dynamic PostgreSQL user (and lease) on
 * every refresh, while the connection pool keeps using the original one - leaking users until their max TTL.
 * Database credentials already have their own lifecycle (lease renewal + {@link DatabaseCredentialRotator}).
 *
 * <p>How it works: {@code spring.cloud.vault.database.enabled} is defined as
 * {@code ${sample.vault.database.import-enabled:true}} in {@code application.yml}. Once the application has started,
 * this listener adds a property source that sets {@code sample.vault.database.import-enabled=false}, and
 * {@code spring.cloud.refresh.additional-property-sources-to-retain} makes Spring Cloud carry that property source
 * into the environment it builds for a refresh. Startup is unaffected; refreshes only reload the KV secrets.
 */
@Component
@ConditionalOnProperty(prefix = "spring.cloud.vault.database", name = "enabled", havingValue = "true")
public class DatabaseImportRefreshGuard implements ApplicationListener<ApplicationStartedEvent> {

    public static final String PROPERTY_SOURCE_NAME = "vaultDatabaseImportRefreshGuard";
    public static final String IMPORT_ENABLED_PROPERTY = "sample.vault.database.import-enabled";

    private static final Logger log = LoggerFactory.getLogger(DatabaseImportRefreshGuard.class);

    @Override
    public void onApplicationEvent(ApplicationStartedEvent event) {
        ConfigurableEnvironment environment = event.getApplicationContext().getEnvironment();
        if (!environment.getPropertySources().contains(PROPERTY_SOURCE_NAME)) {
            environment.getPropertySources().addLast(
                    new MapPropertySource(PROPERTY_SOURCE_NAME, Map.of(IMPORT_ENABLED_PROPERTY, "false")));
            log.debug("Vault database credentials will not be re-requested on context refresh");
        }
    }
}
