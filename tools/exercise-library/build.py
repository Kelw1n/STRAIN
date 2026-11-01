#!/usr/bin/env python3
"""Собирает библиотеку упражнений для iPhone и Android.

Текст техники и мышцы — из открытого набора Exercises Dataset
(https://github.com/hasaneyldrm/exercises-dataset, лицензия MIT на данные и
тексты; картинки оттуда НЕ берём — они © Gym visual). Русские названия,
группы и инвентарь — свои.

    python3 build.py путь/к/exercises.json

Пишет один и тот же файл в оба приложения:
    TexasProgram/Resources/exercise-library.json
    android/app/src/main/assets/exercise-library.json
"""

import json
import os
import sys

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), "..", ".."))
OUTPUTS = [
    os.path.join(ROOT, "TexasProgram", "Resources", "exercise-library.json"),
    os.path.join(ROOT, "android", "app", "src", "main", "assets", "exercise-library.json"),
]

# Группа мышц — ключ, по нему фильтр «Мышцы» и разделы списка.
GROUPS = {
    "chest": "Грудь", "back": "Спина", "shoulders": "Плечи", "biceps": "Бицепс", "triceps": "Трицепс",
    "forearms": "Предплечья", "quads": "Квадрицепс", "hamstrings": "Бицепс бедра", "glutes": "Ягодицы",
    "calves": "Икры", "abs": "Пресс", "cardio": "Кардио",
}

EQUIPMENT = {
    "barbell": "Штанга", "dumbbell": "Гантели", "machine": "Тренажёр", "cable": "Блок", "body": "Свой вес",
    "kettlebell": "Гиря", "smith": "Смит", "ez": "EZ-гриф", "band": "Резина", "trapbar": "Трэп-гриф", "other": "Другое",
}

# Мышцы набора → наши группы (для «второстепенных» мышц).
MUSCLES = {
    "pectorals": "chest", "chest": "chest", "upper chest": "chest", "serratus anterior": "chest",
    "lats": "back", "upper back": "back", "back": "back", "traps": "back", "trapezius": "back", "rhomboids": "back",
    "lower back": "back", "spine": "back", "latissimus dorsi": "back",
    "delts": "shoulders", "shoulders": "shoulders", "rear deltoids": "shoulders", "deltoids": "shoulders", "rotator cuff": "shoulders",
    "biceps": "biceps", "brachialis": "biceps",
    "triceps": "triceps",
    "forearms": "forearms", "wrists": "forearms", "wrist flexors": "forearms", "wrist extensors": "forearms", "grip muscles": "forearms", "hands": "forearms",
    "quads": "quads", "quadriceps": "quads", "hip flexors": "quads",
    "hamstrings": "hamstrings",
    "glutes": "glutes", "abductors": "glutes", "adductors": "quads", "inner thighs": "quads",
    "calves": "calves", "soleus": "calves", "ankles": "calves", "shins": "calves",
    "abs": "abs", "obliques": "abs", "core": "abs", "lower abs": "abs",
    "cardiovascular system": "cardio",
}

