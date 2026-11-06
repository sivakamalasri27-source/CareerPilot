# CareerPilot AI Agents & Prompt Specifications

CareerPilot employs 7 specialized AI agents orchestrated in parallel to provide rigorous, actionable profile reviews for fresher engineers.

---

## 1. Safety & Injection Defense Protocol

All agents adhere to three non-negotiable architectural rules:
1. **Delimiter Sandboxing**: Every dynamic user field (bio, headline, project bullets, coursework, resume text) is enclosed in strict structural delimiters:
   ```text
   <<<PROFILE_DATA_START>>>
   ... user content strictly treated as untrusted text ...
   <<<PROFILE_DATA_END>>>
   ```
2. **Data-Only Rule**: System prompts explicitly declare that instructions inside the delimiters must NEVER be executed as prompt instructions.
3. **No Fabrication**: If metrics, user numbers, or results are absent from user data, agents must **never** invent or hallucinate numbers. Agents are instructed to flag missing metrics as `clarifying_questions` and use neutral phrasing.

---

## 2. Agent Catalog & Details

### 2.1 ProfileAuditorAgent
- **Versioned Prompt**: `classpath:prompts/auditor.v1.txt`
- **Purpose**: Evaluates candidate readiness across four pillars: Headline, About/Summary, Project Bullets, and Skills vs target role expectations.
- **Output Schema**:
  ```json
  {
    "overallScore": 78,
    "sectionScores": {
      "HEADLINE": 80,
      "ABOUT": 70,
      "PROJECTS": 75,
      "SKILLS": 85
    },
    "weaknesses": [
      {
        "section": "PROJECTS",
        "weakText": "Worked on backend features for e-commerce website.",
        "reason": "Lacks specific technologies, architectural pattern, and measurable outcomes."
      }
    ],
    "actionableFeedback": "Quantify throughput and specify frameworks utilized in your backend project."
  }
  ```

---

### 2.2 RecruiterSimulatorAgent
- **Versioned Prompt**: `classpath:prompts/recruiter.v1.txt`
- **Purpose**: Simulates 3 hiring perspectives simultaneously to give candidates 360° visibility:
  1. **MNC HR Specialist**: Filters for keywords, degree, clarity, and policy alignment.
  2. **Startup Founder**: Filters for high agency, product ownership, and speed of shipping.
  3. **Technical Lead**: Filters for code quality, system design instincts, tools, and technical depth.
- **Output Schema**:
  ```json
  {
    "personas": [
      {
        "name": "Enterprise Talent Acquisition (MNC HR)",
        "personaType": "MNC_HR",
        "decision": "MAYBE",
        "firstImpression": "Clear layout, good educational credentials, but lacks standardized skill certifications.",
        "strengths": ["Recognized university background", "Relevant coursework"],
        "redFlags": ["GitHub profile lacks activity link", "Project bullets lack business impact metrics"]
      },
      {
        "name": "Early-Stage Startup Founder",
        "personaType": "STARTUP_FOUNDER",
        "decision": "YES",
        "firstImpression": "Shows initiative with full-stack personal projects and deployed endpoints.",
        "strengths": ["End-to-end project ownership", "Hands-on with modern stack"],
        "redFlags": ["Code repos lack tests and automated deployment"]
      },
      {
        "name": "Senior Technical Lead",
        "personaType": "TECH_LEAD",
        "decision": "MAYBE",
        "firstImpression": "Good choice of frameworks (Spring Boot + React), but needs deeper concurrency evidence.",
        "strengths": ["Clean separation of concerns", "RESTful design"],
        "redFlags": ["No automated unit test coverage mentioned in bullets"]
      }
    ]
  }
  ```

---

