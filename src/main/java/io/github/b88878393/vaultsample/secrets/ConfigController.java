package io.github.b88878393.vaultsample.secrets;

import java.util.List;
import org.springframework.core.env.Environment;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Shows which configuration values the application currently sees (secrets are masked). */
@RestController
@RequestMapping("/api/config")
public class ConfigController {

    private final AppProperties appProperties;
    private final PartnerApiClient partnerApiClient;
    private final Environment environment;

    public ConfigController(AppProperties appProperties, PartnerApiClient partnerApiClient,
                            Environment environment) {
        this.appProperties = appProperties;
        this.partnerApiClient = partnerApiClient;
        this.environment = environment;
    }

    @GetMapping
    public ConfigView currentConfig() {
        return new ConfigView(
                appProperties.getGreeting(),
                appProperties.getMaxNoteLength(),
                partnerApiClient.baseUrl(),
                partnerApiClient.maskedApiKey(),
                List.of(environment.getActiveProfiles()));
    }

    public record ConfigView(String greeting, int maxNoteLength, String partnerBaseUrl,
                             String partnerApiKeyMasked, List<String> activeProfiles) {
    }
}
