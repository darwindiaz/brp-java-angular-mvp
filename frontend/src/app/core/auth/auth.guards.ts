import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { AuthService } from './auth.service';
import { canOpenModule, ModuleRoute } from './navigation';

export const requireSession: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return (auth.authenticated() && !!auth.bearer()) || router.parseUrl('/login');
};

export const requireModule = (module: ModuleRoute): CanActivateFn => async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  if (!auth.bearer() || !auth.authenticated()) return router.parseUrl('/login');
  return canOpenModule(auth.user(), module) || router.parseUrl('/acceso-denegado');
};

export const requireGuest: CanActivateFn = async () => {
  const auth = inject(AuthService);
  const router = inject(Router);
  await auth.restore();
  return !auth.authenticated() || router.parseUrl('/inicio');
};
