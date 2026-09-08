import { CommonModule } from '@angular/common';
import { Component, OnInit, inject, signal } from '@angular/core';
import { RouterLink } from '@angular/router';
import { DashboardApiService } from '../../core/api/dashboard-api.service';
import { DashboardResponse, StockItem } from '../../core/models/dashboard';
import { AuthService } from '../../core/auth/auth.service';

@Component({
    selector: 'app-dashboard',
    imports: [CommonModule, RouterLink],
    templateUrl: './dashboard.component.html',
    styleUrl: '../../app.scss'
})
export class DashboardComponent implements OnInit {
    private readonly dashboardApi = inject(DashboardApiService);
    private readonly auth = inject(AuthService);

    protected readonly dashboard = signal<DashboardResponse | null>(null);
    protected readonly error = signal<string | null>(null);
    protected readonly today = new Intl.DateTimeFormat('en-IN', {
        weekday: 'long',
        day: 'numeric',
        month: 'long'
    }).format(new Date());

    protected readonly greeting =
        new Date().getHours() < 12
            ? 'Good morning'
            : new Date().getHours() < 17
                ? 'Good afternoon'
                : 'Good evening';

    protected readonly displayName =
        this.auth.user()?.name?.split(' ')[0] || 'there';

    protected readonly quickActions = [
        {
            label: 'Take an order',
            detail: 'Create and queue a customer order',
            route: '/orders',
            icon: '≡'
        },
        {
            label: 'Add stock',
            detail: 'Record a purchase or delivery',
            route: '/inventory',
            icon: '□'
        },
        {
            label: 'Record activity',
            detail: 'Update the kitchen with a simple sentence',
            route: '/activity',
            icon: '+'
        },
        {
            label: 'Upload receipt',
            detail: 'Review a receipt before adding stock',
            route: '/receipts',
            icon: '▣'
        }
    ];

    ngOnInit(): void {
        this.loadDashboard();
    }

    protected attentionItems(items: StockItem[]): StockItem[] {
        return items.filter((item) => item.status !== 'good').slice(0, 4);
    }

    protected metricRoute(label: string): string {
        if (label === 'Stock items' || label === 'Expiring soon') {
            return '/inventory';
        }

        if (label === "Today's prep") {
            return '/recipes';
        }

        if (label === 'Restock signals') {
            return '/inventory';
        }

        return '/feedback';
    }

    private loadDashboard(): void {
        this.dashboardApi.getDashboard().subscribe({
            next: (dashboard) => {
                this.dashboard.set(dashboard);
            },
            error: () => {
                this.error.set(
                    'Your kitchen overview is temporarily unavailable.'
                );
            }
        });
    }
}