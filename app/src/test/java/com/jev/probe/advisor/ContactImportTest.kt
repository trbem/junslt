package com.jev.probe.advisor

import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class ContactImportTest {
    private val source = "小林，昵称林林，是我的同事。喜欢爬山。阿陈，是大学同学。"
    private val valid = """{"contacts":[{"name":"小林","aliases":["林林"],"relationship":"同事","notes":["喜欢爬山"],"evidence":"小林，昵称林林，是我的同事。喜欢爬山。"},{"name":"阿陈","aliases":[],"relationship":"大学同学","notes":[],"evidence":"阿陈，是大学同学。"}]}"""
    @Test fun extractsSeveralPeopleWithQuotes() {
        val contacts = ContactImportParser.parse(valid, source)
        assertEquals(2, contacts.size); assertEquals(listOf("林林"), contacts[0].aliases)
        assertEquals("大学同学", contacts[1].relationship)
    }
    @Test fun rejectsInventedFactsAndWrongEvidence() {
        listOf(valid.replace("喜欢爬山", "喜欢游泳"), valid.replace("大学同学", "女朋友"),
            valid.replace("林林", "林妹妹")).forEach { bad ->
            assertThrows(InvalidContactImport::class.java) { ContactImportParser.parse(bad, source) }
        }
    }
    @Test fun leavesMissingNamesForUserAndSupportsNoPeople() {
        val missing = """{"contacts":[{"name":"","aliases":[],"relationship":"同事","notes":[],"evidence":"同事"}]}"""
        assertEquals("", ContactImportParser.parse(missing, "同事").single().name)
        assertTrue(ContactImportParser.parse("{\"contacts\":[]}", source).isEmpty())
    }
    @Test fun malformedGenerationRetriesExactlyOnce() {
        var calls = 0
        val result = ContactImportGeneration.run(source) { attempt -> calls++; if (attempt == 0) "invalid" else valid }
        assertEquals(2, calls); assertEquals(2, result.size)
        calls = 0
        assertThrows(InvalidContactImport::class.java) { ContactImportGeneration.run(source) { calls++; "{}" } }
        assertEquals(2, calls)
    }
    @Test fun transportFailureDoesNotTriggerFormatRetry() {
        var calls = 0
        assertThrows(IOException::class.java) { ContactImportGeneration.run(source) { calls++; throw IOException() } }
        assertEquals(1, calls)
    }
    @Test fun decodesUtf8BomAndGb18030() {
        assertEquals(source, ContactText.decode(source.toByteArray(Charsets.UTF_8)))
        assertEquals(source, ContactText.decode(byteArrayOf(0xef.toByte(), 0xbb.toByte(), 0xbf.toByte()) + source.toByteArray()))
        assertEquals(source, ContactText.decode(source.toByteArray(java.nio.charset.Charset.forName("GB18030"))))
    }
    @Test fun refusesOversizedEmptyOrBinaryDataWithoutTruncation() {
        assertThrows(IllegalArgumentException::class.java) { ContactText.validate(" ") }
        assertThrows(IllegalArgumentException::class.java) { ContactText.validate("字".repeat(12001)) }
        assertThrows(IllegalArgumentException::class.java) { ContactText.decode(ByteArray(ContactText.MAX_BYTES + 1)) }
        assertThrows(IllegalArgumentException::class.java) { ContactText.decode(byteArrayOf(0, 1, 2)) }
    }
}
