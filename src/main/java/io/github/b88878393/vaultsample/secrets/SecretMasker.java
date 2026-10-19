package io.github.b88878393.vaultsample.secrets;

/** Utility to render secrets in a form that is safe for logs and API responses. */
public final class SecretMasker {

    private static final int VISIBLE_PREFIX = 4;
    private static final int VISIBLE_SUFFIX = 2;

    private SecretMasker() {
    }

    /**
     * Masks all but the first four and last two characters. Values of eight characters or fewer are fully
     * masked so that short secrets are not effectively revealed.
     */
    public static String mask(String secret) {
        if (secret == null || secret.isEmpty()) {
            return "";
        }
        if (secret.length() <= VISIBLE_PREFIX + VISIBLE_SUFFIX + 2) {
            return "*".repeat(secret.length());
        }
        int hidden = secret.length() - VISIBLE_PREFIX - VISIBLE_SUFFIX;
        return secret.substring(0, VISIBLE_PREFIX) + "*".repeat(hidden)
                + secret.substring(secret.length() - VISIBLE_SUFFIX);
    }
}
