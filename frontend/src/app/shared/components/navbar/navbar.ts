import { Component } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-navbar',
  imports: [NgIf, RouterLink],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class Navbar {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }

  getDashboardLink(): string {
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

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
