from pydantic import BaseModel, Field
from typing import Optional


class PredictResponse(BaseModel):
    predictedClass: str = Field(..., alias="predictedClass")
    score: float = Field(..., ge=0.0, le=1.0)
    scoreType: str = Field(..., alias="scoreType")
    modelName: str = Field(..., alias="modelName")
    modelVersion: str = Field(..., alias="modelVersion")
    durationMs: int = Field(..., alias="durationMs")

    class Config:
        populate_by_name = True
