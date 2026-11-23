import { Module, Role, SessionUser } from './auth.service';

export interface ModuleRoute {
  path: string;
  label: string;
  permission: Module;
  role?: Role;
}

export const moduleRoutes: readonly ModuleRoute[] = [
  { path: 'radicacion', label: 'Radicación', permission: 'RADICACION', role: 'RADICADOR' },
  { path: 'analisis', label: 'Análisis', permission: 'ANALISIS', role: 'ANALISTA' },
  { path: 'aprobacion', label: 'Aprobación', permission: 'APROBACION', role: 'JEFE' },
  { path: 'seguimiento', label: 'Seguimiento', permission: 'SEGUIMIENTO' },
  { path: 'administracion', label: 'Administración', permission: 'ADMINISTRACION', role: 'ADMINISTRADOR' },
];

export function canOpenModule(user: SessionUser | null, route: ModuleRoute): boolean {
  return !!user?.active && (!route.role || route.role === user.role) && user.modules.includes(route.permission);
}
