import { Component } from '@angular/core';
import { Router } from '@angular/router';
import { AuthService } from '../../../core/services/auth.service';
import { Navbar } from '../../../shared/components/navbar/navbar';
import { Footer } from '../../../shared/components/footer/footer';

@Component({
  selector: 'app-recruiter-dashboard',
  imports: [Navbar, Footer],
  templateUrl: './recruiter-dashboard.html',
  styleUrl: './recruiter-dashboard.css'
})
export class RecruiterDashboard {
  constructor(
    private readonly authService: AuthService,
    private readonly router: Router
  ) {}

  getUserEmail(): string {
    return this.authService.currentUser()?.email || 'N/A';
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }
}
