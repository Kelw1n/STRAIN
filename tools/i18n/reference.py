"""Эталон перевода на Python: тот же порядок правил, что в Localization.swift и Localization.kt.

Запуск: python3 tools/i18n/reference.py tools/i18n/en.json "фраза" ... — печатает перевод.
"""
import json, re, sys
CYR = re.compile('[Ѐ-ӿ]')
NUM = re.compile(r'^([+−-]?\d[\d .,]*?) (\D.*)$', re.S)
TAILNUM = re.compile(r'^(\D.*?) (\d[\d .,]*)$', re.S)
SEPS = [" · ", "\n", "; ", ", "]
def pattern(k):
    parts = k.split('{}')
    pat = '^' + re.escape(parts[0])
    for i in range(1, len(parts)):
        tight = (parts[i-1][-1:] and CYR.match(parts[i-1][-1])) or (parts[i][:1] and CYR.match(parts[i][0]))
        pat += ('([^\u0400-\u04FF]+?)' if tight else '(.+?)') + re.escape(parts[i])
    return pat + '$'
class T:
    def __init__(self, d):
        self.exact = {}; tpl = []
        for k, v in d.items():
            if '{}' not in k: self.exact[k] = v; continue
            lit = k.replace('{}', '')
            if not CYR.search(lit): continue
            pat = pattern(k)
            weak = len(re.sub(r'[^\u0400-\u04FFA-Za-z]', '', lit)) <= 3
            tpl.append((re.compile(pat, re.S), v, len(lit), weak, k))
            if k.upper() != k:
                tpl.append((re.compile(pattern(k.upper()), re.S), v.upper(), len(lit), weak, k.upper()))
        self.tpl = [(r, v, w) for r, v, _, w, _ in sorted(tpl, key=lambda x: (-x[2], x[4]))]
        frag = lambda k: k != k.strip()
        for k, v in list(self.exact.items()):
            if frag(k) and k.strip() and k.strip() not in self.exact: self.exact[k.strip()] = v.strip()
        self.upper = {k.upper(): v.upper() for k, v in self.exact.items()}
        self.lower = {k.lower(): v for k, v in self.exact.items()}
        plain = [(k, v) for k, v in d.items() if '{}' not in k and len(k.strip()) >= 2 and CYR.search(k)]
        self.prefixes = sorted([(k, v) for k, v in plain if k.endswith(' ')], key=lambda x: -len(x[0]))
        self.suffixes = sorted([(k, v) for k, v in plain if not (k[0].isalnum() or k[0] in '«"(')], key=lambda x: -len(x[0]))
    def tr(self, text, depth=0):
        if not CYR.search(text): return text
        if depth == 0: text = text.replace('\u00a0', ' ').replace('\u202f', ' ')
        if text in self.exact: return self.exact[text]
        t = text.strip()
        if t != text and t in self.exact: return text.replace(t, self.exact[t])
        if t == t.upper() and t in self.upper: return text.replace(t, self.upper[t])
        if t == t.lower() and t in self.lower: return text.replace(t, lowfirst(self.lower[t]))
        if depth >= 3: return text
        for r, v, weak in self.tpl:
            m = r.match(text)
            if m:
                out = fill(v, [self.tr(g, depth + 1) for g in m.groups()])
                # Короткий шаблон («{} и {}») с недопереведённым остатком даст кашу из двух языков.
                if weak and CYR.search(out): continue
                return out
        m = NUM.match(text)
        if m:
            rest = self.tr(m.group(2), depth + 1)
            if not CYR.search(rest): return m.group(1) + ' ' + rest
        m = TAILNUM.match(text)
        if m:
            head = self.tr(m.group(1), depth + 1)
            if not CYR.search(head): return head + ' ' + m.group(2)
        for k, v in self.prefixes:
            if text.startswith(k) and len(text) > len(k):
                return v + self.tr(text[len(k):], depth + 1)
        for k, v in self.suffixes:
            if text.endswith(k) and len(text) > len(k):
                return self.tr(text[:-len(k)], depth + 1) + v
        for s in SEPS:
            if s in text:
                return s.join(self.tr(p, depth + 1) for p in text.split(s))
        return text
def lowfirst(v):
    return v[:1].lower() + v[1:] if len(v) > 1 and v[1:2].islower() else v
def fill(target, vals):
    out, nxt, i = [], 0, 0
    while i < len(target):
        c = target[i]
        if c == '{':
            j = target.find('}', i)
            if j > i:
                inner = target[i+1:j]
                if inner == '':
                    out.append(vals[nxt] if nxt < len(vals) else ''); nxt += 1; i = j + 1; continue
                if inner.isdigit() and int(inner) >= 1:
                    n = int(inner); out.append(vals[n-1] if n-1 < len(vals) else ''); i = j + 1; continue
        out.append(c); i += 1
    return ''.join(out)
if __name__ == '__main__':
    t = T(json.load(open(sys.argv[1], encoding='utf-8')))
    print(len(t.prefixes), 'prefixes', len(t.suffixes), 'suffixes')
    for s in sys.argv[2:]: print(repr(s), '->', repr(t.tr(s)))
