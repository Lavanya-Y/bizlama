package com.bizlama.api.domain;

import java.time.Instant;
import java.util.List;

public record RecipeVersion (
    String id,
    String dishId,
    int versionNumber,
    List<RecipeVersionIngredient> ingredients,
    List<String> instructions,
    String changeReason,
    Instant createdAt,
    boolean active
) {
    public record RecipeIngredient (
        String ingredientId,
        double quantity,
        String unit
    ) {}
}