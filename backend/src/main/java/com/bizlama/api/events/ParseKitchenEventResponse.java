package com.bizlama.api.events;

import java.util.List;
public record ParseKitchenEventResponse (
    List<ParsedKitchenEventType> events,
    boolean requiresConfirmation,
    boolean autoApplied
) {
    
}
