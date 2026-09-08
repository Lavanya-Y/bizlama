package com.bizlama.api.stock;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import com.bizlama.api.common.PageResponse;
import com.bizlama.api.domain.InventoryLot;
import com.bizlama.api.domain.InventorySummary;
import com.bizlama.api.domain.StockLot;
import com.bizlama.api.domain.StockMovement;
import com.bizlama.api.shelflife.ShelfLifeGuidanceProvider;
import com.bizlama.api.store.OperationalRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/stock")
public class StockController {

    private final OperationalRepository store;
    private final ShelfLifeGuidanceProvider shelfLifeGuidanceProvider;

    public StockController(
            OperationalRepository store,
            ShelfLifeGuidanceProvider shelfLifeGuidanceProvider) {
        this.store = store;
        this.shelfLifeGuidanceProvider = shelfLifeGuidanceProvider;
    }

    @GetMapping
    public List<StockLot> lots() {
        return store.stockLots();
    }

    @GetMapping("/search")
    public PageResponse<InventoryLot> search(
            @RequestParam(defaultValue = "") String query,
            @RequestParam(defaultValue = "all") String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {

        int safePage = Math.max(0, page);
        int safeSize = Math.max(1, Math.min(size, 100));

        return PageResponse.of(
                store.searchStockLots(query, status, safePage, safeSize),
                safePage,
                safeSize,
                store.countStockLots(query, status)
        );
    }

    @GetMapping("/summary")
    public InventorySummary summary() {
        return store.inventorySummary();
    }

    @GetMapping("/movements")
    public List<StockMovement> movements() {
        return store.stockMovements();
    }

    @PostMapping("/purchases")
    @ResponseStatus(HttpStatus.CREATED)
    public StockLot purchase(
            @Valid @RequestBody PurchaseRequest request) {

        if (store.ingredient(request.ingredientId()).isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.NOT_FOUND,
                    "Ingredient not found."
            );
        }

        LocalDate expiry = request.expiresAt();

        if (expiry == null) {
            expiry = shelfLifeGuidanceProvider
                    .findForIngredient(request.ingredientId())
                    .map(guidance ->
                            guidance.expiresOn(request.purchasedAt()))
                    .orElseThrow(() ->
                            new ResponseStatusException(
                                    HttpStatus.UNPROCESSABLE_ENTITY,
                                    "Expiry date is required because no "
                                            + "shelf-life guidance is available."
                            ));
        }

        if (!expiry.isAfter(request.purchasedAt())) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Expiry must be after the purchase date."
            );
        }

        String id = UUID.randomUUID().toString();

        StockLot lot = new StockLot(
                id,
                request.ingredientId(),
                request.quantity(),
                request.unit(),
                request.purchasedAt(),
                expiry,
                request.source()
        );

        return store.addPurchase(lot);
    }

    public record PurchaseRequest(
            @NotBlank String ingredientId,
            @Positive double quantity,
            @NotBlank String unit,
            @NotNull LocalDate purchasedAt,
            LocalDate expiresAt,
            @NotBlank String source
    ) {
    }
}