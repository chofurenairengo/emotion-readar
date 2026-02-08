from fastapi import FastAPI

from app.api.routers.health import router as health_router
from app.api.routers.realtime import router as realtime_router
from app.api.routers.sessions import router as sessions_router
from app.core.lifespan import lifespan
from app.middleware.cors import apply_cors


def create_app() -> FastAPI:
    app = FastAPI(lifespan=lifespan)

    apply_cors(app)

    app.include_router(health_router, prefix="/api")
    app.include_router(realtime_router, prefix="/api")
    app.include_router(sessions_router, prefix="/api")

    return app


app = create_app()
