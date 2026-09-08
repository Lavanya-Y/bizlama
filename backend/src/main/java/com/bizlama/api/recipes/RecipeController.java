package com.bizlama.api.recipes;

import com.bizlama.api.domain.Dish;
import com.bizlama.api.domain.RecipeVersion;
import com.bizlama.api.store.OperationalRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/recipes")
public class RecipeController {

    private final OperationalRepository repository;

    public RecipeController(OperationalRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public List<RecipeVersion> list() {
        return repository.recipes();
    }

    @PostMapping("/dishes")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeVersion createDish(
            @Valid @RequestBody CreateDishRequest request) {

        if (repository.menuCategory(request.categoryId()).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Choose a valid menu category."
            );
        }

        if (new HashSet<>(
                request.ingredients()
                        .stream()
                        .map(IngredientLine::ingredientId)
                        .toList()
        ).size() != request.ingredients().size()) {
            throw new ResponseStatusException(
                    HttpStatus.UNPROCESSABLE_ENTITY,
                    "Each ingredient can appear only once."
            );
        }

        for (var item : request.ingredients()) {
            if (repository.ingredient(item.ingredientId()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unknown ingredient: " + item.ingredientId()
                );
            }
        }

        String dishId = slug(request.name());
        String recipeId = dishId + "-v1";

        List<RecipeVersion.RecipeIngredient> ingredients =
                request.ingredients()
                        .stream()
                        .map(item ->
                                new RecipeVersion.RecipeIngredient(
                                        item.ingredientId(),
                                        item.quantity(),
                                        item.unit().trim()
                                )
                        )
                        .toList();

        Dish dish = new Dish(
                dishId,
                request.name().trim(),
                request.price(),
                recipeId,
                true,
                request.categoryId(),
                null
        );

        RecipeVersion recipe = new RecipeVersion(
                recipeId,
                dishId,
                1,
                ingredients,
                request.instructions()
                        .stream()
                        .map(String::trim)
                        .toList(),
                "Initial recipe",
                Instant.now(),
                true
        );

        return repository.createDishWithRecipe(
                dish,
                request.preparationMinutes(),
                recipe
        );
    }

    @PostMapping("/dishes/{dishId}/proposals")
    @ResponseStatus(HttpStatus.CREATED)
    public RecipeVersion propose(
            @PathVariable String dishId,
            @Valid @RequestBody RecipeProposal request) {

        if (repository.dish(dishId).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Dish not found"
            );
        }

        int version = repository.nextRecipeVersion(dishId);

        String id = dishId
                + "-v" + version
                + "-" + UUID.randomUUID()
                        .toString()
                        .substring(0, 5);

        List<RecipeVersion.RecipeIngredient> ingredients =
                request.ingredients()
                        .stream()
                        .map(item ->
                                new RecipeVersion.RecipeIngredient(
                                        item.ingredientId(),
                                        item.quantity(),
                                        item.unit().trim()
                                )
                        )
                        .toList();

        for (var item : ingredients) {
            if (repository.ingredient(item.ingredientId()).isEmpty()) {
                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Unknown ingredient: " + item.ingredientId()
                );
            }
        }

        return repository.saveRecipeProposal(
                new RecipeVersion(
                        id,
                        dishId,
                        version,
                        ingredients,
                        request.instructions()
                                .stream()
                                .map(String::trim)
                                .toList(),
                        request.changeReason(),
                        Instant.now(),
                        false
                )
        );
    }

    @PostMapping("/{recipeId}/activate")
    public RecipeVersion activate(@PathVariable String recipeId) {
        try {
            return repository.activateRecipe(recipeId);
        } catch (IllegalArgumentException error) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    error.getMessage()
            );
        }
    }

    public record RecipeProposal(
            @NotEmpty
            List<@Valid IngredientLine> ingredients,

            @NotEmpty
            List<@NotBlank String> instructions,

            @NotBlank
            String changeReason
    ) {
    }

    public record IngredientLine(
            @NotBlank String ingredientId,
            @Positive double quantity,
            @NotBlank String unit
    ) {
    }

    public record CreateDishRequest(
            @NotBlank String name,
            @NotNull @Positive BigDecimal price,
            @NotBlank String categoryId,
            @Positive int preparationMinutes,
            @NotEmpty List<@Valid IngredientLine> ingredients,
            @NotEmpty List<@NotBlank String> instructions
    ) {
    }

    private String slug(String name) {
        String base = name
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("^-|-$", "");

        return base + "-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8);
    }
}