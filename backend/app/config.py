import os
from pathlib import Path
from dotenv import load_dotenv, dotenv_values

ROOT = Path(__file__).resolve().parents[1]
load_dotenv(ROOT / '.env')
DATABASE_URL = os.getenv('DATABASE_URL', '')
JWT_SECRET = os.getenv('JWT_SECRET', '')
FRONTEND_ORIGIN = os.getenv('FRONTEND_ORIGIN', 'http://localhost:5173')
PLANNER_PROVIDER = os.getenv('PLANNER_PROVIDER', 'openrouter')
RESEARCH_PROVIDER = os.getenv('RESEARCH_PROVIDER', 'groq')
OPENROUTER_MODEL = os.getenv('OPENROUTER_MODEL', 'nvidia/nemotron-3-super-120b-a12b:free')
GROQ_MODEL = os.getenv('GROQ_MODEL', 'llama-3.3-70b-versatile')

def get_api_key(name):
    """Pick up locally saved credentials on each request; never expose their values."""
    if name not in {'OPENROUTER_API_KEY', 'GROQ_API_KEY'}:
        raise ValueError('Unsupported model credential')
    saved = (dotenv_values(ROOT / '.env').get(name) or '').strip()
    return saved or (os.getenv(name) or '').strip()
