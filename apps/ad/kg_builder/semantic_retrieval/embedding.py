"""Optional vector recall with deterministic local fallback and safe caching."""

from __future__ import annotations

from dataclasses import dataclass
import hashlib
import json
import math
import os
from pathlib import Path
import re
import tempfile
import threading
from typing import Protocol, Sequence

import numpy as np

from .models import CatalogSnapshot
from .normalizer import normalize_text, stable_hash


class EmbeddingUnavailableError(RuntimeError):
    """Raised when an optional embedding backend cannot be loaded."""


class EmbeddingProvider(Protocol):
    @property
    def provider_id(self) -> str: ...

    def encode(self, texts: Sequence[str]) -> np.ndarray: ...


def _normalize_rows(values: np.ndarray) -> np.ndarray:
    matrix = np.asarray(values, dtype=np.float32)
    if matrix.ndim == 1:
        matrix = matrix.reshape(1, -1)
    norms = np.linalg.norm(matrix, axis=1, keepdims=True)
    norms[norms == 0] = 1.0
    matrix = matrix / norms
    if not np.isfinite(matrix).all():
        raise EmbeddingUnavailableError("embedding 返回了非有限数值")
    return matrix


class HashingNgramEmbeddingProvider:
    """No-download character/word n-gram vectors for safe default recall."""

    def __init__(self, dimensions: int = 1024) -> None:
        self.dimensions = max(128, int(dimensions))

    @property
    def provider_id(self) -> str:
        return f"hashing-char-word-ngram-v1:{self.dimensions}"

    @staticmethod
    def _features(text: str) -> list[str]:
        normalized = normalize_text(text)
        features: list[str] = []
        for size in (2, 3, 4):
            features.extend(
                f"c{size}:{normalized[start:start + size]}"
                for start in range(max(0, len(normalized) - size + 1))
            )
        for token in re.findall(r"[A-Za-z0-9_]+|[\u4e00-\u9fff]{2,}", str(text or "")):
            token = normalize_text(token)
            if token:
                features.append(f"w:{token}")
        return features

    def encode(self, texts: Sequence[str]) -> np.ndarray:
        matrix = np.zeros((len(texts), self.dimensions), dtype=np.float32)
        for row_index, text in enumerate(texts):
            for feature in self._features(str(text or "")):
                digest = hashlib.sha256(feature.encode("utf-8")).digest()
                index = int.from_bytes(digest[:8], "big") % self.dimensions
                sign = 1.0 if digest[8] & 1 else -1.0
                matrix[row_index, index] += sign
        return _normalize_rows(matrix)


_MODEL_CACHE: dict[str, object] = {}
_MODEL_LOCK = threading.RLock()


class SentenceTransformerEmbeddingProvider:
    """Lazy multilingual SentenceTransformer provider shared process-wide."""

    def __init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2") -> None:
        self.model_name = model_name

    @property
    def provider_id(self) -> str:
        return f"sentence-transformer:{self.model_name}"

    def _model(self):
        with _MODEL_LOCK:
            if self.model_name in _MODEL_CACHE:
                return _MODEL_CACHE[self.model_name]
            try:
                os.environ.setdefault("TOKENIZERS_PARALLELISM", "false")
                from sentence_transformers import SentenceTransformer
            except Exception as exc:
                raise EmbeddingUnavailableError(
                    "sentence-transformers 未安装，已降级为字典召回"
                ) from exc
            try:
                model = SentenceTransformer(self.model_name)
            except Exception as exc:
                raise EmbeddingUnavailableError(f"embedding 模型加载失败: {exc}") from exc
            _MODEL_CACHE[self.model_name] = model
            return model

    def encode(self, texts: Sequence[str]) -> np.ndarray:
        try:
            values = self._model().encode(
                list(texts),
                normalize_embeddings=True,
                show_progress_bar=False,
            )
        except EmbeddingUnavailableError:
            raise
        except Exception as exc:
            raise EmbeddingUnavailableError(f"embedding 计算失败: {exc}") from exc
        return _normalize_rows(np.asarray(values, dtype=np.float32))


@dataclass(frozen=True)
class VectorHit:
    code: str
    semantic_type: str
    score: float


