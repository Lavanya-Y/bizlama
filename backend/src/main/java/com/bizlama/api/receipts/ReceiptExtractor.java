package com.bizlama.api.receipts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public interface ReceiptExtractor {

    Extraction extract(
            String uri,
            String filename,
            String mimeType
    );

    record Extraction(
            String merchant,
            LocalDate purchaseDate,
            BigDecimal total,
            List<Line> lines
    ) {
    }

    record Line(
            String rawName,
            double quantity,
            String unit,
            BigDecimal unitPrice,
            double confidence
    ) {
    }
}