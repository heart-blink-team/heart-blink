package kr.heartblink.car.data

/**
 * Fixes the couple's own words in a sentence the speech recognizer wrote down: names, nicknames, places
 * and words the two often use. The recognizer does not know "여니" and writes "연이" or "여기"; this puts
 * the couple's word back when the heard word sounds almost the same.
 *
 * Sounds are compared letter by letter (자모), after linking a final consonant to a following ㅇ the way
 * Korean is spoken (연이 sounds like 여니). Letters that sound alike (ㅐ/ㅔ, ㄴ/ㄹ, ㄱ/ㅋ ...) cost half.
 * Everything runs on the car; nothing is sent anywhere.
 */
object WordFix {
    /** One replaced word: [start] until [end] in the fixed text, and what the recognizer had written. */
    data class Change(val start: Int, val end: Int, val heard: String, val word: String)

    data class Fixed(val text: String, val changes: List<Change>) {
        /** Puts back what was heard for [change]; the other changes move with the text. */
        fun revert(change: Change): Fixed {
            val text = text.replaceRange(change.start, change.end, change.heard)
            val shift = change.heard.length - (change.end - change.start)
            val rest = changes.filter { it != change }.map {
                if (it.start > change.start) it.copy(start = it.start + shift, end = it.end + shift) else it
            }
            return Fixed(text, rest)
        }
    }

    /** Particles that can follow a name; the longest is tried first. */
    private val Particles = listOf(
        "이랑", "한테", "에게", "에서", "까지", "부터", "처럼", "이야", "이가", "이는", "이도", "이를", "이의",
        "랑", "가", "는", "은", "을", "를", "도", "야", "아", "에", "의", "로", "와", "과", "님",
    ).sortedByDescending { it.length }

    fun fix(text: String, words: Collection<String>): Fixed {
        val vocab = words.map { it.trim() }.filter { it.length >= 2 && it.all(::isHangul) }.distinct()
        if (vocab.isEmpty() || text.isBlank()) return Fixed(text, emptyList())
        val out = StringBuilder()
        val changes = mutableListOf<Change>()
        var i = 0
        Regex("\\S+").findAll(text).forEach { m ->
            out.append(text, i, m.range.first)
            val token = m.value
            val best = bestMatch(token, vocab)
            if (best == null) {
                out.append(token)
            } else {
                val (stem, particle, word) = best
                val start = out.length
                out.append(word)
                changes += Change(start, out.length, stem, word)
                out.append(particle)
                // keep any punctuation that came after the particle
                out.append(token.substring(stem.length + particle.length))
            }
            i = m.range.last + 1
        }
        out.append(text, i, text.length)
        return Fixed(out.toString(), changes)
    }

    /** (heard stem, particle, couple word) for the closest word, or null when nothing is close enough. */
    private fun bestMatch(token: String, vocab: List<String>): Triple<String, String, String>? {
        val core = token.takeWhile(::isHangul)
        if (core.length < 2) return null
        val splits = buildList {
            add(core to "")
            Particles.filter { core.endsWith(it) && core.length - it.length >= 2 }
                .forEach { add(core.dropLast(it.length) to it) }
        }
        var best: Triple<String, String, String>? = null
        var bestCost = Double.MAX_VALUE
        for ((stem, particle) in splits) {
            if (stem in vocab) return null
            for (word in vocab) {
                if (kotlin.math.abs(word.length - stem.length) > 1) continue
                val cost = distance(sound(stem), sound(word))
                val limit = if (word.length <= 2) 0.5 else 1.0
                if (cost <= limit && cost < bestCost && firstSoundsAlike(stem, word)) {
                    best = Triple(stem, particle, word)
                    bestCost = cost
                }
            }
        }
        return best
    }

    // ------------------------------------------------------------------ sounds

    private const val Base = 0xAC00
    private const val Initials = "ㄱㄲㄴㄷㄸㄹㅁㅂㅃㅅㅆㅇㅈㅉㅊㅋㅌㅍㅎ"
    private const val Vowels = "ㅏㅐㅑㅒㅓㅔㅕㅖㅗㅘㅙㅚㅛㅜㅝㅞㅟㅠㅡㅢㅣ"
    /** Finals as the one consonant they sound like (ㄳ is heard as ㄱ, ㄼ as ㄹ ...). */
    private val Finals = listOf("", "ㄱ", "ㄲ", "ㄱ", "ㄴ", "ㄴ", "ㄴ", "ㄷ", "ㄹ", "ㄱ", "ㅁ", "ㄹ", "ㄹ", "ㄹ", "ㄹ", "ㄹ", "ㅁ", "ㅂ", "ㅂ", "ㅅ", "ㅆ", "ㅇ", "ㅈ", "ㅊ", "ㅋ", "ㅌ", "ㅍ", "ㅎ")

    private fun isHangul(c: Char) = c.code in Base..0xD7A3

    private class Syllable(var initial: String, val vowel: String, var final: String)

    /** The letters a word is spoken with: 연이 -> ㅕㄴㅣ, 좋아 -> ㅈㅗㅏ (a silent ㅇ is left out). */
    internal fun sound(word: String): List<String> {
        val s = word.filter(::isHangul).map {
            val code = it.code - Base
            Syllable(Initials[code / 588].toString(), Vowels[(code % 588) / 28].toString(), Finals[code % 28])
        }
        for (k in 0 until s.size - 1) {
            val next = s[k + 1]
            val f = s[k].final
            if (next.initial == "ㅇ" && f.isNotEmpty() && f != "ㅇ") {
                if (f != "ㅎ") next.initial = f
                s[k].final = ""
            }
        }
        return s.flatMap { listOfNotNull(it.initial.takeIf { i -> i != "ㅇ" }, it.vowel, it.final.ifEmpty { null }) }
    }

    private val AlikeGroups = listOf(
        "ㄱㄲㅋ", "ㄷㄸㅌ", "ㅂㅃㅍ", "ㅈㅉㅊ", "ㅅㅆ", "ㄴㄹ", "ㄴㅇ", "ㄴㅁ",
        "ㅐㅔ", "ㅒㅖ", "ㅓㅗ", "ㅗㅜ", "ㅕㅛ", "ㅚㅙㅞ", "ㅜㅡ", "ㅢㅣ",
    )

    private fun alike(a: String, b: String) = AlikeGroups.any { a in it && b in it }

    private fun firstSoundsAlike(a: String, b: String): Boolean {
        val x = sound(a).firstOrNull() ?: return false
        val y = sound(b).firstOrNull() ?: return false
        return x == y || alike(x, y)
    }

    /** Edit distance where alike letters cost half and a dropped ㅎ costs half. */
    internal fun distance(a: List<String>, b: List<String>): Double {
        val d = Array(a.size + 1) { DoubleArray(b.size + 1) }
        for (i in 0..a.size) d[i][0] = i.toDouble()
        for (j in 0..b.size) d[0][j] = j.toDouble()
        for (i in 1..a.size) for (j in 1..b.size) {
            val sub = when {
                a[i - 1] == b[j - 1] -> 0.0
                alike(a[i - 1], b[j - 1]) -> 0.5
                else -> 1.0
            }
            val del = if (a[i - 1] == "ㅎ") 0.5 else 1.0
            val ins = if (b[j - 1] == "ㅎ") 0.5 else 1.0
            d[i][j] = minOf(d[i - 1][j - 1] + sub, d[i - 1][j] + del, d[i][j - 1] + ins)
        }
        return d[a.size][b.size]
    }
}
