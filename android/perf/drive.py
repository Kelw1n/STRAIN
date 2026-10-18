#!/usr/bin/env python3
"""Замер плавности STRAIN на эмуляторе.

Ставит APK, проходит первый запуск, гоняет сценарии (вкладки, прокрутки,
план, тренировка) и по каждому считает время кадров из `dumpsys gfxinfo`.

Главная цифра — «работа» кадра: сколько UI-поток сам потратил на ввод,
анимации, компоновку и запись отрисовки (SyncQueued − HandleInputStart).
Ожидание программного GPU эмулятора в неё не входит — иначе все кадры
выглядят одинаково медленными, и разницу между версиями не видно.
Буфер gfxinfo держит только 120 кадров, поэтому кадры собираются в фоне
раз в секунду и склеиваются по времени начала.

    python3 drive.py app-release.apk out_dir
"""
import base64
import json
import os
import re
import subprocess
import sys
import threading
import time
import xml.etree.ElementTree as ET

PKG = "com.texasprogram.app"
APK, OUT = sys.argv[1], sys.argv[2]
os.makedirs(OUT, exist_ok=True)


def adb(*args, check=True, text=True):
    result = subprocess.run(["adb", *args], capture_output=True, text=text, timeout=120)
    if check and result.returncode != 0:
        raise RuntimeError(f"adb {' '.join(args)}: {result.stderr}")
    return result.stdout


def sh(cmd):
    return adb("shell", cmd)


def screen_size():
    m = re.search(r"(\d+)x(\d+)", sh("wm size"))
    return int(m.group(1)), int(m.group(2))


W, H = screen_size()


def nodes():
    """Узлы интерфейса: текст, подпись и прямоугольник."""
    for _ in range(3):
        sh("uiautomator dump /sdcard/ui.xml >/dev/null 2>&1")
        raw = adb("exec-out", "cat /sdcard/ui.xml", check=False)
        if raw.strip().startswith("<?xml"):
            break
        time.sleep(0.5)
    try:
        root = ET.fromstring(raw)
    except ET.ParseError:
        return []
    found = []
    for node in root.iter("node"):
        bounds = re.findall(r"\d+", node.get("bounds", ""))
        if len(bounds) != 4:
            continue
        x1, y1, x2, y2 = map(int, bounds)
        found.append({
            "text": node.get("text", ""),
            "desc": node.get("content-desc", ""),
            "x": (x1 + x2) // 2, "y": (y1 + y2) // 2,
            "h": y2 - y1,
        })
    return found


def dismiss_system_dialogs(found):
    """Эмулятор иногда показывает «… isn't responding» поверх приложения — жмём «Wait»."""
    if not any("isn't responding" in n["text"] or "не отвечает" in n["text"] for n in found):
        return False
    for node in found:
        if node["text"] in ("Wait", "Подождать"):
            tap(node["x"], node["y"], pause=1.0)
            return True
    sh("input keyevent KEYCODE_BACK")
    time.sleep(1.0)
    return True


def find(label, contains=False):
    found = nodes()
    if dismiss_system_dialogs(found):
        found = nodes()
    for node in found:
        for value in (node["text"], node["desc"]):
            if value == label or (contains and label in value):
                return node
    return None


def wait_for(label, timeout=20, contains=False):
    end = time.time() + timeout
    while time.time() < end:
        node = find(label, contains)
        if node:
            return node
        time.sleep(0.7)
    return None


def tap(x, y, pause=0.6):
    sh(f"input tap {x} {y}")
    time.sleep(pause)


def tap_label(label, timeout=15, contains=False, pause=0.8):
    node = wait_for(label, timeout, contains)
    if not node:
        print(f"  ! не нашёл «{label}»")
        return False
    tap(node["x"], node["y"], pause)
    return True


def swipe_up(times=6, duration=220, pause=0.35):
    for _ in range(times):
        sh(f"input swipe {W // 2} {int(H * 0.78)} {W // 2} {int(H * 0.28)} {duration}")
        time.sleep(pause)


def swipe_down(times=6, duration=220, pause=0.35):
    for _ in range(times):
        sh(f"input swipe {W // 2} {int(H * 0.3)} {W // 2} {int(H * 0.8)} {duration}")
        time.sleep(pause)


def screenshot(name):
    """Снимок экрана, уменьшенный до JPEG: в лог уходит строками base64."""
    data = subprocess.run(["adb", "exec-out", "screencap", "-p"], capture_output=True, timeout=60).stdout
    path = os.path.join(OUT, f"{name}.png")
    with open(path, "wb") as f:
        f.write(data)
    try:
        from PIL import Image
        image = Image.open(path).convert("RGB")
        image.thumbnail((400, 900))
        image.save(os.path.join(OUT, f"{name}.jpg"), quality=60)
        os.remove(path)
    except Exception as error:
        print("  ! снимок не уменьшен:", error)


