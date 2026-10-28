package ua.kyiv.putivnyk.audio

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AudioGuideChunksTest {

    @Test
    fun shortSentencesAreKeptTogether() {
        val chunks = AudioGuideEngine.splitIntoChunks("Перше речення. Друге речення! Третє?", maxChars = 100)
        assertEquals(listOf("Перше речення. Друге речення! Третє?"), chunks)
    }

    @Test
    fun chunksStopAtSentenceEnds() {
        val text = "Софійський собор збудовано в XI столітті. Його мозаїки й фрески збереглися до наших днів. " +
            "Дзвіниця заввишки 76 метрів."
        val chunks = AudioGuideEngine.splitIntoChunks(text, maxChars = 60)
        assertEquals(3, chunks.size)
        assertTrue(chunks.all { it.length <= 60 })
        assertEquals(text, chunks.joinToString(" "))
    }

    @Test
    fun aVeryLongSentenceIsCutAtSpaces() {
        val sentence = (1..80).joinToString(" ") { "слово$it" }
        val chunks = AudioGuideEngine.splitIntoChunks(sentence, maxChars = 120)
        assertTrue(chunks.size > 1)
        assertTrue(chunks.all { it.length <= 120 })
        assertEquals(sentence.split(' '), chunks.flatMap { it.split(' ') })
    }

    @Test
    fun emptyTextGivesNoChunks() {
        assertTrue(AudioGuideEngine.splitIntoChunks("   ").isEmpty())
    }
}
