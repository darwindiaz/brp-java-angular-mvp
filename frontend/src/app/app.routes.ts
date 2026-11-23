import { Routes } from '@angular/router';
import { requireGuest, requireModule, requireSession } from './core/auth/auth.guards';
import { moduleRoutes } from './core/auth/navigation';

const moduleComponents: Partial<Record<(typeof moduleRoutes)[number]['permission'], NonNullable<Routes[number]['loadComponent']>>> = {
  RADICACION: () => import('./features/registration/offer-registration.component').then(m => m.OfferRegistrationComponent),
  ANALISIS: () => import('./features/analysis/analysis-list/analysis-list.component').then(m => m.AnalysisListComponent),
  APROBACION: () => import('./features/approval/approval-list.component').then(m => m.ApprovalListComponent),
};

export const routes: Routes = [
  {
    path: 'login',
    canActivate: [requireGuest],
    loadComponent: () => import('./features/auth/login.component').then(m => m.LoginComponent),
  },
  {
    path: 'diagnostico',
    loadComponent: () => import('./features/home/connection-check.component')
      .then((component) => component.ConnectionCheckComponent),
  },
  {
    path: '',
    canActivateChild: [requireSession],
    loadComponent: () => import('./layouts/workspace.component').then(m => m.WorkspaceComponent),
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'inicio' },
      { path: 'inicio', loadComponent: () => import('./features/home/home.component').then(m => m.HomeComponent) },
      { path: 'acceso-denegado', loadComponent: () => import('./shared/access-denied.component').then(m => m.AccessDeniedComponent) },
      ...moduleRoutes.map(module => ({
        path: module.path,
        data: { title: module.label },
        canActivate: [requireModule(module)],
        loadComponent: moduleComponents[module.permission]
          ?? (() => import('./shared/module-unavailable.component').then(m => m.ModuleUnavailableComponent)),
      })),
    ],
  },
  { path: '**', redirectTo: 'inicio' },
];
