import { Component, OnInit, signal, computed, HostListener } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIf, NgFor, NgClass, DatePipe } from '@angular/common';
import { AuthService } from '../../../core/services/auth.service';
import { NotificationService, NotificationDto } from '../../../core/services/notification.service';
import { RecruiterProfileService } from '../../../core/services/recruiter-profile.service';

@Component({
  selector: 'app-navbar',
  imports: [NgIf, NgFor, NgClass, DatePipe, RouterLink],
  templateUrl: './navbar.html',
  styleUrl: './navbar.css'
})
export class Navbar implements OnInit {
  notifications = signal<NotificationDto[]>([]);
  isOpen = signal<boolean>(false);
  isProfileDropdownOpen = signal<boolean>(false);
  recruiterProfile = signal<any | null>(null);

  unreadCount = computed(() => {
    return this.notifications().filter(n => n.status === 'PENDING').length;
  });

  @HostListener('document:click', ['$event'])
  onDocumentClick(event: MouseEvent): void {
    this.isOpen.set(false);
    this.isProfileDropdownOpen.set(false);
  }

  constructor(
    private readonly authService: AuthService,
    private readonly notificationService: NotificationService,
    private readonly profileService: RecruiterProfileService,
    private readonly router: Router
  ) {}

  ngOnInit(): void {
    if (this.isLoggedIn()) {
      this.loadNotifications();
      this.loadRecruiterProfile();
      // Poll notifications every 30 seconds
      setInterval(() => {
        if (this.isLoggedIn()) {
          this.loadNotifications();
        }
      }, 30000);
    }
  }

  loadRecruiterProfile(): void {
    const user = this.authService.currentUser();
    if (user && user.role === 'ROLE_RECRUITER') {
      this.profileService.getProfile().subscribe({
        next: (res) => {
          if (res.success && res.data) {
            this.recruiterProfile.set(res.data);
          }
        },
        error: (err) => console.error('Failed to load recruiter profile in navbar:', err)
      });
    }
  }

  toggleProfileDropdown(event: MouseEvent): void {
    event.stopPropagation();
    this.isProfileDropdownOpen.update(open => !open);
  }

  loadNotifications(): void {
    this.notificationService.getMyNotifications().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.notifications.set(res.data);
        }
      },
      error: (err) => console.error('Failed to load navbar notifications:', err)
    });
  }

  toggleNotifications(event: MouseEvent): void {
    event.stopPropagation();
    this.isOpen.update(open => !open);
    if (this.isOpen() && this.unreadCount() > 0) {
      // Auto-read all when opened for simplicity
      this.markAllAsRead();
    }
  }

  markAsRead(id: string, event: MouseEvent): void {
    event.stopPropagation();
    this.notificationService.markAsRead(id).subscribe({
      next: () => this.loadNotifications()
    });
  }

  markAllAsRead(): void {
    this.notificationService.markAllAsRead().subscribe({
      next: () => this.loadNotifications()
    });
  }

  isLoggedIn(): boolean {
    return this.authService.isAuthenticated();
  }

  isStudent(): boolean {
    const user = this.authService.currentUser();
    return user?.role === 'ROLE_STUDENT';
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
