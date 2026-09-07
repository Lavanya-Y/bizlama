import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { KitchenEventsApiService } from '../../core/api/kitchen-events-api.service';
import { RecentEvent } from '../../core/models/dashboard';
import { ParsedKitchenEvent } from '../../core/models/kitchen-event';

@Component({
    selector: 'app-activity',
    imports: [CommonModule, FormsModule],
    templateUrl: './activity.component.html'
})
export class ActivityComponent implements OnInit {
    private readonly eventsApi = inject(KitchenEventsApiService);
    private readonly dashboardApi = inject(DashboardApiService);

    protected readonly statement = signal('');
    protected readonly examples = [
        'Bought 2 kg sugar',
        'Made 10 paneer sandwiches',
        'Wasted 300 g tomatoes'
    ];
    protected readonly parsedEvents = signal<ParsedKitchenEvent[]>([]);
    protected readonly recentEvents = signal<RecentEvent[]>([]);
    protected readonly error = signal<string | null>(null);
    protected readonly success = signal<string | null>(null);
    protected readonly isProcessing = signal(false);
    protected readonly isListening = signal(false);
    protected readonly voiceSupported =
        typeof window !== 'undefined' &&
        !!(
            (window as any).SpeechRecognition ||
            (window as any).webkitSpeechRecognition
        );

    ngOnInit(): void {
        this.loadRecentEvents();
    }

    protected useExample(example: string): void {
        this.statement.set(example);
        this.parsedEvents.set([]);
        this.error.set(null);
        this.success.set(null);
    }

    protected parseEvent(): void {
        const statement = this.statement().trim();

        if (!statement) {
            return;
        }

        this.error.set(null);
        this.success.set(null);
        this.parsedEvents.set([]);
        this.isProcessing.set(true);

        this.eventsApi.parse(statement).subscribe({
            next: (response) => {
                if (response.autoApplied) {
                    this.success.set(
                        `${response.events.length} ${response.events.length === 1 ? 'update' : 'updates'
                        } saved automatically. ${response.events
                            .map((event) => event.summary)
                            .join(' ')}`
                    );
                    this.statement.set('');
                    this.loadRecentEvents();
                } else {
                    this.parsedEvents.set(response.events);
                }

                this.isProcessing.set(false);
            },
            error: (response) => {
                this.error.set(
                    response.error?.detail ??
                    'BizLaMa could not understand that update.'
                );
                this.isProcessing.set(false);
            }
        });
    }

    protected confirmEvent(): void {
        const events = this.parsedEvents();

        if (!events.length) {
            return;
        }

        this.isProcessing.set(true);

        this.eventsApi.confirm(events).subscribe({
            next: () => {
                this.success.set(
                    `${events.length} ${events.length === 1 ? 'update' : 'updates'
                    } confirmed and saved.`
                );
                this.parsedEvents.set([]);
                this.statement.set('');
                this.isProcessing.set(false);
                this.loadRecentEvents();
            },
            error: () => {
                this.error.set('BizLaMa could not save that update.');
                this.isProcessing.set(false);
            }
        });
    }

    protected discardEvent(): void {
        this.parsedEvents.set([]);
    }

    protected startVoiceEntry(): void {
        const Recognition =
            (window as any).SpeechRecognition ||
            (window as any).webkitSpeechRecognition;

        if (!Recognition) {
            this.error.set(
                'Voice input is not supported in this browser. You can still type the update.'
            );
            return;
        }

        const recognition = new Recognition();

        recognition.lang = 'en-IN';
        recognition.interimResults = false;
        recognition.maxAlternatives = 1;

        recognition.onstart = () => {
            this.isListening.set(true);
        };

        recognition.onend = () => {
            this.isListening.set(false);
        };

        recognition.onerror = (event: any) => {
            this.isListening.set(false);
            this.error.set(
                event.error === 'not-allowed'
                    ? 'Microphone access is blocked. Allow microphone access in the browser, then try again.'
                    : 'I could not hear that clearly. Please try again or type the update.'
            );
        };

        recognition.onresult = (event: any) => {
            this.statement.set(event.results[0][0].transcript);
            this.error.set(null);
            this.success.set(
                'Voice captured. Check the words, then review the update.'
            );
        };

        recognition.start();
    }

    private loadRecentEvents(): void {
        this.dashboardApi.getDashboard().subscribe({
            next: (dashboard) => {
                this.recentEvents.set(dashboard.recentEvents);
            },
            error: () => {
                this.error.set('BizLaMa could not load recent activity.');
            }
        });
    }
}