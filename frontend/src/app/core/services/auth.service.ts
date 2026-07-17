import { Injectable, signal, computed } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { Observable, tap } from 'rxjs';

export interface UserSession {
  userId: string;
  email: string;
  role: string;
}

export interface LoginResponse {
  accessToken: string;
  refreshToken: string;
  email: string;
  role: string;
  userId: string;
}

@Injectable({
  providedIn: 'root'
})
export class AuthService {
  private readonly apiUrl = 'http://localhost:8080/api/v1/auth';

  // Signals for state management
  readonly currentUser = signal<UserSession | null>(null);
  readonly isAuthenticated = computed(() => this.currentUser() !== null);
  readonly isAuthLoading = signal<boolean>(true);

  constructor(private readonly http: HttpClient) {
    this.restoreSession();
  }

  register(payload: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/register`, payload);
  }

  login(payload: any): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/login`, payload).pipe(
      tap(res => {
        if (res.success && res.data) {
          this.saveSession(res.data);
        }
      })
    );
  }

  loginWithGoogle(token: string): Observable<any> {
    return this.http.post<any>(`${this.apiUrl}/google`, { idToken: token }).pipe(
      tap(res => {
        if (res.success && res.data) {
          this.saveSession(res.data);
        }
      })
    );
  }

  refreshToken(): Observable<any> {
    const refreshToken = localStorage.getItem('refresh_token');
    return this.http.post<any>(`${this.apiUrl}/refresh`, { refreshToken }).pipe(
      tap(res => {
        if (res.success && res.data) {
          this.saveSession(res.data);
        } else {
          this.logout();
        }
      })
    );
  }

  verifyEmail(token: string): Observable<any> {
    return this.http.get(`${this.apiUrl}/verify-email`, { params: { token } });
  }

  forgotPassword(email: string): Observable<any> {
    return this.http.post(`${this.apiUrl}/forgot-password`, { email });
  }

  resetPassword(payload: any): Observable<any> {
    return this.http.post(`${this.apiUrl}/reset-password`, payload);
  }

  logout(): void {
    localStorage.removeItem('access_token');
    localStorage.removeItem('refresh_token');
    localStorage.removeItem('user_session');
    this.currentUser.set(null);
  }

  getAccessToken(): string | null {
    return localStorage.getItem('access_token');
  }

  private saveSession(data: LoginResponse): void {
    localStorage.setItem('access_token', data.accessToken);
    localStorage.setItem('refresh_token', data.refreshToken);
    
    const session: UserSession = {
      userId: data.userId,
      email: data.email,
      role: data.role
    };
    
    localStorage.setItem('user_session', JSON.stringify(session));
    this.currentUser.set(session);
  }

  private restoreSession(): void {
    this.isAuthLoading.set(true);
    try {
      const sessionStr = localStorage.getItem('user_session');
      const token = localStorage.getItem('access_token');
      
      if (sessionStr && token) {
        const session: UserSession = JSON.parse(sessionStr);
        this.currentUser.set(session);
      }
    } catch (e) {
      console.error('Failed to restore auth session', e);
      this.logout();
    } finally {
      this.isAuthLoading.set(false);
    }
  }
}
