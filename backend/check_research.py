from types import SimpleNamespace
from unittest.mock import patch,AsyncMock
from uuid import uuid4
from fastapi import HTTPException
from app.db import pool,transaction,query
from app.research import research_note
from app import services as s

def main():
    pool.open(wait=True);user=uuid4()
    try:
        with transaction() as c:
            c.execute('INSERT INTO users(id,email,name,password_hash) VALUES (%s,%s,%s,%s)',(user,f'{user}@research.local','Research check','unused'))
        project=s.create_project(user,{'name':'Research check'})
        source={'articles':[{'title':'Database normalization','url':'https://en.wikipedia.org/wiki/Database_normalization','excerpt':'Normalization organizes relational data.'}]}
        model=SimpleNamespace(invoke=lambda *args:SimpleNamespace(content='Normalization organizes relational data [1].'))
        with patch('app.research.get_model',return_value=model),patch('app.research.fetch_sources',new=AsyncMock(return_value=source)):
            note=research_note(user,project['id'],'Database normalization')
        assert note['sources'][0]['url']==source['articles'][0]['url']
        assert '[1]' in note['content']
        assert query('SELECT count(*) AS n FROM research_notes WHERE user_id=%s',(user,),one=True)['n']==1
        with patch('app.research.get_model',return_value=model),patch('app.research.fetch_sources',new=AsyncMock(side_effect=RuntimeError('Simulated source failure'))):
            try:
                research_note(user,project['id'],'Unavailable topic')
                raise AssertionError('Source failure claimed success')
            except HTTPException as e:assert e.status_code==502
        assert query('SELECT count(*) AS n FROM research_notes WHERE user_id=%s',(user,),one=True)['n']==1
        print('PASS: cited research persistence, audit transaction, and no saved note after source failure. Model/source payloads controlled; live MCP tested separately.')
    finally:
        with transaction() as c:
            c.execute('DELETE FROM projects WHERE user_id=%s',(user,))
            c.execute('DELETE FROM audit_logs WHERE user_id=%s',(user,))
            c.execute('DELETE FROM users WHERE id=%s',(user,))
        pool.close()

if __name__=='__main__':main()
