from datetime import datetime, timedelta, timezone
from uuid import UUID, uuid4
import jwt
from fastapi import Depends, HTTPException
from fastapi.security import HTTPAuthorizationCredentials, HTTPBearer
from pwdlib import PasswordHash
from psycopg.errors import UniqueViolation
from .config import JWT_SECRET
from .db import query, transaction

hashing = PasswordHash.recommended()
bearer = HTTPBearer(auto_error=False)

def public_user(row):
    return {k:row[k] for k in ('id','email','name','created_at')}

def register(data):
    try:
        with transaction() as c:
            row=c.execute('INSERT INTO users(id,email,name,password_hash) VALUES (%s,%s,%s,%s) RETURNING *',
                          (uuid4(),data.email.strip().lower(),data.name.strip(),hashing.hash(data.password))).fetchone()
    except UniqueViolation:
        raise HTTPException(409,'An account with this email already exists')
    return session(row)

def session(row):
    token=jwt.encode({'sub':str(row['id']),'exp':datetime.now(timezone.utc)+timedelta(hours=8)}, JWT_SECRET, algorithm='HS256')
    return {'access_token':token,'token_type':'bearer','user':public_user(row)}

def login(data):
    row=query('SELECT * FROM users WHERE email=%s',(data.email.strip().lower(),),one=True)
    # Perform a hash verification even when the email does not exist.
    target=row['password_hash'] if row else DUMMY_HASH
    if not hashing.verify(data.password,target) or not row:
        raise HTTPException(401,'Incorrect email or password')
    return session(row)

DUMMY_HASH=hashing.hash('unused-local-dummy-password')

def current_user(credentials: HTTPAuthorizationCredentials | None = Depends(bearer)):
    if not credentials:
        raise HTTPException(401,'Please log in')
    try:
        payload=jwt.decode(credentials.credentials,JWT_SECRET,algorithms=['HS256'])
        user=UUID(payload['sub'])
    except (jwt.PyJWTError, ValueError, KeyError):
        raise HTTPException(401,'Your session expired. Please log in again.')
    row=query('SELECT id,email,name,created_at FROM users WHERE id=%s',(user,),one=True)
    if not row:
        raise HTTPException(401,'Account not found')
    return row
