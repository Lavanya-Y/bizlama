export type KitchenEventType =
    'PURCHASE' |
    'PRODUCTION' |
    'WASTE';

export interface ParsedKitchenEvent {
    type: KitchenEventType;
    item: string;
    itemId: string;
    quantity: number;
    unit: string;
    confidence: number;
    summary: string;
    decisionReason: string;
}

export interface ParseKitchenEventResponse {
    events: ParsedKitchenEvent[];
    requiresConfirmation: boolean;
    autoApplied: boolean;
}