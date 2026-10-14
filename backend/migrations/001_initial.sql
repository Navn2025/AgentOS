CREATE TABLE users (
    id uuid PRIMARY KEY,
    email text NOT NULL UNIQUE,
    name text NOT NULL,
    password_hash text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE projects (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    name text NOT NULL CHECK (length(trim(name)) BETWEEN 1 AND 200),
    description text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'active' CHECK (status IN ('active','completed','archived')),
    version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id)
);
CREATE TABLE tasks (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    project_id uuid NOT NULL,
    title text NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 300),
    description text NOT NULL DEFAULT '',
    status text NOT NULL DEFAULT 'pending' CHECK (status IN ('pending','in_progress','completed')),
    priority text NOT NULL DEFAULT 'medium' CHECK (priority IN ('low','medium','high')),
    due_at timestamptz,
    version integer NOT NULL DEFAULT 1,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id),
    FOREIGN KEY (project_id, user_id) REFERENCES projects(id, user_id) ON DELETE CASCADE
);
CREATE TABLE reminders (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    task_id uuid,
    title text NOT NULL CHECK (length(trim(title)) BETWEEN 1 AND 300),
    remind_at timestamptz NOT NULL,
    state text NOT NULL DEFAULT 'scheduled' CHECK (state IN ('scheduled','dismissed')),
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (task_id, user_id) REFERENCES tasks(id, user_id) ON DELETE CASCADE
);
CREATE TABLE conversations (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    title text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    UNIQUE (id, user_id)
);
CREATE TABLE messages (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    conversation_id uuid NOT NULL,
    role text NOT NULL CHECK (role IN ('user','assistant')),
    content text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (conversation_id, user_id) REFERENCES conversations(id, user_id) ON DELETE CASCADE
);
CREATE TABLE agent_runs (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    conversation_id uuid NOT NULL,
    input text NOT NULL,
    status text NOT NULL DEFAULT 'queued' CHECK (status IN ('queued','planning','executing','awaiting_input','completed','failed','cancelled','interrupted')),
    steps jsonb NOT NULL DEFAULT '[]',
    results jsonb NOT NULL DEFAULT '[]',
    question jsonb,
    error text,
    started_at timestamptz NOT NULL DEFAULT now(),
    finished_at timestamptz,
    FOREIGN KEY (conversation_id, user_id) REFERENCES conversations(id, user_id) ON DELETE CASCADE
);
CREATE TABLE audit_logs (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    entity_type text NOT NULL,
    entity_id uuid,
    action text NOT NULL,
    detail jsonb NOT NULL DEFAULT '{}',
    created_at timestamptz NOT NULL DEFAULT now()
);
CREATE TABLE research_notes (
    id uuid PRIMARY KEY,
    user_id uuid NOT NULL REFERENCES users(id),
    project_id uuid NOT NULL,
    title text NOT NULL,
    content text NOT NULL,
    sources jsonb NOT NULL DEFAULT '[]',
    created_at timestamptz NOT NULL DEFAULT now(),
    FOREIGN KEY (project_id, user_id) REFERENCES projects(id, user_id) ON DELETE CASCADE
);
CREATE INDEX projects_owner_status ON projects(user_id, status);
CREATE INDEX tasks_owner_status_due ON tasks(user_id, status, due_at);
CREATE INDEX tasks_project ON tasks(project_id);
CREATE INDEX reminders_owner_due ON reminders(user_id, remind_at);
CREATE INDEX messages_conversation ON messages(conversation_id, created_at);
CREATE INDEX runs_owner_started ON agent_runs(user_id, started_at DESC);
CREATE INDEX audit_owner_created ON audit_logs(user_id, created_at DESC);
CREATE INDEX notes_project ON research_notes(project_id);
CREATE VIEW pending_task_summary AS
SELECT t.user_id, p.name AS project, t.title, t.priority, t.due_at
FROM tasks t JOIN projects p ON p.id=t.project_id AND p.user_id=t.user_id
WHERE t.status <> 'completed';
