"""Снимки Paparazzi — в лог строками «SNAP <папка>/<имя> <i>/<n> <base64>»."""
import base64
import glob
import io
import os
import sys

from PIL import Image

prefix = sys.argv[1] if len(sys.argv) > 1 else "android"
for path in sorted(glob.glob("android/app/src/test/snapshots/images/*.png")):
    image = Image.open(path).convert("RGB")
    width = 540
    image = image.resize((width, round(image.height * width / image.width)))
    buffer = io.BytesIO()
    image.save(buffer, "JPEG", quality=60)
    data = base64.b64encode(buffer.getvalue()).decode()
    name = prefix + "/" + os.path.basename(path).replace(".png", ".jpg")
    chunks = [data[i:i + 4000] for i in range(0, len(data), 4000)]
    for index, chunk in enumerate(chunks):
        print(f"SNAP {name} {index + 1}/{len(chunks)} {chunk}")
