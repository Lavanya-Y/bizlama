import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import {
    ParseKitchenEventResponse,
    ParsedKitchenEvent
} from '../models/kitchen-event';

@Injectable({ providedIn: 'root' })
export class KitchenEventsApiService {
    private readonly http = inject(HttpClient);
    private readonly baseUrl = '/api/events';

    parse(statement: string) {
        return this.http.post<ParseKitchenEventResponse>(
            `${this.baseUrl}/parse`,
            { statement }
        );
    }

    confirm(events: ParsedKitchenEvent[]) {
        return this.http.post<void>(
            `${this.baseUrl}/confirm`,
            events
        );
    }
}