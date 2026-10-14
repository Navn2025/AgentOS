"""Read-only, bundled MCP server. No database access or arbitrary URL fetching."""
import httpx
from mcp.server.fastmcp import FastMCP

mcp=FastMCP('AgentOS research')

@mcp.tool()
def search_wikipedia(topic: str) -> dict:
    """Search Wikipedia and return up to three article excerpts with source URLs."""
    topic=topic.strip()
    if not 2<=len(topic)<=200:
        raise ValueError('Topic must contain 2–200 characters')
    with httpx.Client(timeout=20,headers={'User-Agent':'AgentOSCollegeDemo/1.0 (educational research client)'}) as client:
        r=client.get('https://en.wikipedia.org/w/api.php',params={'action':'query','generator':'search','gsrsearch':topic,'gsrlimit':3,
            'prop':'extracts|info','exintro':1,'explaintext':1,'exchars':5000,'inprop':'url','format':'json'})
        r.raise_for_status()
        pages=r.json().get('query',{}).get('pages',{})
        return {'topic':topic,'source':'Wikipedia','articles':[{'title':p['title'],'url':p['fullurl'],'excerpt':p.get('extract','')}
            for p in sorted(pages.values(),key=lambda p:p.get('index',0))]}

if __name__=='__main__':
    mcp.run(transport='stdio')
