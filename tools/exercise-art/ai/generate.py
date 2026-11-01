#!/usr/bin/env python3
"""Картинки упражнений: серый 3D-манекен на светлом фоне, рабочие мышцы красным.

Рисует модель Google Gemini по описанию из exercises.json — своим ключом
(секрет GEMINI_API_KEY в настройках репозитория), в CI: .github/workflows/exercise-art.yml.
Готовые картинки — out/<key>.jpg, 960×540. Уже нарисованные пропускаются,
если не попросить заново.

    GEMINI_API_KEY=... python3 generate.py --keys pilot
    GEMINI_API_KEY=... python3 generate.py --keys bench,squat --force --reference bench
"""

import argparse
import base64
import io
import json
import os
import sys
import time
import urllib.error
import urllib.request

from PIL import Image

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "out")
API = "https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent"
SIZE = (960, 540)

STYLE = """Create a clean 3D render for a fitness app exercise catalog.
Subject: a single faceless anatomical male mannequin made of smooth matte light-gray material, like a gray clay ecorche statue: athletic build, clearly sculpted individual muscles, no skin texture, no hair, no clothes, no facial features, no genitals.
Action: {pose}.
Equipment: realistic gym equipment rendered entirely in the same matte light-gray material as the figure, no colors, no brand marks.
Muscle highlight: only the {highlight} are colored saturated red-orange (#F2432C) with visible muscle fiber striations, placed exactly on their anatomical location, on both sides of the body when visible. Every other part of the figure and equipment stays gray.
Camera: {view}, the whole body and all equipment fully in frame and centered, the figure occupying about 75% of the image height, with empty margin around it.
Background: seamless very light gray studio backdrop (#E6E6E6 fading to #F4F4F4) with a soft contact shadow on the floor, soft diffused studio lighting, no other objects.
Style: high-quality 3D anatomy and fitness illustration render, realistic materials, sharp focus, wide 16:9 frame.
Do not add any text, labels, numbers, logos, watermarks, borders or extra people."""

REFERENCE = """The attached image is a style reference only: match its material, gray tones, red-orange highlight color, lighting, background, camera distance and rendering style exactly, but show the new exercise described above."""


def load_exercises():
    with open(os.path.join(HERE, "exercises.json"), encoding="utf-8") as f:
        return json.load(f)


def pick(exercises, keys):
    if keys in ("", "all"):
        return exercises
    if keys == "pilot":
        return [e for e in exercises if e.get("pilot")]
    wanted = [k.strip() for k in keys.split(",") if k.strip()]
    known = {e["key"]: e for e in exercises}
    missing = [k for k in wanted if k not in known]
    if missing:
        sys.exit(f"Нет в exercises.json: {', '.join(missing)}")
    return [known[k] for k in wanted]


def prompt(exercise):
    return STYLE.format(pose=exercise["pose"], highlight=exercise["highlight"], view=exercise["view"])


def request(model, key, parts):
    body = {
        "contents": [{"parts": parts}],
        "generationConfig": {"responseModalities": ["IMAGE"], "imageConfig": {"aspectRatio": "16:9"}},
    }
    req = urllib.request.Request(
        API.format(model=model),
        data=json.dumps(body).encode(),
        headers={"Content-Type": "application/json", "x-goog-api-key": key},
    )
    with urllib.request.urlopen(req, timeout=180) as response:
        return json.load(response)


def image_from(answer):
    for candidate in answer.get("candidates", []):
        for part in candidate.get("content", {}).get("parts", []):
            data = part.get("inlineData") or part.get("inline_data")
            if data and data.get("data"):
                return base64.b64decode(data["data"])
    reason = [c.get("finishReason") for c in answer.get("candidates", [])]
    raise RuntimeError(f"в ответе нет картинки (finishReason={reason}, feedback={answer.get('promptFeedback')})")


def generate(model, key, exercise, reference):
    parts = [{"text": prompt(exercise) + ("\n" + REFERENCE if reference else "")}]
    if reference:
        parts.append({"inline_data": {"mime_type": "image/jpeg", "data": base64.b64encode(reference).decode()}})
    delay = 20
    for attempt in range(4):
        try:
            return image_from(request(model, key, parts))
        except urllib.error.HTTPError as error:
            text = error.read().decode(errors="replace")[:600]
            if error.code in (429, 500, 502, 503, 504) and attempt < 3:
                print(f"  {error.code}, повтор через {delay} с: {text[:200]}", flush=True)
                time.sleep(delay)
                delay *= 2
                continue
            raise RuntimeError(f"HTTP {error.code}: {text}") from None
        except RuntimeError:
            if attempt < 3:
                time.sleep(5)
                continue
            raise


def save(raw, path):
    image = Image.open(io.BytesIO(raw)).convert("RGB")
    # Под 16:9 обрезаем по центру, если модель вернула другой формат.
    target = SIZE[0] / SIZE[1]
    width, height = image.size
    if width / height > target:
        new = int(height * target)
        image = image.crop(((width - new) // 2, 0, (width - new) // 2 + new, height))
    elif width / height < target:
        new = int(width / target)
        image = image.crop((0, (height - new) // 2, width, (height - new) // 2 + new))
    image = image.resize(SIZE, Image.LANCZOS)
    image.save(path, "JPEG", quality=84, optimize=True, progressive=True)


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--keys", default="pilot", help="pilot, all или ключи через запятую")
    parser.add_argument("--model", default="gemini-2.5-flash-image")
    parser.add_argument("--force", action="store_true", help="перерисовать уже готовые")
    parser.add_argument("--reference", default="", help="ключ готовой картинки — образец стиля")
    args = parser.parse_args()

    key = os.environ.get("GEMINI_API_KEY", "").strip()
    if not key:
        sys.exit("Нет ключа: добавь секрет GEMINI_API_KEY в Settings → Secrets and variables → Actions.")

    os.makedirs(OUT, exist_ok=True)
    reference = None
    if args.reference:
        path = os.path.join(OUT, args.reference + ".jpg")
        if not os.path.exists(path):
            sys.exit(f"Образца {args.reference}.jpg ещё нет в out/")
        with open(path, "rb") as f:
            reference = f.read()

    failed = []
    done = 0
    for exercise in pick(load_exercises(), args.keys):
        path = os.path.join(OUT, exercise["key"] + ".jpg")
        if os.path.exists(path) and not args.force:
            continue
        # Образец сам себе не образец.
        own_reference = reference if exercise["key"] != args.reference else None
        print(f"→ {exercise['key']} — {exercise['name']}", flush=True)
        try:
            save(generate(args.model, key, exercise, own_reference), path)
            done += 1
        except Exception as error:  # noqa: BLE001 — одна неудача не останавливает остальные
            print(f"  ✗ {error}", flush=True)
            failed.append(exercise["key"])
            if "HTTP 4" in str(error) and "429" not in str(error) and done == 0:
                # Ключ не подходит или модели нет — дальше будет то же самое.
                break

    print(f"Готово: {done}, не вышло: {len(failed)} {' '.join(failed)}")
    if failed and done == 0:
        sys.exit(1)


if __name__ == "__main__":
    main()
