package com.bizlama.api.events;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;

public record ConfirmKitchenEventRequest (@NotEmpty List<@Valid ParsedKitchenEventType> events) {
    
}
