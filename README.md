# 🚀 NEXUS — AI-Powered Smart Job Matching & Resume Parsing Dashboard

A production-grade, AI-driven job matching and talent discovery platform designed to align job seekers with relevant opportunities using **Hybrid RAG Semantic Search (pgvector + BM25 RRF)**, **Deterministic 6-Factor Scoring**, **Fast In-Memory PDF Parsing**, and an **Interactive AI Career Assistant**.

---

## 👥 Team Details & Project Metadata
- **Team Name**: the solver squad
- **Team Members**:
  - Ruturaj Ambure
  - Shantanu Gudmewar
  - Atharv Bhavsar

---

## 🌐 Live Application Deployment
- **Frontend Web App**: [https://nexus-frontend-gmd1.onrender.com](https://nexus-frontend-gmd1.onrender.com)
- **Backend REST API**: [https://nexus-backend-gmd1.onrender.com](https://nexus-backend-gmd1.onrender.com)
- **GitHub Repository**: [https://github.com/Ruturaj24062006/Smart-Job-Matching-Dashboard](https://github.com/Ruturaj24062006/Smart-Job-Matching-Dashboard)

---

## 🔑 Pre-Seeded Accounts & Test Credentials

You can log in directly using the pre-seeded recruiter/company accounts or register a new student account:

### 🏢 Recruiter Account (Nexora Technologies / Demo Recruiter)
- **Email**: `hr@nexoratech.example` or `demo.recruiter@careermatch.com`
- **Password**: `NexoraRecruiterPass123!` or `RecruiterPass123!`
- **Role**: `ROLE_RECRUITER`

### 🎓 Student Account Registration
- Navigate to `/register` to create a fresh student account.
- Post-login automatically redirects to the **Student Dashboard** as the default landing view.

---

## 🧠 Core Engineering Architecture & Algorithms

NEXUS is engineered with a multi-layered matching and parsing architecture to deliver accurate, sub-second recommendations:

```
┌───────────────────────────────────────────────────────────────────────────────────────────┐
│                                    NEXUS MATCHING PIPELINE                                │
└───────────────────────────────────────────────────────────────────────────────────────────┘
                                              │
       ┌──────────────────────────────────────┴──────────────────────────────────────┐
       │                                                                             │
┌──────▼────────────────────────────┐                       ┌────────────────────────▼─────┐
│    PRIMARY: RESUME PARSING        │                       │    FALLBACK: STUDENT PROFILE │
│ (PDFBox -> Embedding -> Groq LLM) │                       │ (Skills, Exp, Edu, Projects) │
└──────┬────────────────────────────┘                       └────────────────────────┬─────┘
       │                                                                             │
       └──────────────────────────────────────┬──────────────────────────────────────┘
                                              │
                                   ┌──────────▼──────────┐
                                   │  HYBRID RAG SEARCH  │
                                   └──────────┬──────────┘
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      │                                               │
           ┌──────────▼──────────┐                         ┌──────────▼──────────┐
           │   DENSE VECTOR      │                         │     SPARSE FTS      │
           │  (pgvector Cosine)  │                         │    (Postgres BM25)  │
           └──────────┬──────────┘                         └──────────┬──────────┘
                      │                                               │
                      └───────────────────────┬───────────────────────┘
                                              │
                                   ┌──────────▼──────────┐
                                   │  RRF RANK FUSION    │
                                   │    (SQL RRF k=60)   │
                                   └──────────┬──────────┘
                                              │
                                   ┌──────────▼──────────┐
                                   │  6-FACTOR SCORING   │
                                   │ (Deterministic Engine│
                                   └──────────┬──────────┘
                                              │
                                   ┌──────────▼──────────┐
                                   │   SCORE DONUT RING  │
                                   │ (Sorted & Cached)   │
                                   └─────────────────────┘
```

### 1. 🔍 Hybrid RAG Search Engine (pgvector + BM25 RRF)
- **Dense Vector Retrieval**: Uses the quantized ONNX `all-MiniLM-L6-v2` model in Java to generate 384-dimensional vector embeddings stored directly in PostgreSQL using `pgvector`. Performs high-speed cosine distance similarity search (`<=>`).
- **Sparse Full-Text Retrieval (BM25)**: Utilizes PostgreSQL `tsvector` with disjunctive Cover Density scoring (`websearch_to_tsquery`) across skill tags, experience job titles, project tech stacks, education majors, and resume text.
- **Reciprocal Rank Fusion (RRF)**: Merges dense and sparse search results in SQL using $RRF(d) = \sum \frac{1}{k + r_i(d)}$ ($k=60$) to achieve optimal precision and recall.

### 2. 🎯 Deterministic 6-Factor Scoring Engine
Every candidate job is passed through a deterministic scoring matrix yielding a composite score out of 100%:
- 🛠️ **Technical Skill Overlap (40%)**: Skill tag overlap against job requirements & description.
- 🚀 **Project Fit (20%)**: Project technologies, titles, and descriptions matching candidate job domain.
- 🏢 **Years of Experience Fit (15%)**: Work experience duration mapped against required seniority levels (Entry, Mid, Senior).
- 🎯 **Domain & Preference Fit (10%)**: Education field of study, past experience titles, and target job role preferences.
- 🤝 **Behavioral & Soft Skills Match (10%)**: Bio and experience text scanned for soft skill indicators (leadership, teamwork, communication, agile).
- 🎓 **Education & Certifications Fit (5%)**: Degree status and professional certifications.

### 3. ⚡ Optimized In-Memory Resume Ingestion Pipeline
- **Direct PDF Parsing**: Utilizes Apache PDFBox to read PDF files directly from memory streams (`byte[]`), completely bypassing disk I/O bottlenecks.
- **Parallel Task Execution**: Runs ONNX vector embedding generation and Groq LLM profile extraction (`Llama 3.3 70B Versatile`) in parallel using a dedicated Spring thread pool (`resumeProcessingExecutor`).
- **Connection Pooling**: Utilizes Apache HttpComponents 5 (`httpclient5`) for high-performance HTTP connection pooling to external AI APIs.

### 4. 🛡️ Fail-Safe Profile Fallback Mechanism
- If resume parsing fails (e.g. image-only PDF, network glitch, or AI timeout), the platform does **NOT** block the user.
- The workflow seamlessly transitions to generating the vector embedding and BM25 keywords directly from the student's **Profile details** (skills, education, projects, experience, preferences).
- Ensures uninterrupted job matching until a new resume is uploaded.

---

## 🛠️ Complete Technology Stack

### 🔹 Backend Architecture
- **Language & Runtime**: Java 21, Spring Boot 3.3.0
- **Security**: Spring Security 6, Stateless JWT Tokens (`jjwt`), BCrypt Password Encoding
- **Database & Storage**: PostgreSQL 16+, `pgvector` extension, Hibernate 6, Spring Data JPA
- **Caching & Messaging**: Redis (1-hour TTL for match caches), RabbitMQ with Spring AMQP & `LocalEventFallbackListener`
- **AI & Embeddings**: LangChain4j, ONNX Quantized `all-MiniLM-L6-v2` model (in-process 384d vector generation)
- **External APIs**: Groq API (`llama-3.3-70b-versatile` LLM), Resend Email API, Supabase Auth
- **HTTP Client**: Apache HttpComponents 5 (`httpclient5`)

### 🔹 Frontend Architecture
- **Framework**: Angular 18 (Standalone Components, Signals API, RxJS, Reactive & Template-Driven Forms)
- **UI & Styling**: Modern Vanilla CSS3, Custom HSL Color Systems, Glassmorphism Cards, CSS Flexbox & Grid layouts, Animated SVG Icons
- **Routing**: Angular Router with strict Role-Based Route Guards (`authGuard`, `roleGuard`)
- **Interceptors**: HTTP JWT Auth Interceptor with automatic bearer token attachment

### 🔹 DevOps & Cloud Infrastructure
- **Hosting**: Render Cloud Platform (Web Services for Spring Boot backend, Static Sites for Angular frontend)
- **Database Hosting**: Supabase Cloud PostgreSQL with pgvector enabled
- **Build Tools**: Apache Maven (`mvnw`), Angular CLI (`ng build`)

---

## ✨ Key Features & User Experience

### 🎓 For Students
1. **Interactive Dashboard**:
   - Live job recommendations ranked by composite match score (e.g. `94% Match`).
   - Circular Score Donut Rings with visual fit indicators (*Great Fit*, *Good Fit*).
   - Instant filtering by job role, city location, experience level, job type, and skill tags.
   - Collapsible **Job Boards** sidebar (`All Matches`, `.Net Developer`, `Java Developer`, `Frontend Engineer`, `AI Engineer`).
2. **Ask AI 🤖 Career Coach**:
   - Click **Ask AI** on any job card or in the job details modal to launch an interactive chat with NEXUS AI.
   - Receives personalized explanations on why the job fits their profile, skill gap analyses, and recommendations.
3. **Dedicated Student Profile Page**:
   - Complete 9-section profile editor: **Personal Information**, **Professional Information**, **Skills** (technical & soft skill chips), **Education**, **Work Experience**, **Projects**, **Certifications**, **Resume**, and **Account Settings**.
   - Dynamic **Profile Strength Bar** calculating completion percentage in real time.
4. **Inline Resume Upload & Progress Tracker**:
   - Real-time progress bar with ETA countdown and detailed stage indicators (*Uploading*, *Parsing*, *Embedding*, *Saving*).

### 🏢 For Recruiters
1. **Job Posting Management**: Create and manage job openings with custom requirements, required skills, preferred skills, location, work mode, and salary ranges.
2. **AI Applicant Ranking**: Automatically ranks incoming applicants based on their calculated match score against job specifications.

---

## 📁 Repository Structure

```
Smart-Job-Matching-Dashboard/
├── backend/
│   ├── src/main/java/com/careermatch/backend/
│   │   ├── ai/          # EmbeddingService (ONNX) & GroqService (LLM)
│   │   ├── admin/       # Admin controllers & services
│   │   ├── auth/        # JWT Security, Supabase integration & Auth controllers
│   │   ├── company/     # Company entity & repository
│   │   ├── config/      # AppConfig, SecurityConfig, ThreadPool & Redis config
│   │   ├── job/         # Job entities, repositories, controllers & JobDataSeeder
│   │   ├── matching/    # SearchService (RRF RAG), ScoringService & MatchingService
│   │   ├── recruiter/   # Recruiter management & applicant ranking
│   │   ├── resume/      # PdfParserService, ResumeService, Queue listeners
│   │   └── student/     # Student profile entities, controllers & services
│   ├── pom.xml          # Dependencies (Spring Boot, pgvector, httpclient5, ONNX)
│   └── mvnw.cmd         # Maven wrapper script
├── frontend/
│   ├── src/app/
│   │   ├── core/        # Auth guards, services (student profile, matches, applications)
│   │   ├── features/
│   │   │   ├── auth/            # Login, Register, Password flows
│   │   │   ├── student/
│   │   │   │   ├── dashboard/   # StudentDashboard component & html
│   │   │   │   ├── find-jobs/   # FindJobs component & search interface
│   │   │   │   ├── profile-review/ # Multi-section Profile page
│   │   │   │   └── onboarding/  # Quick onboarding & setup
│   │   │   └── recruiter/       # Recruiter dashboard, Create job, Ranking
│   │   └── shared/      # Navbar, Footer, UI components
│   ├── angular.json     # Angular CLI configuration & production build budgets
│   └── package.json     # Frontend dependencies
└── README.md
```

---

## ⚡ Local Setup & Installation Guide

### Prerequisites
- **Java JDK 21** or higher
- **Node.js 18+** & **npm**
- **PostgreSQL 16+** with `pgvector` extension installed (`CREATE EXTENSION IF NOT EXISTS vector;`)
- **Redis Server** (optional, fallback to local memory in dev)

### 1. Backend Setup (`/backend`)
```bash
cd backend

# Configure environment variables in application.yml or environment
export DB_HOST=localhost
export DB_PORT=5432
export DB_NAME=careermatch
export DB_USER=postgres
export DB_PASSWORD=your_password
export GROQ_API_KEY=your_groq_api_key

# Build and run Spring Boot server
./mvnw.cmd spring-boot:run
```
Backend API will start at: `http://localhost:8080`

### 2. Frontend Setup (`/frontend`)
```bash
cd frontend

# Install Node dependencies
npm install

# Start Angular development server
npm start
```
Frontend App will start at: `http://localhost:4200`

---

## 📜 Summary of Pushed Features & Video Demonstrations

- **Full Student Profile Page**: 9 modular sections, sticky navigation, progress bar, inline upload modal.
- **Collapsible Sidebar**: Job boards sub-list toggle with smooth chevron animations.
- **Nexora Technologies Pvt. Ltd. Integration**: Seeded company profile and job postings in Hinjawadi, Pune with match scoring.
- **Ask AI & Apply Now**: Integrated on all job cards for immediate interactive AI coaching and job application submission.
- **Fail-Safe Profile Fallback**: Guarantees sub-second match scoring even when uploaded PDF parsing encounters unsupported formats.
