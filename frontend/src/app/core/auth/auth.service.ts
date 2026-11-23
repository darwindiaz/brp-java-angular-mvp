import { HttpClient } from '@angular/common/http';
import { computed, inject, Injectable, signal } from '@angular/core';
import { Router } from '@angular/router';
import { firstValueFrom } from 'rxjs';

import { environment } from '../../../environments/environment';

export type Role = 'ADMINISTRADOR' | 'RADICADOR' | 'ANALISTA' | 'JEFE';
export type Module = 'RADICACION' | 'ANALISIS' | 'APROBACION' | 'SEGUIMIENTO' | 'ADMINISTRACION';

export interface SessionUser {
  id: number;
  name: string;
  login: string;
  role: Role;
  active: boolean;
  createdAt: string;
  updatedAt: string;
  modules: Module[];
}

interface Credentials { login: string; password: string }
interface TokenResponse { accessToken: string; tokenType: 'Bearer'; expiresIn: number }
interface Token { value: string; expiration: number }

const storageKey = 'brp.accessToken';

function parseToken(value: string | null): Token | null {
  if (!value) return null;
  try {
    const payload = value.split('.')[1].replace(/-/g, '+').replace(/_/g, '/');
    const { exp } = JSON.parse(atob(payload)) as { exp?: unknown };
    return typeof exp === 'number' && Number.isSafeInteger(exp) && exp * 1000 > Date.now()
      ? { value, expiration: exp * 1000 }
      : null;
  } catch {
    return null;
  }
}

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly router = inject(Router);
  private readonly profile = signal<SessionUser | null>(null);
  private readonly notice = signal(false);
  private token: Token | null = null;
  private timer?: ReturnType<typeof setTimeout>;
  private restoration?: Promise<void>;

  readonly user = this.profile.asReadonly();
  readonly sessionExpired = this.notice.asReadonly();
  readonly authenticated = computed(() => this.user() !== null);

  restore(): Promise<void> {
    return this.restoration ??= this.restoreStoredSession();
  }

  async login(credentials: Credentials): Promise<void> {
    await this.restore();
    this.notice.set(false);
    const response = await firstValueFrom(
      this.http.post<TokenResponse>(`${environment.apiBaseUrl}/auth/login`, credentials),
    );
    const token = parseToken(response.accessToken);
    if (!token || response.tokenType !== 'Bearer') throw new Error('Invalid session response');
    await this.confirm(token);
  }

  bearer(): string | null {
    const token = this.token;
    if (token && Date.now() >= token.expiration) {
      this.invalidate(token.value);
      return null;
    }
    return token?.value ?? null;
  }

  invalidate(rejectedToken: string | null): void {
    if (!rejectedToken || this.token?.value !== rejectedToken) return;
    this.clear();
    this.notice.set(true);
    void this.router.navigateByUrl('/login', { replaceUrl: true });
  }

  logout(): void {
    this.clear();
    this.notice.set(false);
    void this.router.navigateByUrl('/login', { replaceUrl: true });
  }

  private async restoreStoredSession(): Promise<void> {
    const stored = this.readStoredToken();
    const token = parseToken(stored);
    if (!token) {
      this.clear();
      this.notice.set(stored !== null);
      return;
    }
    try {
      await this.confirm(token);
    } catch {
      // La navegación protegida vuelve al login al quedar sin perfil validado.
    }
  }

  private async confirm(token: Token): Promise<void> {
    clearTimeout(this.timer);
    this.token = token;
    this.timer = setTimeout(() => this.invalidate(token.value), token.expiration - Date.now());
    try {
      const user = await firstValueFrom(this.http.get<SessionUser>(`${environment.apiBaseUrl}/auth/me`));
      if (this.token !== token || !user.active || Date.now() >= token.expiration) {
        throw new Error('Session could not be confirmed');
      }
      this.writeStoredToken(token.value);
      this.profile.set(user);
    } catch (error) {
      if (this.token === token) this.clear();
      throw error;
    }
  }

  private clear(): void {
    clearTimeout(this.timer);
    this.token = null;
    this.profile.set(null);
    this.writeStoredToken(null);
  }

  private readStoredToken(): string | null {
    try {
      return sessionStorage.getItem(storageKey);
    } catch {
      return null;
    }
  }

  private writeStoredToken(value: string | null): void {
    try {
      if (value === null) sessionStorage.removeItem(storageKey);
      else sessionStorage.setItem(storageKey, value);
    } catch {
      // El estado en memoria permite continuar si el navegador bloquea el almacenamiento.
    }
  }
}
