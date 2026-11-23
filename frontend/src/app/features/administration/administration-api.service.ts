import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { map } from 'rxjs';
import { environment } from '../../../environments/environment';
import type { Module, Role } from '../../core/auth/auth.service';
import type { AdministrationUser, CreateUserRequest, ModuleOption } from './models/administration.models';

function decodeUsers<T extends AdministrationUser | AdministrationUser[]>(source: string): T {
  return JSON.parse(source, (key: string, value: unknown, context?: { source?: string }) => {
    if (key !== 'id' || typeof value !== 'number') return value;
    if (context?.source) return userPathId(context.source);
    if (!Number.isSafeInteger(value)) throw new RangeError('El navegador no puede identificar al usuario con precisión.');
    return userPathId(String(value));
  }) as T;
}

function userPathId(id: string): string {
  if (!/^[1-9]\d*$/.test(id)) throw new RangeError('Identificador de usuario inválido.');
  return id;
}

@Injectable({ providedIn: 'root' })
export class AdministrationApi {
  private readonly http = inject(HttpClient);
  private readonly url = `${environment.apiBaseUrl}/admin`;
  private readonly timeout = 30_000;

  users() {
    return this.http.get(`${this.url}/users`, { responseType: 'text', timeout: this.timeout })
      .pipe(map(decodeUsers<AdministrationUser[]>));
  }

  user(id: string) {
    return this.http.get(this.userUrl(id), { responseType: 'text', timeout: this.timeout })
      .pipe(map(decodeUsers<AdministrationUser>));
  }

  create(input: CreateUserRequest) {
    return this.http.post(`${this.url}/users`, input, { responseType: 'text', timeout: this.timeout })
      .pipe(map(decodeUsers<AdministrationUser>));
  }

  changeRole(id: string, role: Role) {
    return this.http.patch<void>(`${this.userUrl(id)}/role`, { role }, { timeout: this.timeout });
  }

  modules() {
    return this.http.get<ModuleOption[]>(`${this.url}/modules`, { timeout: this.timeout });
  }

  assignedModules(id: string) {
    return this.http.get<Module[]>(`${this.userUrl(id)}/modules`, { timeout: this.timeout });
  }

  changeModule(id: string, module: Module, grant: boolean) {
    const url = `${this.userUrl(id)}/modules/${encodeURIComponent(module)}`;
    return grant ? this.http.put<void>(url, null, { timeout: this.timeout })
      : this.http.delete<void>(url, { timeout: this.timeout });
  }

  private userUrl(id: string): string {
    return `${this.url}/users/${userPathId(id)}`;
  }
}

export interface FieldError { field: string; message: string }

export function errorStatus(error: unknown): number | undefined {
  return error instanceof HttpErrorResponse ? error.status : undefined;
}

export function administrationError(error: unknown): { message: string; fields: FieldError[] } {
  const status = errorStatus(error);
  let body: unknown = error instanceof HttpErrorResponse ? error.error : null;
  if (typeof body === 'string') {
    try { body = JSON.parse(body) as unknown; } catch { body = null; }
  }
  const problem = body && typeof body === 'object' ? body as { detail?: unknown; errors?: unknown } : null;
  const fields = status === 400 && Array.isArray(problem?.errors)
    ? problem.errors.flatMap((item: unknown) => {
      if (!item || typeof item !== 'object') return [];
      const { field, message } = item as Partial<FieldError>;
      if (typeof field !== 'string' || typeof message !== 'string') return [];
      return [{ field, message: field === 'password' ? 'Revisa los requisitos de la contraseña.' : message }];
    })
    : [];
  switch (status) {
    case 401: return { message: '', fields: [] };
    case 403: return { message: 'Tu cuenta no tiene permiso para administrar usuarios.', fields };
    case 404: return { message: 'El usuario o el recurso ya no está disponible.', fields };
    case 409: return {
      message: typeof problem?.detail === 'string' ? problem.detail : 'El cambio entra en conflicto con el estado actual.',
      fields,
    };
    case 400: return { message: 'Revisa los datos de la solicitud.', fields };
    case 0: case 408: case 504: return { message: 'No se pudo completar la conexión. Intenta de nuevo.', fields };
    default: return { message: 'No se pudo completar la operación.', fields };
  }
}
