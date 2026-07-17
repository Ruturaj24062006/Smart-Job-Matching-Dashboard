import { Component } from '@angular/core';
import { RouterLink } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-unauthorized',
  imports: [RouterLink],
  templateUrl: './unauthorized.html',
  styleUrl: './unauthorized.css'
})
export class Unauthorized {
  constructor(private readonly authService: AuthService) {}

  protected getHomeRoute(): string {
    const user = this.authService.currentUser();
    if (!user) return '/';
    
    switch (user.role) {
      case 'ROLE_STUDENT':
        return '/student/dashboard';
      case 'ROLE_RECRUITER':
        return '/recruiter/dashboard';
      case 'ROLE_ADMIN':
        return '/admin/dashboard';
      default:
        return '/';
    }
  }

  protected isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }
}