class SemanticVectorIndex:
    """Small immutable matrix index; builds only when vector fallback is needed."""

    FORMAT_VERSION = "1"
    TEXT_TEMPLATE_VERSION = "catalog-item-v1"

    def __init__(
        self,
        snapshot: CatalogSnapshot,
        provider: EmbeddingProvider,
        cache_dir: str | Path,
    ) -> None:
        self.snapshot = snapshot
        self.provider = provider
        self.cache_dir = Path(cache_dir)
        self._lock = threading.RLock()
        self._ready = False
        self._disabled_reason = ""
        self._rows: list[dict[str, str]] = []
        self._vectors = np.empty((0, 0), dtype=np.float32)

    @property
    def disabled_reason(self) -> str:
        return self._disabled_reason

    @property
    def cache_key(self) -> str:
        return stable_hash({
            "format": self.FORMAT_VERSION,
            "snapshot": self.snapshot.snapshot_key,
            "provider": self.provider.provider_id,
            "template": self.TEXT_TEMPLATE_VERSION,
            "items": sorted(self.snapshot.items),
        })

    def _paths(self) -> tuple[Path, Path, Path]:
        root = self.cache_dir / self.cache_key
        return root / "manifest.json", root / "items.json", root / "vectors.npz"

    def _load_cache(self) -> bool:
        manifest_path, items_path, vectors_path = self._paths()
        if not manifest_path.exists() or not items_path.exists() or not vectors_path.exists():
            return False
        try:
            manifest = json.loads(manifest_path.read_text(encoding="utf-8"))
            rows = json.loads(items_path.read_text(encoding="utf-8"))
            with np.load(vectors_path, allow_pickle=False) as payload:
                vectors = np.asarray(payload["vectors"], dtype=np.float32)
            if (
                manifest.get("formatVersion") != self.FORMAT_VERSION
                or manifest.get("cacheKey") != self.cache_key
                or not isinstance(rows, list)
                or vectors.ndim != 2
                or vectors.shape[0] != len(rows)
                or int(manifest.get("itemCount") or -1) != len(rows)
                or not np.isfinite(vectors).all()
            ):
                return False
            self._rows = rows
            self._vectors = _normalize_rows(vectors)
            return True
        except Exception:
            return False

    def _write_cache(self) -> None:
        manifest_path, items_path, vectors_path = self._paths()
        target_dir = manifest_path.parent
        target_dir.mkdir(parents=True, exist_ok=True)
        manifest = {
            "formatVersion": self.FORMAT_VERSION,
            "cacheKey": self.cache_key,
            "provider": self.provider.provider_id,
            "itemCount": len(self._rows),
            "dimensions": int(self._vectors.shape[1]) if self._vectors.ndim == 2 else 0,
        }
        temporary: list[tuple[Path, Path]] = []
        try:
            for target, writer in (
                (manifest_path, lambda handle: handle.write(json.dumps(manifest, ensure_ascii=False, sort_keys=True).encode("utf-8"))),
                (items_path, lambda handle: handle.write(json.dumps(self._rows, ensure_ascii=False, sort_keys=True).encode("utf-8"))),
            ):
                with tempfile.NamedTemporaryFile(dir=target_dir, delete=False) as handle:
                    writer(handle)
                    temporary.append((Path(handle.name), target))
            with tempfile.NamedTemporaryFile(dir=target_dir, delete=False) as handle:
                np.savez_compressed(handle, vectors=self._vectors)
                temporary.append((Path(handle.name), vectors_path))
            for source, target in temporary:
                os.replace(source, target)
        finally:
            for source, _target in temporary:
                if source.exists():
                    source.unlink(missing_ok=True)

    def _build(self) -> None:
        rows: list[dict[str, str]] = []
        texts: list[str] = []
        for item in sorted(self.snapshot.items.values(), key=lambda value: (value.semantic_type, value.code)):
            rows.append({"code": item.code, "semanticType": item.semantic_type})
            texts.append(item.embedding_text())
        if not rows:
            self._rows = []
            self._vectors = np.empty((0, 0), dtype=np.float32)
            return
        vectors = self.provider.encode(texts)
        if vectors.shape[0] != len(rows):
            raise EmbeddingUnavailableError("embedding 行数与目录条目数不一致")
        self._rows = rows
        self._vectors = vectors
        try:
            self._write_cache()
        except OSError:
            # Cache failure must never disable online recall.
            pass

    def ensure_ready(self) -> bool:
        if self._ready:
            return not self._disabled_reason
        with self._lock:
            if self._ready:
                return not self._disabled_reason
            try:
                if not self._load_cache():
                    self._build()
            except Exception as exc:
                self._disabled_reason = str(exc)
            self._ready = True
            return not self._disabled_reason

    def search(
        self,
        query: str,
        *,
        semantic_types: set[str] | None = None,
        allowed_codes: set[str] | None = None,
        top_k: int = 20,
        min_score: float = 0.45,
    ) -> list[VectorHit]:
        if not query.strip() or not self.ensure_ready() or not self._rows:
            return []
        try:
            query_vector = self.provider.encode([query])[0]
        except Exception:
            # A transient query failure must not permanently disable the
            # shared process-wide index. Build/load failures remain sticky;
            # query failures degrade only the current request.
            return []
        scores = np.dot(self._vectors, query_vector)
        hits: list[VectorHit] = []
        for index in np.argsort(-scores):
            row = self._rows[int(index)]
            score = float(scores[int(index)])
            if not math.isfinite(score) or score < min_score:
                continue
            code = row["code"]
            semantic_type = row["semanticType"]
            if semantic_types and semantic_type not in semantic_types:
                continue
            if allowed_codes is not None and code not in allowed_codes:
                continue
            hits.append(VectorHit(code=code, semantic_type=semantic_type, score=score))
            if len(hits) >= max(1, top_k):
                break
        return hits
