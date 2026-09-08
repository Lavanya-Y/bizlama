package com.bizlama.api.events;

public record ParsedKitchenEventType(
    KitchenEventType type, 
    String item,
    String itemId,
    double quantity,
    String unit,
    double confidence,
    String summary,
    String decisionReason
) {
    
}
