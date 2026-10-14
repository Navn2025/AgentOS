import os
from pathlib import Path
from dotenv import load_dotenv

ROOT = Path(__file__).resolve().parents[1]
# The project's .env wins over machine-wide variables (e.g. another project's GROQ_API_KEY).
load_dotenv(ROOT / '.env', override=True)
DATABASE_URL = os.getenv('DATABASE_URL', '')
JWT_SECRET = os.getenv('JWT_SECRET', '')
FRONTEND_ORIGIN = os.getenv('FRONTEND_ORIGIN', 'http://localhost:5173')
PLANNER_PROVIDER = os.getenv('PLANNER_PROVIDER', 'openrouter')
RESEARCH_PROVIDER = os.getenv('RESEARCH_PROVIDER', 'groq')
OPENROUTER_MODEL = os.getenv('OPENROUTER_MODEL', 'nvidia/nemotron-3-super-120b-a12b:free')
GROQ_MODEL = os.getenv('GROQ_MODEL', 'qwen/qwen3.8-27b')
GROQ_PLANNER_MODEL = os.getenv('GROQ_PLANNER_MODEL', 'openai/gpt-oss-120b')
