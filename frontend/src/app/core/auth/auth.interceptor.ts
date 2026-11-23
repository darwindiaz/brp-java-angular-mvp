import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { catchError, throwError } from 'rxjs';

import { environment } from '../../../environments/environment';
import { AuthService } from './auth.service';

export const authInterceptor: HttpInterceptorFn = (request, next) => {
  const api = environment.apiBaseUrl;
  if (!request.url.startsWith(`${api}/`)) return next(request);
  const url = new URL(request.url, window.location.origin);
  if (url.origin !== window.location.origin || !url.pathname.startsWith(`${api}/`)
      || url.pathname === `${api}/auth/login` || url.pathname === `${api}/health`) {
    return next(request);
  }

  const auth = inject(AuthService);
  const token = auth.bearer();
  const authenticatedRequest = token
    ? request.clone({ setHeaders: { Authorization: `Bearer ${token}` } })
    : request;
  return next(authenticatedRequest).pipe(catchError((error: unknown) => {
    if (error instanceof HttpErrorResponse && error.status === 401) auth.invalidate(token);
    return throwError(() => error);
  }));
};
