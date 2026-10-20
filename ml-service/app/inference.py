import io
import json
import logging
import time
from pathlib import Path

import numpy as np
import torch
import torch.nn.functional as F
from PIL import Image

from .config import (
    CLASSES,
    IMAGE_SIZE,
    METADATA_PATH,
    METRICS_PATH,
    MODEL_NAME,
    MODEL_PATH,
    MODEL_VERSION,
    FALLBACK_ENABLED,
)
from .model import DefectClassifier

logger = logging.getLogger(__name__)

_model: DefectClassifier | None = None
_device: torch.device | None = None
_loaded: bool = False


def _load_model() -> None:
    global _model, _device, _loaded
    if _loaded:
        return
    _device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    if MODEL_PATH.exists():
        try:
            model = DefectClassifier(num_classes=len(CLASSES))
            state = torch.load(MODEL_PATH, map_location=_device, weights_only=True)
            model.load_state_dict(state)
            model.to(_device)
            model.eval()
            _model = model
            logger.info("Loaded model from %s on %s", MODEL_PATH, _device)
        except Exception:
            logger.exception("Failed to load model, falling back to heuristic")
            _model = None
    else:
        logger.warning("No trained model at %s", MODEL_PATH)
    _loaded = True


def _preprocess(image: Image.Image) -> torch.Tensor:
    image = image.convert("RGB").resize((IMAGE_SIZE, IMAGE_SIZE))
    arr = np.asarray(image, dtype=np.float32) / 255.0
    # Normalize with ImageNet stats (matches common transfer-learning practice)
    mean = np.array([0.485, 0.456, 0.406], dtype=np.float32)
    std = np.array([0.229, 0.224, 0.225], dtype=np.float32)
    arr = (arr - mean) / std
    tensor = torch.from_numpy(arr).permute(2, 0, 1).unsqueeze(0)
    return tensor


def _heuristic_predict(image: Image.Image) -> tuple[str, float]:
    """Deterministic fallback when no trained model is available.

    Uses simple image statistics so the response is stable and honest
    (scoreType="heuristic", never a fake probability).
    """
    gray = np.asarray(image.convert("L"), dtype=np.float32) / 255.0
    contrast = float(gray.std())
    dark_ratio = float((gray < 0.35).mean())
    # Very rough rules derived from the synthetic dataset generator's visual
    # characteristics. Kept intentionally conservative.
    if dark_ratio > 0.45 and contrast > 0.25:
        return "CRACK", 0.55
    if contrast > 0.30:
        return "SCRATCH", 0.50
    if dark_ratio > 0.25:
        return "DENT", 0.48
    if contrast < 0.08:
        return "DISCOLORATION", 0.45
    return "OK", 0.60


def predict(image_bytes: bytes) -> dict:
    """Run inference on raw image bytes. Returns the wire-format dict."""
    start = time.perf_counter()
    image = Image.open(io.BytesIO(image_bytes))

    _load_model()
    if _model is not None:
        tensor = _preprocess(image).to(_device)
        with torch.no_grad():
            logits = _model(tensor)
            probs = F.softmax(logits, dim=1)[0]
            score, idx = torch.max(probs, dim=0)
            predicted = CLASSES[idx.item()]
            confidence = float(score.item())
        score_type = "probability"
    elif FALLBACK_ENABLED:
        predicted, confidence = _heuristic_predict(image)
        score_type = "heuristic"
    else:
        raise RuntimeError("No model loaded and fallback disabled")

    duration_ms = int((time.perf_counter() - start) * 1000)
    return {
        "predictedClass": predicted,
        "score": round(confidence, 4),
        "scoreType": score_type,
        "modelName": MODEL_NAME,
        "modelVersion": MODEL_VERSION,
        "durationMs": duration_ms,
    }
