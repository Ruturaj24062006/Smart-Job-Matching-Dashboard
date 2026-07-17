import { Component, OnInit, signal } from '@angular/core';
import { ActivatedRoute, RouterLink } from '@angular/router';
import { NgIf } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-verify-email',
  imports: [NgIf, RouterLink],
  templateUrl: './verify-email.html',
  styleUrl: './verify-email.css'
})
export class VerifyEmail implements OnInit {
  status = signal<'verifying' | 'success' | 'error'>('verifying');
  message = signal<string>('Verifying your email address, please wait...');

  constructor(
    private readonly route: ActivatedRoute,
    private readonly authService: AuthService
  ) {}

  ngOnInit(): void {
    const token = this.route.snapshot.queryParamMap.get('token');
    if (!token) {
      this.status.set('error');
      this.message.set('No verification token provided. Please register again or request a new link.');
      return;
    }

    this.authService.verifyEmail(token).subscribe({
      next: (res) => {
        if (res.success) {
          this.status.set('success');
          this.message.set(res.message || 'Your email has been verified successfully!');
        } else {
          this.status.set('error');
          this.message.set(res.message || 'Failed to verify email. The token might have expired.');
        }
      },
      error: (err) => {
        this.status.set('error');
        this.message.set(err.error?.message || 'An error occurred during verification. The token might be invalid.');
      }
    });
  }
}
