"""FastAPI endpoints for catalog search and semantic mapping."""

from __future__ import annotations

import logging
import os
import secrets
from typing import Any, Callable

from fastapi import APIRouter, Depends, Header, HTTPException, Query, Response
from pydantic import BaseModel, Field

from .service import SemanticMappingService


class SemanticMapRequest(BaseModel):
    question: str = Field(min_length=1, max_length=500)
    allowedMeasureCodes: list[str] | None = None
    allowedDimensionCodes: list[str] | None = None
    preferredTables: list[str] | None = None
    topK: int = Field(default=10, ge=1, le=50)
    includeVector: bool = True


logger = logging.getLogger(__name__)


def create_semantic_retrieval_router(
    service_factory: Callable[[], SemanticMappingService],
) -> APIRouter:
    router = APIRouter(prefix="/api/semantic-retrieval", tags=["semantic-retrieval"])

    def require_api_token(
        semantic_token: str = Header("", alias="X-InsightMind-Semantic-Token"),
    ) -> None:
        expected = os.getenv("INSIGHTMIND_SEMANTIC_API_TOKEN", "").strip()
        if not expected:
            raise HTTPException(status_code=503, detail="语义 API 未配置访问凭证")
        if not semantic_token or not secrets.compare_digest(semantic_token, expected):
            raise HTTPException(status_code=403, detail="语义 API 凭证无效")

    def service() -> SemanticMappingService:
        try:
            return service_factory()
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="业务图谱不存在") from exc
        except Exception as exc:
            logger.exception("Semantic service factory failed")
            raise HTTPException(status_code=503, detail="语义召回服务暂不可用") from exc

    @router.get("/status")
    def status(response: Response):
        response.headers["Cache-Control"] = "no-store"
        result = service().status()
        if not result.get("ok"):
            raise HTTPException(status_code=503, detail="语义召回不可用")
        return result

    @router.get("/catalog")
    def catalog(
        response: Response,
        includeValues: bool = False,
        _authorized: None = Depends(require_api_token),
    ):
        response.headers["Cache-Control"] = "no-store"
        try:
            snapshot = service().snapshot
            return snapshot.to_dict(include_values=includeValues)
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="业务图谱不存在") from exc
        except Exception as exc:
            logger.exception("Semantic catalog endpoint failed")
            raise HTTPException(status_code=503, detail="语义目录加载失败") from exc

    @router.get("/search")
    def search(
        response: Response,
        keyword: str = Query(..., min_length=1, max_length=500),
        types: str = "measure,dimension",
        limit: int = Query(20, ge=1, le=100),
        includeVector: bool = True,
        _authorized: None = Depends(require_api_token),
    ):
        response.headers["Cache-Control"] = "no-store"
        semantic_types = {
            value.strip().lower()
            for value in types.split(",")
            if value.strip().lower() in {"measure", "dimension", "value"}
        }
        if not semantic_types:
            raise HTTPException(status_code=400, detail="types 仅支持 measure/dimension/value")
        if "value" in semantic_types and len(semantic_types) > 1:
            raise HTTPException(status_code=400, detail="value 搜索需单独调用")
        try:
            result = service().search(
                keyword,
                semantic_types=semantic_types,
                top_k=limit,
                include_vector=includeVector,
            )
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="业务图谱不存在") from exc
        except Exception as exc:
            logger.exception("Semantic search endpoint failed")
            raise HTTPException(status_code=503, detail="语义检索失败") from exc
        return {
            "ok": True,
            "keyword": keyword,
            "items": [candidate.to_dict() for candidate in result.candidates],
            "vectorUsed": result.vector_used,
            "vectorDisabledReason": "unavailable" if result.vector_disabled_reason else "",
            "diagnostics": result.diagnostics,
        }

    @router.post("/map")
    def map_question(
        req: SemanticMapRequest,
        response: Response,
        _authorized: None = Depends(require_api_token),
    ):
        response.headers["Cache-Control"] = "no-store"
        question = (req.question or "").strip()
        if not question:
            raise HTTPException(status_code=400, detail="question 不能为空")
        for values, label in (
            (req.allowedMeasureCodes, "allowedMeasureCodes"),
            (req.allowedDimensionCodes, "allowedDimensionCodes"),
            (req.preferredTables, "preferredTables"),
        ):
            if values is not None and len(values) > 500:
                raise HTTPException(status_code=400, detail=f"{label} 最多 500 项")
        try:
            result = service().map(
                question,
                allowed_measure_codes=req.allowedMeasureCodes,
                allowed_dimension_codes=req.allowedDimensionCodes,
                preferred_tables=req.preferredTables,
                top_k=req.topK,
                include_vector=req.includeVector,
            )
        except FileNotFoundError as exc:
            raise HTTPException(status_code=404, detail="业务图谱不存在") from exc
        except Exception as exc:
            logger.exception("Semantic map endpoint failed")
            raise HTTPException(status_code=503, detail="语义映射失败") from exc
        return {"ok": True, **result.to_dict()}

    return router
