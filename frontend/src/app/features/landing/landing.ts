import { Component } from '@angular/core';
import { NgFor, NgIf, NgClass } from '@angular/common';
import { RouterLink } from '@angular/router';
import { Navbar } from '../../shared/components/navbar/navbar';
import { Footer } from '../../shared/components/footer/footer';
import { JobCard, JobCardSkill } from '../../shared/components/job-card/job-card';

interface LandingTestimonial {
  quote: string;
  name: string;
  role: string;
  avatarLetter: string;
}

interface LandingFeatureRow {
  title: string;
  traditional: string;
  nexus: string;
}

interface LandingFaqItem {
  question: string;
  answer: string;
}

@Component({
  selector: 'app-landing',
  imports: [NgFor, NgIf, NgClass, RouterLink, Navbar, Footer, JobCard],
  templateUrl: './landing.html',
  styleUrl: './landing.css'
})
export class Landing {
  // FAQ Accordion State
  protected openFaqIndex: number | null = null;

  protected toggleFaq(index: number): void {
    this.openFaqIndex = this.openFaqIndex === index ? null : index;
  }

  protected readonly faqs: LandingFaqItem[] = [
    {
      question: "How does the match score calculation work?",
      answer: "NEXUS uses custom recruiter-inspired heuristics and semantic vector embeddings to analyze the compatibility between your profile and the job description across technical skills, education alignment, behavioral fit, and experience depth."
    },
    {
      question: "Do I need to upload a resume to see matching jobs?",
      answer: "No! If you don't upload a resume, you can quickly onboard by inputting your primary skills, target role, and location. NEXUS will calculate match scores based on your inputted profile."
    },
    {
      question: "Is there any visa and sponsorship tracking?",
      answer: "Yes, NEXUS parses job listings for sponsorship availability and compares it with your career preferences to highlight visa constraints before you apply."
    },
    {
      question: "How do recruiters see my applications?",
      answer: "Recruiters see applications organized into status bins (Applied, Under Review, Shortlisted, Interview, Selected, Rejected) sorted by match score rank."
    }
  ];
  // Testimonials content
  protected readonly testimonials: LandingTestimonial[] = [
    {
      quote: "For the first time, I understood exactly why I was getting rejected. I fixed two key skills on my resume and landed an interview at Google in 2 weeks.",
      name: "Alex Chen",
      role: "M.S. in Computer Science",
      avatarLetter: "A"
    },
    {
      quote: "As an international student, sponsorship transparency is a lifesaver. NEXUS saved me hundreds of hours of wasted job applications.",
      name: "Priya Patel",
      role: "Software Engineering Graduate",
      avatarLetter: "P"
    },
    {
      quote: "NEXUS is like having a former recruiter look over your resume and give you the answers before you even click submit. Indispensable.",
      name: "Marcus Thorne",
      role: "Senior Backend Developer",
      avatarLetter: "M"
    }
  ];

  // Comparison table feature list
  protected readonly comparisonTable: LandingFeatureRow[] = [
    {
      title: "Match Transparency",
      traditional: "Binary Match / Black Box",
      nexus: "Detailed % Score Breakdown"
    },
    {
      title: "Sponsorship Indexing",
      traditional: "Hidden or Manual Filter",
      nexus: "Instant Smart Sponsor Filter"
    },
    {
      title: "Resume Relevance",
      traditional: "Static PDF Keyword Match",
      nexus: "Multi-Dimensional Vector Profile"
    },
    {
      title: "Developer Feedback",
      traditional: "None / Ghosted",
      nexus: "Real-time Skill Gap Roadmaps"
    }
  ];

  // Mock skills array for the demo JobCard
  protected readonly demoJobSkills: JobCardSkill[] = [
    { name: 'Java', matched: true },
    { name: 'Spring Boot', matched: true },
    { name: 'PostgreSQL', matched: true },
    { name: 'RabbitMQ', matched: true },
    { name: 'Redis', matched: true },
    { name: 'Docker', matched: true },
    { name: 'Kubernetes', matched: false }
  ];
}
