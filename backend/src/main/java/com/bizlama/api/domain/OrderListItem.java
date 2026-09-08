package com.bizlama.api.domain;

public record OrderListItem (
    String id,
    BigDecimal total,
    Order.Status status,
    Instant createdAt,
    long itemCount
) {
    
}
