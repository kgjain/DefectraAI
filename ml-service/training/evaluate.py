"""Standalone evaluation: loads the saved model and reports test-set metrics.

Used to independently verify artifacts without re-running training.
Reports test accuracy, per-class accuracy, and a 6x6 confusion matrix
(rows = actual class, columns = predicted class).
"""
import argparse
import json
import sys
from pathlib import Path

import torch
from torch.utils.data import DataLoader

sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.config import ARTIFACTS_DIR, CLASSES, IMAGE_SIZE  # noqa: E402
from app.model import DefectClassifier  # noqa: E402
from train import build_loaders, evaluate, set_seed  # noqa: E402


def confusion_matrix(model: DefectClassifier, loader: DataLoader,
                     device: torch.device, num_classes: int) -> list[list[int]]:
    """rows = actual class index, columns = predicted class index."""
    matrix = [[0] * num_classes for _ in range(num_classes)]
    with torch.no_grad():
        for images, targets in loader:
            images = images.to(device)
            preds = model(images).argmax(dim=1).cpu().tolist()
            for actual, predicted in zip(targets.tolist(), preds):
                matrix[actual][predicted] += 1
    return matrix


def main(data_dir: Path, artifacts: Path, batch_size: int):
    set_seed(42)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    _, _, test_dl, _ = build_loaders(data_dir, batch_size)
    model = DefectClassifier(num_classes=len(CLASSES)).to(device)
    state = torch.load(artifacts / "model.pt", map_location=device, weights_only=True)
    model.load_state_dict(state)
    model.eval()
    acc, per_class = evaluate(model, test_dl, device)
    matrix = confusion_matrix(model, test_dl, device, len(CLASSES))
    report = {
        "testAccuracy": round(acc, 4),
        "perClassAccuracy": {k: round(v, 4) for k, v in per_class.items()},
        "confusionMatrix": {
            "classes": CLASSES,
            "rowsAreActual": True,
            "matrix": matrix,
        },
    }
    print(json.dumps(report, indent=2))


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--artifacts", type=Path, default=ARTIFACTS_DIR)
    parser.add_argument("--batch-size", type=int, default=16)
    args = parser.parse_args()
    main(args.data, args.artifacts, args.batch_size)
