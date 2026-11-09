package kr.heartblink.car.data

import org.junit.Assert.assertEquals
import org.junit.Test

class WordFixTest {
    private val couple = listOf("지훈", "서연", "후니", "여니", "응암동")

    private fun fixed(text: String) = WordFix.fix(text, couple).text

    @Test fun linkedSoundIsPutBack() {
        assertEquals("여니야 사랑해", fixed("연이야 사랑해"))
        assertEquals("후니가 왔어", fixed("훈이가 왔어"))
    }

    @Test fun alikeLettersArePutBack() {
        assertEquals("서연이랑 갔던 곳", fixed("서영이랑 갔던 곳"))
        assertEquals("후니한테 말해", fixed("호니한테 말해"))
        assertEquals("지훈아 고마워", fixed("지운아 고마워"))
    }

    @Test fun everydayWordsStay() {
        assertEquals("여기 기억나", fixed("여기 기억나"))
        assertEquals("우리 집 가자", fixed("우리 집 가자"))
        assertEquals("지금 서점 앞이야", fixed("지금 서점 앞이야"))
        assertEquals("응암동 가자", fixed("응암동 가자"))
    }

    @Test fun punctuationStays() {
        assertEquals("여니야!", fixed("연이야!"))
    }

    @Test fun revertPutsBackWhatWasHeard() {
        val f = WordFix.fix("연이야 서영이랑 와", couple)
        assertEquals("여니야 서연이랑 와", f.text)
        val back = f.revert(f.changes.first())
        assertEquals("연이야 서연이랑 와", back.text)
        assertEquals("서연", back.text.substring(back.changes.single().start, back.changes.single().end))
    }
}
