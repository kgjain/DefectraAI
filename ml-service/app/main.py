import logging

from fastapi import FastAPI, File, UploadFile, HTTPException

from .inference import predict
from .schemas import PredictResponse

logging.basicConfig(level=logging.INFO)
logger = logging.getLogger(__name__)

app = FastAPI(title="DEFECTRA ML Service", version="1.0.0")


@app.get("/health")
def health():
    return {"status": "UP"}


@app.post("/predict", response_model=PredictResponse)
async def predict_endpoint(file: UploadFile = File(...)):
    ct = (file.content_type or "").lower()
    # Some clients (e.g. PowerShell Invoke-RestMethod -Form) omit or mis-set
    # the part content-type; only reject when an explicit non-image type is sent.
    if ct and ct not in ("image/jpeg", "image/png", "image/webp", "image/bmp",
                          "application/octet-stream", ""):
        raise HTTPException(status_code=415, detail="Unsupported image type")
    data = await file.read()
    if len(data) > 15 * 1024 * 1024:
        raise HTTPException(status_code=413, detail="Image too large")
    try:
        result = predict(data)
    except Exception:
        logger.exception("Prediction failed")
        raise HTTPException(status_code=500, detail="Prediction failed")
    return result
