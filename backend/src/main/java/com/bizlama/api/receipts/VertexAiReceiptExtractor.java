package com.bizlama.api.receipts;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.genai.Client;
import com.google.genai.types.Content;
import com.google.genai.types.Part;

@Component
@ConditionalOnProperty(
        name = "bizlama.receipts.ai-enabled",
        havingValue = "true"
)
public class VertexAiReceiptExtractor implements ReceiptExtractor {

    private final ObjectMapper mapper;
    private final String model;

    public VertexAiReceiptExtractor(
            ObjectMapper mapper,
            @Value("${bizlama.receipts.model:gemini-2.5-flash}")
            String model) {
        this.mapper = mapper;
        this.model = model;
    }

    @Override
    public Extraction extract(
            String uri,
            String filename,
            String mimeType) {

        String prompt = """
                Extract this grocery receipt.
                Return only JSON with merchant,
                purchaseDate (YYYY-MM-DD or null), total, and items.
                Each item must have rawName, quantity, unit
                (g, kg, ml, L, or pieces), unitPrice,
                and confidence from 0 to 1.
                Do not invent unreadable values.
                """;

        try (Client client = new Client()) {

            Content content = Content.fromParts(
                    Part.fromText(prompt),
                    Part.fromUri(uri, mimeType)
            );

            String json = client.models
                    .generateContent(model, content, null)
                    .text()
                    .replaceAll("^```json\\s*|\\s*```$", "");

            JsonNode root = mapper.readTree(json);

            List<Line> lines = new ArrayList<>();

            root.path("items").forEach(item ->
                    lines.add(
                            new Line(
                                    item.path("rawName")
                                            .asText("Unknown item"),
                                    item.path("quantity")
                                            .asDouble(1),
                                    item.path("unit")
                                            .asText("pieces"),
                                    decimal(item.get("unitPrice")),
                                    item.path("confidence")
                                            .asDouble(0.5)
                            )
                    )
            );

            LocalDate date =
                    root.path("purchaseDate").isTextual()
                            ? LocalDate.parse(
                                    root.path("purchaseDate").asText()
                            )
                            : null;

            return new Extraction(
                    root.path("merchant")
                            .asText("Unknown merchant"),
                    date,
                    decimal(root.get("total")),
                    lines
            );

        } catch (Exception error) {
            throw new IllegalStateException(
                    "Vertex AI could not extract the receipt",
                    error
            );
        }
    }

    private BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull()
                ? null
                : node.decimalValue();
    }
}