package com.bizlama.api.data.bigquery;

import java.time.Instant;
import java.util.Map;

public interface AnalyticsPublisher {

    void publish(
            String eventId,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt
    );
}