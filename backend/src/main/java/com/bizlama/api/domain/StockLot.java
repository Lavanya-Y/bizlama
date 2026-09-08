package com.bizlama.api.domain;

import java.time.LocalDate;

public record StockLot (
    String id,
    String ingredientId,
    double quantityRemaining,
    String unit,
    LocalDate purchasedAt,
    LocalDate expiresAt,
    String source
) {
}
