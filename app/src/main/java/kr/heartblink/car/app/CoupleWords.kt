package kr.heartblink.car.app

import kr.heartblink.car.data.WordFix

/**
 * The words the speech recognizer should not get wrong for this couple: both names, the nicknames they
 * gave each other's cars, the navigation's saved places, and longer words that came up in more than one
 * of their messages. Kept in memory only.
 */
object CoupleWords {
    @Volatile private var fromMessages: Set<String> = emptySet()

    fun words(): Set<String> = buildSet {
        val v = Session.view
        listOf(v.meName, v.partnerName, v.myNickname, v.partnerNickname).forEach { if (it.isNotBlank()) add(it) }
        NaviWatch.places.value.forEach { p -> p.name.split(' ').forEach { add(it) } }
        addAll(fromMessages)
    }

    /** Reads the couple's messages again for their repeated words (3 letters or more, in 2 messages or more). */
    suspend fun refresh() {
        val repo = Session.repository ?: return
        val bodies = (repo.sentMessages() + repo.receivedMessages()).map { it.body }
        fromMessages = bodies
            .flatMap { body -> body.split(Regex("\\s+")).map { w -> w.takeWhile { c -> c in '가'..'힣' } }.filter { it.length >= 3 }.toSet() }
            .groupingBy { it }.eachCount()
            .filterValues { it >= 2 }.keys
    }

    fun fix(text: String): WordFix.Fixed = WordFix.fix(text, words())
}