# (имя в наборе, ключ, русское название, группа, инвентарь)
CATALOG = [
    # Грудь
    ("barbell bench press", "bench_press", "Жим штанги лёжа", "chest", "barbell"),
    ("barbell incline bench press", "incline_bench_press", "Жим штанги на наклонной", "chest", "barbell"),
    ("barbell decline bench press", "decline_bench_press", "Жим штанги на скамье с обратным наклоном", "chest", "barbell"),
    ("barbell close-grip bench press", "close_grip_bench", "Жим лёжа узким хватом", "triceps", "barbell"),
    ("dumbbell bench press", "db_bench_press", "Жим гантелей лёжа", "chest", "dumbbell"),
    ("dumbbell incline bench press", "db_incline_press", "Жим гантелей на наклонной", "chest", "dumbbell"),
    ("dumbbell fly", "db_fly", "Разведение гантелей лёжа", "chest", "dumbbell"),
    ("dumbbell incline fly", "db_incline_fly", "Разведение гантелей на наклонной", "chest", "dumbbell"),
    ("cable cross-over variation", "cable_crossover", "Сведение в кроссовере", "chest", "cable"),
    ("cable upper chest crossovers", "cable_low_crossover", "Сведение в кроссовере снизу вверх", "chest", "cable"),
    ("lever seated fly", "pec_deck", "Сведение в тренажёре (бабочка)", "chest", "machine"),
    ("lever chest press", "machine_chest_press", "Жим от груди в тренажёре", "chest", "machine"),
    ("lever incline chest press", "machine_incline_press", "Жим на наклонной в тренажёре", "chest", "machine"),
    ("smith bench press", "smith_bench_press", "Жим лёжа в Смите", "chest", "smith"),
    ("chest dip", "chest_dip", "Отжимания на брусьях", "chest", "body"),
    ("push-up", "push_up", "Отжимания", "chest", "body"),
    ("dumbbell pullover", "db_pullover", "Пуловер с гантелью лёжа", "chest", "dumbbell"),
    # Спина
    ("barbell deadlift", "deadlift", "Становая тяга", "back", "barbell"),
    ("barbell sumo deadlift", "sumo_deadlift", "Становая тяга сумо", "glutes", "barbell"),
    ("trap bar deadlift", "trap_bar_deadlift", "Тяга трэп-грифа", "back", "trapbar"),
    ("barbell rack pull", "rack_pull", "Тяга с плинтов", "back", "barbell"),
    ("barbell bent over row", "barbell_row", "Тяга штанги в наклоне", "back", "barbell"),
    ("dumbbell one arm bent-over row", "db_row", "Тяга гантели в наклоне", "back", "dumbbell"),
    ("lever t bar row", "t_bar_row", "Тяга Т-грифа", "back", "machine"),
    ("cable seated row", "cable_row", "Тяга горизонтального блока", "back", "cable"),
    ("lever seated row", "machine_row", "Тяга в тренажёре сидя", "back", "machine"),
    ("lever high row", "machine_high_row", "Верхняя тяга в рычажном тренажёре", "back", "machine"),
    ("cable pulldown", "lat_pulldown", "Тяга верхнего блока", "back", "cable"),
    ("cable bar lateral pulldown", "lat_pulldown_wide", "Тяга верхнего блока широким хватом", "back", "cable"),
    ("cable straight arm pulldown", "straight_arm_pulldown", "Пуловер на верхнем блоке прямыми руками", "back", "cable"),
    ("pull-up", "pull_up", "Подтягивания", "back", "body"),
    ("chin-up", "chin_up", "Подтягивания обратным хватом", "back", "body"),
    ("inverted row", "inverted_row", "Австралийские подтягивания", "back", "body"),
    ("hyperextension", "hyperextension", "Гиперэкстензия", "back", "body"),
    ("barbell good morning", "good_morning", "Гуд монинг", "hamstrings", "barbell"),
    ("barbell shrug", "barbell_shrug", "Шраги со штангой", "back", "barbell"),
    ("dumbbell shrug", "db_shrug", "Шраги с гантелями", "back", "dumbbell"),
    # Плечи
    ("barbell standing wide military press", "ohp", "Жим штанги стоя", "shoulders", "barbell"),
    ("barbell seated overhead press", "seated_ohp", "Жим штанги сидя", "shoulders", "barbell"),
    ("dumbbell seated shoulder press", "db_shoulder_press", "Жим гантелей сидя", "shoulders", "dumbbell"),
    ("dumbbell standing overhead press", "db_standing_press", "Жим гантелей стоя", "shoulders", "dumbbell"),
    ("dumbbell arnold press", "arnold_press", "Жим Арнольда", "shoulders", "dumbbell"),
    ("lever shoulder press", "machine_shoulder_press", "Жим в тренажёре сидя", "shoulders", "machine"),
    ("dumbbell lateral raise", "lateral_raise", "Махи гантелями в стороны", "shoulders", "dumbbell"),
    ("cable lateral raise", "cable_lateral_raise", "Махи в сторону на блоке", "shoulders", "cable"),
    ("dumbbell front raise", "front_raise", "Подъём гантелей перед собой", "shoulders", "dumbbell"),
    ("dumbbell rear fly", "rear_delt_fly", "Разведение гантелей в наклоне", "shoulders", "dumbbell"),
    ("lever seated reverse fly", "reverse_pec_deck", "Обратная бабочка", "shoulders", "machine"),
    ("cable rear delt row (with rope)", "face_pull", "Тяга каната к лицу", "shoulders", "cable"),
    ("barbell upright row", "upright_row", "Тяга штанги к подбородку", "shoulders", "barbell"),
    # Бицепс
    ("barbell curl", "barbell_curl", "Подъём штанги на бицепс", "biceps", "barbell"),
    ("ez barbell curl", "ez_curl", "Подъём EZ-грифа на бицепс", "biceps", "ez"),
    ("dumbbell biceps curl", "db_curl", "Подъём гантелей на бицепс", "biceps", "dumbbell"),
    ("dumbbell hammer curl", "hammer_curl", "Молотки с гантелями", "biceps", "dumbbell"),
    ("dumbbell incline curl", "incline_curl", "Сгибания с гантелями на наклонной", "biceps", "dumbbell"),
    ("dumbbell concentration curl", "concentration_curl", "Концентрированные сгибания", "biceps", "dumbbell"),
    ("barbell preacher curl", "preacher_curl", "Сгибания на скамье Скотта", "biceps", "barbell"),
    ("lever preacher curl", "machine_preacher_curl", "Сгибания в тренажёре Скотта", "biceps", "machine"),
    ("cable curl", "cable_curl", "Сгибания на нижнем блоке", "biceps", "cable"),
    ("cable hammer curl (with rope)", "cable_hammer_curl", "Молотки на блоке с канатом", "biceps", "cable"),
    # Трицепс
    ("cable pushdown", "triceps_pushdown", "Разгибания на верхнем блоке", "triceps", "cable"),
    ("cable pushdown (with rope attachment)", "rope_pushdown", "Разгибания на блоке с канатом", "triceps", "cable"),
    ("cable high pulley overhead tricep extension", "overhead_cable_extension", "Разгибания из-за головы на блоке", "triceps", "cable"),
    ("barbell lying triceps extension skull crusher", "skull_crusher", "Французский жим лёжа", "triceps", "barbell"),
    ("dumbbell seated triceps extension", "db_overhead_extension", "Французский жим гантели сидя", "triceps", "dumbbell"),
    ("triceps dip", "triceps_dip", "Отжимания на брусьях на трицепс", "triceps", "body"),
    ("bench dip (knees bent)", "bench_dip", "Обратные отжимания от скамьи", "triceps", "body"),
    ("lever triceps extension", "machine_triceps_extension", "Разгибания в тренажёре", "triceps", "machine"),
    # Предплечья
    ("barbell wrist curl", "wrist_curl", "Сгибания запястий со штангой", "forearms", "barbell"),
    ("farmers walk", "farmers_walk", "Прогулка фермера", "forearms", "dumbbell"),
    # Ноги
    ("barbell full squat", "squat", "Приседания со штангой", "quads", "barbell"),
    ("barbell front squat", "front_squat", "Фронтальные приседания", "quads", "barbell"),
    ("smith squat", "smith_squat", "Приседания в Смите", "quads", "smith"),
    ("sled hack squat", "hack_squat", "Гакк-приседания", "quads", "machine"),
    ("dumbbell goblet squat", "goblet_squat", "Гоблет-приседания", "quads", "dumbbell"),
    ("sled 45° leg press (side pov)", "leg_press", "Жим ногами", "quads", "machine"),
    ("lever leg extension", "leg_extension", "Разгибания ног в тренажёре", "quads", "machine"),
    ("barbell lunge", "barbell_lunge", "Выпады со штангой", "quads", "barbell"),
    ("dumbbell lunge", "db_lunge", "Выпады с гантелями", "quads", "dumbbell"),
    ("walking lunge", "walking_lunge", "Выпады в ходьбе", "quads", "body"),
    ("dumbbell single leg split squat", "bulgarian_split_squat", "Болгарские выпады", "quads", "dumbbell"),
    ("dumbbell step-up", "step_up", "Зашагивания на тумбу", "quads", "dumbbell"),
    ("barbell romanian deadlift", "romanian_deadlift", "Румынская тяга", "hamstrings", "barbell"),
    ("dumbbell romanian deadlift", "db_romanian_deadlift", "Румынская тяга с гантелями", "hamstrings", "dumbbell"),
    ("barbell straight leg deadlift", "stiff_leg_deadlift", "Становая тяга на прямых ногах", "hamstrings", "barbell"),
    ("lever lying leg curl", "lying_leg_curl", "Сгибание ног лёжа", "hamstrings", "machine"),
    ("lever seated leg curl", "seated_leg_curl", "Сгибание ног сидя", "hamstrings", "machine"),
    ("barbell glute bridge", "hip_thrust", "Ягодичный мост со штангой", "glutes", "barbell"),
    ("cable pull through (with rope)", "cable_pull_through", "Протяжка на блоке между ног", "glutes", "cable"),
    ("cable kickback", "cable_kickback", "Отведение ноги назад на блоке", "glutes", "cable"),
    ("lever seated hip abduction", "hip_abduction", "Разведение ног в тренажёре", "glutes", "machine"),
    ("lever seated hip adduction", "hip_adduction", "Сведение ног в тренажёре", "quads", "machine"),
    ("smith sumo squat", "sumo_squat", "Приседания сумо в Смите", "glutes", "smith"),
    ("lever standing calf raise", "standing_calf_raise", "Подъём на носки стоя", "calves", "machine"),
    ("lever seated calf raise", "seated_calf_raise", "Подъём на носки сидя", "calves", "machine"),
    ("sled 45в° calf press", "leg_press_calf", "Подъём на носки в жиме ногами", "calves", "machine"),
    # Пресс
    ("crunch floor", "crunch", "Скручивания", "abs", "body"),
    ("cable kneeling crunch", "cable_crunch", "Скручивания на блоке стоя на коленях", "abs", "cable"),
    ("hanging leg raise", "hanging_leg_raise", "Подъём ног в висе", "abs", "body"),
    ("lying leg raise flat bench", "lying_leg_raise", "Подъём ног лёжа", "abs", "body"),
    ("weighted front plank", "plank", "Планка", "abs", "body"),
    ("russian twist", "russian_twist", "Русские скручивания", "abs", "body"),
    ("dead bug", "dead_bug", "Мёртвый жук", "abs", "body"),
    ("wheel run", "ab_wheel", "Ролик для пресса", "abs", "other"),
    ("reverse crunch", "reverse_crunch", "Обратные скручивания", "abs", "body"),
    # Кардио и функционал
    ("kettlebell swing", "kettlebell_swing", "Махи гирей", "glutes", "kettlebell"),
    ("burpee", "burpee", "Бёрпи", "cardio", "body"),
    ("mountain climber", "mountain_climber", "Скалолаз", "cardio", "body"),
    ("jump squat", "jump_squat", "Приседания с выпрыгиванием", "cardio", "body"),
]


