"""Laptop tools: read-only Gmail, files in allowed folders, apps, websites and system status.
The graph reaches these functions only after the user approves the step (agents.resolve_node)."""
from datetime import datetime
import email
from email.header import decode_header, make_header
from email.utils import parsedate_to_datetime
import html
import imaplib
import json
import os
from pathlib import Path
import platform
import re
import subprocess
import time
from urllib.parse import quote_plus, urlparse
import webbrowser
from fastapi import HTTPException

EMAIL_BODY_LIMIT = 2500
FILE_TEXT_LIMIT = 12000
TEXT_SUFFIXES = {'.txt','.md','.csv','.json','.log','.py','.js','.ts','.tsx','.html','.css','.sql','.xml','.yaml','.yml','.ini','.java','.c','.cpp','.go'}
READABLE_SUFFIXES = TEXT_SUFFIXES | {'.pdf', '.docx'}
SKIP_DIRS = {'node_modules','.git','.venv','venv','__pycache__','appdata','$recycle.bin'}

# ---------- Gmail ----------

def _header(value):
    try:
        return str(make_header(decode_header(value or ''))).strip()
    except Exception:
        return (value or '').strip()

def _clean(text):
    text = re.sub(r'(?is)<(script|style)\b.*?</\1>', ' ', text)
    text = html.unescape(re.sub(r'(?s)<[^>]+>', ' ', text))
    text = re.sub(r'https?://\S+', '[link]', text)
    return re.sub(r'\s+', ' ', text).strip()

def parse_message(raw):
    message = email.message_from_bytes(raw)
    plain, rich, attachments = [], [], []
    for part in message.walk():
        if part.is_multipart():
            continue
        if part.get_filename() or 'attachment' in str(part.get('Content-Disposition', '')).lower():
            attachments.append(_header(part.get_filename()) or 'attachment')
            continue
        if part.get_content_type() not in ('text/plain', 'text/html'):
            continue
        payload = part.get_payload(decode=True) or b''
        text = payload.decode(part.get_content_charset() or 'utf-8', errors='replace')
        (plain if part.get_content_type() == 'text/plain' else rich).append(text)
    body = _clean(' '.join(plain) if plain else ' '.join(rich))
    try:
        received = parsedate_to_datetime(message['Date']).isoformat()
    except Exception:
        received = message.get('Date', '')
    return {'from': _header(message['From']), 'subject': _header(message['Subject']) or '(no subject)', 'date': received,
            'body': body[:EMAIL_BODY_LIMIT] + (' …' if len(body) > EMAIL_BODY_LIMIT else ''), 'attachments': attachments}

def gmail_configured():
    return bool(os.getenv('GMAIL_ADDRESS') and os.getenv('GMAIL_APP_PASSWORD'))

def connect_gmail(address, app_password):
    """Check the App Password with Google, then save it to backend/.env (local, gitignored)."""
    from dotenv import set_key
    from .config import ROOT
    password = app_password.replace(' ', '')
    try:
        with imaplib.IMAP4_SSL('imap.gmail.com', 993, timeout=30) as box:
            box.login(address.strip(), password)
    except imaplib.IMAP4.error:
        raise HTTPException(401, 'Google refused this login. Use your full Gmail address and a 16-character App Password from myaccount.google.com/apppasswords, not your normal password.')
    except OSError:
        raise HTTPException(503, 'Could not reach Gmail. Check your internet connection.')
    for key, value in (('GMAIL_ADDRESS', address.strip()), ('GMAIL_APP_PASSWORD', password)):
        set_key(ROOT / '.env', key, value)
        os.environ[key] = value
    return {'connected': True, 'address': address.strip()}

