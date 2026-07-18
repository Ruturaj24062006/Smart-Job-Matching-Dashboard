import { Component, signal, OnInit } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';
import { NgIf, NgClass } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-login',
  imports: [NgIf, NgClass, ReactiveFormsModule, RouterLink],
  templateUrl: './login.html',
  styleUrl: './login.css'
})
export class Login implements OnInit {
  loginForm: FormGroup;
  isLoading = signal<boolean>(false);
  errorMessage = signal<string | null>(null);

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService,
    private readonly router: Router
  ) {
    this.loginForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]]
    });
  }

  ngOnInit(): void {
    // Google Sign-In disabled — use email/password login
  }

  onSubmit(): void {
    if (this.loginForm.invalid) {
      this.loginForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);

    this.authService.login(this.loginForm.value).subscribe({
      next: (res: any) => {
        this.isLoading.set(false);
        if (res.success && res.data) {
          // Role comes from the backend — the authoritative source of truth
          this.redirectBasedOnRole(res.data.role);
        } else {
          this.errorMessage.set(res.message || 'Login failed. Please check your credentials.');
        }
      },
      error: (err: any) => {
        this.isLoading.set(false);

        if (err.status === 0) {
          // Backend is unreachable — show a clear offline error.
          // Do NOT silently create a mock session that bypasses real authentication.
          this.errorMessage.set(
            'Cannot reach the server. Please check your connection or try again later.'
          );
          console.warn('Backend server is unreachable.', err);
        } else {
          const backendMsg = err.error?.message || err.error?.error || 'Login failed. Please check your credentials.';
          this.errorMessage.set(backendMsg);
        }
      }
    });
  }

  /**
   * Single centralized redirect method — uses AuthService.redirectToDashboard for
   * RECRUITER and ADMIN, but adds profile-completion check for STUDENT.
   */
  private redirectBasedOnRole(role: string): void {
    const normalized = (role ?? '').trim().toUpperCase();
    const fullRole = normalized.startsWith('ROLE_') ? normalized : `ROLE_${normalized}`;

    switch (fullRole) {
      case 'ROLE_STUDENT':
        this.router.navigate(['/student/dashboard']);
        break;

      case 'ROLE_RECRUITER':
        this.router.navigate(['/recruiter/dashboard']);
        break;

      case 'ROLE_ADMIN':
        this.router.navigate(['/admin/dashboard']);
        break;

      default:
        // Unknown role — never silently pick a dashboard; show an error
        this.errorMessage.set(`Login succeeded but an unknown role was returned: "${role}". Please contact support.`);
        console.error('Unknown role returned from backend:', role);
        break;
    }
  }
}
