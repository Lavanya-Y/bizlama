package com.bizlama.api.domain;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

public record ReceiptImport (
    String id,
    String OriginalFilename,
    String objectUri,
    Status status,
    String merchant,
    LocalDate purchaseDate,
    BigDecimal total,
    Instant createdAt,
   List<ReceiptItem> items ) {

    public enum Status {NEEDS_REVIEW, READY, CONFIRMED, FAILED}  
    public record ReceiptItem (
        String id,
        String rawName,
        String ingredientId,
        String canonicalName,
        double quantity,
        String unit,
        BigDecimal unitPrice,
        double confidence,
        boolean selected
    ) {}
}
