package com.bizlama.api.receipts;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
@ConditionalOnProperty(
        name = "bizlama.receipts.storage-mode",
        havingValue = "local",
        matchIfMissing = true
)
public class LocalReceiptFileStore implements ReceiptFileStore {

    private final Path directory;

    public LocalReceiptFileStore(
            @Value("${bizlama.receipts.local-directory:./.data/receipts}")
            String directory) {

        this.directory = Path.of(directory)
                .toAbsolutePath()
                .normalize();
    }

    @Override
    public StoredReceipt store(String receiptId, MultipartFile file) {
        try {
            Files.createDirectories(directory);

            String extension = extension(file.getOriginalFilename());

            Path target = directory
                    .resolve(receiptId + extension)
                    .normalize();

            if (!target.startsWith(directory)) {
                throw new IllegalArgumentException(
                        "Unsafe receipt filename"
                );
            }

            file.transferTo(target);

            return new StoredReceipt(
                    target.toUri().toString(),
                    contentType(file)
            );

        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not store receipt",
                    error
            );
        }
    }

    private String extension(String filename) {
        if (filename == null || !filename.contains(".")) {
            return "";
        }

        String value = filename
                .substring(filename.lastIndexOf('.'))
                .toLowerCase();

        return value.matches(
                "\\.(jpg|jpeg|png|webp|pdf|txt)"
        ) ? value : "";
    }

    private String contentType(MultipartFile file) {
        return file.getContentType() == null
                ? "application/octet-stream"
                : file.getContentType();
    }
}