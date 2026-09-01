"""Thread-safe lifecycle, reload, and registry for semantic mapping."""

from __future__ import annotations

from dataclasses import dataclass
from collections import OrderedDict
import os
from pathlib import Path
import threading
import time
from typing import Any, Callable, Iterable

from rdflib import Graph

from .catalog import build_catalog_snapshot
from .dictionary import load_dictionary
from .embedding import (
    EmbeddingProvider,
    HashingNgramEmbeddingProvider,
    SemanticVectorIndex,
)
from .feedback_adapter import load_enabled_dictionary_entries
from .mapper import SemanticMapper
from .models import CatalogSnapshot, SemanticMatchResult
from .normalizer import file_sha256, stable_hash
from .retriever import RecallResponse, SemanticRetriever


def _env_bool(name: str, default: bool) -> bool:
    raw = os.getenv(name)
    if raw is None:
        return default
    return raw.strip().lower() in {"1", "true", "yes", "on"}


@dataclass(frozen=True)
class SemanticMappingConfig:
    dictionary_paths: tuple[Path, ...]
    vector_enabled: bool = True
    hashing_dimensions: int = 1024
    fuzzy_threshold: float = 0.72
    vector_threshold: float = 0.45
    max_values_per_dimension: int = 200
    max_dimension_cardinality: int = 500
    cache_dir: Path = Path(".cache/semantic-retrieval")
    feedback_dictionary_enabled: bool = False
    feedback_refresh_seconds: int = 30

    @classmethod
    def from_env(cls, app_dir: str | Path | None = None) -> "SemanticMappingConfig":
        app_path = Path(app_dir) if app_dir else Path(__file__).resolve().parents[2]
        configured_dictionary = os.getenv("INSIGHTMIND_SEMANTIC_DICTIONARY", "").strip()
        paths = (
            (Path(configured_dictionary),)
            if configured_dictionary
            else (app_path / "semantic_dictionary.yaml", app_path / "synonyms.yaml")
        )
        return cls(
            dictionary_paths=tuple(paths),
            vector_enabled=_env_bool("INSIGHTMIND_SEMANTIC_VECTOR_ENABLED", True),
            hashing_dimensions=max(128, int(os.getenv("INSIGHTMIND_SEMANTIC_HASH_DIMENSIONS", "1024"))),
            fuzzy_threshold=float(os.getenv("INSIGHTMIND_SEMANTIC_FUZZY_THRESHOLD", "0.72")),
            vector_threshold=float(os.getenv(
                "INSIGHTMIND_SEMANTIC_VECTOR_THRESHOLD", "0.15"
            )),
            max_values_per_dimension=max(
                1, int(os.getenv("INSIGHTMIND_SEMANTIC_MAX_VALUES_PER_DIMENSION", "200"))
            ),
            max_dimension_cardinality=max(
                1, int(os.getenv("INSIGHTMIND_SEMANTIC_MAX_DIMENSION_CARDINALITY", "500"))
            ),
            cache_dir=Path(
                os.getenv(
                    "INSIGHTMIND_SEMANTIC_CACHE_DIR",
                    str(app_path / ".cache" / "semantic-retrieval"),
                )
            ),
            feedback_dictionary_enabled=_env_bool(
                "INSIGHTMIND_SEMANTIC_FEEDBACK_ENABLED", False
            ),
            feedback_refresh_seconds=max(
                1, int(os.getenv("INSIGHTMIND_SEMANTIC_FEEDBACK_REFRESH_SECONDS", "30"))
            ),
        )

    @property
    def signature(self) -> str:
        return stable_hash({
            "dictionaryPaths": [str(path.resolve()) for path in self.dictionary_paths],
            "vectorEnabled": self.vector_enabled,
            "hashingDimensions": self.hashing_dimensions,
            "fuzzyThreshold": self.fuzzy_threshold,
            "vectorThreshold": self.vector_threshold,
            "maxValues": self.max_values_per_dimension,
            "maxCardinality": self.max_dimension_cardinality,
            "cacheDir": str(self.cache_dir.resolve()),
            "feedback": self.feedback_dictionary_enabled,
            "feedbackRefreshSeconds": self.feedback_refresh_seconds,
        })


