"""Check hot loading and hidden-input saving with synthetic credentials in temp files."""
import importlib.util
import os
from pathlib import Path
from tempfile import TemporaryDirectory
from unittest.mock import patch
from dotenv import dotenv_values
from app import config

def main():
    with TemporaryDirectory() as temporary:
        root = Path(temporary)
        env = root / '.env'
        env.write_text("OPENROUTER_API_KEY=''\nGROQ_API_KEY=''\nDATABASE_URL=preserve-this-value\n", encoding='utf-8')
        with patch.object(config, 'ROOT', root), patch.dict(os.environ, {'OPENROUTER_API_KEY':'', 'GROQ_API_KEY':''}):
            assert not config.get_api_key('OPENROUTER_API_KEY')
            env.write_text('OPENROUTER_API_KEY=synthetic-key-one\nGROQ_API_KEY=synthetic-key-two\nDATABASE_URL=preserve-this-value\n', encoding='utf-8')
            assert config.get_api_key('OPENROUTER_API_KEY') == 'synthetic-key-one'
            env.write_text('OPENROUTER_API_KEY=synthetic-key-updated\nGROQ_API_KEY=synthetic-key-two\nDATABASE_URL=preserve-this-value\n', encoding='utf-8')
            assert config.get_api_key('OPENROUTER_API_KEY') == 'synthetic-key-updated'
        helper_path = Path(__file__).resolve().parents[1] / 'scripts' / 'set_api_keys.py'
        spec = importlib.util.spec_from_file_location('local_key_helper', helper_path)
        helper = importlib.util.module_from_spec(spec)
        spec.loader.exec_module(helper)
        with patch.object(helper, 'ENV', env), patch.object(helper.getpass, 'getpass', side_effect=['synthetic-replacement', '']), patch('builtins.print'):
            helper.main()
        values = dotenv_values(env)
        assert values['OPENROUTER_API_KEY'] == 'synthetic-replacement'
        assert values['GROQ_API_KEY'] == 'synthetic-key-two'
        assert values['DATABASE_URL'] == 'preserve-this-value'
    print('PASS: empty inherited variables, saved-key hot reload, secure helper, retained existing credentials, and unchanged database settings. Synthetic keys only.')

if __name__ == '__main__':
    main()
