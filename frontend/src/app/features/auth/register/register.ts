import { Component, signal } from '@angular/core';
import { FormBuilder, FormGroup, Validators, ReactiveFormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';
import { NgIf, NgClass } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';

@Component({
  selector: 'app-register',
  imports: [NgIf, NgClass, ReactiveFormsModule, RouterLink],
  templateUrl: './register.html',
  styleUrl: './register.css'
})
export class Register {
  registerForm: FormGroup;
  selectedRole = signal<'ROLE_STUDENT' | 'ROLE_RECRUITER'>('ROLE_STUDENT');
  isLoading = signal<boolean>(false);
  successMessage = signal<string | null>(null);
  errorMessage = signal<string | null>(null);

  constructor(
    private readonly fb: FormBuilder,
    private readonly authService: AuthService
  ) {
    this.registerForm = this.fb.group({
      email: ['', [Validators.required, Validators.email]],
      password: ['', [Validators.required, Validators.minLength(6)]],
      firstName: ['', [Validators.required]],
      lastName: ['', [Validators.required]],
      companyName: [''],
      jobTitle: ['']
    });
  }

  selectRole(role: 'ROLE_STUDENT' | 'ROLE_RECRUITER'): void {
    this.selectedRole.set(role);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const firstName = this.registerForm.get('firstName');
    const lastName = this.registerForm.get('lastName');
    const companyName = this.registerForm.get('companyName');
    const jobTitle = this.registerForm.get('jobTitle');

    if (role === 'ROLE_STUDENT') {
      firstName?.setValidators([Validators.required]);
      lastName?.setValidators([Validators.required]);
      companyName?.clearValidators();
      jobTitle?.clearValidators();
    } else {
      firstName?.clearValidators();
      lastName?.clearValidators();
      companyName?.setValidators([Validators.required]);
      jobTitle?.setValidators([Validators.required]);
    }

    firstName?.updateValueAndValidity();
    lastName?.updateValueAndValidity();
    companyName?.updateValueAndValidity();
    jobTitle?.updateValueAndValidity();
  }

  onSubmit(): void {
    if (this.registerForm.invalid) {
      this.registerForm.markAllAsTouched();
      return;
    }

    this.isLoading.set(true);
    this.errorMessage.set(null);
    this.successMessage.set(null);

    const formVal = this.registerForm.value;
    const payload: any = {
      email: formVal.email,
      password: formVal.password,
      role: this.selectedRole()
    };

    if (this.selectedRole() === 'ROLE_STUDENT') {
      payload.firstName = formVal.firstName;
      payload.lastName = formVal.lastName;
    } else {
      payload.companyName = formVal.companyName;
      payload.jobTitle = formVal.jobTitle;
    }

    this.authService.register(payload).subscribe({
      next: (res) => {
        this.isLoading.set(false);
        if (res.success) {
          this.successMessage.set('Registration successful! A verification link has been sent to your email.');
          this.registerForm.reset();
        } else {
          this.errorMessage.set(res.message || 'Registration failed. Please try again.');
        }
      },
      error: (err) => {
        this.isLoading.set(false);
        this.errorMessage.set(err.error?.message || 'An error occurred during registration. Please try again.');
      }
    });
  }
}
