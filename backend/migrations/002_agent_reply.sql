-- Natural-language answer composed from confirmed tool results; spoken aloud in agent mode.
ALTER TABLE agent_runs ADD COLUMN reply text;
