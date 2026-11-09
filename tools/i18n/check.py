"""Русские строки из кода, которых нет в словаре en.json.

Запуск: python3 tools/i18n/check.py — печатает недостающие фразы.
Строки-подсказки для ИИ и служебные (их на экране нет) перечислены в SKIP.
"""
import json, os, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import collect, ROOT

SKIP_MARKERS = ['JSON', 'Ты — тренер', 'ДАННЫЕ', 'Проанализируй фото', '(?', '[а-я', '\\d', 'reply in English', 'in English']

def main():
    en = json.load(open(os.path.join(ROOT, 'tools/i18n/en.json'), encoding='utf-8'))
    missing = []
    for lit, info in sorted(collect().items()):
        if lit in en or lit.strip() in en: continue
        if any(m in lit for m in SKIP_MARKERS): continue
        missing.append((lit, sorted(info['files'])))
    for lit, where in missing:
        print(json.dumps(lit, ensure_ascii=False), where)
    print(len(missing), 'missing', file=sys.stderr)

if __name__ == '__main__':
    main()
