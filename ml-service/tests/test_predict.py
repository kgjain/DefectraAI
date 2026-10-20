import io
import sys
from pathlib import Path

import pytest
from PIL import Image

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.inference import predict, _heuristic_predict
from app.main import app
from fastapi.testclient import TestClient


client = TestClient(app)


def _png_bytes(color=(128, 128, 128), size=(64, 64)) -> bytes:
    img = Image.new("RGB", size, color)
    buf = io.BytesIO()
    img.save(buf, format="PNG")
    return buf.getvalue()


class TestHealth:
    def test_health(self):
        resp = client.get("/health")
        assert resp.status_code == 200
        assert resp.json()["status"] == "UP"


class TestPredict:
    def test_predict_returns_required_fields(self):
        resp = client.post("/predict", files={"file": ("test.png", _png_bytes(), "image/png")})
        assert resp.status_code == 200
        data = resp.json()
        assert set(data.keys()) == {
            "predictedClass", "score", "scoreType", "modelName", "modelVersion", "durationMs"
        }
        assert 0.0 <= data["score"] <= 1.0
        assert data["modelName"] == "defect-classifier"
        assert data["modelVersion"] == "0.1.0"
        assert data["scoreType"] in ("probability", "heuristic")
        assert data["predictedClass"] in (
            "OK", "SCRATCH", "DENT", "DISCOLORATION", "CRACK", "MISALIGNMENT"
        )

    def test_predict_is_deterministic_for_same_input(self):
        payload = _png_bytes(color=(200, 200, 200))
        r1 = client.post("/predict", files={"file": ("a.png", payload, "image/png")}).json()
        r2 = client.post("/predict", files={"file": ("b.png", payload, "image/png")}).json()
        assert r1["predictedClass"] == r2["predictedClass"]
        assert r1["score"] == r2["score"]

    def test_predict_rejects_non_image(self):
        resp = client.post("/predict", files={"file": ("bad.txt", b"not an image", "text/plain")})
        assert resp.status_code in (415, 500)

    def test_predict_rejects_oversized(self):
        big = b"\x00" * (16 * 1024 * 1024)
        resp = client.post("/predict", files={"file": ("big.png", big, "image/png")})
        assert resp.status_code == 413


class TestHeuristicFallback:
    def test_heuristic_returns_known_class(self):
        img = Image.new("RGB", (64, 64), (128, 128, 128))
        label, score = _heuristic_predict(img)
        assert label in ("OK", "SCRATCH", "DENT", "DISCOLORATION", "CRACK", "MISALIGNMENT")
        assert 0.0 <= score <= 1.0

    def test_heuristic_deterministic(self):
        img = Image.new("RGB", (64, 64), (200, 200, 200))
        a = _heuristic_predict(img)
        b = _heuristic_predict(img)
        assert a == b


class TestInferenceModule:
    def test_predict_bytes_returns_dict(self):
        result = predict(_png_bytes())
        assert isinstance(result, dict)
        assert "predictedClass" in result
        assert "score" in result
