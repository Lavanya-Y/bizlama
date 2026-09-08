package com.bizlama.api.receipts;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.bizlama.api.catalog.ProductNormalizer;
import com.bizlama.api.domain.ReceiptImport;
import com.bizlama.api.domain.StockLot;
import com.bizlama.api.shelflife.ShelfLifeGuidanceProvider;
import com.bizlama.api.store.OperationalRepository;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

@RestController
@RequestMapping("/api/receipts")
public class ReceiptController {

    private final ReceiptFileStore fileStore;
    private final ReceiptExtractor extractor;
    private final ProductNormalizer normalizer;
    private final OperationalRepository repository;
    private final ShelfLifeGuidanceProvider shelfLife;

    public ReceiptController(
            ReceiptFileStore fileStore,
            ReceiptExtractor extractor,
            ProductNormalizer normalizer,
            OperationalRepository repository,
            ShelfLifeGuidanceProvider shelfLife) {

        this.fileStore = fileStore;
        this.extractor = extractor;
        this.normalizer = normalizer;
        this.repository = repository;
        this.shelfLife = shelfLife;
    }

    @GetMapping
    public List<ReceiptImport> list() {
        return repository.receipts();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ReceiptImport upload(
            @RequestParam("file") MultipartFile file) {

        if (file.isEmpty()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Choose a receipt image or PDF."
            );
        }

        String id = "RCT-"
                + UUID.randomUUID()
                        .toString()
                        .substring(0, 8)
                        .toUpperCase();

        ReceiptFileStore.StoredReceipt stored =
                fileStore.store(id, file);

        ReceiptExtractor.Extraction extraction =
                extractor.extract(
                        stored.uri(),
                        file.getOriginalFilename(),
                        stored.mimeType()
                );

        List<ReceiptImport.ReceiptItem> items =
                extraction.lines()
                        .stream()
                        .map(line -> {
                            var match = normalizer.normalize(
                                    line.rawName()
                            );

                            double confidence = Math.min(
                                    line.confidence(),
                                    match.map(
                                            ProductNormalizer.Match::confidence
                                    ).orElse(0.0)
                            );

                            return new ReceiptImport.ReceiptItem(
                                    UUID.randomUUID().toString(),
                                    line.rawName(),
                                    match.map(
                                            ProductNormalizer.Match::ingredientId
                                    ).orElse(null),
                                    match.map(
                                            ProductNormalizer.Match::canonicalName
                                    ).orElse(null),
                                    line.quantity(),
                                    line.unit(),
                                    line.unitPrice(),
                                    confidence,
                                    true
                            );
                        })
                        .toList();

        ReceiptImport receipt = new ReceiptImport(
                id,
                file.getOriginalFilename(),
                stored.uri(),
                ReceiptImport.Status.NEEDS_REVIEW,
                extraction.merchant(),
                extraction.purchaseDate(),
                extraction.total(),
                Instant.now(),
                items
        );

        repository.saveReceipt(receipt);

        repository.addActivity(
                "Receipt",
                "Uploaded "
                        + file.getOriginalFilename()
                        + " for review"
        );

        return receipt;
    }

    @PostMapping("/{id}/confirm")
    public ReceiptImport confirm(
            @PathVariable String id,
            @Valid @RequestBody ConfirmReceiptRequest request) {

        ReceiptImport receipt = repository.receipt(id)
                .orElseThrow(() ->
                        new ResponseStatusException(
                                HttpStatus.NOT_FOUND,
                                "Receipt not found"
                        ));

        repository.replaceReceiptItems(id, request.items());

        LocalDate purchased =
                request.purchaseDate() == null
                        ? LocalDate.now()
                        : request.purchaseDate();

        for (ReceiptImport.ReceiptItem item : request.items()) {

            if (!item.selected()) {
                continue;
            }

            if (item.ingredientId() == null
                    || repository.ingredient(item.ingredientId()).isEmpty()) {

                throw new ResponseStatusException(
                        HttpStatus.UNPROCESSABLE_ENTITY,
                        "Every selected line needs a recognized ingredient."
                );
            }

            LocalDate expires = shelfLife
                    .findForIngredient(item.ingredientId())
                    .map(value -> value.expiresOn(purchased))
                    .orElse(purchased.plusDays(7));

            repository.addPurchase(
                    new StockLot(
                            UUID.randomUUID().toString(),
                            item.ingredientId(),
                            item.quantity(),
                            item.unit(),
                            purchased,
                            expires,
                            "receipt:" + id
                    )
            );
        }

        repository.confirmReceipt(id);

        return repository.receipt(id).orElse(receipt);
    }

    public record ConfirmReceiptRequest(
            LocalDate purchaseDate,
            @NotNull List<@Valid ReceiptItemRequest> lines
    ) {

        public List<ReceiptImport.ReceiptItem> items() {
            return lines.stream()
                    .map(line ->
                            new ReceiptImport.ReceiptItem(
                                    line.id() == null
                                            ? UUID.randomUUID().toString()
                                            : line.id(),
                                    line.rawName(),
                                    line.ingredientId(),
                                    line.canonicalName(),
                                    line.quantity(),
                                    line.unit(),
                                    line.unitPrice(),
                                    line.confidence(),
                                    line.selected()
                            )
                    )
                    .toList();
        }
    }

    public record ReceiptItemRequest(
            String id,
            @NotBlank String rawName,
            String ingredientId,
            String canonicalName,
            @Positive double quantity,
            @NotBlank String unit,
            BigDecimal unitPrice,
            double confidence,
            boolean selected
    ) {
    }
}