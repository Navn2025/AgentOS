from datetime import datetime
from typing import Literal
from uuid import UUID
from pydantic import BaseModel, Field, field_validator

class Register(BaseModel):
    email: str = Field(min_length=3, max_length=254)
    password: str = Field(min_length=8, max_length=200)
    name: str = Field(min_length=1, max_length=100)

    @field_validator('email')
    @classmethod
    def email_format(cls, value):
        value = value.strip().lower()
        if '@' not in value or '.' not in value.split('@')[-1]:
            raise ValueError('Enter a valid email address')
        return value

class Login(BaseModel):
    email: str
    password: str

class ProjectCreate(BaseModel):
    name: str = Field(min_length=1, max_length=200)
    description: str = Field(default='', max_length=10000)
    tasks: list[str] = Field(default_factory=list, max_length=30)

class ProjectUpdate(BaseModel):
    name: str | None = Field(default=None, min_length=1, max_length=200)
    description: str | None = Field(default=None, max_length=10000)
    status: Literal['active', 'completed', 'archived'] | None = None
    version: int = Field(ge=1)

class TaskCreate(BaseModel):
    project_id: UUID
    title: str = Field(min_length=1, max_length=300)
    description: str = Field(default='', max_length=10000)
    priority: Literal['low','medium','high'] = 'medium'
    due_at: datetime | None = None

    @field_validator('due_at')
    @classmethod
    def aware_date(cls, value):
        if value and value.tzinfo is None:
            raise ValueError('Include a timezone in due_at')
        return value

class TaskUpdate(BaseModel):
    title: str | None = Field(default=None, min_length=1, max_length=300)
    description: str | None = Field(default=None, max_length=10000)
    status: Literal['pending','in_progress','completed'] | None = None
    priority: Literal['low','medium','high'] | None = None
    due_at: datetime | None = None
    version: int = Field(ge=1)

    _aware_date = field_validator('due_at')(TaskCreate.aware_date.__func__)

class ReminderCreate(BaseModel):
    title: str = Field(min_length=1, max_length=300)
    task_id: UUID | None = None
    remind_at: datetime

    _aware_date = field_validator('remind_at')(TaskCreate.aware_date.__func__)

class CommandInput(BaseModel):
    input: str = Field(min_length=1, max_length=4000)
    conversation_id: UUID | None = None

class ResumeInput(BaseModel):
    approved: bool | None = None
    # A record UUID or a file option index; the endpoint checks it against the offered options.
    selected_id: str | None = Field(default=None, max_length=64)

class GmailConnect(BaseModel):
    address: str = Field(min_length=5, max_length=254)
    app_password: str = Field(min_length=16, max_length=40)

class ResearchInput(BaseModel):
    project_id: UUID
    topic: str = Field(min_length=2, max_length=200)
