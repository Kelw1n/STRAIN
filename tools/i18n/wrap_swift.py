"""Оборачивает русские литералы в подписях SwiftUI в L(...).

Берётся первый аргумент Text(, Button(, Label(, Toggle(, … и .navigationTitle(, .alert( и т. п.
Внутри него каждый строковый литерал с русскими буквами становится L("…").
Повторный запуск ничего не меняет: уже обёрнутые литералы пропускаются.
"""
import glob, os, re, sys
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from extract import read, CYR

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))
APIS = ['Text', 'Button', 'Label', 'Toggle', 'Picker', 'Section', 'TextField', 'SecureField', 'Stepper',
        'DatePicker', 'LabeledContent', 'Menu', 'Link']
MODS = ['navigationTitle', 'alert', 'confirmationDialog', 'accessibilityLabel', 'accessibilityHint', 'accessibilityValue']
CALL = re.compile(r'(?<![\w.])(' + '|'.join(APIS) + r')\(|\.(' + '|'.join(MODS) + r')\(')

def tokens(text):
    """Позиции вне строк и комментариев: [(start, end, kind)] для литералов."""
    lits, i, n = [], 0, len(text)
    while i < n:
        if text.startswith('//', i):
            j = text.find('\n', i); i = n if j < 0 else j; continue
        if text.startswith('/*', i):
            j = text.find('*/', i + 2); i = n if j < 0 else j + 2; continue
        if text[i] == '"':
            triple = text.startswith('"""', i)
            start = i
            lit, _, i = read(text, i + (3 if triple else 1), 'swift', triple)
            lits.append((start, i, lit, triple))
            continue
        i += 1
    return lits

def first_arg_end(text, i, lits_by_start):
    """Конец первого аргумента: запятая или скобка на нулевой глубине."""
    depth, n = 0, len(text)
    while i < n:
        if i in lits_by_start:
            i = lits_by_start[i]; continue
        c = text[i]
        if c in '([{': depth += 1
        elif c in ')]}':
            if depth == 0: return i
            depth -= 1
        elif c == ',' and depth == 0: return i
        elif text.startswith('//', i):
            j = text.find('\n', i); i = n if j < 0 else j; continue
        i += 1
    return n

def process(path):
    text = open(path, encoding='utf-8').read()
    lits = tokens(text)
    starts = {s: e for s, e, _, _ in lits}
    inside = lambda pos: any(s < pos < e for s, e, _, _ in lits)
    wraps = set()
    for m in CALL.finditer(text):
        if inside(m.start()): continue
        arg_start = m.end()
        # Именованный первый аргумент (verbatim:, value:, …) — не подпись.
        if re.match(r'\s*[A-Za-z_]\w*\s*:(?!:)', text[arg_start:arg_start + 40]): continue
        end = first_arg_end(text, arg_start, starts)
        for s, e, lit, triple in lits:
            if s >= arg_start and e <= end and CYR.search(lit) and not triple:
                before = text[max(0, s - 2):s]
                if before == 'L(' : continue
                wraps.add((s, e))
    if not wraps: return 0
    out, last = [], 0
    for s, e in sorted(wraps):
        out.append(text[last:s]); out.append('L(' + text[s:e] + ')'); last = e
    out.append(text[last:])
    open(path, 'w', encoding='utf-8').write(''.join(out))
    return len(wraps)

if __name__ == '__main__':
    total = 0
    for path in sorted(glob.glob(f'{ROOT}/TexasProgram/**/*.swift', recursive=True)):
        if path.endswith('Localization.swift'): continue
        total += process(path)
    print('wrapped', total)
