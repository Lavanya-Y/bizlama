package com.bizlama.api.shelflife;

import java.util.Map;
import java.util.Optional;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(
        name = "bizlama.shelf-life.mode",
        havingValue = "memory"
)
public class InMemoryShelfLifeGuidanceProvider
        implements ShelfLifeGuidanceProvider {

    private final Map<String, ShelfLifeGuidance> guidance = Map.of(
            "paneer", new ShelfLifeGuidance(
                    "paneer", 4, "temporary-demo-guidance"
            ),
            "bread", new ShelfLifeGuidance(
                    "bread", 3, "temporary-demo-guidance"
            ),
            "butter", new ShelfLifeGuidance(
                    "butter", 14, "temporary-demo-guidance"
            ),
            "milk", new ShelfLifeGuidance(
                    "milk", 5, "temporary-demo-guidance"
            ),
            "tomatoes", new ShelfLifeGuidance(
                    "tomatoes", 5, "temporary-demo-guidance"
            )
    );

    @Override
    public Optional<ShelfLifeGuidance> findForIngredient(String ingredientId) {
        return Optional.ofNullable(guidance.get(ingredientId));
    }
}