def read_emails(count=3, unread_only=False, search=None):
    if not gmail_configured():
        raise HTTPException(503, 'Gmail is not connected yet. Click Connect Gmail and enter your Gmail address and App Password.')
    count = max(1, min(int(count or 3), 10))
    criteria = ['UNSEEN' if unread_only else 'ALL']
    if search:
        # Gmail's own search syntax, e.g. from:amazon or subject:invoice.
        criteria = ['X-GM-RAW', '"' + search.replace('"', '').replace('\\', '') + (' is:unread' if unread_only else '') + '"']
    try:
        with imaplib.IMAP4_SSL('imap.gmail.com', 993, timeout=30) as box:
            box.login(os.environ['GMAIL_ADDRESS'], os.environ['GMAIL_APP_PASSWORD'].replace(' ', ''))
            box.select('INBOX', readonly=True)
            _, found = box.uid('SEARCH', None, *criteria)
            uids = found[0].split()[-count:][::-1]
            messages = []
            for uid in uids:
                # BODY.PEEK leaves messages unread; the partial fetch caps very large mails.
                _, parts = box.uid('FETCH', uid, '(BODY.PEEK[]<0.400000>)')
                raw = next((p[1] for p in parts if isinstance(p, tuple)), None)
                if raw:
                    messages.append(parse_message(raw))
            return messages
    except imaplib.IMAP4.error:
        raise HTTPException(401, 'Gmail refused the login. GMAIL_APP_PASSWORD must be a 16-character Google App Password, not your normal password.')
    except OSError:
        raise HTTPException(503, 'Could not reach Gmail. Check your internet connection.')

# ---------- Files ----------

def file_roots():
    folders = {}
    try:
        import winreg
        with winreg.OpenKey(winreg.HKEY_CURRENT_USER, r'Software\Microsoft\Windows\CurrentVersion\Explorer\User Shell Folders') as key:
            for name, value in (('Desktop', 'Desktop'), ('Documents', 'Personal'), ('Downloads', '{374DE290-123F-4565-9164-39C4925E467B}')):
                try:
                    folders[name] = Path(os.path.expandvars(winreg.QueryValueEx(key, value)[0]))
                except OSError:
                    pass
    except ImportError:
        pass
    for name in ('Desktop', 'Documents', 'Downloads'):
        folders.setdefault(name, Path.home() / name)
    for extra in filter(None, os.getenv('AGENT_FILE_ROOTS', '').split(';')):
        folders[Path(extra).name or extra] = Path(extra)
    return {name: path.resolve() for name, path in folders.items() if path.is_dir()}

def _allowed(path):
    path = path.resolve()
    return any(path == root or root in path.parents for root in file_roots().values())

def _folder(folder):
    roots = file_roots()
    if not folder:
        return None
    first, _, rest = folder.replace('\\', '/').strip('/').partition('/')
    root = next((p for n, p in roots.items() if n.lower() == first.lower()), None)
    path = (root / rest) if root else Path(folder)
    if not path.is_absolute() or not _allowed(path) or not path.is_dir():
        raise HTTPException(404, f'I can only use folders inside {", ".join(roots)}. "{folder}" was not found there.')
    return path.resolve()

def _entry(path):
    stat = path.stat()
    return {'name': path.name, 'path': str(path), 'type': 'folder' if path.is_dir() else (path.suffix.lower().lstrip('.') or 'file'),
            'size_kb': round(stat.st_size / 1024, 1), 'modified': datetime.fromtimestamp(stat.st_mtime).astimezone().isoformat(timespec='minutes')}

def list_files(folder=None):
    if not folder:
        return [{'name': name, 'path': str(path), 'type': 'folder', 'items': sum(1 for _ in path.iterdir())} for name, path in file_roots().items()]
    entries = [p for p in _folder(folder).iterdir() if not p.name.startswith(('.', '~$')) and p.name.lower() != 'desktop.ini']
    entries.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return [_entry(p) for p in entries[:50]]

def find_files(name, limit=25):
    needle = (name or '').lower().strip()
    if not needle:
        raise HTTPException(422, 'Tell me which file name to look for.')
    suffix = Path(needle).suffix
    stem = Path(needle).stem if suffix else needle
    exact, partial, scanned = [], [], 0
    for root in file_roots().values():
        for current, dirs, files in os.walk(root):
            dirs[:] = [d for d in dirs if not d.startswith('.') and d.lower() not in SKIP_DIRS]
            for file in files:
                scanned += 1
                lower = file.lower()
                if lower == needle or Path(lower).stem == stem:
                    exact.append(Path(current) / file)
                elif stem in lower and (not suffix or lower.endswith(suffix)):
                    partial.append(Path(current) / file)
            if scanned > 60000 or len(exact) >= limit:
                break
    matches = exact or partial
    matches.sort(key=lambda p: p.stat().st_mtime, reverse=True)
    return [_entry(p) for p in matches[:limit]]

