"""Trains the DefectClassifier on the synthetic dataset.

Splits 70/15/15 train/val/test, trains for a configurable number of epochs,
evaluates on the held-out test set, and writes model.pt + metadata.json +
metrics.json to the artifacts directory.
"""
import argparse
import json
import random
from pathlib import Path

import numpy as np
import torch
import torch.nn as nn
from torch.utils.data import DataLoader, Subset
from torchvision import transforms
from torchvision.datasets import ImageFolder

import sys
sys.path.insert(0, str(Path(__file__).resolve().parent.parent))

from app.model import DefectClassifier  # noqa: E402
from app.config import CLASSES, IMAGE_SIZE, ARTIFACTS_DIR  # noqa: E402


def set_seed(seed: int) -> None:
    random.seed(seed)
    np.random.seed(seed)
    torch.manual_seed(seed)


def build_loaders(data_dir: Path, batch_size: int):
    train_tf = transforms.Compose([
        transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
        transforms.RandomHorizontalFlip(),
        transforms.RandomRotation(10),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])
    # val/test use a deterministic transform so metrics are reproducible and
    # match the production inference preprocessing (resize + normalize only).
    eval_tf = transforms.Compose([
        transforms.Resize((IMAGE_SIZE, IMAGE_SIZE)),
        transforms.ToTensor(),
        transforms.Normalize([0.485, 0.456, 0.406], [0.229, 0.224, 0.225]),
    ])
    train_full = ImageFolder(str(data_dir), transform=train_tf)
    eval_full = ImageFolder(str(data_dir), transform=eval_tf)
    # ImageFolder sorts class names alphabetically; remap to our canonical order.
    for ds in (train_full, eval_full):  # alphabetical
        remap = {ds.classes.index(c): CLASSES.index(c) for c in CLASSES}
        ds.samples = [(path, remap[label]) for path, label in ds.samples]
        ds.targets = [label for _, label in ds.samples]
    n = len(train_full)
    n_train = int(0.70 * n)
    n_val = int(0.15 * n)
    n_test = n - n_train - n_val
    # same membership as torch.utils.data.random_split(..., manual_seed(42))
    perm = torch.randperm(n, generator=torch.Generator().manual_seed(42)).tolist()
    train_ds = Subset(train_full, perm[:n_train])
    val_ds = Subset(eval_full, perm[n_train:n_train + n_val])
    test_ds = Subset(eval_full, perm[n_train + n_val:])
    train_dl = DataLoader(train_ds, batch_size=batch_size, shuffle=True)
    val_dl = DataLoader(val_ds, batch_size=batch_size)
    test_dl = DataLoader(test_ds, batch_size=batch_size)
    return train_dl, val_dl, test_dl, CLASSES


def evaluate(model, loader, device):
    model.eval()
    correct = 0
    total = 0
    per_class_correct = {c: 0 for c in CLASSES}
    per_class_total = {c: 0 for c in CLASSES}
    with torch.no_grad():
        for images, labels in loader:
            images, labels = images.to(device), labels.to(device)
            outputs = model(images)
            _, preds = torch.max(outputs, 1)
            correct += (preds == labels).sum().item()
            total += labels.size(0)
            for p, t in zip(preds, labels):
                t_cls = CLASSES[t.item()]
                per_class_total[t_cls] += 1
                if p.item() == t.item():
                    per_class_correct[t_cls] += 1
    acc = correct / total if total else 0.0
    per_class_acc = {
        c: (per_class_correct[c] / per_class_total[c] if per_class_total[c] else 0.0)
        for c in CLASSES
    }
    return acc, per_class_acc


def train(data_dir: Path, epochs: int, batch_size: int, lr: float, seed: int, out_dir: Path):
    set_seed(seed)
    device = torch.device("cuda" if torch.cuda.is_available() else "cpu")
    train_dl, val_dl, test_dl, class_names = build_loaders(data_dir, batch_size)
    assert class_names == CLASSES, f"Class mismatch: {class_names} vs {CLASSES}"

    model = DefectClassifier(num_classes=len(CLASSES)).to(device)
    criterion = nn.CrossEntropyLoss()
    optimizer = torch.optim.Adam(model.parameters(), lr=lr, weight_decay=1e-4)
    scheduler = torch.optim.lr_scheduler.StepLR(optimizer, step_size=max(1, epochs // 3), gamma=0.5)

    best_val_acc = 0.0
    history = []
    for epoch in range(epochs):
        model.train()
        running_loss = 0.0
        for images, labels in train_dl:
            images, labels = images.to(device), labels.to(device)
            optimizer.zero_grad()
            outputs = model(images)
            loss = criterion(outputs, labels)
            loss.backward()
            optimizer.step()
            running_loss += loss.item() * images.size(0)
        avg_loss = running_loss / len(train_dl.dataset)
        val_acc, _ = evaluate(model, val_dl, device)
        scheduler.step()
        history.append({"epoch": epoch + 1, "trainLoss": round(avg_loss, 4), "valAcc": round(val_acc, 4)})
        print(f"epoch {epoch + 1}/{epochs}  loss={avg_loss:.4f}  val_acc={val_acc:.4f}")
        if val_acc >= best_val_acc:
            best_val_acc = val_acc
            out_dir.mkdir(parents=True, exist_ok=True)
            torch.save(model.state_dict(), out_dir / "model.pt")

    # Final test evaluation with best weights
    state = torch.load(out_dir / "model.pt", map_location=device, weights_only=True)
    model.load_state_dict(state)
    test_acc, per_class_acc = evaluate(model, test_dl, device)
    print(f"test_acc={test_acc:.4f}")

    metrics = {
        "testAccuracy": round(test_acc, 4),
        "perClassAccuracy": {k: round(v, 4) for k, v in per_class_acc.items()},
        "epochs": epochs,
        "history": history,
    }
    (out_dir / "metrics.json").write_text(json.dumps(metrics, indent=2))

    metadata = {
        "modelName": "defect-classifier",
        "modelVersion": "0.1.0",
        "classes": CLASSES,
        "imageSize": IMAGE_SIZE,
        "trainedAt": __import__("datetime").datetime.utcnow().isoformat() + "Z",
        "datasetSize": len(train_dl.dataset) + len(val_dl.dataset) + len(test_dl.dataset),
        "trainSize": len(train_dl.dataset),
        "valSize": len(val_dl.dataset),
        "testSize": len(test_dl.dataset),
        "framework": f"torch {torch.__version__}",
    }
    (out_dir / "metadata.json").write_text(json.dumps(metadata, indent=2))
    print(f"Artifacts written to {out_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--data", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--epochs", type=int, default=8)
    parser.add_argument("--batch-size", type=int, default=16)
    parser.add_argument("--lr", type=float, default=1e-3)
    parser.add_argument("--seed", type=int, default=42)
    parser.add_argument("--out", type=Path, default=ARTIFACTS_DIR)
    args = parser.parse_args()
    train(args.data, args.epochs, args.batch_size, args.lr, args.seed, args.out)
