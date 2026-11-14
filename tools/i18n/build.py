"""Раскладывает словарь tools/i18n/en.json по приложениям.

iOS:     TexasProgram/Resources/i18n-en.json
Android: android/app/src/main/assets/i18n-en.json
Файл один и тот же: перевод на обеих платформах совпадает дословно.
"""
import json, os
ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
en = json.load(open(os.path.join(ROOT, 'tools/i18n/en.json'), encoding='utf-8'))
data = json.dumps(dict(sorted(en.items())), ensure_ascii=False, separators=(',', ':'))
for rel in ['TexasProgram/Resources/i18n-en.json', 'android/app/src/main/assets/i18n-en.json']:
    with open(os.path.join(ROOT, rel), 'w', encoding='utf-8') as f:
        f.write(data)
print(len(en), 'phrases')
