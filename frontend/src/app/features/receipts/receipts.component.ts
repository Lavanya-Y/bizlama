import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Ingredient, StockApiService } from '../../core/api/stock-api.service';
import {
    ReceiptImport,
    ReceiptItem,
    ReceiptsApiService
} from '../../core/api/receipts-api.service';

@Component({
    selector: 'app-receipts',
    imports: [CommonModule, FormsModule],
    templateUrl: './receipts.component.html'
})
export class ReceiptsComponent implements OnInit {
    private readonly api = inject(ReceiptsApiService);
    private readonly stockApi = inject(StockApiService);

    protected readonly receipts = signal<ReceiptImport[]>([]);
    protected readonly ingredients = signal<Ingredient[]>([]);
    protected readonly selected = signal<ReceiptImport | null>(null);
    protected readonly uploading = signal(false);
    protected readonly message = signal('');

    ngOnInit(): void {
        this.reload();
        this.stockApi.ingredients().subscribe((values) => this.ingredients.set(values));
    }

    protected chooseFile(event: Event): void {
        const file = (event.target as HTMLInputElement).files?.[0];

        if (!file) {
            return;
        }

        this.uploading.set(true);
        this.message.set('Uploading securely and preparing the review.');

        this.api.upload(file).subscribe({
            next: (receipt) => {
                this.selected.set(receipt);

                if (!receipt.items.length) {
                    this.addLine();
                }

                this.uploading.set(false);
                this.message.set(
                    receipt.items.length
                        ? 'Your receipt is ready to review.'
                        : 'Receipt saved. Add the visible items, then confirm.'
                );

                this.reload(false);
            },
            error: (error) => {
                this.uploading.set(false);
                this.message.set(
                    error.error?.detail ?? 'Could not upload this receipt.'
                );
            }
        });
    }

    protected open(receipt: ReceiptImport): void {
        this.selected.set(structuredClone(receipt));
    }

    protected updatePurchaseDate(value: string): void {
        const receipt = this.selected();

        if (receipt) {
            this.selected.set({
                ...receipt,
                purchaseDate: value
            });
        }
    }

    protected addLine(): void {
        const receipt = this.selected();

        if (!receipt) {
            return;
        }

        const ingredient = this.ingredients()[0];

        const line: ReceiptItem = {
            id: crypto.randomUUID(),
            rawName: '',
            ingredientId: ingredient?.id ?? null,
            canonicalName: ingredient?.name ?? null,
            quantity: 1,
            unit: ingredient?.baseUnit ?? 'g',
            unitPrice: null,
            confidence: 1,
            selected: true
        };

        this.selected.set({
            ...receipt,
            items: [...receipt.items, line]
        });
    }

    protected updateLine(
        index: number,
        field: keyof ReceiptItem,
        value: unknown
    ): void {
        const receipt = this.selected();

        if (!receipt) {
            return;
        }

        const items = receipt.items.map((item, itemIndex) =>
            itemIndex === index
                ? { ...item, [field]: value }
                : item
        );

        this.selected.set({
            ...receipt,
            items
        });
    }

    protected chooseIngredient(index: number, ingredientId: string): void {
        const ingredient = this.ingredients().find(
            (value) => value.id === ingredientId
        );

        this.updateLine(index, 'ingredientId', ingredientId);
        this.updateLine(
            index,
            'canonicalName',
            ingredient?.name ?? null
        );

        if (ingredient) {
            this.updateLine(index, 'unit', ingredient.baseUnit);
        }
    }

    protected removeLine(index: number): void {
        const receipt = this.selected();

        if (!receipt) {
            return;
        }

        this.selected.set({
            ...receipt,
            items: receipt.items.filter((_, itemIndex) => itemIndex !== index)
        });
    }

    protected confirm(): void {
        const receipt = this.selected();

        if (!receipt) {
            return;
        }

        this.api.confirm(receipt).subscribe({
            next: (value) => {
                this.selected.set(value);
                this.message.set(
                    'Receipt confirmed. Selected items were added to inventory.'
                );
                this.reload(false);
            },
            error: (error) => {
                this.message.set(
                    error.error?.detail ??
                    'Review the receipt lines before confirming.'
                );
            }
        });
    }

    protected confidence(item: ReceiptItem): string {
        return `${Math.round(item.confidence * 100)}%`;
    }

    protected matchLabel(item: ReceiptItem): string {
        return item.confidence >= 0.9
            ? 'Strong match'
            : 'Check match';
    }

    private reload(clear = true): void {
        this.api.list().subscribe((values) => {
            this.receipts.set(values);

            if (clear && !this.selected() && values.length) {
                this.selected.set(structuredClone(values[0]));
            }
        });
    }
}