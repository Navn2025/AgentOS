import os
from fastapi import HTTPException
from langchain_openrouter import ChatOpenRouter
from langchain_groq import ChatGroq
from .config import PLANNER_PROVIDER,RESEARCH_PROVIDER,OPENROUTER_MODEL,GROQ_MODEL,GROQ_PLANNER_MODEL

def model_name(role='planner'):
    provider=PLANNER_PROVIDER if role=='planner' else RESEARCH_PROVIDER
    if provider=='groq':
        # Planning and replies use different Groq models so each gets its own free-tier token budget.
        return GROQ_PLANNER_MODEL if role=='planner' else GROQ_MODEL
    return OPENROUTER_MODEL

def get_model(role='planner'):
    provider=PLANNER_PROVIDER if role=='planner' else RESEARCH_PROVIDER
    if provider=='openrouter':
        key=os.getenv('OPENROUTER_API_KEY','')
        if not key:
            raise HTTPException(503,'Add OPENROUTER_API_KEY to backend/.env and restart the backend to enable the planner.')
        return ChatOpenRouter(model=OPENROUTER_MODEL,api_key=key,temperature=0,max_tokens=2500,
                              timeout=60000,max_retries=1,app_title='AgentOS')
    if provider=='groq':
        key=os.getenv('GROQ_API_KEY','')
        if not key:
            raise HTTPException(503,'Add GROQ_API_KEY to backend/.env and restart the backend to enable this model role.')
        model=model_name(role)
        extra={'reasoning_effort':'low'} if model.startswith('openai/gpt-oss') else {}
        return ChatGroq(model=model,api_key=key,temperature=0,max_tokens=2500,timeout=60,max_retries=3,**extra)
    raise HTTPException(503,'Provider must be openrouter or groq')
