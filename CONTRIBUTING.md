# Contributing to CareerPilot

Thank you for your interest in contributing to CareerPilot! We welcome pull requests, bug reports, feature suggestions, and documentation improvements.

---

## Code of Conduct

Please be respectful, collaborative, and constructive when opening issues, reviewing PRs, and engaging in discussions.

---

## Development Setup

### Prerequisites
- **JDK 17** or higher
- **Node.js 20** or higher
- **Docker & Docker Compose** (optional for local full-stack run, required for Testcontainers integration tests)
- **PostgreSQL 16** (if running locally without Docker)

### Running Locally

1. **Clone the repository**:
   ```bash
   git clone https://github.com/your-org/careerpilot.git
   cd careerpilot
   ```

2. **Configure environment variables**:
   ```bash
   cp .env.example .env
   ```

3. **Backend Development**:
   ```bash
   cd backend
   ./mvnw clean install
   ./mvnw spring-boot:run
   ```

4. **Frontend Development**:
   ```bash
   cd frontend
   npm ci
   npm run dev
   ```

5. **Full Stack with Docker Compose**:
   ```bash
   docker-compose up --build
   ```

---

## Quality Standards & Guidelines

1. **Testing**:
   - Every new service or agent capability must include unit tests and integration tests.
   - We target **70%+ code coverage** on service and agent packages. JaCoCo is enforced in Maven verify.
   - Run tests before pushing:
     - Backend: `./mvnw clean verify`
     - Frontend: `npm test` & `npm run build`

2. **AI & Prompt Safety**:
   - All prompts must be versioned in `backend/src/main/resources/prompts/`.
   - Never pass raw user inputs into system prompts without strict delimiters (`<<<...>>>`).
   - Treat user data strictly as untrusted data.
   - Implement deterministic fallback logic whenever LLM output fails schema validation or parsing.

3. **Commit Messages**:
   Follow Conventional Commits style:
   - `feat(...)`: New feature or agent capability
   - `fix(...)`: Bug fix
   - `test(...)`: Adding or updating tests
   - `docs(...)`: Documentation changes
   - `refactor(...)`: Code refactoring without behavior change

---

## Submitting Pull Requests

1. Fork the repo and create a feature branch (`git checkout -b feat/your-feature-name`).
2. Make your changes adhering to code style and test coverage standards.
3. Ensure all tests pass:
   ```bash
   # Backend
   cd backend && ./mvnw verify
   # Frontend
   cd frontend && npm test && npm run build
   ```
4. Push to your branch and open a PR against `main`.
5. Ensure all GitHub Actions CI checks pass.
