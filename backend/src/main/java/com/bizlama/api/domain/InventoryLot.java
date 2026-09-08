package com.bizlama.api.domain;

import java.time.LocalDate;

public record InventoryLot (
    String id,
    String ingredientId,
    String ingredientName,
    double quantityRemaining,
    String unit,
    LocalDate purchaseDate,
    LocalDate expiresAt,
    String source,
    String status
) {
    
}