class SemanticMappingService:
    def __init__(
        self,
        ttl_path: str | Path,
        *,
        source_ttl_path: str | Path | None = None,
        config: SemanticMappingConfig | None = None,
        embedding_provider: EmbeddingProvider | None = None,
        feedback_loader: Callable[[str], list[dict[str, Any]]] | None = None,
        log_cb: Callable[[str], None] | None = None,
    ) -> None:
        self.ttl_path = Path(ttl_path)
        self.source_ttl_path = Path(source_ttl_path) if source_ttl_path else None
        self.config = config or SemanticMappingConfig.from_env()
        self._provided_embedding_provider = embedding_provider
        self._feedback_loader = feedback_loader or load_enabled_dictionary_entries
        self._log = log_cb or (lambda _message: None)
        self._lock = threading.RLock()
        self._state_token = ""
        self._snapshot: CatalogSnapshot | None = None
        self._retriever: SemanticRetriever | None = None
        self._mapper: SemanticMapper | None = None
        self._last_error = ""
        self._feedback_entries: list[dict[str, Any]] = []
        self._feedback_revision = ""
        self._feedback_checked_at = 0.0

    @staticmethod
    def _file_token(path: Path | None) -> tuple[str, int, int, int, int]:
        if path is None or not path.exists():
            return (str(path or ""), 0, 0, 0, 0)
        stat = path.stat()
        return (
            str(path.resolve()),
            stat.st_mtime_ns,
            stat.st_ctime_ns,
            int(getattr(stat, "st_ino", 0) or 0),
            stat.st_size,
        )

    def _current_state_token(self) -> str:
        return stable_hash({
            "ttl": self._file_token(self.ttl_path),
            "source": self._file_token(self.source_ttl_path),
            "dictionary": [self._file_token(path) for path in self.config.dictionary_paths],
            "config": self.config.signature,
            "feedbackRevision": self._feedback_revision,
        })

    def _embedding_provider(self) -> EmbeddingProvider:
        if self._provided_embedding_provider is not None:
            return self._provided_embedding_provider
        return HashingNgramEmbeddingProvider(self.config.hashing_dimensions)

    def refresh(self, *, force: bool = False) -> CatalogSnapshot:
        token = self._current_state_token()
        feedback_due = bool(
            self.config.feedback_dictionary_enabled
            and (
                force
                or time.monotonic() - self._feedback_checked_at
                >= self.config.feedback_refresh_seconds
            )
        )
        if (
            not force
            and not feedback_due
            and self._snapshot is not None
            and token == self._state_token
        ):
            return self._snapshot
        with self._lock:
            feedback_due = bool(
                self.config.feedback_dictionary_enabled
                and (
                    force
                    or time.monotonic() - self._feedback_checked_at
                    >= self.config.feedback_refresh_seconds
                )
            )
            if feedback_due:
                try:
                    graph_hash_for_feedback = file_sha256(self.ttl_path)
                    entries = self._feedback_loader(graph_hash_for_feedback)
                    self._feedback_entries = [
                        item for item in entries if isinstance(item, dict)
                    ]
                    self._feedback_revision = stable_hash(self._feedback_entries)
                except Exception as exc:
                    self._log(f"[Semantic] 审核字典刷新失败，保留上一版本: {exc}")
                finally:
                    self._feedback_checked_at = time.monotonic()
            token = self._current_state_token()
            if not force and self._snapshot is not None and token == self._state_token:
                return self._snapshot
            if not self.ttl_path.exists():
                raise FileNotFoundError(f"业务图谱不存在: {self.ttl_path}")

            graph = Graph()
            graph.parse(str(self.ttl_path), format="turtle")
            source_graph: Graph | None = None
            source_graph_hash = ""
            if self.source_ttl_path is not None and self.source_ttl_path.exists():
                source_graph = Graph()
                source_graph.parse(str(self.source_ttl_path), format="turtle")
                source_graph_hash = file_sha256(self.source_ttl_path)
            graph_hash = file_sha256(self.ttl_path)
            extra_entries = (
                list(self._feedback_entries)
                if self.config.feedback_dictionary_enabled else []
            )
            dictionary = load_dictionary(
                self.config.dictionary_paths,
                extra_entries=extra_entries,
            )
            snapshot = build_catalog_snapshot(
                graph,
                graph_hash=graph_hash,
                source_graph_hash=source_graph_hash,
                dictionary=dictionary,
                source_graph=source_graph,
                max_values_per_dimension=self.config.max_values_per_dimension,
                max_dimension_cardinality=self.config.max_dimension_cardinality,
            )
            vector_index = None
            if self.config.vector_enabled:
                vector_index = SemanticVectorIndex(
                    snapshot,
                    self._embedding_provider(),
                    self.config.cache_dir,
                )
            retriever = SemanticRetriever(
                snapshot,
                vector_index=vector_index,
                fuzzy_threshold=self.config.fuzzy_threshold,
                vector_threshold=self.config.vector_threshold,
            )
            mapper = SemanticMapper(snapshot, retriever)
            self._snapshot = snapshot
            self._retriever = retriever
            self._mapper = mapper
            self._state_token = token
            self._last_error = ""
            self._log(
                f"[Semantic] 目录加载完成: {len(snapshot.items)} 个语义元素, "
                f"{len(snapshot.aliases)} 个检索词, {len(snapshot.values)} 个维值"
            )
            return snapshot

    @property
    def snapshot(self) -> CatalogSnapshot:
        return self.refresh()

    def map(
        self,
        question: str,
        *,
        allowed_measure_codes: Iterable[str] | None = None,
        allowed_dimension_codes: Iterable[str] | None = None,
        preferred_tables: Iterable[str] | None = None,
        assumed_measure_code: str | None = None,
        top_k: int = 10,
        include_vector: bool = True,
    ) -> SemanticMatchResult:
        self.refresh()
        assert self._mapper is not None
        return self._mapper.map(
            question,
            allowed_measure_codes=allowed_measure_codes,
            allowed_dimension_codes=allowed_dimension_codes,
            preferred_tables=preferred_tables,
            assumed_measure_code=assumed_measure_code,
            top_k=top_k,
            include_vector=include_vector and self.config.vector_enabled,
        )

    def search(
        self,
        query: str,
        *,
        semantic_types: Iterable[str] | None = None,
        allowed_codes: Iterable[str] | None = None,
        allowed_tables: Iterable[str] | None = None,
        top_k: int = 20,
        include_vector: bool = True,
    ) -> RecallResponse:
        self.refresh()
        assert self._retriever is not None
        requested_types = set(semantic_types or ())
        if requested_types == {"value"}:
            return self._retriever.search_values(
                query,
                allowed_dimension_codes=allowed_codes,
                allowed_tables=allowed_tables,
                top_k=top_k,
            )
        return self._retriever.search(
            query,
            semantic_types=requested_types or None,
            allowed_codes=allowed_codes,
            allowed_tables=allowed_tables,
            top_k=top_k,
            include_vector=include_vector and self.config.vector_enabled,
        )

    def status(self) -> dict[str, Any]:
        try:
            snapshot = self.refresh()
            return {
                "ok": True,
                "version": snapshot.version,
                "itemCount": len(snapshot.items),
                "measureCount": sum(
                    item.semantic_type == "measure" for item in snapshot.items.values()
                ),
                "dimensionCount": sum(
                    item.semantic_type == "dimension" for item in snapshot.items.values()
                ),
                "valueCount": len(snapshot.values),
                "aliasCount": len(snapshot.aliases),
                "degraded": bool(snapshot.warnings),
                "warningCount": len(snapshot.warnings),
                "vectorEnabled": self.config.vector_enabled,
                "embeddingProvider": (
                    self._retriever.vector_index.provider.provider_id
                    if self._retriever and self._retriever.vector_index else ""
                ),
            }
        except Exception as exc:
            self._last_error = str(exc)
            self._log(f"[Semantic] 状态检查失败: {exc}")
            return {"ok": False, "error": "semantic_retrieval_unavailable"}