def frames():
    """Кадры из буфера gfxinfo: {начало: (работа UI, UI до отправки, полное)}, в мс."""
    raw = sh(f"dumpsys gfxinfo {PKG} framestats")
    result = {}
    for block in re.findall(r"---PROFILEDATA---\n(.*?)---PROFILEDATA---", raw, re.S):
        lines = [l for l in block.strip().splitlines() if l.strip()]
        if not lines:
            continue
        header = lines[0].rstrip(",").split(",")
        idx = {name: i for i, name in enumerate(header)}
        for line in lines[1:]:
            cols = line.rstrip(",").split(",")
            try:
                if int(cols[idx["Flags"]]) != 0:
                    continue
                start = int(cols[idx["IntendedVsync"]])
                handle = int(cols[idx["HandleInputStart"]])
                anim = int(cols[idx["AnimationStart"]])
                ui_end = int(cols[idx["SyncQueued"]])
                done = int(cols[idx["FrameCompleted"]])
            except (KeyError, ValueError, IndexError):
                continue
            begin = handle if handle > 0 else anim
            if start <= 0 or begin <= 0 or ui_end <= begin or done <= start:
                continue
            result[start] = ((ui_end - begin) / 1e6, (ui_end - start) / 1e6, (done - start) / 1e6)
    return result


class Collector:
    """Раз в секунду забирает буфер кадров, пока идёт сценарий."""

    def __init__(self):
        self.frames = {}
        self.stop = threading.Event()
        self.thread = threading.Thread(target=self.run, daemon=True)

    def run(self):
        while not self.stop.wait(1.0):
            try:
                self.frames.update(frames())
            except Exception:
                pass

    def __enter__(self):
        self.thread.start()
        return self

    def __exit__(self, *_):
        self.stop.set()
        self.thread.join()
        self.frames.update(frames())


def summary(raw):
    m = re.search(r"Janky frames: (\d+) \(([\d.]+)%\)", raw)
    slow_ui = re.search(r"Number Slow UI thread: (\d+)", raw)
    return {
        "janky_pct": float(m.group(2)) if m else None,
        "slow_ui": int(slow_ui.group(1)) if slow_ui else None,
    }


def pct(values, p):
    if not values:
        return 0.0
    values = sorted(values)
    return values[min(len(values) - 1, int(round(p / 100 * (len(values) - 1))))]


results = []


def scenario(name, action):
    sh(f"dumpsys gfxinfo {PKG} reset")
    time.sleep(0.3)
    started = time.time()
    with Collector() as collector:
        try:
            action()
        except Exception as error:  # сценарий не должен ронять весь замер
            print(f"  ! {name}: {error}")
    elapsed = time.time() - started
    data = list(collector.frames.values())
    stats = summary(sh(f"dumpsys gfxinfo {PKG}"))
    work = [f[0] for f in data]
    ui = [f[1] for f in data]
    total = [f[2] for f in data]
    share = lambda values, limit: round(100 * sum(1 for v in values if v > limit) / max(1, len(values)), 1)
    row = {
        "name": name,
        "frames": len(data),
        "work_p50": round(pct(work, 50), 1),
        "work_p90": round(pct(work, 90), 1),
        "work_p99": round(pct(work, 99), 1),
        "work_over8": share(work, 8),
        "work_over16": share(work, 16.7),
        "ui_p90": round(pct(ui, 90), 1),
        "total_p90": round(pct(total, 90), 1),
        "janky_pct": stats["janky_pct"],
        "seconds": round(elapsed, 1),
    }
    results.append(row)
    print(json.dumps(row, ensure_ascii=False))
    screenshot(name)


# MARK: Установка и первый запуск

adb("install", "-r", "-g", APK)
sh(f"pm grant {PKG} android.permission.POST_NOTIFICATIONS")
sh("settings put global window_animation_scale 1")
sh("settings put global transition_animation_scale 1")
sh("settings put global animator_duration_scale 1")
# Системные окна «приложение не отвечает» на медленном эмуляторе закрывают экран.
sh("settings put global hide_error_dialogs 1")
sh("am broadcast -a android.intent.action.CLOSE_SYSTEM_DIALOGS")
launch = sh(f"am start -W -n {PKG}/.MainActivity")
m = re.search(r"TotalTime: (\d+)", launch)
cold_start = int(m.group(1)) if m else None
print("холодный запуск, мс:", cold_start)
time.sleep(3)

if wait_for("Техасский метод", 25):
    tap_label("Техасский метод", pause=1.5)
    for _ in range(6):
        if find("Построить программу"):
            break
        swipe_up(1)
    tap_label("Построить программу", pause=3)
screenshot("00-after-onboarding")


