package com.bizlama.api.system;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system")
public class SystemStatusController {

    private final String databaseUrl;
    private final String receiptStorage;
    private final boolean analyticsEnabled;
    private final boolean aiEnabled;
    private final String authMode;

    public SystemStatusController(
            @Value("${spring.datasource.url}") String databaseUrl,
            @Value("${bizlama.receipts.storage-mode:local}") String receiptStorage,
            @Value("${bizlama.bigquery.enabled:false}") boolean analyticsEnabled,
            @Value("${bizlama.receipts.ai-enabled:false}") boolean aiEnabled,
            @Value("${bizlama.auth.mode:local}") String authMode) {

        this.databaseUrl = databaseUrl;
        this.receiptStorage = receiptStorage;
        this.analyticsEnabled = analyticsEnabled;
        this.aiEnabled = aiEnabled;
        this.authMode = authMode;
    }

    @GetMapping("/status")
    public WorkspaceStatus status() {

        boolean cloudDatabase =
                databaseUrl.startsWith("jdbc:postgresql");

        return new WorkspaceStatus(
                cloudDatabase ? "cloud" : "local",
                List.of(
                        new Connection(
                                "Operational data",
                                cloudDatabase
                                        ? "Cloud SQL"
                                        : "Local database",
                                true
                        ),
                        new Connection(
                                "Receipt files",
                                receiptStorage.equalsIgnoreCase("gcs")
                                        ? "Cloud Storage"
                                        : "Local files",
                                true
                        ),
                        new Connection(
                                "Analytics",
                                analyticsEnabled
                                        ? "BigQuery"
                                        : "Available after cloud deployment",
                                analyticsEnabled
                        ),
                        new Connection(
                                "Receipt recognition",
                                aiEnabled
                                        ? "Vertex AI"
                                        : "Manual review mode",
                                aiEnabled
                        ),
                        new Connection(
                                "Sign-in",
                                authMode.equalsIgnoreCase("identity-platform")
                                        ? "Identity Platform"
                                        : "Local owner account",
                                true
                        )
                )
        );
    }

    public record WorkspaceStatus(
            String environment,
            List<Connection> connections
    ) {
    }

    public record Connection(
            String name,
            String provider,
            boolean connected
    ) {
    }
}