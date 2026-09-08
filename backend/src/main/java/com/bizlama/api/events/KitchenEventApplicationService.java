package com.bizlama.api.events;

import com.bizlama.api.domain.RecipeVersion;
import com.bizlama.api.domain.StockLot;
import com.bizlama.api.domain.StockMovement;
import com.bizlama.api.shelflife.ShelfLifeGuidanceProvider;
import com.bizlama.api.store.OperationalRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class KitchenEventApplicationService {

    private final OperationalRepository repository;
    private final ShelfLifeGuidanceProvider shelfLife;

    public KitchenEventApplicationService(
            OperationalRepository repository,
            ShelfLifeGuidanceProvider shelfLife
    ) {
        this.repository = repository;
        this.shelfLife = shelfLife;
    }

    @Transactional
    public void apply(ParsedKitchenEvent event) {
        String reference = UUID.randomUUID().toString();

        switch (event.type()) {

            case PURCHASE -> {
                LocalDate purchased = LocalDate.now();

                LocalDate expires = shelfLife
                        .findForIngredient(event.itemId())
                        .map(value -> value.expiresOn(purchased))
                        .orElse(purchased.plusDays(7));

                repository.addPurchase(
                        new StockLot(
                                reference,
                                event.itemId(),
                                event.quantity(),
                                event.unit(),
                                purchased,
                                expires,
                                "natural-language"
                        )
                );
            }

            case WASTE -> repository.consume(
                    event.itemId(),
                    event.quantity(),
                    StockMovement.MovementType.WASTE,
                    "kitchen-event",
                    reference
            );

            case PRODUCTION -> {
                RecipeVersion recipe = repository
                        .dish(event.itemId())
                        .flatMap(dish ->
                                repository.recipe(
                                        dish.activeRecipeVersionId()
                                )
                        )
                        .orElseThrow(() ->
                                new IllegalStateException(
                                        "No active recipe for " + event.item()
                                )
                        );

                for (RecipeVersion.RecipeIngredient ingredient
                        : recipe.ingredients()) {

                    repository.consume(
                            ingredient.ingredientId(),
                            ingredient.quantity() * event.quantity(),
                            StockMovement.MovementType.PRODUCTION_CONSUMPTION,
                            "production",
                            reference
                    );
                }
            }
        }

        repository.addActivity(
                label(event.type()),
                event.summary()
        );
    }

    @Transactional
    public void applyAll(List<ParsedKitchenEvent> events) {
        events.forEach(this::apply);
    }

    private String label(KitchenEventType type) {
        return switch (type) {
            case PURCHASE -> "Purchase";
            case PRODUCTION -> "Production";
            case WASTE -> "Waste";
        };
    }
}