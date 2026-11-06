# CareerPilot REST API Documentation

All CareerPilot REST endpoints are rooted at `/api/v1`. Authenticated endpoints require a standard `Authorization: Bearer <access_token>` header. Swagger/OpenAPI UI is available at `/swagger-ui.html` when enabled.

---

## 1. Authentication & Session Management

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `POST` | `/api/v1/auth/register` | Register new candidate account | No |
| `POST` | `/api/v1/auth/login` | Authenticate with email and password | No |
| `POST` | `/api/v1/auth/refresh` | Rotate refresh token and issue new access token | No |
| `POST` | `/api/v1/auth/logout` | Revoke active refresh token family | Yes |
| `GET` | `/api/v1/auth/me` | Inspect current authenticated candidate identity | Yes |

---

## 2. Profile Builder CRUD

| Method | Endpoint | Description | Auth Required |
|---|---|---|---|
| `GET` | `/api/v1/profile` | Retrieve complete candidate profile (bio, skills, projects, education) | Yes |
| `PUT` | `/api/v1/profile` | Update profile headline, about bio, github_username, and target role | Yes |
| `POST` | `/api/v1/profile/skills` | Add new claimed skill with proficiency level | Yes |
| `DELETE`| `/api/v1/profile/skills/{id}`| Remove skill from profile | Yes |
| `POST` | `/api/v1/profile/projects` | Add project with role, technologies, and bullet points | Yes |
| `PUT` | `/api/v1/profile/projects/{id}`| Update existing project details | Yes |
| `DELETE`| `/api/v1/profile/projects/{id}`| Delete project from profile | Yes |
| `POST` | `/api/v1/profile/education` | Add education milestone | Yes |
| `DELETE`| `/api/v1/profile/education/{id}`| Remove education milestone | Yes |

---

## 3. AI Agents & Multi-Agent Orchestration

| Method | Endpoint | Description | Rate Limited |
|---|---|---|---|
| `POST` | `/api/v1/agents/audit` | Execute ProfileAuditor + RecruiterSimulator in parallel | Yes |
| `POST` | `/api/v1/agents/recruiter-simulation` | Run 3-persona recruiter panel simulation | Yes |
| `POST` | `/api/v1/agents/translate-experience` | Rewrite project into STAR bullets and create PENDING suggestion | Yes |
| `POST` | `/api/v1/agents/verify-skills` | Verify claimed skills against public GitHub repositories | Yes |
| `POST` | `/api/v1/agents/skill-gap` | Compute deterministic gap vs curated role and generate learning roadmap | Yes |

---

## 4. Suggestions Inbox & Human-in-the-Loop Approval

| Method | Endpoint | Description | Status Codes |
|---|---|---|---|
| `GET` | `/api/v1/suggestions` | List suggestions (supports `?status=PENDING`, pagination) | `200` |
| `GET` | `/api/v1/suggestions/{id}` | Retrieve specific suggestion detail | `200`, `404` |
| `PATCH`| `/api/v1/suggestions/{id}/approve` | Apply suggested text to profile entity and mark APPROVED | `200`, `404`, `409` |
| `PATCH`| `/api/v1/suggestions/{id}/reject` | Mark suggestion REJECTED without modifying profile | `200`, `404`, `409` |

> [!NOTE]
> **Conflict Detection (409)**: If the target project or profile section changed after the suggestion was generated, or if the suggestion has already been decided, the endpoint returns HTTP 409 Conflict with an actionable message.

---

## 5. In-Memory Resume Ingestion

| Method | Endpoint | Description | Max Payload |
|---|---|---|---|
| `POST` | `/api/v1/resume/upload` | Multipart file upload (`multipart/form-data`). Extracts text in memory with Apache PDFBox, verifies magic bytes, and creates PENDING suggestions. | 5 MB |
| `GET` | `/api/v1/resume` | Retrieve history of uploaded resumes and processing status. | - |

---

## 6. Roles Catalog & Weekly Coach

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/roles` | List curated fresher job roles with prerequisite skill weights. |
| `GET` | `/api/v1/coach/weekly-tasks` | Get tasks for specified ISO week (defaults to current week). |
| `POST` | `/api/v1/coach/weekly-tasks/generate` | Generate 3 concrete weekly tasks (idempotent; supports `?regenerate=true`). |
| `PATCH`| `/api/v1/coach/weekly-tasks/{id}/complete` | Mark weekly task as DONE. |
| `GET` | `/api/v1/coach/streak` | Get current and longest consecutive weekly completion streak. |

---

## 7. Reports History

| Method | Endpoint | Description |
|---|---|---|
| `GET` | `/api/v1/reports` | List historical audit reports for authenticated candidate. |
| `GET` | `/api/v1/reports/{id}` | Get full breakdown and score details for a specific audit report. |

---

## 8. Standard Error Format

All error responses adhere to the RFC 7807 compliant schema:
```json
{
  "timestamp": "2026-10-16T15:30:00Z",
  "status": 409,
  "error": "Conflict",
  "message": "Target project was modified after suggestion was created. Please review latest text.",
  "path": "/api/v1/suggestions/12/approve"
}
```
In production mode (`SPRING_PROFILES_ACTIVE=prod`), stack traces are completely stripped and replaced with generic safe error responses on unhandled exceptions.
