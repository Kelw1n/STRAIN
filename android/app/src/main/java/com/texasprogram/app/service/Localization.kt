package com.texasprogram.app.service

import android.content.Context
import org.json.JSONObject
import java.time.format.DateTimeFormatter
import java.time.temporal.TemporalAccessor
import java.util.Locale

/// Язык интерфейса на выбор в настройках. «Как в системе» — русский, если
/// телефон на русском (или близком языке), иначе английский. Те же правила — в Localization.swift.
enum class AppLanguage(val code: String) {
    SYSTEM("system"), RU("ru"), EN("en");

    val title: String
        get() = when (this) {
            SYSTEM -> if (L10n.isEnglish) "System" else "Как в системе"
            RU -> "Русский"
            EN -> "English"
        }

    val resolved: AppLanguage
        get() = when (this) {
            RU, EN -> this
            SYSTEM -> {
                val preferred = Locale.getDefault().language.lowercase()
                val russianLike = setOf("ru", "uk", "be", "kk", "ky", "uz", "hy", "az", "ka", "tg")
                if (preferred in russianLike) RU else EN
            }
        }

    companion object {
        const val PREFS = "strain_settings"
        const val KEY = "app_language"

        fun from(code: String?): AppLanguage = entries.firstOrNull { it.code == code } ?: SYSTEM

        fun current(context: Context): AppLanguage =
            from(context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null))

        fun save(context: Context, language: AppLanguage) {
            context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, language.code).apply()
            L10n.setEnglish(language.resolved == EN)
        }
    }
}

/// Перевод с русского на английский по общему с iOS словарю `i18n-en.json`.
///
/// Ключ — русская фраза как она есть в коде; подстановки (числа, имена)
/// помечены `{}`. Собранная во время работы фраза («Неделя 3 из 12») узнаётся
/// по шаблону «Неделя {} из {}», а подставленные куски переводятся отдельно.
object L10n {
    private class Template(val regex: Regex, val target: String)

    @Volatile private var exact: Map<String, String> = emptyMap()
    @Volatile private var templates: List<Template> = emptyList()
    @Volatile private var loaded = false
    private var appContext: Context? = null
    @Volatile var isEnglish: Boolean = false
        private set
    private val cache = HashMap<String, String>()

    /// Запоминает выбранный язык. Словарь читается позже, при первом переводе:
    /// на русском он не нужен вовсе.
    fun init(context: Context) {
        appContext = context.applicationContext
        isEnglish = AppLanguage.current(context).resolved == AppLanguage.EN
    }

    @Synchronized
    private fun ensureLoaded() {
        if (loaded) return
        appContext?.let(::load)
        loaded = true
    }

    fun setEnglish(value: Boolean) {
        isEnglish = value
        synchronized(cache) { cache.clear() }
    }

    private fun load(context: Context) {
        val text = runCatching { context.assets.open("i18n-en.json").bufferedReader().use { it.readText() } }.getOrNull() ?: return
        val json = JSONObject(text)
        val plain = HashMap<String, String>()
        val patterns = ArrayList<Pair<Template, Int>>()
        for (key in json.keys()) {
            val value = json.getString(key)
            if (!key.contains("{}")) { plain[key] = value; continue }
            val literal = key.replace("{}", "")
            // Шаблон без русских букв вне подстановок поймал бы что угодно.
            if (!literal.any(::isCyrillic)) continue
            val pattern = "^" + key.split("{}").joinToString("(.+?)") { Regex.escape(it) } + "$"
            patterns += Template(Regex(pattern, RegexOption.DOT_MATCHES_ALL), value) to literal.length
        }
        exact = plain
        // Чем длиннее неизменная часть, тем точнее шаблон: такие проверяем первыми.
        templates = patterns.sortedByDescending { it.second }.map { it.first }
    }

    /// Язык для дат и чисел: «12 марта» или «March 12».
    val locale: Locale get() = if (isEnglish) Locale.US else Locale.forLanguageTag("ru")

    fun isCyrillic(c: Char): Boolean = c in 'Ѐ'..'ӿ'

    fun tr(text: String): String {
        if (!isEnglish || text.none(::isCyrillic)) return text
        if (!loaded) ensureLoaded()
        synchronized(cache) { cache[text]?.let { return it } }
        val result = translate(text, 0)
        synchronized(cache) {
            if (cache.size > 5000) cache.clear()
            cache[text] = result
        }
        return result
    }

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return it }
        // Хвостовые пробелы и переводы строк — частая разница между фразой в коде и на экране.
        val trimmed = text.trim()
        if (trimmed != text) exact[trimmed]?.let { return text.replace(trimmed, it) }
        if (depth >= 2) return text
        for (template in templates) {
            val match = template.regex.matchEntire(text) ?: continue
            val values = match.groupValues.drop(1).map { if (it.any(::isCyrillic)) translate(it, depth + 1) else it }
            return fill(template.target, values)
        }
        return text
    }

    /// `{}` — по порядку, `{2}` — по номеру: в английском слова иногда меняются местами.
    private fun fill(target: String, values: List<String>): String {
        val out = StringBuilder()
        var next = 0
        var i = 0
        while (i < target.length) {
            val c = target[i]
            if (c == '{') {
                val close = target.indexOf('}', i)
                if (close > i) {
                    val inner = target.substring(i + 1, close)
                    if (inner.isEmpty()) {
                        out.append(values.getOrElse(next) { "" }); next++; i = close + 1; continue
                    }
                    inner.toIntOrNull()?.takeIf { it >= 1 }?.let {
                        out.append(values.getOrElse(it - 1) { "" }); i = close + 1
                    }
                    if (i == close + 1) continue
                }
            }
            out.append(c); i++
        }
        return out.toString()
    }
}

/// Перевод фразы интерфейса на выбранный язык.
fun L(text: String): String = L10n.tr(text)

/// Формат даты на текущем языке. Форматтер пересоздаётся только после смены языка.
class LocalizedDateFormat(private val ru: String, private val en: String) {
    @Volatile private var cached: Pair<Boolean, DateTimeFormatter>? = null

    fun format(date: TemporalAccessor): String {
        val english = L10n.isEnglish
        val formatter = cached?.takeIf { it.first == english }?.second
            ?: DateTimeFormatter.ofPattern(if (english) en else ru, L10n.locale).also { cached = english to it }
        return formatter.format(date)
    }
}
