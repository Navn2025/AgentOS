"""Optional live provider smoke check. Sends only synthetic text, never user records."""
from dotenv import dotenv_values
from app.config import ROOT
from app.providers import get_model
from app.agents import Plan

def main():
    config=dotenv_values(ROOT/'.env')
    if not config.get('OPENROUTER_API_KEY') or not config.get('GROQ_API_KEY'):
        raise SystemExit('NOT RUN: add both API keys to backend/.env first. No hosted model calls were made.')
    plan=get_model('planner').with_structured_output(Plan,method='function_calling').invoke([
        ('system','Return one step with action=list_projects, no IDs or extra fields.'),('human','List my projects.')])
    assert len(plan.steps)==1 and plan.steps[0].action=='list_projects'
    print('PASS: OpenRouter authenticated, structured function/tool calling verified.')
    response=get_model('research').invoke([('human','Reply with only the word READY.')])
    assert response.content
    print('PASS: Groq authenticated and returned a model response.')

if __name__=='__main__':main()
