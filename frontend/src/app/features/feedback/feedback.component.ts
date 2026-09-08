import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ExperimentsApiService } from '../../core/api/experiments-api.service';
import { FeedbackApiService, FeedbackItem } from '../../core/api/feedback-api.service';
import { Experiment } from '../../core/models/experiment';

@Component({
    selector: 'app-feedback',
    imports: [CommonModule, FormsModule],
    templateUrl: './feedback.component.html'
})
export class FeedbackComponent implements OnInit {
    private readonly experimentsApi = inject(ExperimentsApiService);
    private readonly feedbackApi = inject(FeedbackApiService);

    protected readonly experiment = signal<Experiment | null>(null);
    protected readonly comments = signal<FeedbackItem[]>([]);
    protected readonly comment = signal('');
    protected readonly rating = signal(4);
    protected readonly message = signal('');

    ngOnInit(): void {
        this.reload();
    }

    protected add(): void {
        const value = this.comment().trim();

        if (!value) {
            return;
        }

        this.feedbackApi
            .create('paneer-sandwich-v1', value, this.rating())
            .subscribe({
                next: () => {
                    this.comment.set('');
                    this.message.set('Feedback saved.');
                    this.reload();
                },
                error: (error) => {
                    this.message.set(
                        error.error?.detail ?? 'Could not save feedback.'
                    );
                }
            });
    }

    protected remove(id: string): void {
        this.feedbackApi.remove(id).subscribe(() => this.reload());
    }

    protected approve(): void {
        this.experimentsApi.approveExperiment().subscribe((value) => {
            this.experiment.set(value);
            this.message.set(
                'Experiment approved. The permanent recipe still requires a separate owner decision after results are reviewed.'
            );
        });
    }

    private reload(): void {
        this.experimentsApi
            .getExperiment()
            .subscribe((value) => this.experiment.set(value));

        this.feedbackApi
            .list('paneer-sandwich-v1')
            .subscribe((values) => this.comments.set(values));
    }
}