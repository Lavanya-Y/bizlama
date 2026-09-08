package com.bizlama.api.receipts;

import java.io.IOException;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;

@Component
@ConditionalOnProperty(
        name = "bizlama.receipts.storage-mode",
        havingValue = "gcs"
)
public class GcsReceiptFileStore implements ReceiptFileStore {

    private final Storage storage =
            StorageOptions.getDefaultInstance().getService();

    private final String bucket;

    public GcsReceiptFileStore(
            @Value("${bizlama.receipts.bucket}") String bucket) {
        this.bucket = bucket;
    }

    @Override
    public StoredReceipt store(
            String receiptId,
            MultipartFile file) {

        try {
            String objectName =
                    "receipts/" + receiptId + "/" +
                            safeName(file.getOriginalFilename());

            String contentType =
                    file.getContentType() == null
                            ? "application/octet-stream"
                            : file.getContentType();

            BlobInfo blob = BlobInfo.newBuilder(
                            BlobId.of(bucket, objectName))
                    .setContentType(contentType)
                    .build();

            storage.create(blob, file.getBytes());

            return new StoredReceipt(
                    "gs://" + bucket + "/" + objectName,
                    contentType
            );

        } catch (IOException error) {
            throw new IllegalStateException(
                    "Could not upload receipt to Cloud Storage",
                    error
            );
        }
    }

    private String safeName(String name) {
        return name == null
                ? "receipt"
                : name.replaceAll("[^A-Za-z0-9._-]", "_");
    }
}