package com.neuroflow.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Sends drift alerts to Slack and/or Discord incoming webhooks.
 * Disabled when URLs are blank. Cooldown via Redis avoids spam.
 */
@Service
public class WebhookNotificationService {

    private static final Logger log = LoggerFactory.getLogger(WebhookNotificationService.class);
    private static final String COOLDOWN_KEY = "neuroflow:webhook:drift:cooldown";

    private final StringRedisTemplate redisTemplate;
    private final RestClient restClient;
    private final boolean enabled;
    private final String slackWebhookUrl;
    private final String discordWebhookUrl;
    private final int cooldownSeconds;

    public WebhookNotificationService(
            StringRedisTemplate redisTemplate,
            @Value("${neuroflow.webhooks.enabled:true}") boolean enabled,
            @Value("${neuroflow.webhooks.slack-url:}") String slackWebhookUrl,
            @Value("${neuroflow.webhooks.discord-url:}") String discordWebhookUrl,
            @Value("${neuroflow.webhooks.cooldown-seconds:300}") int cooldownSeconds) {
        this.redisTemplate = redisTemplate;
        this.restClient = RestClient.create();
        this.enabled = enabled;
        this.slackWebhookUrl = blankToNull(slackWebhookUrl);
        this.discordWebhookUrl = blankToNull(discordWebhookUrl);
        this.cooldownSeconds = Math.max(0, cooldownSeconds);
    }

    public Map<String, Object> status() {
        Map<String, Object> status = new LinkedHashMap<>();
        status.put("enabled", enabled);
        status.put("slackConfigured", slackWebhookUrl != null);
        status.put("discordConfigured", discordWebhookUrl != null);
        status.put("cooldownSeconds", cooldownSeconds);
        return status;
    }

    public void notifyDriftDetected(
            String modelName,
            double driftShare,
            int driftedFeatures,
            int sampleSize,
            String triggerSource) {
        if (!enabled || (slackWebhookUrl == null && discordWebhookUrl == null)) {
            return;
        }
        if (isCoolingDown()) {
            log.debug("Webhook drift notify skipped (cooldown active)");
            return;
        }

        String title = "NeuroFlow — DATA DRIFT DETECTED";
        String body = String.format(
                "model=%s · share=%.0f%% · driftedFeatures=%d · sampleSize=%d · source=%s · at=%s",
                modelName == null ? "loan-risk-model" : modelName,
                driftShare * 100.0,
                driftedFeatures,
                sampleSize,
                triggerSource == null ? "unknown" : triggerSource,
                Instant.now()
        );

        boolean anySent = false;
        if (slackWebhookUrl != null) {
            anySent |= postSlack(title, body);
        }
        if (discordWebhookUrl != null) {
            anySent |= postDiscord(title, body);
        }
        if (anySent) {
            markCooldown();
        }
    }

    /**
     * Force-send a test payload (ignores cooldown). Used by ADMIN demo endpoint.
     */
    public Map<String, Object> sendTest() {
        Map<String, Object> result = new LinkedHashMap<>(status());
        if (!enabled) {
            result.put("sent", false);
            result.put("reason", "webhooks_disabled");
            return result;
        }
        if (slackWebhookUrl == null && discordWebhookUrl == null) {
            result.put("sent", false);
            result.put("reason", "no_webhook_urls_configured");
            return result;
        }

        String title = "NeuroFlow — webhook test";
        String body = "Test notification from NeuroFlow backend at " + Instant.now();
        boolean slackOk = slackWebhookUrl != null && postSlack(title, body);
        boolean discordOk = discordWebhookUrl != null && postDiscord(title, body);
        result.put("slackSent", slackOk);
        result.put("discordSent", discordOk);
        result.put("sent", slackOk || discordOk);
        return result;
    }

    private boolean postSlack(String title, String body) {
        try {
            Map<String, Object> payload = Map.of(
                    "text", "*" + title + "*\n" + body
            );
            restClient.post()
                    .uri(slackWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Slack webhook notified for drift alert");
            return true;
        } catch (Exception ex) {
            log.warn("Slack webhook failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean postDiscord(String title, String body) {
        try {
            Map<String, Object> embed = new LinkedHashMap<>();
            embed.put("title", title);
            embed.put("description", body);
            embed.put("color", 15158332); // red-ish

            Map<String, Object> payload = Map.of(
                    "content", "**" + title + "**",
                    "embeds", java.util.List.of(embed)
            );
            restClient.post()
                    .uri(discordWebhookUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .toBodilessEntity();
            log.info("Discord webhook notified for drift alert");
            return true;
        } catch (Exception ex) {
            log.warn("Discord webhook failed: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isCoolingDown() {
        if (cooldownSeconds <= 0) {
            return false;
        }
        try {
            return Boolean.TRUE.equals(redisTemplate.hasKey(COOLDOWN_KEY));
        } catch (Exception ex) {
            log.debug("Redis cooldown check failed: {}", ex.getMessage());
            return false;
        }
    }

    private void markCooldown() {
        if (cooldownSeconds <= 0) {
            return;
        }
        try {
            redisTemplate.opsForValue().set(COOLDOWN_KEY, "1", Duration.ofSeconds(cooldownSeconds));
        } catch (Exception ex) {
            log.debug("Redis cooldown set failed: {}", ex.getMessage());
        }
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
