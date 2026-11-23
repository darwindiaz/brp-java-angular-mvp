import type { Module, Role, SessionUser } from '../../../core/auth/auth.service';

export type AdministrationUser = Omit<SessionUser, 'id'> & { id: string };
export interface ModuleOption { code: Module; name: string }
export interface CreateUserRequest { name: string; login: string; password: string; role: Role }

export const roles = ['ADMINISTRADOR', 'RADICADOR', 'ANALISTA', 'JEFE'] as const satisfies readonly Role[];

export function normalizeLogin(login: string): string {
  return login.trim().toLowerCase();
}
