package com.bizlama.api.domain;

public record InventorySummary (
    long ingredients,
    long activelots,
    long expiringlots,
    long expiredlots) {
    
}