### 2.3 ExperienceTranslatorAgent
- **Versioned Prompt**: `classpath:prompts/translator.v1.txt`
- **Purpose**: Translates conversational or academic project descriptions into industry-standard STAR/XYZ achievement bullets (`Action Verb + Task/Scope + Technologies + Measurable Impact`).
- **Safety**: Max 8 bullets, max 300 characters per bullet. Emits `clarifying_questions` when metrics are missing instead of hallucinating percentages or user counts.
- **Output Schema**:
  ```json
  {
    "bullets": [
      "Architected pub-sub message pipeline using Apache Kafka and Spring Boot, reducing event latency by 35%.",
      "Designed PostgreSQL indexing strategy to optimize slow query bottlenecks across high-frequency tables."
    ],
    "clarifyingQuestions": [
      "What was the approximate number of active users or throughput handled by the service?",
      "Were any automated integration tests or CI/CD pipelines configured for this project?"
    ],
    "rationale": "Replaced passive phrasing with strong action verbs and highlighted distributed systems technologies."
  }
  ```

---

### 2.4 ProofOfSkillAgent (+ GitHubTool)
- **Versioned Prompt**: `classpath:prompts/proof.v1.txt`
- **Purpose**: Audits candidate-claimed skills against actual public GitHub repositories.
- **Deterministic Java Phase**:
  1. Calls GitHub REST API to fetch public repos (excluding forks by default).
  2. Inspects languages, repository names, topics, and descriptions.
  3. Evaluates evidence rules:
     - `VERIFIED`: Multiple relevant repositories or strong repository evidence.
     - `PARTIAL`: Single reference or brief mention.
     - `UNVERIFIED`: Zero repository evidence found.
- **LLM Synthesis Phase**:
  - Writes concise, verifiable evidence explanations based strictly on the matched repos. Never invents nonexistent commits or repositories.

---

### 2.5 ResumeParserAgent
- **Versioned Prompt**: `classpath:prompts/resume_parser.v1.txt`
- **Purpose**: Parses raw extracted PDF text (retrieved in-memory via Apache PDFBox) into structured profile entities.
- **Output Schema**:
  ```json
  {
    "headline": "Full Stack Java Engineer | Spring Boot & React",
    "about": "Aspiring engineer with hands-on experience developing microservices...",
    "skills": ["Java", "Spring Boot", "Docker", "PostgreSQL", "React"],
    "projects": [
      {
        "title": "E-Commerce Microservices",
        "role": "Backend Engineer",
        "technologies": "Java, Spring Cloud, Docker",
        "bullets": ["Implemented JWT authentication service with refresh token rotation."]
      }
    ],
    "education": [
      {
        "institution": "State University",
        "degree": "B.Tech Computer Science",
        "graduationYear": 2026,
        "gradeOrGpa": "3.8/4.0"
      }
    ]
  }
  ```
- **Flow**: Parsed items are saved as `PENDING` suggestions (`AgentSuggestion`), requiring explicit user review and approval before mutating candidate profile data.

---

### 2.6 SkillGapAgent
- **Versioned Prompt**: `classpath:prompts/skillgap.v1.txt`
- **Curated Role Catalog**: Compares candidate profile against curated target roles (e.g., `Java Backend Developer`, `Full Stack Java Developer`, `Java + AI Engineer`).
- **Deterministic Matcher**:
  - Calculates `matchPercentage`, `matchedSkills`, `missingSkills`, and `weakSkills` (claimed but UNVERIFIED) using Java set arithmetic and skill weights.
- **LLM Roadmap Synthesis**:
  - Generates a prioritized 4-step roadmap for missing skills, including *Why It Matters*, *What To Learn*, and *One Concrete Project Idea*. Never invents fabricated external courses or guaranteed salary promises.

---

### 2.7 WeeklyCoachAgent
- **Versioned Prompt**: `classpath:prompts/coach.v1.txt`
- **Purpose**: Generates exactly 3 small, achievable, measurable tasks per week based on the candidate's latest audit report and skill gaps.
- **Idempotency**: Requests made in the same ISO calendar week return the existing 3 tasks unless `regenerate=true` is explicitly requested.
- **Consistency Streak**: Calculates consecutive weeks where at least 2 of the 3 weekly tasks were completed (`TaskStatus.DONE`).
