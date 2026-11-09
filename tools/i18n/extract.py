"""Собирает русские строки из кода iOS и Android в один список шаблонов.

Подстановки в строке (`\\(x)` в Swift, `${x}` и `$x` в Kotlin) заменяются на `{}`:
так одна и та же фраза с числом внутри узнаётся на обеих платформах.
Запуск: python3 tools/i18n/extract.py  → tools/i18n/strings.json
"""
import glob, json, os, re, sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
CYR = re.compile('[А-Яа-яЁё]')

def scan(text, lang):
    """Строковые литералы файла: (шаблон, [выражения подстановок])."""
    out = []
    i, n = 0, len(text)
    while i < n:
        c = text[i]
        if text.startswith('//', i):
            j = text.find('\n', i); i = n if j < 0 else j; continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if c == "'" and lang == 'kt':  # символ: 'a', '\n'
            j = i + 1
            while j < n and text[j] != "'":
                j += 2 if text[j] == '\\' else 1
            i = j + 1; continue
        if c == '"':
            if text.startswith('"""', i):
                lit, exprs, i = read(text, i + 3, lang, triple=True)
            else:
                lit, exprs, i = read(text, i + 1, lang, triple=False)
            out.append((lit, exprs))
            continue
        i += 1
    return out

def read(text, i, lang, triple):
    parts, exprs = [], []
    n = len(text)
    while i < n:
        if triple and text.startswith('"""', i):
            return ''.join(parts), exprs, i + 3
        c = text[i]
        if not triple and c == '"':
            return ''.join(parts), exprs, i + 1
        if not triple and c == '\n':
            return ''.join(parts), exprs, i + 1
        if c == '\\':
            nxt = text[i + 1] if i + 1 < n else ''
            if lang == 'swift' and nxt == '(':
                expr, i = balanced(text, i + 2, '(', ')', lang)
                parts.append('{}'); exprs.append(expr); continue
            esc = {'n': '\n', 't': '\t', '"': '"', '\\': '\\', "'": "'", '$': '$', '0': '\0', 'r': '\r'}
            if nxt == 'u' and lang == 'swift' and text[i + 2:i + 3] == '{':
                j = text.find('}', i)
                parts.append(chr(int(text[i + 3:j], 16))); i = j + 1; continue
            if nxt == 'u' and lang == 'kt':
                parts.append(chr(int(text[i + 2:i + 6], 16))); i += 6; continue
            parts.append(esc.get(nxt, nxt)); i += 2; continue
        if lang == 'kt' and c == '$':
            if text[i + 1:i + 2] == '{':
                expr, i = balanced(text, i + 2, '{', '}', lang)
                parts.append('{}'); exprs.append(expr); continue
            m = re.match(r'[A-Za-z_][A-Za-z0-9_]*', text[i + 1:])
            if m:
                parts.append('{}'); exprs.append(m.group(0)); i += 1 + len(m.group(0)); continue
        parts.append(c); i += 1
    return ''.join(parts), exprs, i

def balanced(text, i, open_, close, lang):
    depth, start, n = 1, i, len(text)
    while i < n:
        c = text[i]
        if c == '"':
            _, _, i = read(text, i + 1, lang, triple=False); continue
        if c == open_: depth += 1
        elif c == close:
            depth -= 1
            if depth == 0: return text[start:i], i + 1
        i += 1
    return text[start:], n

def collect():
    found = {}
    sources = [(p, 'swift') for p in glob.glob(f'{ROOT}/TexasProgram/**/*.swift', recursive=True)]
    sources += [(p, 'swift') for p in glob.glob(f'{ROOT}/StrainWidgets/**/*.swift', recursive=True)]
    sources += [(p, 'kt') for p in glob.glob(f'{ROOT}/android/app/src/main/**/*.kt', recursive=True)]
    for path, lang in sorted(sources):
        rel = os.path.relpath(path, ROOT)
        for lit, exprs in scan(open(path, encoding='utf-8').read(), lang):
            if not CYR.search(lit): continue
            entry = found.setdefault(lit, {'files': set(), 'specifiers': set()})
            entry['files'].add('ios' if lang == 'swift' else 'android')
            for k, e in enumerate(exprs):
                m = re.search(r'specifier:\s*"([^"]+)"', e)
                if m: entry['specifiers'].add((k, m.group(1)))
    return found

if __name__ == '__main__':
    found = collect()
    data = {k: {'where': sorted(v['files']), 'specifiers': sorted(v['specifiers'])} for k, v in sorted(found.items())}
    out = os.path.join(ROOT, 'tools/i18n/strings.json')
    json.dump(data, open(out, 'w', encoding='utf-8'), ensure_ascii=False, indent=1)
    print(len(data), 'strings,', sum(1 for k in data if '{}' in k), 'templates')
