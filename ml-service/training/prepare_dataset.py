"""Generates a synthetic surface-defect dataset for training.

No real labelled defect images are available in this environment, so this
script procedurally renders product-surface images with realistic defect
overlays (scratches, dents, discoloration, cracks, misalignment) plus clean
"OK" samples. The generator is deterministic (seeded) so experiments are
reproducible.
"""
import argparse
import random
from pathlib import Path

import numpy as np
from PIL import Image, ImageDraw, ImageFilter

CLASSES = ["OK", "SCRATCH", "DENT", "DISCOLORATION", "CRACK", "MISALIGNMENT"]

# Base surface colours (R, G, B)
SURFACE_COLORS = [
    (180, 180, 190),
    (200, 195, 185),
    (160, 165, 170),
    (210, 205, 200),
    (140, 145, 150),
    (190, 185, 175),
]


def _base_surface(rng: random.Random, size: int) -> Image.Image:
    color = rng.choice(SURFACE_COLORS)
    img = Image.new("RGB", (size, size), color)
    draw = ImageDraw.Draw(img)
    # Subtle noise / texture
    for _ in range(rng.randint(200, 600)):
        x, y = rng.randint(0, size - 1), rng.randint(0, size - 1)
        shade = rng.randint(-12, 12)
        c = tuple(max(0, min(255, ch + shade)) for ch in color)
        draw.point((x, y), fill=c)
    return img


def _add_scratch(rng: random.Random, img: Image.Image) -> None:
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(1, 3)):
        x1, y1 = rng.randint(0, img.width), rng.randint(0, img.height)
        length = rng.randint(img.width // 4, img.width // 2)
        angle = rng.uniform(0, 3.14159)
        x2 = int(x1 + length * np.cos(angle))
        y2 = int(y1 + length * np.sin(angle))
        brightness = rng.randint(40, 90)
        draw.line((x1, y1, x2, y2), fill=(brightness, brightness, brightness), width=rng.randint(1, 2))
    img = img.filter(ImageFilter.GaussianBlur(radius=0.4))


def _add_dent(rng: random.Random, img: Image.Image) -> None:
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(1, 4)):
        cx, cy = rng.randint(20, img.width - 20), rng.randint(20, img.height - 20)
        r = rng.randint(8, 25)
        # Dark core with soft edge to mimic shading
        draw.ellipse((cx - r, cy - r, cx + r, cy + r), fill=(90, 90, 95))
    img = img.filter(ImageFilter.GaussianBlur(radius=2))


def _add_discoloration(rng: random.Random, img: Image.Image) -> None:
    arr = np.asarray(img).astype(np.int16)
    tint = np.array([rng.randint(-40, -15), rng.randint(10, 35), rng.randint(10, 35)], dtype=np.int16)
    cx, cy = rng.randint(0, img.width), rng.randint(0, img.height)
    r = rng.randint(img.width // 4, img.width // 2)
    yy, xx = np.ogrid[: img.height, : img.width]
    mask = ((xx - cx) ** 2 + (yy - cy) ** 2) <= r * r
    arr[mask] = np.clip(arr[mask] + tint, 0, 255)
    Image.fromarray(arr.astype(np.uint8)).copy()


def _add_crack(rng: random.Random, img: Image.Image) -> None:
    draw = ImageDraw.Draw(img)
    for _ in range(rng.randint(1, 2)):
        points = [(rng.randint(0, img.width), rng.randint(0, img.height))]
        for _ in range(rng.randint(4, 10)):
            px, py = points[-1]
            points.append((px + rng.randint(-30, 30), py + rng.randint(-30, 30)))
        draw.line(points, fill=(30, 30, 30), width=rng.randint(1, 3))
    img = img.filter(ImageFilter.GaussianBlur(radius=0.6))


def _add_misalignment(rng: random.Random, img: Image.Image) -> None:
    """Simulates a shifted panel / off-centre feature."""
    draw = ImageDraw.Draw(img)
    w, h = img.size
    offset = rng.randint(6, 18)
    # Draw a rectangle outline shifted from centre
    cx, cy = w // 2, h // 2
    bw, bh = w // 3, h // 3
    draw.rectangle(
        (cx - bw + offset, cy - bh, cx + bw + offset, cy + bh),
        outline=(60, 60, 60),
        width=2,
    )


def generate_sample(rng: random.Random, label: str, size: int) -> Image.Image:
    img = _base_surface(rng, size)
    if label == "SCRATCH":
        _add_scratch(rng, img)
    elif label == "DENT":
        _add_dent(rng, img)
    elif label == "DISCOLORATION":
        _add_discoloration(rng, img)
    elif label == "CRACK":
        _add_crack(rng, img)
    elif label == "MISALIGNMENT":
        _add_misalignment(rng, img)
    # OK: clean surface, no defect overlay
    return img


def generate_dataset(out_dir: Path, per_class: int, size: int, seed: int) -> None:
    rng = random.Random(seed)
    for label in CLASSES:
        class_dir = out_dir / label
        class_dir.mkdir(parents=True, exist_ok=True)
        for i in range(per_class):
            img = generate_sample(rng, label, size)
            img.save(class_dir / f"{label}_{i:04d}.png")
    print(f"Generated {per_class * len(CLASSES)} images under {out_dir}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--out", type=Path, default=Path(__file__).parent / "data")
    parser.add_argument("--per-class", type=int, default=80)
    parser.add_argument("--size", type=int, default=224)
    parser.add_argument("--seed", type=int, default=42)
    args = parser.parse_args()
    generate_dataset(args.out, args.per_class, args.size, args.seed)
