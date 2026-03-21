from pydantic import BaseModel, EmailStr, Field, ConfigDict, BeforeValidator, PlainSerializer, WithJsonSchema
from typing import Optional, List, Any, Annotated
from datetime import datetime
from bson import ObjectId

def validate_object_id(v: Any) -> ObjectId:
    if isinstance(v, ObjectId):
        return v
    if ObjectId.is_valid(v):
        return ObjectId(v)
    raise ValueError("Invalid ObjectId")

PyObjectId = Annotated[
    Any,
    BeforeValidator(validate_object_id),
    PlainSerializer(lambda x: str(x), when_used='json'),
    WithJsonSchema({'type': 'string'}),
]

class UserBase(BaseModel):
    email: EmailStr
    full_name: Optional[str] = None

class UserCreate(UserBase):
    password: str

class LoginRequest(BaseModel):
    email: EmailStr
    password: str

class UserInDB(UserBase):
    hashed_password: str
    created_at: datetime = Field(default_factory=datetime.utcnow)

class UserOut(UserBase):
    id: Optional[PyObjectId] = Field(alias="_id", default=None)
    
    model_config = ConfigDict(
        populate_by_name=True,
        arbitrary_types_allowed=True
    )

class ExerciseRecord(BaseModel):
    user_id: str
    exercise_type: str
    side: str
    reps: int
    detection_rate: float
    video_url: Optional[str] = None
    processed_video_url: Optional[str] = None
    raw_video_url: Optional[str] = None
    original_video_url: Optional[str] = None
    cloudinary_public_id: Optional[str] = None
    processed_cloudinary_public_id: Optional[str] = None
    raw_cloudinary_public_id: Optional[str] = None
    original_cloudinary_public_id: Optional[str] = None
    raw_video_size_bytes: Optional[int] = None
    processed_video_size_bytes: Optional[int] = None
    timestamp: datetime = Field(default_factory=datetime.utcnow)

class ExerciseRecordRequest(BaseModel):
    exercise_type: str
    side: str
    reps: int
    detection_rate: float

class Token(BaseModel):
    access_token: str
    token_type: str

class TokenData(BaseModel):
    email: Optional[str] = None
