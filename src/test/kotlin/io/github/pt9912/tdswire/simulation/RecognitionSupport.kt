package io.github.pt9912.tdswire.simulation

import kotlin.test.assertEquals
import kotlin.test.assertIs

/** Hilfen für die Tests der Anweisungserkennung. */
fun recognize(sql: String): Recognition = StatementParser.recognize(sql)

fun assertRecognized(expected: Statement, sql: String) {
    val result = recognize(sql)
    assertIs<Recognition.Recognized>(result, "erwartet erkannt: $sql")
    assertEquals(expected, result.statement, sql)
}

fun assertNotRecognized(sql: String, reason: Unrecognized? = null) {
    val result = recognize(sql)
    assertIs<Recognition.NotRecognized>(result, "erwartet nicht erkannt: $sql")
    if (reason != null) assertEquals(reason, result.reason, sql)
}

/** Der Zwilling wird erkannt, jede Abwandlung nicht: ein Negativfall ist damit nicht aus einem fremden Grund rot. */
fun assertOnlyExtensionFails(twin: String, vararg variants: String, reason: Unrecognized? = null) {
    assertIs<Recognition.Recognized>(recognize(twin), "Zwilling muss erkannt sein: $twin")
    for (variant in variants) assertNotRecognized(variant, reason)
}
