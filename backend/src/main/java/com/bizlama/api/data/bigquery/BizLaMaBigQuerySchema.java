package com.bizlama.api.data.bigquery;

import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class BizLaMaBigQuerySchema {

    private final BigQueryProperties properties;

    public BizLaMaBigQuerySchema(BigQueryProperties properties) {
        this.properties = properties;
    }

    public List<String> requiredTables() {
        return List.of(
                "raw_foodcom_recipes",
                "raw_foodcom_reviews",
                "raw_foodkeeper_guidance",
                "orders_history",
                "stock_movements_history",
                "feedback_history",
                "feedback_embeddings",
                "feedback_themes",
                "experiment_outcomes"
        );
    }

    public String foodComRecipeTarget() {
        return properties.table("raw_foodcom_recipes");
    }

    public String foodComReviewTarget() {
        return properties.table("raw_foodcom_reviews");
    }

    public String foodKeeperTarget() {
        return properties.table("raw_foodkeeper_guidance");
    }
}