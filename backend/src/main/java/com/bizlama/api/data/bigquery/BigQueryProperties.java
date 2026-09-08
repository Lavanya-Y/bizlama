package com.bizlama.api.data.bigquery;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "bizlama.bigquery")
public record BigQueryProperties(
        boolean enabled,
        String projectId,
        String dataset,
        String importBucket
) {

    public String table(String tableName) {
        if (projectId == null || projectId.isBlank()) {
            throw new IllegalStateException(
                    "BIZLAMA_GCP_PROJECT_ID is required when BigQuery is enabled."
            );
        }

        return "`" + projectId + "." + dataset + "." + tableName + "`";
    }
}