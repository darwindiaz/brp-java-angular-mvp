import { Routes } from '@angular/router';

export const routes: Routes = [
  {
    path: 'diagnostico',
    loadComponent: () => import('./features/home/connection-check.component')
      .then((component) => component.ConnectionCheckComponent),
  },
  { path: '', pathMatch: 'full', redirectTo: 'diagnostico' },
  { path: '**', redirectTo: 'diagnostico' },
];
