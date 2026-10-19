package io.github.b88878393.vaultsample.secrets;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.context.config.annotation.RefreshScope;
import org.springframework.stereotype.Component;

/**
 * Stand-in for a client of a third-party API that needs an API key.
 *
 * <p>Shows the {@code @Value} style of consuming a Vault secret. Because the bean is {@link RefreshScope
 * refresh-scoped}, it is re-created lazily after {@code POST /actuator/refresh}, picking up a rotated key
 * from Vault. (Without {@code @RefreshScope}, an {@code @Value} field keeps its startup value forever.)
 *
 * <p>The key itself is never logged or returned; only a masked form is exposed for the demo.
 */
@Component
@RefreshScope
public class PartnerApiClient {

    private final String baseUrl;
    private final String apiKey;

    public PartnerApiClient(@Value("${partner.base-url}") String baseUrl,
                            @Value("${partner.api-key}") String apiKey) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey;
    }

    public String baseUrl() {
        return baseUrl;
    }

    /** Masked API key, safe to display (e.g. {@code demo********01}). */
    public String maskedApiKey() {
        return SecretMasker.mask(apiKey);
    }

    /**
     * Would build the authorization header for outgoing calls. Kept package-private and unused by the
     * REST layer so that the raw secret never leaves the application.
     */
    String authorizationHeader() {
        return "Bearer " + apiKey;
    }
}
