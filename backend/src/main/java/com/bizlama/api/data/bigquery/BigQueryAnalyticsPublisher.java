package com.bizlama.api.data.bigquery;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.bigquery.BigQuery;
import com.google.cloud.bigquery.BigQueryOptions;
import com.google.cloud.bigquery.InsertAllRequest;
import com.google.cloud.bigquery.InsertAllResponse;
import com.google.cloud.bigquery.TableId;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "bizlama.bigquery.enabled",
        havingValue = "true"
)
public class BigQueryAnalyticsPublisher implements AnalyticsPublisher {

    private static final Logger log =
            LoggerFactory.getLogger(BigQueryAnalyticsPublisher.class);

    private final BigQuery bigQuery =
            BigQueryOptions.getDefaultInstance().getService();

    private final BigQueryProperties properties;
    private final ObjectMapper mapper;

    public BigQueryAnalyticsPublisher(
            BigQueryProperties properties,
            ObjectMapper mapper
    ) {
        this.properties = properties;
        this.mapper = mapper;
    }

    @Override
    public void publish(
            String eventId,
            String eventType,
            Map<String, Object> payload,
            Instant occurredAt
    ) {
        try {
            Map<String, Object> row = new LinkedHashMap<>();

            row.put("event_id", eventId);
            row.put("event_type", eventType);
            row.put("occurred_at", occurredAt.toString());
            row.put("payload_json", mapper.writeValueAsString(payload));

            TableId table = TableId.of(
                    properties.projectId(),
                    properties.dataset(),
                    "operational_events"
            );

            InsertAllResponse response = bigQuery.insertAll(
                    InsertAllRequest.newBuilder(table)
                            .addRow(eventId, row)
                            .build()
            );

            if (response.hasErrors()) {
                log.warn(
                        "BigQuery rejected analytics event {}: {}",
                        eventId,
                        response.getInsertErrors()
                );
            }

        } catch (JsonProcessingException | RuntimeException error) {
            log.warn(
                    "Operational write succeeded, but analytics event {} "
                            + "could not be published",
                    eventId,
                    error
            );
        }
    }
}