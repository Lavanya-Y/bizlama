import { Injectable, computed, inject, signal } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, map, of, switchMap, tap } from 'rxjs';

export interface AuthConfig {
    mode: 'local' | 'identity-platform';
    projectId: string;
    identityApiKey?: string;
}

export interface AuthUser {
    email: string;
    name: string;
    role: string;
}

export interface AuthSession {
    accessToken: string;
    expiresAt: string;
    user: AuthUser;
}

interface IdentityResponse {
    idToken: string;
    email: string;
    displayName?: string;
    expiresIn: string;
}

@Injectable({ providedIn: 'root' })
export class AuthService {
    private readonly http = inject(HttpClient);

    private readonly storageKey = 'bizlama.session';
    private initialization?: Promise<void>;

    readonly config = signal<AuthConfig | null>(null);

    readonly session = signal<AuthSession | null>(
        this.restoreSession()
    );

    readonly user = computed(() => this.session()?.user ?? null);

    readonly authenticated = computed(() => {
        const session = this.session();

        return !!session &&
            new Date(session.expiresAt).getTime() > Date.now();
    });

    initialize(): Promise<void> {
        if (this.initialization) {
            return this.initialization;
        }

        this.initialization = new Promise((resolve) => {
            this.http.get<AuthConfig>('/api/auth/config').subscribe({
                next: (config) => {
                    this.config.set(config);
                    resolve();
                },
                error: () => {
                    this.config.set({
                        mode: 'local',
                        projectId: 'bizlama'
                    });
                    resolve();
                }
            });
        });

        return this.initialization;
    }

    login(email: string, password: string): Observable<void> {
        return this.initializeAsObservable().pipe(
            switchMap(() => {
                const config = this.config();

                if (config?.mode === 'identity-platform') {
                    if (!config.identityApiKey) {
                        throw new Error('Identity Platform is not configured.');
                    }

                    return this.http.post<IdentityResponse>(
                        `https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=${encodeURIComponent(config.identityApiKey)}`,
                        {
                            email,
                            password,
                            returnSecureToken: true
                        }
                    ).pipe(
                        map((value) => ({
                            accessToken: value.idToken,
                            expiresAt: new Date(
                                Date.now() + Number(value.expiresIn) * 1000
                            ).toISOString(),
                            user: {
                                email: value.email,
                                name: value.displayName || value.email.split('@')[0],
                                role: 'OWNER'
                            }
                        } as AuthSession))
                    );
                }

                return this.http.post<AuthSession>(
                    '/api/auth/login',
                    {
                        email,
                        password
                    }
                );
            }),
            tap(session => this.saveSession(session)), map(() => void 0));
    }

    token(): string | null {
        return this.authenticated()
            ? this.session()?.accessToken ?? null
            : null;
    }

    logout(): void {
        sessionStorage.removeItem(this.storageKey);
        this.session.set(null);
    }

    private initializeAsObservable(): Observable<void> {
        return this.config()
            ? of(void 0)
            : new Observable((subscriber) => {
                this.initialize().then(() => {
                    subscriber.next();
                    subscriber.complete();
                });
            });
    }

    private saveSession(session: AuthSession): void {
        sessionStorage.setItem(
            this.storageKey,
            JSON.stringify(session)
        );

        this.session.set(session);
    }

    private restoreSession(): AuthSession | null {
        try {
            const raw = sessionStorage.getItem(this.storageKey);

            if (!raw) {
                return null;
            }

            const session = JSON.parse(raw) as AuthSession;

            if (
                !session.accessToken ||
                new Date(session.expiresAt).getTime() <= Date.now()
            ) {
                sessionStorage.removeItem(this.storageKey);
                return null;
            }

            return session;
        } catch {
            sessionStorage.removeItem(this.storageKey);
            return null;
        }
    }
}