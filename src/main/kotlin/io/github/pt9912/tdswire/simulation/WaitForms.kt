package io.github.pt9912.tdswire.simulation

import kotlin.time.Duration
import kotlin.time.Duration.Companion.hours
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * Form Z von `SPEC-036` (`ADR-0058` Entscheidung 5): `WAITFOR DELAY '<hh>:<mm>:<ss>'` als einziger Text, mit
 * höchstens einem abschließenden `;` (das der Parser vorher entfernt). Die Schlüsselwörter gelten ohne Beachtung der
 * Schreibweise (ASCII), das Zeichenliteral trägt kein `N`, `hh` liegt von 00 bis 23, `mm` und `ss` von 00 bis 59.
 * Gemessen ist nur `'00:00:10'`; der Bereich und `00:00:00` sind Setzung. Nicht erkannt sind Bruchteile von Sekunden,
 * `WAITFOR TIME`, Variablen und Parameter als Frist, Folgeanweisungen und `WAITFOR (RECEIVE …)`.
 */
internal object WaitForms {
    private val DELAY = Regex("([0-9]{2}):([0-9]{2}):([0-9]{2})")
    private const val MAX_HOUR = 23
    private const val MAX_MINUTE_OR_SECOND = 59
    private const val LENGTH = 3

    /** Die Wartezeit des Textes oder `null`, wenn er keine Form Z ist; [tokens] ohne das abschließende `;`. */
    fun recognize(tokens: List<Token>): Recognition? {
        val literal = tokens.getOrNull(2) as? Token.Text
        val parts = literal?.takeUnless { it.national }?.let { DELAY.matchEntire(it.value) }?.destructured
        val hour = parts?.component1()?.toInt()
        val minute = parts?.component2()?.toInt()
        val second = parts?.component3()?.toInt()
        val isForm = tokens.size == LENGTH && tokens[0].isKeyword("WAITFOR") && tokens[1].isKeyword("DELAY")
        val inRange = hour != null && minute != null && second != null &&
            hour <= MAX_HOUR && minute <= MAX_MINUTE_OR_SECOND && second <= MAX_MINUTE_OR_SECOND
        return if (isForm && inRange) Recognition.Wait(delay(hour, minute, second)) else null
    }

    private fun delay(hour: Int, minute: Int, second: Int): Duration = hour.hours + minute.minutes + second.seconds
}
