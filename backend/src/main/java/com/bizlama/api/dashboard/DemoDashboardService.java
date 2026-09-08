package com.bizlama.api.dashboard;

import com.bizlama.api.domain.Ingredient;
import com.bizlama.api.domain.Order;
import com.bizlama.api.domain.StockLot;
import com.bizlama.api.store.OperationalRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class DemoDashboardService {

    private final OperationalRepository repository;

    public DemoDashboardService(OperationalRepository repository) {
        this.repository = repository;
    }

    public DashboardResponse getDashboard() {

        Map<String, Ingredient> ingredients = repository.ingredients()
                .stream()
                .collect(Collectors.toMap(
                        Ingredient::id,
                        value -> value
                ));

        List<StockLot> lots = repository.stockLots();

        List<DashboardResponse.StockItem> stock = lots.stream()
                .map(lot -> stockItem(
                        lot,
                        ingredients.get(lot.ingredientId())
                ))
                .toList();

        List<DashboardResponse.PrepRequirement> prep = prepRequirements();

        long expiring = lots.stream()
                .filter(lot -> !lot.expiresAt().isAfter(
                        LocalDate.now().plusDays(2)
                ))
                .count();

        int prepCount = prep.stream()
                .mapToInt(DashboardResponse.PrepRequirement::quantity)
                .sum();

        List<DashboardResponse.RestockSuggestion> restock =
                restockSuggestions(lots, ingredients);

        List<DashboardResponse.RecentEvent> recentEvents =
                repository.activities()
                        .stream()
                        .map(event -> new DashboardResponse.RecentEvent(
                                event.type(),
                                event.description(),
                                friendlyTime(event.occurredAt())
                        ))
                        .toList();

        return new DashboardResponse(
                List.of(
                        new DashboardResponse.Metric(
                                "Stock items",
                                String.valueOf(lots.size()),
                                "Available lots across your stockroom",
                                "neutral"
                        ),
                        new DashboardResponse.Metric(
                                "Expiring soon",
                                String.valueOf(expiring),
                                "Lots expiring in the next two days",
                                expiring > 0 ? "warning" : "good"
                        ),
                        new DashboardResponse.Metric(
                                "Today's prep",
                                prepCount + " dishes",
                                "Calculated from queued orders and active recipes",
                                "neutral"
                        ),
                        new DashboardResponse.Metric(
                                "Restock signals",
                                String.valueOf(restock.size()),
                                "Suggestions based on usable quantity and demand",
                                restock.isEmpty() ? "good" : "warning"
                        )
                ),
                stock,
                prep,
                restock,
                recentEvents
        );
    }

    public void addRecentEvent(String type, String description) {
        repository.addActivity(type, description);
    }

    private DashboardResponse.StockItem stockItem(
            StockLot lot,
            Ingredient ingredient
    ) {

        long days = ChronoUnit.DAYS.between(
                LocalDate.now(),
                lot.expiresAt()
        );

        String label =
                days < 0 ? "Expired" :
                days == 0 ? "Expires today" :
                days == 1 ? "Expires tomorrow" :
                "Expires in " + days + " days";

        String status =
                days <= 0 ? "critical" :
                days <= 2 ? "warning" :
                "good";

        return new DashboardResponse.StockItem(
                ingredient == null
                        ? lot.ingredientId()
                        : ingredient.name(),
                format(lot.quantityRemaining()) + " " + lot.unit(),
                label,
                status
        );
    }

    private List<DashboardResponse.PrepRequirement> prepRequirements() {

        Map<String, Integer> quantityByDish = new LinkedHashMap<>();

        repository.orders()
                .stream()
                .filter(order ->
                        order.status() == Order.Status.QUEUED ||
                        order.status() == Order.Status.PREPARING
                )
                .flatMap(order -> order.items().stream())
                .forEach(item ->
                        quantityByDish.merge(
                                item.dishId(),
                                item.quantity(),
                                Integer::sum
                        )
                );

        List<DashboardResponse.PrepRequirement> result =
                new ArrayList<>();

        quantityByDish.forEach((dishId, quantity) ->
                repository.dish(dishId).ifPresent(dish -> {

                    String ingredients = repository
                            .recipe(dish.activeRecipeVersionId())
                            .map(recipe ->
                                    recipe.ingredients()
                                            .stream()
                                            .map(item ->
                                                    format(item.quantity() * quantity)
                                                            + " "
                                                            + item.unit()
                                                            + " "
                                                            + ingredientName(
                                                                    item.ingredientId()
                                                            )
                                            )
                                            .collect(Collectors.joining(
                                                    ", "
                                            ))
                            )
                            .orElse("Recipe not configured");

                    result.add(
                            new DashboardResponse.PrepRequirement(
                                    dish.name(),
                                    quantity,
                                    ingredients
                            )
                    );
                })
        );

        return result;
    }

    private List<DashboardResponse.RestockSuggestion> restockSuggestions(
            List<StockLot> lots,
            Map<String, Ingredient> ingredients
    ) {

        Map<String, Double> totals = new LinkedHashMap<>();

        lots.forEach(lot ->
                totals.merge(
                        lot.ingredientId(),
                        lot.quantityRemaining(),
                        Double::sum
                )
        );

        List<DashboardResponse.RestockSuggestion> result =
                new ArrayList<>();

        totals.forEach((id, amount) -> {

            if (List.of(
                    "paneer",
                    "bread",
                    "milk",
                    "tomatoes",
                    "dosa-batter"
            ).contains(id)) {

                Ingredient ingredient = ingredients.get(id);

                result.add(
                        new DashboardResponse.RestockSuggestion(
                                ingredient == null
                                        ? id
                                        : ingredient.name(),
                                format(Math.max(500, 1500 - amount))
                                        + " "
                                        + ingredient.baseUnit(),
                                "Usable stock is below the operating buffer"
                        )
                );
            }
        });

        return result;
    }

    private String ingredientName(String id) {
        return repository.ingredient(id)
                .map(Ingredient::name)
                .orElse(id);
    }

    private String format(double value) {
        return value == Math.rint(value)
                ? String.valueOf((long) value)
                : String.format(Locale.ROOT, "%.1f", value);
    }

    private String friendlyTime(Instant instant) {

        Duration age = Duration.between(
                instant,
                Instant.now()
        );

        if (age.toMinutes() < 2) {
            return "Just now";
        }

        if (age.toHours() < 1) {
            return age.toMinutes() + " minutes ago";
        }

        LocalDate date = instant
                .atZone(ZoneId.systemDefault())
                .toLocalDate();

        if (date.equals(LocalDate.now())) {
            return "Today, " +
                    DateTimeFormatter
                            .ofPattern("HH:mm")
                            .withZone(ZoneId.systemDefault())
                            .format(instant);
        }

        return DateTimeFormatter
                .ofPattern("d MMM, HH:mm")
                .withZone(ZoneId.systemDefault())
                .format(instant);
    }
}