_REGISTRY_LIMIT = 8
_REGISTRY: OrderedDict[tuple[str, str, str], SemanticMappingService] = OrderedDict()
_REGISTRY_LOCK = threading.RLock()


def get_semantic_mapping_service(
    ttl_path: str | Path,
    source_ttl_path: str | Path | None = None,
    *,
    config: SemanticMappingConfig | None = None,
    log_cb: Callable[[str], None] | None = None,
) -> SemanticMappingService:
    resolved_config = config or SemanticMappingConfig.from_env()
    ttl_key = str(Path(ttl_path).resolve())
    source_key = str(Path(source_ttl_path).resolve()) if source_ttl_path else ""
    key = (ttl_key, source_key, resolved_config.signature)
    with _REGISTRY_LOCK:
        service = _REGISTRY.get(key)
        if service is None:
            service = SemanticMappingService(
                ttl_path,
                source_ttl_path=source_ttl_path,
                config=resolved_config,
                log_cb=log_cb,
            )
            _REGISTRY[key] = service
            while len(_REGISTRY) > _REGISTRY_LIMIT:
                _REGISTRY.popitem(last=False)
        else:
            _REGISTRY.move_to_end(key)
        return service


def reset_semantic_mapping_registry() -> None:
    with _REGISTRY_LOCK:
        _REGISTRY.clear()