def locate_file(path=None, folder=None):
    """Return candidate files for read_file: an exact allowed path, the newest file in a folder, or name matches."""
    if path:
        path = path.replace('\\', '/').strip()
        candidates = [Path(path)] if Path(path).is_absolute() else []
        first, _, rest = path.partition('/')
        root = next((r for n, r in file_roots().items() if n.lower() == first.lower()), None)
        if root and rest:
            candidates.append(root / rest)
        if folder and not Path(path).is_absolute():
            candidates.append(_folder(folder) / path)
        for candidate in candidates:
            if candidate.is_file() and _allowed(candidate):
                return [_entry(candidate.resolve())]
        found = [f for f in find_files(Path(path).name) if Path(f['path']).suffix.lower() in READABLE_SUFFIXES]
        if folder:
            base = _folder(folder)
            found = [f for f in found if base in Path(f['path']).parents] or found
        return found
    if folder:
        newest = [f for f in list_files(folder) if Path(f['path']).suffix.lower() in READABLE_SUFFIXES]
        return newest[:1]
    raise HTTPException(422, 'Tell me which file to read.')

def read_file(path):
    file = Path(path).resolve()
    if not _allowed(file) or not file.is_file():
        raise HTTPException(404, 'That file is outside the folders AgentOS may read.')
    suffix = file.suffix.lower()
    if suffix in TEXT_SUFFIXES:
        with open(file, 'rb') as handle:
            text = handle.read(1_000_000).decode('utf-8', errors='replace')
    elif suffix == '.pdf':
        from pypdf import PdfReader
        reader = PdfReader(str(file))
        text = '\n'.join((page.extract_text() or '') for page in reader.pages[:30])
    elif suffix == '.docx':
        from docx import Document
        text = '\n'.join(p.text for p in Document(str(file)).paragraphs)
    else:
        raise HTTPException(415, f'I can read text, PDF and Word (.docx) files, not {suffix or "this type of"} files.')
    text = re.sub(r'\n{3,}', '\n\n', text).strip()
    return {**_entry(file), 'content': text[:FILE_TEXT_LIMIT] or '(The file has no readable text.)', 'truncated': len(text) > FILE_TEXT_LIMIT}

# ---------- Apps and websites ----------

APP_ALIASES = {'notepad': 'notepad.exe', 'calculator': 'calc.exe', 'calc': 'calc.exe', 'paint': 'mspaint.exe',
    'file explorer': 'explorer.exe', 'explorer': 'explorer.exe', 'files': 'explorer.exe', 'task manager': 'taskmgr.exe',
    'settings': 'ms-settings:', 'chrome': 'chrome.exe', 'google chrome': 'chrome.exe', 'edge': 'msedge.exe',
    'microsoft edge': 'msedge.exe', 'word': 'winword.exe', 'excel': 'excel.exe', 'powerpoint': 'powerpnt.exe',
    'outlook': 'olk.exe', 'camera': 'microsoft.windows.camera:', 'spotify': 'spotify:', 'store': 'ms-windows-store:',
    'command prompt': 'cmd.exe', 'terminal': 'wt.exe', 'snipping tool': 'snippingtool.exe'}
SITES = {'youtube': 'https://www.youtube.com', 'gmail': 'https://mail.google.com', 'google': 'https://www.google.com',
    'github': 'https://github.com', 'chatgpt': 'https://chatgpt.com', 'claude': 'https://claude.ai', 'linkedin': 'https://www.linkedin.com',
    'whatsapp': 'https://web.whatsapp.com', 'instagram': 'https://www.instagram.com', 'facebook': 'https://www.facebook.com',
    'twitter': 'https://x.com', 'x': 'https://x.com', 'netflix': 'https://www.netflix.com', 'amazon': 'https://www.amazon.in',
    'flipkart': 'https://www.flipkart.com', 'wikipedia': 'https://en.wikipedia.org', 'stackoverflow': 'https://stackoverflow.com',
    'google maps': 'https://maps.google.com', 'maps': 'https://maps.google.com', 'google drive': 'https://drive.google.com',
    'drive': 'https://drive.google.com', 'google calendar': 'https://calendar.google.com', 'calendar': 'https://calendar.google.com'}
SEARCH = {'youtube': 'https://www.youtube.com/results?search_query=', 'google': 'https://www.google.com/search?q=',
    'amazon': 'https://www.amazon.in/s?k=', 'wikipedia': 'https://en.wikipedia.org/w/index.php?search=',
    'github': 'https://github.com/search?q=', 'flipkart': 'https://www.flipkart.com/search?q='}

