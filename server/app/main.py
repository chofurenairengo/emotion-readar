from fastapi import FastAPI

from app.api.routers.chat import router as chat_router
from app.api.routers.health import router as health_router
from app.middleware.cors import apply_cors


def create_app() -> FastAPI:
    app = FastAPI()

    apply_cors(app)

    app.include_router(health_router, prefix="/api")
    app.include_router(chat_router, prefix="/api")

    return app


app = create_app()
