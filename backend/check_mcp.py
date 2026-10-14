import asyncio
from app.research import fetch_sources

if __name__=='__main__':
    result=asyncio.run(fetch_sources('Database normalization'))
    assert result['articles'],result
    assert all(a['url'].startswith('https://en.wikipedia.org/') for a in result['articles'])
    print('PASS: real MCP stdio discovery, tool invocation, Wikipedia retrieval and source URLs.')
    print('Articles:', ', '.join(a['title'] for a in result['articles']))
