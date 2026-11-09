"""Добавляет недостающие импорты перевода в Kotlin-файлы Android.

Запуск: python3 tools/i18n/imports.py — после правок с `L(...)`, `L10n.` и т. п.
"""
import os, re
ROOT = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__)))), 'android/app/src')
NEEDS = {name: re.compile(r'(?<![\w.])' + re.escape(name) + tail) for name, tail in
         [('L', r'\('), ('L10n', r'\.'), ('LocalizedDateFormat', r'\('), ('AppLanguage', r'\.')]}
for sub in ['main/java', 'snapshots/java', 'test/java']:
    for folder, _, files in os.walk(os.path.join(ROOT, sub)):
        for name in files:
            if not name.endswith('.kt'): continue
            path = os.path.join(folder, name)
            text = open(path, encoding='utf-8').read()
            if re.search(r'^package com\.texasprogram\.app\.service$', text, re.M): continue
            code = re.sub(r'//[^\n]*', '', text)
            added = []
            for symbol, pattern in NEEDS.items():
                line = f'import com.texasprogram.app.service.{symbol}\n'
                if pattern.search(code) and line not in text:
                    first = re.search(r'^import ', text, re.M)
                    text = text[:first.start()] + line + text[first.start():]
                    added.append(symbol)
            if added:
                open(path, 'w', encoding='utf-8').write(text)
                print(os.path.relpath(path, ROOT), added)