def tab(label):
    return lambda: tap_label(label, pause=1.0)


def tabs():
    for _ in range(3):
        for label in ["Тренировка", "Активность", "Друзья", "Профиль", "Главное"]:
            tap_label(label, pause=0.9)


def feed():
    tap_label("Главное", pause=2.5)
    swipe_up(8)
    swipe_down(8)


def hub():
    tap_label("Тренировка", pause=1.2)
    swipe_up(4)
    swipe_down(4)


def progress():
    tap_label("Активность", pause=1.2)
    swipe_up(6)
    swipe_down(6)


def plan():
    tap_label("Тренировка", pause=1.0)
    tap_label("Программы", pause=1.2)
    for week in ["2", "3", "4", "1"]:
        tap_label(week, pause=0.6)
    tap_label("ДЕНЬ 1", contains=True, pause=1.2)
    swipe_up(3)
    sh("input keyevent KEYCODE_BACK")
    time.sleep(0.8)
    sh("input keyevent KEYCODE_BACK")
    time.sleep(0.8)


def workout():
    tap_label("Тренировка", pause=1.0)
    tap_label("НАЧАТЬ ТРЕНИРОВКУ", pause=2.0)
    for _ in range(8):
        node = wait_for("Отметить подход", 4)
        if not node:
            swipe_up(1)
            continue
        tap(node["x"], node["y"], pause=0.7)
    swipe_up(5)
    swipe_down(5)
    # Тренировку сворачиваем — дальше нужна панель вкладок.
    if not tap_label("Свернуть тренировку", timeout=4, pause=1.2):
        sh("input keyevent KEYCODE_BACK")
        time.sleep(1.2)


def community():
    tap_label("Друзья", pause=2.0)
    swipe_up(3)
    swipe_down(3)


def tab_drag():
    """Палец ведёт по панели вкладок туда и обратно — вкладки листаются на ходу."""
    node = wait_for("Главное", 10)
    last = wait_for("Профиль", 10)
    if not node or not last:
        print("  ! панель вкладок не нашлась")
        return
    y = node["y"]
    for _ in range(3):
        sh(f"input swipe {node['x']} {y} {last['x']} {y} 1400")
        time.sleep(0.8)
        sh(f"input swipe {last['x']} {y} {node['x']} {y} 1400")
        time.sleep(0.8)


def post_open():
    """Пост из ленты: экран въезжает справа, «Назад» — обратно."""
    tap_label("Главное", pause=1.5)
    for _ in range(3):
        swipe_up(1)
        node = find("Комментарии")
        if node:
            tap(node["x"], node["y"], pause=1.5)
            swipe_up(2)
            sh("input keyevent KEYCODE_BACK")
            time.sleep(1.2)


scenario("tabs", tabs)
scenario("feed-scroll", feed)
scenario("workout-hub-scroll", hub)
scenario("progress-scroll", progress)
scenario("plan", plan)
scenario("workout-sets", workout)
scenario("community", community)
scenario("tab-drag", tab_drag)
scenario("post-open", post_open)

# MARK: Отчёт

with open(os.path.join(OUT, "results.json"), "w") as f:
    json.dump({"cold_start_ms": cold_start, "scenarios": results}, f, ensure_ascii=False, indent=1)

lines = [
    f"### Плавность Android (эмулятор)\n",
    f"Холодный запуск: **{cold_start} мс**\n",
    "Работа — сколько UI-поток сам тратит на кадр (ввод, анимации, компоновка, запись отрисовки). "
    "Бюджет кадра на 60 Гц — 16,7 мс, на 120 Гц — 8,3 мс.\n",
    "| Сценарий | Кадров | Работа p50 | p90 | p99 | > 8 мс | > 16 мс | UI p90 | Полный p90 | Janky (система) |",
    "|---|---|---|---|---|---|---|---|---|---|",
]
for r in results:
    lines.append(
        f"| {r['name']} | {r['frames']} | {r['work_p50']} | {r['work_p90']} | {r['work_p99']} | "
        f"{r['work_over8']}% | {r['work_over16']}% | {r['ui_p90']} | {r['total_p90']} | {r['janky_pct']}% |"
    )
report = "\n".join(lines)
print(report)
with open(os.path.join(OUT, "report.md"), "w") as f:
    f.write(report + "\n")

# Снимки — в лог строками base64, как у snapshots.yml.
for name in sorted(os.listdir(OUT)):
    if not name.endswith(".jpg"):
        continue
    data = base64.b64encode(open(os.path.join(OUT, name), "rb").read()).decode()
    chunks = [data[i:i + 4000] for i in range(0, len(data), 4000)]
    for index, chunk in enumerate(chunks):
        print(f"SNAP {os.path.basename(OUT)}/{name} {index + 1}/{len(chunks)} {chunk}")
