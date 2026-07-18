import { Component, OnInit, OnDestroy, signal, computed } from '@angular/core';
import { Router, RouterLink } from '@angular/router';
import { NgIf, NgFor, NgClass, LowerCasePipe, DatePipe } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AuthService } from '../../../core/services/auth.service';
import { StudentProfileService, StudentProfileDto } from '../../../core/services/student-profile.service';
import { JobMatchesService, MatchResponse, MatchDetailsResponse } from '../../../core/services/job-matches.service';
import { JobApplicationsService } from '../../../core/services/job-applications.service';
import { Navbar } from '../../../shared/components/navbar/navbar';
import { Footer } from '../../../shared/components/footer/footer';

@Component({
  selector: 'app-find-jobs',
  imports: [Navbar, Footer, NgIf, NgFor, NgClass, LowerCasePipe, FormsModule, DatePipe, RouterLink],
  templateUrl: './find-jobs.html',
  styleUrl: './find-jobs.css'
})
export class FindJobs implements OnInit, OnDestroy {
  // Signals for state management
  profile = signal<StudentProfileDto | null>(null);
  matches = signal<MatchResponse[]>([]);
  savedJobIds = signal<Set<string>>(new Set());
  appliedJobIds = signal<Set<string>>(new Set());

  // Sorting
  currentSort = signal<'recommended' | 'score' | 'date'>('recommended');

  // Search & Filter Inputs
  searchRole = signal<string>('');
  searchLocation = signal<string>('');
  filterExperience = signal<string>('');
  filterJobType = signal<string>('');
  filterSalary = signal<string>('');
  filterSkills = signal<string>('');
  filterSponsorship = signal<boolean>(false);

  // Modal signals
  selectedMatch = signal<MatchDetailsResponse | null>(null);
  isDetailsModalOpen = signal<boolean>(false);
  isAiLoading = signal<boolean>(false);
  aiExplanation = signal<string | null>(null);
  aiSkillGap = signal<any | null>(null);

  // AI Chat signals
  isAiChatOpen = signal<boolean>(false);
  aiChatMessages = signal<{ sender: 'user' | 'ai', text: string }[]>([]);
  userQuestionText = signal<string>('');
  isAiResponding = signal<boolean>(false);

  // Application flow signals
  isApplyModalOpen = signal<boolean>(false);
  applyJobId = signal<string>('');
  applyJobTitle = signal<string>('');
  applyCompanyName = signal<string>('');
  coverLetterText = signal<string>('');
  isSubmittingApplication = signal<boolean>(false);
  applicationSuccess = signal<boolean>(false);

  // Upload & Review Popup modal (for sidebar action)
  isUploadModalOpen = signal<boolean>(false);
  uploadStep = signal<'select' | 'uploading' | 'review' | 'error'>('select');
  uploadProgress = signal<number>(0);
  uploadError = signal<string | null>(null);
  extractedData = signal<any>(null);
  currentResumeId = signal<string | null>(null);
  processingStage = signal<string>('Uploading resume...');
  processingEtaSecs = signal<number>(60);
  reviewRole: string = '';
  reviewCity: string = '';
  reviewExperience: string = '';
  reviewWorkMode: string = 'HYBRID';
  private modalPollIntervalId: any = null;
  private etaIntervalId: any = null;

  // Reactively computed list of filtered and sorted matches
  processedMatches = computed(() => {
    let list = [...this.matches()];

    // Search text filters
    const roleText = this.searchRole().toLowerCase().trim();
    const locText = this.searchLocation().toLowerCase().trim();
    const expLvl = this.filterExperience();
    const jType = this.filterJobType();
    const salText = this.filterSalary().toLowerCase().trim();
    const skillText = this.filterSkills().toLowerCase().trim();
    const needSponsor = this.filterSponsorship();

    if (roleText) {
      list = list.filter(m => 
        m.jobTitle.toLowerCase().includes(roleText) || 
        m.companyName.toLowerCase().includes(roleText)
      );
    }
    if (locText) {
      list = list.filter(m => m.location.toLowerCase().includes(locText));
    }
    if (expLvl) {
      list = list.filter(m => m.experienceLevel && m.experienceLevel.toLowerCase().includes(expLvl.toLowerCase()));
    }
    if (jType) {
      list = list.filter(m => m.jobType === jType);
    }
    if (salText) {
      list = list.filter(m => m.salaryRange && m.salaryRange.toLowerCase().includes(salText));
    }
    if (skillText) {
      const targetSkills = skillText.split(',').map(s => s.trim().toLowerCase()).filter(Boolean);
      list = list.filter(m => {
        if (!m.requiredSkills) return false;
        const req = m.requiredSkills.toLowerCase();
        return targetSkills.every(ts => req.includes(ts));
      });
    }

    // Apply sorting
    const sortBy = this.currentSort();
    if (sortBy === 'recommended') {
      // Sort primarily by score, tie break by date
      list.sort((a, b) => {
        if (b.compositeScore !== a.compositeScore) {
          return b.compositeScore - a.compositeScore;
        }
        return new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime();
      });
    } else if (sortBy === 'score') {
      list.sort((a, b) => b.compositeScore - a.compositeScore);
    } else if (sortBy === 'date') {
      list.sort((a, b) => new Date(b.createdAt || 0).getTime() - new Date(a.createdAt || 0).getTime());
    }

    return list;
  });

