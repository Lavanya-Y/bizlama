package com.bizlama.api.events;

import jakarta.validation.Valid;

import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.bizlama.api.store.OperationalRepository;

@RestController
@RequestMapping("/api/events")
public class KitchenEventController {

    private final KitchenEventParser kitchenEventParser;
    private final KitchenEventApplicationService applicationService;
    private final OperationalRepository repository;
    private final double autoApplyThreshold;

    public KitchenEventController(
            KitchenEventParser kitchenEventParser,
            KitchenEventApplicationService applicationService,
            OperationalRepository repository,
            @Value("${bizlama.automation.auto-apply-threshold:0.90}")
            double autoApplyThreshold
    ) {
        this.kitchenEventParser = kitchenEventParser;
        this.applicationService = applicationService;
        this.repository = repository;
        this.autoApplyThreshold = autoApplyThreshold;
    }

    @PostMapping("/parse")
    public ParseKitchenEventResponse parse(
            @Valid @RequestBody ParseKitchenEventRequest request
    ) {
        List<ParsedKitchenEvent> events =
                kitchenEventParser.parseMany(request.statement());

        boolean autoApply = events.stream()
                .allMatch(event ->
                        event.confidence() >= autoApplyThreshold
                );

        if (autoApply) {
            applicationService.applyAll(events);
        }

        events.forEach(event ->
                repository.auditAiAction(
                        event.type().name(),
                        request.statement(),
                        event.itemId(),
                        event.confidence(),
                        autoApply
                                ? "AUTO_APPLIED"
                                : "AWAITING_CONFIRMATION",
                        event.decisionReason()
                )
        );

        return new ParseKitchenEventResponse(
                events,
                autoApply,
                autoApply
        );
    }

    @PostMapping("/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void confirm(
            @Valid @RequestBody ConfirmKitchenEventRequest request
    ) {
        applicationService.applyAll(request.events());

        request.events().forEach(event ->
                repository.auditAiAction(
                        event.type().name(),
                        event.summary(),
                        event.itemId(),
                        event.confidence(),
                        "OWNER_CONFIRMED",
                        event.decisionReason()
                )
        );
    }
}