# AgentOS

An AI personal workspace built for a college DBMS demonstration. React + Vite, FastAPI, LangGraph, OpenRouter, Groq, and PostgreSQL. **No ORM:** application records use psycopg, parameterized SQL, explicit transactions, and numbered `.sql` migrations.

## Run on this computer

The project database is **`agentos` on your existing PostgreSQL server, `localhost:5432`**. Refresh **Servers → PostgreSQL → Databases** in pgAdmin to see it. Connection credentials are stored only in the ignored `backend/.env`.

From the project root in PowerShell:

```powershell
.\scripts\start.ps1
```

Open **http://localhost:5173**. API documentation: **http://localhost:8000/docs**.

| Demo account | Password | Purpose |
| --- | --- | --- |
| `demo@agentos.local` | `AgentOS2026!` | Navne's sample workspace |
| `reviewer@agentos.local` | `AgentOS2026!` | Separate account for ownership isolation |

Demo records were seeded explicitly. Reseeding skips existing accounts and does not reset your changes:

```powershell
.\.venv\Scripts\python.exe backend\seed.py
```

## Enable the two models

Edit **`backend/.env` locally** and add:

```dotenv
OPENROUTER_API_KEY=your-openrouter-key
GROQ_API_KEY=your-groq-key
```

Saved API keys are read on the next model request; no restart is needed for key changes. Provider/model and database configuration changes still require a backend restart. The planner uses the dedicated **`langchain-openrouter` / `ChatOpenRouter`** integration. Research summaries use **`langchain-groq` / `ChatGroq`**.

If editor changes are not reaching the saved file, use the hidden-input helper from the project root:

```powershell
.\.venv\Scripts\python.exe scripts\set_api_keys.py
```

It writes to the exact backend `.env`, keeps existing values when you press Enter, and never prints the keys.

```dotenv
PLANNER_PROVIDER=openrouter
RESEARCH_PROVIDER=groq
OPENROUTER_MODEL=nvidia/nemotron-3-super-120b-a12b:free
GROQ_MODEL=llama-3.3-70b-versatile
```

The selected OpenRouter model advertised tool calling and zero input/output token prices in the catalog checked on 14 October 2026. Several free models tie at the lowest price. Free endpoints can have quotas or capacity errors; the app reports these failures and never silently selects a paid model. Change `OPENROUTER_MODEL` if needed. [`Nemotron model page`](https://openrouter.ai/nvidia/nemotron-3-super-120b-a12b:free), [`OpenRouter tool calling`](https://openrouter.ai/docs/guides/features/tool-calling).

MCP is provided by a bundled **read-only Wikipedia server** using stdio and `langchain-mcp-adapters`. It searches encyclopedia articles, returns excerpts and source URLs, and has no database access. MCP transport belongs to the application; a tool-calling model does not connect to MCP by itself.

Without model keys, login, CRUD, reminders, analytics, SQL templates, schema inspection, and rollback demonstrations still work. AI commands and research show an actionable missing-key error.

## Fresh installation

Prerequisites: Node.js 22.12+ (Node 24 is installed here), Python 3.12, PostgreSQL, and PowerShell 7. `uv` is preferred for the Python environment.

```powershell
.\scripts\setup.ps1 -ConfigureDatabase
```

The database helper prompts for host, port, username and password, creates an empty database when needed, and applies SQL migrations. It refuses to migrate an existing database containing unrelated tables. Password input is hidden. Setup never resets a database or seeds automatically.

Then add model keys, run the explicit seed command if desired, and start the app. Scripts start hidden local processes and health-check both servers. `scripts/stop.ps1` stops only the processes recorded by `scripts/start.ps1`; it never stops your PostgreSQL service.

## Features

- Secure registration/login with Argon2 hashes and expiring JWTs; sessions remain in browser memory and require login after refresh.
- Project creation with initial tasks in one atomic transaction; editing, archiving, and confirmed deletion.
- Task filtering, deadlines, priorities, status changes, and optimistic version checks.
- Scheduled/due reminders, dismissal, and confirmed deletion. Background notifications are outside this version.
- AI commands with validated plans, allowlisted services, ambiguous-record selection, persisted approvals, conversation history, and confirmed tool results.
- Optional click-to-talk transcription in supported browsers; the transcript remains editable and never executes automatically. The browser may send audio to its transcription service and require internet access. See [Web Speech API support](https://developer.mozilla.org/en-US/docs/Web/API/SpeechRecognition).
- Source-backed research saved to projects through Wikipedia MCP and Groq.
- Database-backed dashboard analytics, live schema/constraints, approved SQL results, audit history, and a deliberate rollback demonstration.
- Agent-run history with steps, results, failures, and timing. Interrupted execution is not automatically replayed after a restart.

## Verify before submission

```powershell
.\scripts\check.ps1 -IncludeMcp
```

Checks use temporary, distinct test accounts in the configured database and remove their generated records afterward. UI tests run **offline in jsdom with mocked HTTP**, not in a browser. They check form submission, confirmation controls, and truthful error presentation. Backend checks exercise real PostgreSQL constraints, transactions, ownership, command endpoints, and durable LangGraph checkpoints.

After both keys are configured, verify hosted tool calling and Groq authentication:

```powershell
Set-Location backend
..\.venv\Scripts\python.exe check_models.py
```

Model calls, a microphone, and an interactive browser demonstration require their respective credentials/permissions and connectivity. See [the live demonstration script](docs/DEMO.md) and [database design](docs/DATABASE.md).

## Project layout

- `frontend/`: React pages, accessible Radix dialogs, Tailwind integration, stylesheet system, and offline acceptance checks.
- `backend/app/`: authentication, REST endpoints, direct SQL services, LangGraph, provider routing, and research.
- `backend/migrations/`: versioned raw SQL. `schema_migrations` records successful applications under an advisory lock.
- `backend/mcp_server.py`: bundled read-only research tools.
- `scripts/`: Windows setup, start, stop, and checks.
- `docs/`: ER diagram, demo steps, sample SQL, and implementation notes.

## Submission package

Include source, `backend/requirements.lock`, `frontend/package-lock.json`, migrations, scripts, and docs. Exclude **`.env`, `.venv`, `.local`, `node_modules`, caches, and logs**. The `.gitignore` already excludes local secrets and generated files. There are no API keys or database passwords in tracked configuration examples.

This version targets a local college demonstration. Public deployment, shared workspaces, calendar integration, RAG, persistent background reminders, and autonomous scheduled agents remain future extensions.
