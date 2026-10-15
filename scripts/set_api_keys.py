"""Run locally: hidden input saves keys to the actual backend .env without printing them."""
import getpass
from pathlib import Path
from dotenv import dotenv_values, set_key

ENV = Path(__file__).resolve().parent.parent / 'backend' / '.env'

def main():
    if not ENV.exists():
        raise SystemExit('Run scripts/setup.ps1 first to configure the database and .env.')
    print(f'Saving credentials to: {ENV}')
    print('Input is hidden. Press Enter to keep an existing key unchanged.')
    for name in ('OPENROUTER_API_KEY', 'GROQ_API_KEY'):
        value = getpass.getpass(f'{name}: ').strip()
        if value:
            if any(character in value for character in ('\n', '\r', '\x00')):
                raise SystemExit('Invalid key input. Keys must be a single line.')
            set_key(ENV, name, value)
    values = dotenv_values(ENV)
    for name in ('OPENROUTER_API_KEY', 'GROQ_API_KEY'):
        print(f'{name}: {"saved" if (values.get(name) or "").strip() else "still empty"}')
    print('The backend picks up saved API keys on the next command. No keys were displayed.')

if __name__ == '__main__':
    main()
