import { Injectable, inject } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Experiment } from '../models/experiment';

@Injectable({ providedIn: 'root' })
export class ExperimentsApiService {
    private readonly http = inject(HttpClient);
    private readonly baseUrl = '/api/experiments/paneer-sandwich';

    getExperiment() {
        return this.http.get<Experiment>(this.baseUrl);
    }

    approveExperiment() {
        return this.http.post<Experiment>(`${this.baseUrl}/approve`, {});
    }
}