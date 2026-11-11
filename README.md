# AI StudyMate

AI StudyMate is a full-stack academic planner built with React, Spring Boot, MySQL, JWT authentication, and the Gemini API.

## Requirements

- Java 21 or later
- Maven 3.9 or later
- Node.js 20 or later
- Docker with Docker Compose
- Docker Compose for the local AI model and database

## Run locally

On Windows, start the API and frontend together (using the local H2 database, no Docker required):

```powershell
powershell -ExecutionPolicy Bypass -File .\setup-local-ai.ps1
powershell -ExecutionPolicy Bypass -File .\start-local.ps1
```

Run the setup command once to download the free local llamafile runtime and Qwen 0.5B model (about 860 MB total). Then run the start command. Keep the terminal open while using the app at `http://127.0.0.1:5174`. The start script launches the local AI, API, and frontend when they are not already running and writes logs under `backend/target/`.

1. Start the database from the project root:

   ```sh
   docker compose up -d mysql ollama
   ```

   Download the small local model once (about 1.3 GB):

   ```sh
   docker exec studymate-ollama ollama pull llama3.2:1b
   ```

   References: [Ollama downloads](https://ollama.com/download), [local API compatibility](https://docs.ollama.com/api/openai-compatibility), and the [Llama 3.2 model](https://ollama.com/library/llama3.2).

2. Start the API in a second terminal:

   ```sh
   cd backend
   mvn spring-boot:run
   ```

   The API is available at `http://localhost:8080`. The development database credentials are in `compose.yaml`. AI uses the local Ollama model by default and requires no API key or paid account. The model runs on your computer; first responses may take longer while it loads. To use Gemini instead, copy `backend/.env.example` to `backend/.env`, set `AI_PROVIDER=gemini` and `GEMINI_API_KEY`, then restart Spring Boot. The local `.env` is ignored by Git and loaded automatically from either the workspace root or `backend/`. Set `JWT_SECRET` to a long random value before deployment.

   If Docker/MySQL is not available, run the API with its local H2 database instead:

   ```sh
   mvn spring-boot:run -Dspring-boot.run.profiles=local
   ```

   The local profile stores data under `backend/target/studymate-local`; use the regular command above to connect to MySQL. It replaces only the database: AI requests still need Ollama running with the configured model, or Gemini configured with an API key as described above.

3. Start the web app from the project root in another terminal:

   ```sh
   npm install
   npm run dev
   ```

   Open `http://localhost:5173`. Vite proxies `/api` requests to the local Spring Boot API.

MySQL tables are created and updated by Hibernate for local development. Do not use `ddl-auto=update` or the sample credentials for a production deployment; use managed secrets and a versioned migration tool before deploying.

## API overview

All routes below, except register and login, require `Authorization: Bearer <token>`.

| Method | Endpoint | Purpose |
| --- | --- | --- |
| POST | `/api/auth/register` | Create a student account and receive a JWT |
| POST | `/api/auth/login` | Sign in and receive a JWT |
| GET | `/api/auth/me` | Return the signed-in student |
| POST | `/api/auth/logout` | Client logout acknowledgement |
| GET, POST | `/api/subjects` | List or create subjects |
| PUT, DELETE | `/api/subjects/{id}` | Update or delete a subject |
| GET, POST | `/api/tasks` | List or create study tasks |
| PUT, DELETE | `/api/tasks/{id}` | Update or delete a task |
| GET, POST | `/api/notes` | List or create notes |
| PUT, DELETE | `/api/notes/{id}` | Update or delete a note |
| GET | `/api/dashboard` | Get task counts, upcoming work, and subject progress |
| POST | `/api/ai/ask` | Ask for an explanation, summary, answer, or study tips |
| POST | `/api/ai/plan` | Generate and save a personalized study plan |
| GET | `/api/ai/plans` | List saved study plans |

Every subject, task, note, and plan is scoped to the authenticated student. Passwords are stored with BCrypt. Gemini keys stay on the backend. AI runs locally by default; set `AI_PROVIDER=gemini` only if you want to use the hosted Gemini API.

## Checks

```sh
npm run build
mvn -f backend/pom.xml test
```

## Environment variables

| Variable | Default | Purpose |
| --- | --- | --- |
| `DB_URL` | Local MySQL URL | JDBC connection URL |
| `DB_USERNAME` | `studymate` | MySQL user |
| `DB_PASSWORD` | `studymate_dev` | MySQL password |
| `JWT_SECRET` | Development-only value | HMAC signing secret; use a strong secret in deployment |
| `JWT_EXPIRATION_MS` | `86400000` | JWT lifetime |
| `CORS_ALLOWED_ORIGIN` | `http://localhost:5173` | Allowed frontend origin |
| `AI_PROVIDER` | `ollama` | `ollama` (local) or `gemini` (hosted) |
| `OLLAMA_BASE_URL` | `http://localhost:11434` | Local Ollama server |
| `OLLAMA_MODEL` | `llama3.2:1b` | Local model name |
| `GEMINI_API_KEY` | Empty | Gemini API key |
| `GEMINI_MODEL` | `gemini-2.5-flash` | Gemini model ID |
