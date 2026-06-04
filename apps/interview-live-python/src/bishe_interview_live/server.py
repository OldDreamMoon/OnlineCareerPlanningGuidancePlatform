from __future__ import annotations

import logging

from fastapi import FastAPI, WebSocket
from fastapi.responses import JSONResponse

from .config import Settings
from .session_bridge import LiveInterviewBridge


def create_app() -> FastAPI:
    settings = Settings.from_env()
    logging.basicConfig(
        level=getattr(logging, settings.log_level, logging.INFO),
        format="%(asctime)s %(levelname)s [%(name)s] %(message)s",
    )

    app = FastAPI(title="Bishe Interview Live Bridge", version="0.1.0")
    app.state.settings = settings

    @app.get(settings.health_path)
    async def healthz() -> JSONResponse:
        return JSONResponse(
            {
                "ok": True,
                "model": settings.model,
                "wsPath": settings.ws_path,
                "apiKeyConfigured": bool(settings.gemini_api_key),
            }
        )

    @app.websocket(settings.ws_path)
    async def live_ws(websocket: WebSocket) -> None:
        bridge = LiveInterviewBridge(websocket, settings)
        await bridge.run()

    return app
