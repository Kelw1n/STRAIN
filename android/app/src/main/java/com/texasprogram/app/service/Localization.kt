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
    /// Те же фразы капсом: «ЖИМ ЛЁЖА» в заголовках, собранных через `uppercase()`.
    @Volatile private var upper: Map<String, String> = emptyMap()
    @Volatile private var templates: List<Template> = emptyList()
    /// Куски фраз, к которым в коде приклеено число или имя: «был(а) » + «5 минут назад»,
    /// «Жим лёжа» + « · тест 1ПМ». Длинные проверяются первыми.
    @Volatile private var prefixes: List<Pair<String, String>> = emptyList()
    @Volatile private var suffixes: List<Pair<String, String>> = emptyList()
    @Volatile private var loaded = false
    private var appContext: Context? = null
    @Volatile var isEnglish: Boolean = false
        private set
    private val cache = HashMap<String, String>()

    /// Запоминает выбранный язык. Словарь читается позже, при первом переводе:
    /// на русском он не нужен вовсе.
    fun init(context: Context) {
        appContext = context.applicationContext
        keepRussianForExistingUsers(context)
        isEnglish = AppLanguage.current(context).resolved == AppLanguage.EN
    }

    /// Кто уже пользовался приложением до английского, остаётся на русском,
    /// даже если телефон на английском: язык не должен смениться сам после
    /// обновления. Новая установка выбора не имеет — берётся язык телефона.
    private fun keepRussianForExistingUsers(context: Context) {
        val settings = context.getSharedPreferences(AppLanguage.PREFS, Context.MODE_PRIVATE)
        if (settings.contains(AppLanguage.KEY)) return
        val store = context.getSharedPreferences("strain.store", Context.MODE_PRIVATE)
        if (!store.contains("profiles")) return
        settings.edit().putString(AppLanguage.KEY, AppLanguage.RU.code).apply()
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
        install(text)
    }

    /// Словарь из текста JSON. Снимки экранов кладут его сюда сами: у них нет приложения.
    internal fun install(text: String) {
        val json = JSONObject(text)
        val plain = HashMap<String, String>()
        val patterns = ArrayList<Pair<Template, Int>>()
        for (key in json.keys()) {
            val value = json.getString(key)
            if (!key.contains("{}")) { plain[key] = value; continue }
            val literal = key.replace("{}", "")
            // Шаблон без русских букв вне подстановок поймал бы что угодно.
            if (!literal.any(::isCyrillic)) continue
            patterns += Template(Regex(pattern(key), RegexOption.DOT_MATCHES_ALL), value) to literal.length
        }
        val pieces = plain.entries.filter { it.key.trim().length >= 2 && it.key.any(::isCyrillic) }.sortedBy { it.key }
        prefixes = pieces.filter { it.key.endsWith(' ') }
            .sortedByDescending { it.key.length }.map { it.key to it.value }
        suffixes = pieces.filter { val first = it.key.first(); !(first.isLetterOrDigit() || first in "«\"(") }
            .sortedByDescending { it.key.length }.map { it.key to it.value }
        // Кусок «Отдых » годится и как отдельное слово «Отдых».
        for ((key, value) in plain.entries.sortedBy { it.key }.toList()) {
            val trimmed = key.trim()
            if (trimmed != key && trimmed.isNotEmpty() && trimmed !in plain) plain[trimmed] = value.trim()
        }
        exact = plain
        upper = plain.entries.associate { it.key.uppercase() to it.value.uppercase() }
        // Чем длиннее неизменная часть, тем точнее шаблон: такие проверяем первыми.
        templates = patterns.sortedByDescending { it.second }.map { it.first }
        loaded = true
        synchronized(cache) { cache.clear() }
    }

    /// Дробная часть числа: «2,5» по-русски, «2.5» по-английски. На входе — с точкой.
    fun decimal(text: String): String = if (isEnglish) text else text.replace('.', ',')

    /// Язык для дат и чисел: «12 марта» или «March 12».
    val locale: Locale get() = if (isEnglish) Locale.US else Locale.forLanguageTag("ru")

    /// Регулярка шаблона. Подстановка вплотную к русской букве («Ж{}», «{}м») — число, а не слово:
    /// иначе «Ж{}» съел бы любую фразу на «Ж».
    private fun pattern(key: String): String {
        val parts = key.split("{}")
        val out = StringBuilder("^").append(Regex.escape(parts[0]))
        for (i in 1 until parts.size) {
            val before = parts[i - 1].lastOrNull()
            val after = parts[i].firstOrNull()
            val tight = (before != null && isCyrillic(before)) || (after != null && isCyrillic(after))
            out.append(if (tight) "([^\\u0400-\\u04FF]+?)" else "(.+?)").append(Regex.escape(parts[i]))
        }
        return out.append("$").toString()
    }

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

    /// Разделители, по которым дробится собранная из кусков строка: «Жим лёжа · тест 1ПМ».
    private val separators = listOf(" · ", "\n", "; ", ", ")

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return it }
        // Хвостовые пробелы и переводы строк — частая разница между фразой в коде и на экране.
        val trimmed = text.trim()
        if (trimmed != text) exact[trimmed]?.let { return text.replace(trimmed, it) }
        if (trimmed == trimmed.uppercase()) upper[trimmed]?.let { return text.replace(trimmed, it) }
        if (depth >= 3) return text
        for (template in templates) {
            val match = template.regex.matchEntire(text) ?: continue
            val values = match.groupValues.drop(1).map { part(it, depth + 1) }
            return fill(template.target, values)
        }
        for ((key, value) in prefixes) {
            if (text.length > key.length && text.startsWith(key)) return value + part(text.substring(key.length), depth + 1)
        }
        for ((key, value) in suffixes) {
            if (text.length > key.length && text.endsWith(key)) return part(text.dropLast(key.length), depth + 1) + value
        }
        // Целиком не нашлась — переводим куски по отдельности.
        for (separator in separators) {
            if (!text.contains(separator)) continue
            return text.split(separator).joinToString(separator) { part(it, depth + 1) }
        }
        return text
    }

    private fun part(text: String, depth: Int): String = if (text.any(::isCyrillic)) translate(text, depth) else text

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
