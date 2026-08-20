"""Shared semantic catalog retrieval and natural-language mapping."""

from .models import (
    CatalogItem,
    CatalogSnapshot,
    CatalogValue,
    RecallCandidate,
    SemanticMatchResult,
    ValueBinding,
)
from .service import (
    SemanticMappingConfig,
    SemanticMappingService,
    get_semantic_mapping_service,
    reset_semantic_mapping_registry,
)

__all__ = [
    "CatalogItem",
    "CatalogSnapshot",
    "CatalogValue",
    "RecallCandidate",
    "SemanticMappingConfig",
    "SemanticMappingService",
    "SemanticMatchResult",
    "ValueBinding",
    "get_semantic_mapping_service",
    "reset_semantic_mapping_registry",
]
