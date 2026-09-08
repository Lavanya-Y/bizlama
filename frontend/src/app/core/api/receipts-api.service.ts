import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';

export interface ReceiptItem {
    id: string;
    rawName: string;
    ingredientId: string | null;
    canonicalName: string | null;
    quantity: number;
    unit: string;
    unitPrice: number | null;
    confidence: number;
    selected: boolean;
}

export interface ReceiptImport {
    id: string;
    originalFilename: string;
    objectUri: string;
    status: 'NEEDS_REVIEW' | 'READY' | 'CONFIRMED' | 'FAILED';
    merchant: string | null;
    purchaseDate: string | null;
    total: number | null;
    createdAt: string;
    items: ReceiptItem[];
}

@Injectable({ providedIn: 'root' })
export class ReceiptsApiService {
    private readonly http = inject(HttpClient);

    list() {
        return this.http.get<ReceiptImport[]>('/api/receipts');
    }

    upload(file: File) {
        const data = new FormData();
        data.append('file', file);
        return this.http.post<ReceiptImport>('/api/receipts', data);
    }

    confirm(receipt: ReceiptImport) {
        return this.http.post<ReceiptImport>(
            `/api/receipts/${receipt.id}/confirm`,
            {
                purchaseDate: receipt.purchaseDate,
                lines: receipt.items
            }
        );
    }
}