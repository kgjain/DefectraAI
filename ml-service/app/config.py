import os
from pathlib import Path

BASE_DIR = Path(__file__).resolve().parent.parent
ARTIFACTS_DIR = Path(os.getenv("ML_ARTIFACTS_DIR", BASE_DIR / "artifacts"))
MODEL_PATH = ARTIFACTS_DIR / "model.pt"
METADATA_PATH = ARTIFACTS_DIR / "metadata.json"
METRICS_PATH = ARTIFACTS_DIR / "metrics.json"

MODEL_NAME = os.getenv("ML_MODEL_NAME", "defect-classifier")
MODEL_VERSION = os.getenv("ML_MODEL_VERSION", "0.1.0")

# Classes the model can predict. Order must match training output order.
CLASSES = ["OK", "SCRATCH", "DENT", "DISCOLORATION", "CRACK", "MISALIGNMENT"]

IMAGE_SIZE = int(os.getenv("ML_IMAGE_SIZE", "224"))

# When no trained model is present the service returns a deterministic
# rule-based fallback so the end-to-end flow still works (honest about
# scoreType="heuristic").
FALLBACK_ENABLED = os.getenv("ML_FALLBACK_ENABLED", "true").lower() == "true"
