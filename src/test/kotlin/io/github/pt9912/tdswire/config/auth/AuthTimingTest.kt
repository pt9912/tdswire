package io.github.pt9912.tdswire.config.auth

import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Zeitgleichheit des Passwortvergleichs (`TDS-QA-03`, `ADR-0011`): Die Dauer eines Aufrufs von
 * [UserListAuthAdapter.authenticate] für einen bekannten Benutzernamen mit falschem Passwort und für einen
 * unbekannten Benutzernamen wird mit den echten Rechenschritten gemessen (kein Test-Adapter), im Wechsel, nach
 * Aufwärmen, als Median. Die Zahlen gelten für den Lauf im Docker-Image dieses Repos auf dem Rechner des Laufs;
 * die Schranken sind Setzungen (Voreinstellung, nicht gemessen), keine Zusage über andere Hardware oder ein Netz.
 * Gemessen wird der Aufruf des Adapters, nicht die Antwortzeit am Draht.
 */
class AuthTimingTest {
    private val iterations = Pbkdf2Limits.ITERATIONS_FLOOR

    /** Median der Dauer je Fall in Millisekunden; die Fälle laufen im Wechsel, damit Drift alle gleich trifft. */
    private fun medians(adapter: AuthAdapter, cases: Map<String, Pair<String, String>>): Map<String, Double> {
        val samples = cases.keys.associateWith { mutableListOf<Long>() }
        repeat(WARMUP + ROUNDS) { round ->
            cases.forEach { (label, credentials) ->
                val request = request(credentials.first, credentials.second)
                val start = System.nanoTime()
                adapter.authenticate(request)
                val elapsed = System.nanoTime() - start
                if (round >= WARMUP) samples.getValue(label) += elapsed
            }
        }
        return samples.mapValues { (_, values) -> values.sorted()[values.size / 2] / NANOS_PER_MILLI }
    }

    private val equalListText = "alice\t${pbkdf2Value("pw-alice", iterations)}\n" +
        "bob\t${pbkdf2Value("pw-bob", iterations)}\n"

    private fun equalCases() = mapOf(
        "bekannt, falsches Passwort" to ("alice" to "falsch"),
        "unbekannt" to ("niemand" to "falsch"),
        "bekannt, richtiges Passwort" to ("alice" to "pw-alice"),
    )

    /** Ob die drei Mediane höchstens um [TOLERANCE] vom kleinsten abweichen. */
    private fun withinTolerance(medians: Map<String, Double>): Boolean {
        val smallest = medians.values.min()
        return medians.values.all { it <= smallest * (1 + TOLERANCE) }
    }

    /**
     * Orakel: Bei einer Liste mit gleicher Iterationszahl liegen die Mediane von bekanntem Benutzernamen mit
     * falschem Passwort, unbekanntem Benutzernamen und richtigem Passwort innerhalb von [TOLERANCE] (Setzung).
     * Die Messung deckt ab: eine Liste mit Einträgen gleicher Iterationszahl; nicht abgedeckt: gemischte Listen
     * (eigener Test), andere Hardware, Last durch gleichzeitige Anmeldungen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Bekannt mit falschem Passwort und unbekannt brauchen bei gleicher Iterationszahl gleich lang`() {
        val adapter = UserListAuthAdapter(parseOk(equalListText))
        val medians = medians(adapter, equalCases())
        assertTrue(withinTolerance(medians), "Mediane in ms: $medians")
    }

    /**
     * Gegenprobe: Ein Adapter, der bei unbekanntem Benutzernamen ohne Scheinvergleich sofort ablehnt, ist an
     * derselben Schranke sichtbar. Ohne sie bewiese das Grün des Orakels nur, dass die Messung nichts trennt.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Ein Adapter ohne Scheinvergleich faellt an der Schranke`() {
        val real = UserListAuthAdapter(parseOk(equalListText))
        val shortcut = object : AuthAdapter {
            override fun authenticate(request: AuthRequest): AuthResult = if (request.userName == "niemand") {
                AuthResult.Rejected(RejectReason.UNKNOWN_USER)
            } else {
                real.authenticate(request)
            }
        }
        val medians = medians(shortcut, equalCases())
        assertTrue(!withinTolerance(medians), "die Schranke trennt den Kurzschluss nicht: $medians")
    }

    /**
     * Grenze, nicht Zusage: In einer gemischten Liste ist die Attrappe der `pbkdf2`-Eintrag mit der höchsten
     * Iterationszahl (`SPEC-051`). Ein bekannter Benutzername mit der halben Iterationszahl braucht gemessen etwa
     * die halbe Zeit der Attrappe, ein `plain`-Eintrag (nur im Testmodus) nur einen Bruchteil. Der Test hält fest,
     * dass beide Abweichungen messbar sind (Faktor über [LEAK_FACTOR], Setzung); wird die Bauart geändert, ist die
     * Grenze in `ADR-0011` und `UserList` neu zu fassen.
     *
     * @spec SPEC-051
     */
    @Test
    fun `Gemischte Listen haben eine messbare Zeitgrenze`() {
        val text = "schnell\t${pbkdf2Value("pw-schnell", iterations)}\n" +
            "langsam\t${pbkdf2Value("pw-langsam", iterations * 2)}\n" +
            "klar\t{plain}pw-klar\n"
        val adapter = UserListAuthAdapter(parseOk(text, testMode = true))
        val medians = medians(
            adapter,
            mapOf(
                "unbekannt (Attrappe)" to ("niemand" to "falsch"),
                "bekannt, halbe Iterationszahl" to ("schnell" to "falsch"),
                "bekannt, plain" to ("klar" to "falsch"),
            ),
        )
        val decoy = medians.getValue("unbekannt (Attrappe)")
        assertTrue(decoy > medians.getValue("bekannt, halbe Iterationszahl") * 1.5, "Mediane in ms: $medians")
        assertTrue(decoy > medians.getValue("bekannt, plain") * LEAK_FACTOR, "Mediane in ms: $medians")
    }

    private companion object {
        const val WARMUP = 3
        const val ROUNDS = 15
        const val NANOS_PER_MILLI = 1_000_000.0
        const val TOLERANCE = 0.25
        const val LEAK_FACTOR = 10
    }
}