  constructor(
    private readonly authService: AuthService,
    private readonly profileService: StudentProfileService,
    private readonly matchesService: JobMatchesService,
    private readonly applicationsService: JobApplicationsService,
    public readonly router: Router
  ) {}

  ngOnInit(): void {
    this.loadProfileAndMatches();
  }

  ngOnDestroy(): void {
    this.stopModalPolling();
  }

  loadProfileAndMatches(): void {
    // Load student profile
    this.profileService.getProfile().subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.profile.set(res.data);
        }
        this.fetchMatches();
        this.loadApplications();
      },
      error: () => {
        this.fetchMatches();
        this.loadApplications();
      }
    });
  }

  fetchMatches(): void {
    this.matchesService.getMatches().subscribe({
      next: (res) => {
        if (res.success && Array.isArray(res.data) && res.data.length > 0) {
          this.matches.set(res.data);
        } else {
          this.triggerMatchGeneration();
        }
      },
      error: () => {
        this.matches.set([]);
      }
    });
  }

  triggerMatchGeneration(): void {
    this.matchesService.generateMatches().subscribe({
      next: () => {
        this.matchesService.getMatches().subscribe({
          next: (mRes) => {
            if (mRes.success && Array.isArray(mRes.data)) {
              this.matches.set(mRes.data);
            }
          }
        });
      }
    });
  }

  loadApplications(): void {
    this.applicationsService.getMyApplications().subscribe({
      next: (res) => {
        if (res.success && Array.isArray(res.data)) {
          const ids = new Set<string>();
          res.data.forEach((app: any) => {
            if (app.jobId) {
              ids.add(app.jobId);
            } else if (app.job && app.job.id) {
              ids.add(app.job.id);
            }
          });
          this.appliedJobIds.set(ids);
        }
      }
    });
  }

  setSort(sortType: 'recommended' | 'score' | 'date'): void {
    this.currentSort.set(sortType);
  }

  getStudentName(): string {
    const prof = this.profile();
    if (prof && prof.firstName) {
      return prof.firstName;
    }
    const email = this.authService.currentUser()?.email || '';
    if (email) {
      const username = email.split('@')[0];
      return username
        .split(/[\._]/)
        .map(part => part.charAt(0).toUpperCase() + part.slice(1).replace(/[0-9]/g, ''))
        .filter(part => part.length > 0)
        .join(' ');
    }
    return 'Student';
  }

  getFitLabel(score: number): string {
    if (score >= 80) return 'Great Fit';
    if (score >= 60) return 'Good Fit';
    return 'Potential Fit';
  }

  getSkillsList(skillsStr?: string): string[] {
    if (!skillsStr) return [];
    return skillsStr.split(',').map(s => s.trim()).filter(Boolean);
  }

  toggleBookmark(jobId: string, event: Event): void {
    event.stopPropagation();
    const set = new Set(this.savedJobIds());
    if (set.has(jobId)) {
      set.delete(jobId);
    } else {
      set.add(jobId);
    }
    this.savedJobIds.set(set);
  }

  isBookmarked(jobId: string): boolean {
    return this.savedJobIds().has(jobId);
  }

  viewMatchDetails(matchId: string): void {
    this.isDetailsModalOpen.set(true);
    this.selectedMatch.set(null);
    this.aiExplanation.set(null);
    this.aiSkillGap.set(null);
    
    this.matchesService.getMatchDetails(matchId).subscribe({
      next: (res) => {
        if (res.success && res.data) {
          this.selectedMatch.set(res.data);
          this.openAiChat();
        }
      },
      error: () => {
        this.isDetailsModalOpen.set(false);
      }
    });
  }

  askAiMatch(matchId: string, event: Event): void {
    event.stopPropagation();
    this.isDetailsModalOpen.set(true);
    this.selectedMatch.set(null);
    this.isAiLoading.set(true);
    this.aiExplanation.set(null);
    this.aiSkillGap.set(null);

    this.matchesService.getMatchDetails(matchId).subscribe({
      next: (res) => {
        this.isAiLoading.set(false);
        if (res.success && res.data) {
          const data = res.data;
          this.selectedMatch.set(data);
          this.aiExplanation.set(data.explanation);
          if (data.skillGap) {
            try {
              this.aiSkillGap.set(JSON.parse(data.skillGap));
            } catch (e) {
              console.error('Failed to parse skill gap JSON', e);
            }
          }
          this.openAiChat();
          this.sendQuestion('Why did I get this score?');
        }
      },
      error: () => {
        this.isAiLoading.set(false);
        this.isDetailsModalOpen.set(false);
      }
    });
  }

  closeModal(): void {
    this.isDetailsModalOpen.set(false);
    this.selectedMatch.set(null);
    this.aiExplanation.set(null);
    this.aiSkillGap.set(null);
    this.isAiChatOpen.set(false);
    this.aiChatMessages.set([]);
  }

  openAiChat(): void {
    this.aiChatMessages.set([
      { sender: 'ai', text: `Hi! I am your career coach AI. Ask me anything about this job (e.g. why did I get this score, what skills am I missing, should I apply, etc.)!` }
    ]);
    this.isAiChatOpen.set(true);
  }

  closeAiChat(): void {
    this.isAiChatOpen.set(false);
  }

  sendQuestion(customQuestion?: string): void {
    const question = (customQuestion || this.userQuestionText()).trim();
    if (!question) return;

    this.aiChatMessages.update(msgs => [...msgs, { sender: 'user', text: question }]);
    this.userQuestionText.set('');
    this.isAiResponding.set(true);

    const match = this.selectedMatch();
    if (!match) {
      this.isAiResponding.set(false);
      return;
    }

    this.matchesService.askAi(match.id, question).subscribe({
      next: (res) => {
        this.isAiResponding.set(false);
        if (res.success && res.data) {
          this.aiChatMessages.update(msgs => [...msgs, { sender: 'ai', text: res.data }]);
        } else {
          this.aiChatMessages.update(msgs => [...msgs, { sender: 'ai', text: 'I encountered an error analyzing this job. Please try again.' }]);
        }
      },
      error: () => {
        this.isAiResponding.set(false);
        this.aiChatMessages.update(msgs => [...msgs, { sender: 'ai', text: 'Sorry, I failed to reach the AI assistant. Check your connection.' }]);
      }
    });
  }

  selectQuickQuestion(question: string): void {
    this.sendQuestion(question);
  }

  initiateApply(jobId: string, jobTitle: string, companyName: string, event: Event): void {
    event.stopPropagation();
    this.applyJobId.set(jobId);
    this.applyJobTitle.set(jobTitle);
    this.applyCompanyName.set(companyName);
    this.coverLetterText.set('');
    this.applicationSuccess.set(false);
    this.isApplyModalOpen.set(true);
  }

  closeApplyModal(): void {
    this.isApplyModalOpen.set(false);
    this.applyJobId.set('');
    this.applyJobTitle.set('');
    this.applyCompanyName.set('');
    this.coverLetterText.set('');
  }

  submitApplication(): void {
    const jobId = this.applyJobId();
    if (!jobId) return;

    this.isSubmittingApplication.set(true);
    this.applicationsService.applyToJob(jobId, this.coverLetterText()).subscribe({
      next: (res) => {
        this.isSubmittingApplication.set(false);
        if (res.success) {
          this.applicationSuccess.set(true);
          this.loadApplications();
          setTimeout(() => {
            this.closeApplyModal();
            this.closeModal(); // close details modal too
          }, 2000);
        }
      },
      error: () => {
        this.isSubmittingApplication.set(false);
      }
    });
  }

  onLogout(): void {
    this.authService.logout();
    this.router.navigate(['/login']);
  }

  resetFilters(): void {
    this.searchRole.set('');
    this.searchLocation.set('');
    this.filterExperience.set('');
    this.filterJobType.set('');
    this.filterSalary.set('');
    this.filterSkills.set('');
    this.filterSponsorship.set(false);
  }

  // Upload Modal triggers (for identical sidebar experience)
  openUploadModal(): void {
    this.uploadStep.set('select');
    this.uploadProgress.set(0);
    this.uploadError.set(null);
    this.extractedData.set(null);
    this.processingStage.set('Uploading resume...');
    this.processingEtaSecs.set(60);
    this.isUploadModalOpen.set(true);
  }

  closeUploadModal(): void {
    this.stopModalPolling();
    this.stopEtaCountdown();
    this.isUploadModalOpen.set(false);
  }

  onModalFileSelected(event: any): void {
    const file = event.target.files?.[0] || event.dataTransfer?.files?.[0];
    if (!file) return;

    const ext = file.name.split('.').pop()?.toLowerCase();
    if (!['pdf', 'docx', 'doc'].includes(ext || '')) {
      this.uploadError.set('Please upload a valid PDF or Word Document (.docx, .doc)');
      return;
    }

    this.startModalUpload(file);
  }

  private startModalUpload(file: File): void {
    this.uploadError.set(null);
    this.uploadStep.set('uploading');
    this.uploadProgress.set(5);
    this.processingStage.set('Uploading resume file...');
    this.processingEtaSecs.set(75);

    this.profileService.uploadResume(file).subscribe({
      next: (res) => {
        this.uploadProgress.set(20);
        this.processingStage.set('Extracting text from PDF...');
        this.processingEtaSecs.set(65);
        if (res.data && res.data.id) {
          this.currentResumeId.set(res.data.id);
        }
        this.startModalPolling();
        this.startEtaCountdown();
      },
      error: (err) => {
        this.uploadStep.set('error');
        this.uploadError.set(err.error?.message || 'Failed to upload resume. Please check the file and try again.');
      }
    });
  }

  private startModalPolling(): void {
    this.stopModalPolling();
    let attempts = 0;
    const maxAttempts = 60;

    this.modalPollIntervalId = setInterval(() => {
      attempts++;

      if (attempts === 3) {
        this.processingStage.set('AI parsing skills & experience...');
        this.uploadProgress.set(40);
      } else if (attempts === 8) {
        this.processingStage.set('Generating candidate embedding vector...');
        this.uploadProgress.set(60);
      } else if (attempts === 15) {
        this.processingStage.set('Saving profile to database...');
        this.uploadProgress.set(80);
      }

      if (attempts > maxAttempts) {
        this.stopModalPolling();
        this.stopEtaCountdown();
        this.uploadStep.set('error');
        this.uploadError.set(
          'AI processing timed out after 3 minutes. The resume may be too complex or the server is busy. Please try uploading a simpler PDF or try again later.'
        );
        return;
      }

      this.profileService.getLatestResume().subscribe({
        next: (res) => {
          if (res.success && res.data) {
            if (res.data.processingStatus === 'FAILED') {
              this.stopModalPolling();
              this.stopEtaCountdown();
              this.uploadStep.set('error');
              this.uploadError.set(
                'AI could not parse your resume. This may be due to a scanned image-only PDF or unsupported formatting. Please try a text-based PDF.'
              );
              return;
            }
            if (res.data.extractedJson) {
              this.stopModalPolling();
              this.stopEtaCountdown();
              const rawJson = res.data.extractedJson;
              let parsed: any = null;
              try {
                parsed = typeof rawJson === 'string' ? JSON.parse(rawJson) : rawJson;
              } catch (e) {
                this.uploadStep.set('error');
                this.uploadError.set('Failed to read extracted resume data. Please try again.');
                return;
              }
              this.extractedData.set(parsed);
              this.reviewRole = parsed?.preferredRole || parsed?.currentTitle || '';
              this.reviewCity = parsed?.location || '';
              this.reviewExperience = parsed?.experienceLevel || '';
              this.reviewWorkMode = parsed?.workMode || 'HYBRID';
              this.uploadProgress.set(100);
              this.uploadStep.set('review');
            }
          }
        },
        error: () => {
          console.warn('Resume status poll failed, retrying...');
        }
      });
    }, 3000);
  }

  private startEtaCountdown(): void {
    this.stopEtaCountdown();
    this.etaIntervalId = setInterval(() => {
      this.processingEtaSecs.update(s => Math.max(0, s - 1));
    }, 1000);
  }

  private stopEtaCountdown(): void {
    if (this.etaIntervalId) {
      clearInterval(this.etaIntervalId);
      this.etaIntervalId = null;
    }
  }

  private stopModalPolling(): void {
    if (this.modalPollIntervalId) {
      clearInterval(this.modalPollIntervalId);
      this.modalPollIntervalId = null;
    }
  }

  confirmModalAndSearchJobs(): void {
    const resumeId = this.currentResumeId();
    if (resumeId) {
      this.profileService.confirmResume(resumeId).subscribe({
        next: () => {
          this.closeUploadModal();
          this.fetchMatches();
        },
        error: () => {
          this.closeUploadModal();
          this.fetchMatches();
        }
      });
    } else {
      this.closeUploadModal();
      this.fetchMatches();
    }
  }
}
