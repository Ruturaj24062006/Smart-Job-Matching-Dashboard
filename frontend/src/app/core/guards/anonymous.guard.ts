import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

/**
 * anonymousGuard — blocks authenticated users from accessing login/register routes
 * and redirects them to their respective dashboards.
 */
export const anonymousGuard: CanActivateFn = (_route, _state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    authService.redirectToDashboard(router);
    return false;
  }
  return true;
};
