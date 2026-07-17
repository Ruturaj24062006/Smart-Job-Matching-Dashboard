import { Component, Input } from '@angular/core';
import { NgFor, NgIf } from '@angular/common';

export interface JobCardSkill {
  name: string;
  matched: boolean;
}

@Component({
  selector: 'app-job-card',
  imports: [NgFor, NgIf],
  templateUrl: './job-card.html',
  styleUrl: './job-card.css'
})
export class JobCard {
  @Input() title: string = 'Software Engineering Intern';
  @Input() companyName: string = 'NEXUS Intelligence Co.';
  @Input() location: string = 'San Francisco, CA (Hybrid)';
  @Input() matchScore: number = 84;
  @Input() techSkillsCount: number = 6;
  @Input() techSkillsTotal: number = 7;
  @Input() skills: JobCardSkill[] = [
    { name: 'Java', matched: true },
    { name: 'Spring Boot', matched: true },
    { name: 'PostgreSQL', matched: true },
    { name: 'Redis', matched: true },
    { name: 'Docker', matched: true },
    { name: 'Angular', matched: true },
    { name: 'AWS', matched: false }
  ];
  @Input() workEducationMatched: boolean = true;
  @Input() visaEligible: boolean = true;
  @Input() aiFeedback: string = 'Your profile matches 84% of components, indicating highly relevant experience in enterprise backend services and database optimization.';
}
