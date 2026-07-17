import { CanActivateFn, Router } from '@angular/router';
import { inject } from '@angular/core';
import { AuthService } from '../services/auth.service';

export const authGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);

  if (authService.isAuthenticated()) {
    return true;
  }

  router.navigate(['/login'], { queryParams: { returnUrl: state.url } });
  return false;
};

export const roleGuard: CanActivateFn = (route, state) => {
  const authService = inject(AuthService);
  const router = inject(Router);
  
  const expectedRoles: string[] = route.data?.['roles'] || [];
  const user = authService.currentUser();

  if (authService.isAuthenticated() && user && expectedRoles.includes(user.role)) {
    return true;
  }

  router.navigate(['/unauthorized']);
  return false;
};
