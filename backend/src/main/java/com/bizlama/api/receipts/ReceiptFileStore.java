package com.bizlama.api.receipts;

import org.springframework.web.multipart.MultipartFile;

public interface ReceiptFileStore {

    StoredReceipt store(String receiptId, MultipartFile file);

    record StoredReceipt(
            String uri,
            String mimeType
    ) {
    }
}