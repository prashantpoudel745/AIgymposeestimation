from motor.motor_asyncio import AsyncIOMotorClient
import os
from dotenv import load_dotenv

load_dotenv()
# Check for both possible environment variable names
DATABASE_URL = os.getenv("MONGO_URI")

if not DATABASE_URL:
    # Use the previous hardcoded fallback if absolutely nothing is found in .env
    DATABASE_URL = "mongodb://localhost:27017/aigymdb"

DB_NAME = "aigym_db"

client = AsyncIOMotorClient(DATABASE_URL)
db = client[DB_NAME]

async def get_database():
    return db
