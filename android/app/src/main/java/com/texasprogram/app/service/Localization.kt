package com.texasprogram.app.service

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
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
    /// `weak` — шаблон с короткой неизменной частью («{} и {}»): годится, только если перевёл всё.
    private class Template(val regex: Regex, val target: String, val weak: Boolean)

    @Volatile private var exact: Map<String, String> = emptyMap()
    /// Те же фразы капсом: «ЖИМ ЛЁЖА» в заголовках, собранных через `uppercase()`.
    @Volatile private var upper: Map<String, String> = emptyMap()
    /// И строчными: «жим лёжа» внутри фразы, собранной через `lowercase()`.
    @Volatile private var lower: Map<String, String> = emptyMap()
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
        // Проверяем один раз — при первом запуске версии с английским. Иначе новая
        // установка на английском телефоне со второго запуска стала бы русской:
        // к тому времени данные приложения уже есть.
        if (settings.getBoolean("app_language_checked", false)) return
        settings.edit().putBoolean("app_language_checked", true).apply()
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
        // kotlinx, а не org.json: тот работает только на телефоне, а словарь читают и тесты.
        val json = Json.parseToJsonElement(text).jsonObject
        val plain = HashMap<String, String>()
        val patterns = ArrayList<Triple<Template, Int, String>>()
        for ((key, element) in json) {
            val value = element.jsonPrimitive.content
            if (!key.contains("{}")) { plain[key] = value; continue }
            val literal = key.replace("{}", "")
            // Шаблон без русских букв вне подстановок поймал бы что угодно.
            if (!literal.any(::isCyrillic)) continue
            val weak = literal.count { it.isLetter() } <= 3
            patterns += Triple(Template(Regex(pattern(key), RegexOption.DOT_MATCHES_ALL), value, weak), literal.length, key)
            // Тот же шаблон капсом: «ДЕНЬ 3» в заголовках через `uppercase()`.
            val upperKey = key.uppercase()
            if (upperKey != key) {
                patterns += Triple(Template(Regex(pattern(upperKey), RegexOption.DOT_MATCHES_ALL), value.uppercase(), weak), literal.length, upperKey)
            }
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
        lower = plain.entries.associate { it.key.lowercase() to it.value }
        // Чем длиннее неизменная часть, тем точнее шаблон: такие проверяем первыми.
        templates = patterns.sortedWith(compareByDescending<Triple<Template, Int, String>> { it.second }.thenBy { it.third }).map { it.first }
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
        // Неразрывные пробелы ставит типографика экранов — в словаре их нет.
        val result = translate(text.replace('\u00A0', ' ').replace('\u202F', ' '), 0)
        synchronized(cache) {
            if (cache.size > 5000) cache.clear()
            cache[text] = result
        }
        return result
    }

    /// Разделители, по которым дробится собранная из кусков строка: «Жим лёжа · тест 1ПМ».
    private val number = Regex("^([+−-]?\\d[\\d .,]*?) (\\D.*)$", RegexOption.DOT_MATCHES_ALL)

    private val tailNumber = Regex("^(\\D.*?) (\\d[\\d .,]*)$", RegexOption.DOT_MATCHES_ALL)

    private val separators = listOf(" · ", "\n", "; ", ", ")

    private fun translate(text: String, depth: Int): String {
        exact[text]?.let { return it }
        // Хвостовые пробелы и переводы строк — частая разница между фразой в коде и на экране.
        val trimmed = text.trim()
        if (trimmed != text) exact[trimmed]?.let { return text.replace(trimmed, it) }
        if (trimmed == trimmed.uppercase()) upper[trimmed]?.let { return text.replace(trimmed, it) }
        if (trimmed == trimmed.lowercase()) lower[trimmed]?.let { return text.replace(trimmed, lowerFirst(it)) }
        if (depth >= 3) return text
        for (template in templates) {
            val match = template.regex.matchEntire(text) ?: continue
            val result = fill(template.target, match.groupValues.drop(1).map { part(it, depth + 1) })
            // Короткий шаблон («{} и {}») с недопереведённым остатком дал бы кашу из двух языков.
            if (template.weak && result.any(::isCyrillic)) continue
            return result
        }
        // «3 тренировки» → «3 workouts»: число и слово, переведённое целиком.
        number.matchEntire(text)?.let { match ->
            val rest = part(match.groupValues[2], depth + 1)
            if (rest.none(::isCyrillic)) return match.groupValues[1] + " " + rest
        }
        // «Ноябрь 2026» → «November 2026»: слово, переведённое целиком, и число.
        tailNumber.matchEntire(text)?.let { match ->
            val head = part(match.groupValues[1], depth + 1)
            if (head.none(::isCyrillic)) return head + " " + match.groupValues[2]
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

    /// «Bench press» → «bench press», но «RPE» остаётся «RPE».
    private fun lowerFirst(text: String): String =
        if (text.length > 1 && text[1].isLowerCase()) text[0].lowercaseChar() + text.substring(1) else text

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
