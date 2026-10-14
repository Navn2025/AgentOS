"""Build a source-only submission archive from an explicit allowlist."""
from pathlib import Path
import zipfile

ROOT=Path(__file__).resolve().parent.parent
OUTPUT=ROOT/'output'/'AgentOS_Submission.zip'

def main():
    paths=[ROOT/'README.md',ROOT/'.gitignore']
    for folder in ('backend/app','backend/migrations','frontend/src','frontend/tests','docs','scripts'):
        paths.extend(p for p in (ROOT/folder).rglob('*') if p.is_file() and p.suffix in {'.py','.sql','.tsx','.ts','.css','.ps1','.md'})
    paths.extend(p for p in (ROOT/'backend').glob('*') if p.is_file() and (p.suffix in {'.py','.txt','.lock'} or p.name=='.env.example'))
    paths.extend(ROOT/'frontend'/name for name in ('package.json','package-lock.json','index.html','tsconfig.json','vite.config.ts','vitest.config.ts'))
    OUTPUT.parent.mkdir(exist_ok=True)
    with zipfile.ZipFile(OUTPUT,'w',zipfile.ZIP_DEFLATED) as z:
        for p in sorted(set(paths)):
            relative=p.relative_to(ROOT)
            assert '.env' not in relative.parts and not set(relative.parts)&{'.venv','.local','node_modules','__pycache__'}
            z.write(p,relative.as_posix())
    with zipfile.ZipFile(OUTPUT) as z:
        assert z.testzip() is None
        assert 'backend/.env' not in z.namelist()
        assert 'frontend/package-lock.json' in z.namelist()
        assert 'backend/requirements.lock' in z.namelist()
        print(f'PASS: source-only archive validated, {len(z.namelist())} files. No .env, dependencies, or local data included.')
    print(OUTPUT)

if __name__=='__main__':main()
