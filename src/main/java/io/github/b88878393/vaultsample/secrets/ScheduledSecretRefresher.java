package io.github.b88878393.vaultsample.secrets;

import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.cloud.context.refresh.ContextRefresher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Optional: periodically re-reads configuration (including the Vault KV secrets) so that values rotated in
 * Vault are picked up without anyone calling {@code POST /actuator/refresh}.
 *
 * <p>KV secrets have no lease, so Vault cannot notify the application when they change; polling is the
 * simplest portable strategy. Disabled by default; enable with {@code app.secrets.auto-refresh.enabled=true}
 * and tune {@code app.secrets.auto-refresh.interval} (e.g. {@code 5m}).
 */
@Component
@ConditionalOnProperty(prefix = "app.secrets.auto-refresh", name = "enabled", havingValue = "true")
public class ScheduledSecretRefresher {

    private static final Logger log = LoggerFactory.getLogger(ScheduledSecretRefresher.class);

    private final ContextRefresher contextRefresher;

    public ScheduledSecretRefresher(ContextRefresher contextRefresher) {
        this.contextRefresher = contextRefresher;
    }

    @Scheduled(fixedDelayString = "${app.secrets.auto-refresh.interval:5m}",
               initialDelayString = "${app.secrets.auto-refresh.interval:5m}")
    public void refresh() {
        Set<String> changedKeys = contextRefresher.refresh();
        if (!changedKeys.isEmpty()) {
            // Only key names are logged, never values.
            log.info("Configuration refreshed from Vault; changed keys: {}", changedKeys);
        }
    }
}
