package com.bizlama.api.events;

import java.util.List;
public record ParseKitchenEventResponse (
    List<ParsedKitchenEvent> events,
    boolean requiresConfirmation,
    boolean autoApplied
) {
    
}