def steps_ru(record):
    steps = (record.get("instruction_steps") or {}).get("ru") or []
    # Последний шаг набора почти всегда «Повторите нужное число раз» — он ничего не добавляет.
    cleaned = [s.strip() for s in steps if s.strip()]
    if cleaned and cleaned[-1].lower().startswith("повтор"):
        cleaned = cleaned[:-1]
    return cleaned


def secondary(record, group):
    result = []
    for muscle in [record.get("target", "")] + list(record.get("secondary_muscles") or []):
        key = MUSCLES.get(str(muscle).lower())
        if key and key != group and key not in result:
            result.append(key)
    return result


def main():
    if len(sys.argv) < 2:
        sys.exit("Укажи путь к data/exercises.json из Exercises Dataset")
    with open(sys.argv[1], encoding="utf-8") as f:
        dataset = {item["name"]: item for item in json.load(f)}

    seen = set()
    exercises = []
    for source, key, name, group, equipment in CATALOG:
        if source not in dataset:
            sys.exit(f"В наборе нет «{source}»")
        if key in seen:
            sys.exit(f"Ключ повторяется: {key}")
        seen.add(key)
        assert group in GROUPS and equipment in EQUIPMENT, key
        record = dataset[source]
        exercises.append({
            "id": key,
            "name": name,
            "group": group,
            "secondary": secondary(record, group),
            "equipment": equipment,
            "steps": steps_ru(record),
        })

    library = {
        "source": "Техника — Exercises Dataset (MIT), github.com/hasaneyldrm/exercises-dataset",
        "groups": [{"id": k, "title": v} for k, v in GROUPS.items()],
        "equipment": [{"id": k, "title": v} for k, v in EQUIPMENT.items()],
        "exercises": exercises,
    }
    text = json.dumps(library, ensure_ascii=False, indent=1) + "\n"
    for path in OUTPUTS:
        os.makedirs(os.path.dirname(path), exist_ok=True)
        with open(path, "w", encoding="utf-8") as f:
            f.write(text)
    print(f"{len(exercises)} упражнений → {', '.join(os.path.relpath(p, ROOT) for p in OUTPUTS)}")


if __name__ == "__main__":
    main()
