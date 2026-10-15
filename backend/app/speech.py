"""Speech-to-text for agent mode: Groq Whisper with filters for silence and noise hallucinations."""
import os
import re
import httpx
from fastapi import HTTPException

WHISPER_MODEL = os.getenv('WHISPER_MODEL', 'whisper-large-v3')
MAX_AUDIO_BYTES = 8_000_000
# A short natural sentence helps Whisper spell the wake word; word lists leak into transcripts of noisy audio.
PROMPT = 'Hey Agent, open YouTube and check my emails.'
# Phrases Whisper is known to invent from noise or silence.
HALLUCINATIONS = {'', 'you', 'thank you', 'thanks', 'thank you for watching', 'thanks for watching', 'bye', 'okay', 'oh',
                  'subscribe', 'please subscribe', 'like and subscribe', 'music', 'applause', 'laughter', 'silence'}

def _normal(text):
    return re.sub(r'[^a-z ]', '', text.lower()).strip()

def clean_transcript(result):
    """Keep only segments Whisper is confident contain speech; drop known hallucinations."""
    segments = result.get('segments') or []
    if segments:
        kept = [s['text'] for s in segments if s.get('no_speech_prob', 0) < 0.6 and s.get('avg_logprob', 0) > -1.0
                and s.get('compression_ratio', 1) < 2.4]
        text = ' '.join(kept)
    else:
        text = result.get('text', '')
    text = re.sub(r'\s+', ' ', text).strip()
    # Whisper can loop on noise ("Documents, Documents, Documents"): keep one copy of any word repeated 3+ times.
    text = re.sub(r'\b(\w+)(?:[\s,.]+\1\b){2,}', r'\1', text, flags=re.I)
    normal = _normal(text)
    if normal in HALLUCINATIONS or normal in _normal(PROMPT) or re.fullmatch(r'[\W_]*', text):
        return ''
    return text

async def transcribe(audio: bytes, content_type: str):
    key = os.getenv('GROQ_API_KEY', '')
    if not key:
        raise HTTPException(503, 'Add GROQ_API_KEY to backend/.env to enable voice transcription.')
    if not audio or len(audio) > MAX_AUDIO_BYTES:
        raise HTTPException(413, 'The voice clip was empty or too long.')
    suffix = 'webm' if 'webm' in content_type else 'ogg' if 'ogg' in content_type else 'wav'
    try:
        async with httpx.AsyncClient(timeout=30) as client:
            response = await client.post('https://api.groq.com/openai/v1/audio/transcriptions',
                headers={'Authorization': f'Bearer {key}'},
                data={'model': WHISPER_MODEL, 'language': 'en', 'temperature': '0', 'response_format': 'verbose_json', 'prompt': PROMPT},
                files={'file': (f'speech.{suffix}', audio, content_type or 'audio/wav')})
    except httpx.HTTPError:
        raise HTTPException(503, 'Could not reach the transcription service. Check your internet connection.')
    if response.status_code == 429:
        raise HTTPException(429, 'Voice transcription limit reached for now. Wait a moment and try again.')
    if response.status_code != 200:
        raise HTTPException(502, 'The transcription service rejected the audio.')
    return {'text': clean_transcript(response.json())}