APP_NICKNAMES = {'vs code': 'visual studio code', 'vscode': 'visual studio code', 'code': 'visual studio code',
    'intellij': 'intellij idea', 'file manager': 'file explorer', 'cmd': 'command prompt', 'powershell': 'windows powershell'}
_start_apps_cache = {'at': 0.0, 'apps': []}

def start_apps():
    """Every app in the Start menu (desktop and Store apps) as (name, AppID), cached for five minutes."""
    if time.time() - _start_apps_cache['at'] > 300:
        out = subprocess.run(['powershell.exe', '-NoProfile', '-Command', 'Get-StartApps | ConvertTo-Json -Compress'],
                             capture_output=True, text=True, timeout=30, creationflags=getattr(subprocess, 'CREATE_NO_WINDOW', 0))
        data = json.loads(out.stdout or '[]')
        data = data if isinstance(data, list) else [data]
        _start_apps_cache.update(at=time.time(), apps=[(d['Name'], d['AppID']) for d in data
                                                        if d.get('Name') and 'uninstall' not in d['Name'].lower()])
    return _start_apps_cache['apps']

def find_app(name):
    wanted = re.sub(r'\s+', ' ', (name or '').lower().removesuffix('.exe')).strip()
    if not wanted:
        raise HTTPException(422, 'Tell me which app to open.')
    wanted = APP_NICKNAMES.get(wanted, wanted)
    apps = start_apps()
    for match in (lambda s: s == wanted, lambda s: s.startswith(wanted), lambda s: wanted in s):
        found = sorted((a for a in apps if match(a[0].lower())), key=lambda a: len(a[0]))
        if found:
            return {'name': found[0][0], 'target': 'shell:AppsFolder\\' + found[0][1]}
    if wanted in APP_ALIASES:
        return {'name': name.strip().title(), 'target': APP_ALIASES[wanted]}
    raise HTTPException(404, f'I could not find an app called "{name}" in your Start menu.')

def open_app(target):
    try:
        os.startfile(target)
    except OSError:
        raise HTTPException(404, 'Windows could not open that app. It may not be installed.')
    return {'opened': target.removeprefix('shell:AppsFolder\\')}

def website_url(site=None, search=None):
    key = (site or '').lower().strip().removeprefix('www.')
    if search:
        return SEARCH.get(key, SEARCH['google']) + quote_plus(search)
    if key in SITES:
        return SITES[key]
    if not key:
        raise HTTPException(422, 'Tell me which website to open.')
    url = key if '://' in key else 'https://' + key
    parsed = urlparse(url)
    if parsed.scheme in ('http', 'https') and '.' in parsed.netloc and ' ' not in parsed.netloc:
        return url
    return SEARCH['google'] + quote_plus(site)

def open_website(url):
    if urlparse(url).scheme not in ('http', 'https'):
        raise HTTPException(422, 'Only web addresses can be opened.')
    webbrowser.open(url)
    return {'opened': url}

# ---------- System ----------

def system_info():
    import psutil
    battery = psutil.sensors_battery()
    disk = psutil.disk_usage(os.getenv('SystemDrive', 'C:') + '\\')
    memory = psutil.virtual_memory()
    usage = {}
    for process in psutil.process_iter(['name', 'memory_info']):
        info = process.info
        if info.get('name') and info.get('memory_info'):
            usage[info['name']] = usage.get(info['name'], 0) + info['memory_info'].rss
    top = sorted(usage.items(), key=lambda item: item[1], reverse=True)[:8]
    return {'local_time': datetime.now().astimezone().strftime('%A %d %B %Y, %I:%M %p'), 'computer': platform.node(),
            'windows': platform.platform(terse=True),
            'battery_percent': round(battery.percent) if battery else None, 'charging': battery.power_plugged if battery else None,
            'cpu_percent': psutil.cpu_percent(interval=0.5), 'memory_used_percent': memory.percent,
            'memory_total_gb': round(memory.total / 2**30, 1), 'disk_free_gb': round(disk.free / 2**30, 1),
            'disk_total_gb': round(disk.total / 2**30, 1),
            'uptime_hours': round((datetime.now().timestamp() - psutil.boot_time()) / 3600, 1),
            'top_apps_by_memory': [{'name': name, 'memory_mb': round(rss / 2**20)} for name, rss in top]}
