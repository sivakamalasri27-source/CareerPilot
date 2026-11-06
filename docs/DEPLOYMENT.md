# CareerPilot Production Deployment Guide

This guide covers deploying CareerPilot using either Docker Compose (recommended for VPS / self-hosted environments) or cloud-native platforms (Railway / Render for the backend and Vercel for the frontend).

---

## 1. Option A: Single-Command Docker Compose (VPS / Server)

### Prerequisites
- Docker Engine 24+ and Docker Compose 2.20+
- A server with at least 2 GB RAM and 1 vCPU (Ubuntu 22.04 LTS recommended)

### Steps

1. **Clone repository and configure environment**:
   ```bash
   git clone https://github.com/your-org/careerpilot.git
   cd careerpilot
   cp .env.example .env
   ```

2. **Populate your `.env` file**:
   ```env
   SPRING_PROFILES_ACTIVE=prod
   DB_PASSWORD=your_super_secure_db_password
   JWT_SECRET=your_random_64_character_hex_or_base64_string
   GEMINI_API_KEY=your_production_google_gemini_api_key
   GITHUB_TOKEN=optional_github_token_for_higher_limits
   CORS_ALLOWED_ORIGINS=https://yourdomain.com,http://localhost:3000
   VITE_API_URL=https://yourdomain.com
   ```

3. **Launch the stack**:
   ```bash
   docker-compose up -d --build
   ```

4. **Verify health**:
   - Backend Actuator: `curl http://localhost:8080/actuator/health` (should return `{"status":"UP"}`)
   - Frontend: `curl -I http://localhost:3000/health` (should return `HTTP/1.1 200 OK`)

---

## 2. Option B: Cloud Deployment (Railway + Vercel)

### Architecture
- **Database**: Managed PostgreSQL on Railway or Supabase.
- **Backend API**: Railway or Render (Java 17 / Docker).
- **Frontend SPA**: Vercel (React 18 + Vite).

---

### Step 1: Provision PostgreSQL
1. Create a new PostgreSQL database on **Railway** or **Supabase**.
2. Copy the full connection URL:
   `postgresql://postgres:password@host.railway.app:5432/railway`

---

### Step 2: Deploy Backend to Railway or Render
1. Create a new project pointing to your GitHub repository.
2. Select root directory `/backend` (or use the root `backend/Dockerfile`).
3. Set the following environment variables:
   | Variable | Value | Description |
   |---|---|---|
   | `SPRING_PROFILES_ACTIVE` | `prod` | Activates production profile |
   | `SPRING_DATASOURCE_URL` | `jdbc:postgresql://<host>:<port>/<dbname>` | JDBC connection string |
   | `SPRING_DATASOURCE_USERNAME` | `<db_user>` | Database username |
   | `SPRING_DATASOURCE_PASSWORD` | `<db_password>` | Database password |
   | `JWT_SECRET` | `<64-character secure secret>` | HMAC secret |
   | `GEMINI_API_KEY` | `<your_gemini_key>` | Google Gemini API key |
   | `GITHUB_TOKEN` | `<optional_github_token>` | Boosts rate limits to 5000/hr |
   | `CORS_ALLOWED_ORIGINS` | `https://your-frontend.vercel.app` | Allowed frontend domains |
   | `SWAGGER_ENABLED` | `false` | Disables Swagger UI in prod |
   | `PORT` | `8080` | Server listening port |

4. Deploy. Flyway will automatically run all database migrations on startup.
5. Note your backend URL (e.g., `https://careerpilot-api.up.railway.app`).

---

### Step 3: Deploy Frontend to Vercel
1. Import your GitHub repository into **Vercel**.
2. Configure project settings:
   - **Framework Preset**: Vite
   - **Root Directory**: `frontend`
   - **Build Command**: `npm run build`
   - **Output Directory**: `dist`
3. Add Environment Variable:
   - `VITE_API_URL`: `https://careerpilot-api.up.railway.app/api/v1`
4. Deploy. Vercel provides automatic SSL, CDN edge caching, and global delivery.

---

## 3. Production Verification Checklist

- [ ] Database migrations executed successfully (`flyway_schema_history` table up to date).
- [ ] Backend health check responds `{"status":"UP"}` on `/actuator/health`.
- [ ] Frontend successfully registers a new user and receives a JWT access token.
- [ ] Token refresh endpoint rotates refresh tokens cleanly without 401s.
- [ ] Run Full Audit executes without rate limiting or circuit breaker errors.
- [ ] Resume PDF upload parses sample PDF into pending suggestions.
- [ ] CORS headers permit the production frontend origin and block untrusted domains.
- [ ] Actuator metrics and Swagger UI are properly restricted or toggled off.
