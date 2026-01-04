from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from app.api.routers.health import router as health_router
from app.api.routers.features import router as features_router
from app.api.routers.realtime import router as realtime_router
from app.api.routers.sessions import router as sessions_router

app = FastAPI()

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(health_router, prefix="/api")
app.include_router(features_router, prefix="/api")
app.include_router(realtime_router, prefix="/api")
app.include_router(sessions_router, prefix="/api")
