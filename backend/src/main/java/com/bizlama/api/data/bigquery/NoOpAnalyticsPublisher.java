package com.bizlama.api.data.bigquery;

import java.time.Instant;
import java.util.Map;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "bizlama.bigquery.enabled",
        havingValue = "false",
        matchIfMissing = true
)
public class NoOpAnalyticsPublisher implements AnalyticsPublisher {

    @Override
    public void publish(
            String eventId,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
    }
}