# Tomorrow's live demonstration

## Before presenting

1. Ensure your PostgreSQL service is running and `agentos` appears in pgAdmin.
2. Run `scripts/check.ps1 -IncludeMcp`. Add both model keys and run `backend/check_models.py` separately.
3. Run `scripts/start.ps1`, open `http://localhost:5173`, and sign in as `demo@agentos.local` / `AgentOS2026!`.
4. Rehearse your actual natural-language commands once; free model capacity can vary. Manual forms and the database demonstration remain functional if the provider is unavailable.
5. Keep `docs/SQL_EXAMPLES.sql` open in pgAdmin. Explain the schema and ownership constraints using Database Explorer.

## Demonstration sequence

1. Show Overview: counts, pending tasks, project progress and real audit activity.
2. Open AI Workspace. Submit: **Create a project called DBMS Demo with tasks ER diagram, SQL queries, and report.** Verify the three tasks appear under Projects/Tasks and INSERT events appear in Operation history.
3. Submit: **Show pending tasks for DBMS Demo.** Explain the JOIN and user ownership filter.
4. Submit: **Mark the task ER diagram as completed.** If matching names are ambiguous, select the intended record. Show changed counts and UPDATE audit events.
5. Submit: **How many tasks have I completed?** Compare the result with the dashboard and the task-count SQL template.
6. Create a reminder through its form with a concrete IST timestamp. Delete it, first cancelling, then confirming. Show the DELETE audit event.
7. Research **Database normalization** for DBMS Demo. Show the MCP retrieval, cited summary and saved project note.
8. In Database Explorer → SQL playground, execute the pending-task JOIN and grouped counts. Use **Test rollback** and explain why the valid project and first task also disappear when the second task violates its constraint.
9. Sign out and use `reviewer@agentos.local` / `AgentOS2026!`. Show that its dashboard and project list contain only its own records. Backend checks additionally prove a direct cross-user PATCH receives 404.
10. Return to the demo account. Show Agent Runs, planned steps, persisted results and timing. Explain OpenRouter planning, Groq summarization, read-only Wikipedia MCP, and deterministic SQL services.
11. If your browser supports speech recognition, click the microphone in AI Workspace, grant permission yourself, speak a command, and review the transcript before pressing Send. Use typed input if browser recognition or its online service is unavailable.

## Short viva answers

**Why PostgreSQL instead of chatbot memory?** Records need durable, relational data with constraints, joins, transactions and auditable updates.

**Why no ORM?** The project uses direct bound SQL, making database operations explicit and easy to demonstrate. Shared service functions keep behavior consistent between forms and agent tools.

**What makes it an agent application?** A LangGraph supervisor creates a structured tool plan, resolves references, pauses for human decisions, invokes validated tools and tracks execution state. Domain tool groups share deterministic services.

**Does the LLM write SQL?** No. It selects allowlisted operations. Backend services validate ownership and bind query parameters.

**How does multi-model routing work?** OpenRouter handles command planning/tool calling; Groq handles research summaries. Both roles are configurable. The application does not silently route failed calls to paid models.

**What does MCP do?** It standardizes communication with the bundled research server. The server exposes a read-only search tool; LangChain adapters discover and invoke it over stdio.

**Can you resume after restart?** Paused approvals use durable PostgreSQL checkpoints. Mid-execution runs are marked interrupted, so the app does not silently duplicate writes.

**What is deliberately outside this submission?** Public hosting, calendar integration, RAG, collaboration, always-on microphones and reliable background notification delivery.
