package io.github.pt9912.tdswire.simulation

import io.github.pt9912.tdswire.domain.SqlType
import java.math.BigDecimal

/**
 * Die Ganzzahltypen des Simulations-Backends (`SPEC-035`): `UInt8` (0 bis 255), `Int16` (−32768 bis 32767), `Int32`
 * und `Int64` mit ihrem Wertebereich und der Überführung einer Zahl, eines Parameters und eines Werts des
 * Massenladens. Die JVM-Klasse des Werts ist `Long` für `Int64`, sonst `Int`. Eine Zahl außerhalb des Wertebereichs
 * ist nicht überführbar (die Funktionen liefern `null`), nie abgeschnitten.
 */
internal object SimIntegers {
    private const val UBYTE_MAX = 255L

    private val byteRange = 0L..UBYTE_MAX
    private val shortRange = Short.MIN_VALUE.toLong()..Short.MAX_VALUE.toLong()
    private val intRange = Int.MIN_VALUE.toLong()..Int.MAX_VALUE.toLong()
    private val longRange = Long.MIN_VALUE..Long.MAX_VALUE

    fun isInteger(type: SqlType): Boolean =
        type == SqlType.UInt8 || type == SqlType.Int16 || type == SqlType.Int32 || type == SqlType.Int64

    /** Der Wertebereich von [type], einem der vier Ganzzahltypen. */
    fun range(type: SqlType): LongRange = when (type) {
        SqlType.UInt8 -> byteRange
        SqlType.Int16 -> shortRange
        SqlType.Int32 -> intRange
        SqlType.Int64 -> longRange
        else -> error("kein Ganzzahltyp")
    }

    /** [number] in der JVM-Klasse von [type], wenn sie in dessen Wertebereich liegt. */
    fun of(number: Long, type: SqlType): Converted? = when {
        number !in range(type) -> null
        type == SqlType.Int64 -> Converted.Value(number)
        else -> Converted.Value(number.toInt())
    }

    /** Eine Zahl ohne Rest (`1.0`, `100`) im Wertebereich von [type]; mit Rest oder darüber `null`. */
    fun number(number: BigDecimal, type: SqlType): Converted? {
        val whole = number.stripTrailingZeros()
        if (whole.scale() > 0) return null
        val bounds = range(type)
        val integer = whole.toBigIntegerExact()
        val inRange = integer >= bounds.first.toBigInteger() && integer <= bounds.last.toBigInteger()
        return if (inRange) of(integer.toLong(), type) else null
    }

    /**
     * Ein Parameter gleichen Typs gilt im Wertebereich der Spalte; ein `Int32`-Parameter gilt in einer `Int64`-Spalte
     * und ein `Int64`-Parameter in einer `Int32`-Spalte im Wertebereich von `Int32`. Jede andere Paarung ist `null`.
     */
    fun parameter(parameterType: SqlType, target: SqlType, value: Any): Converted? {
        val number = (value as? Int)?.toLong() ?: value as? Long
        val allowed = parameterType == target ||
            (parameterType == SqlType.Int32 && target == SqlType.Int64) ||
            (parameterType == SqlType.Int64 && target == SqlType.Int32)
        return if (number != null && allowed) of(number, target) else null
    }

    /** Eine Ganzzahl des Massenladens (`Int` oder `Long`) im Wertebereich von [target]. */
    fun load(value: Any, target: SqlType): Converted? = when (value) {
        is Int -> of(value.toLong(), target)
        is Long -> of(value, target)
        else -> null
    }
}
