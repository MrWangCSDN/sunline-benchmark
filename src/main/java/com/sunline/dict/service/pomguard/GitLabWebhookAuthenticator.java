package com.sunline.dict.service.pomguard;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Constant-time verifier for the dedicated GitLab webhook endpoint. */
@Component
public class GitLabWebhookAuthenticator {
    private final String configuredSecret;

    public GitLabWebhookAuthenticator(@Value("${gitlab.webhook-secret:}") String configuredSecret) {
        this.configuredSecret = configuredSecret;
    }

    public AuthenticationResult authenticate(String suppliedToken) {
        if (configuredSecret == null || configuredSecret.isBlank()) return AuthenticationResult.SECRET_NOT_CONFIGURED;
        if (suppliedToken == null || !MessageDigest.isEqual(configuredSecret.getBytes(StandardCharsets.UTF_8),
                suppliedToken.getBytes(StandardCharsets.UTF_8))) return AuthenticationResult.UNAUTHORIZED;
        return AuthenticationResult.AUTHORIZED;
    }

    public enum AuthenticationResult { AUTHORIZED, SECRET_NOT_CONFIGURED, UNAUTHORIZED }
}
