package io.github.pt9912.tdswire.protocol.rpc

import io.github.pt9912.tdswire.protocol.token.Collation

/**
 * Typ eines RPC-Parameters: die Art und die Angaben der TYPE_INFO. `length` ist die deklarierte
 * Größe in Byte (`INTN`, `FLTN`, `MONEYN`, `DATETIMN`, `GUID`, Dezimal) bzw. die höchste Länge in
 * Byte (Zeichen- und Binärtypen); `precision` und `scale` gelten für Dezimal-, `scale` auch für die
 * Zeittypen; nicht anwendbare Angaben sind 0, `collation` ist nur bei Zeichentypen gesetzt.
 */
data class RpcType(
    val kind: RpcTypeKind,
    val length: Int = 0,
    val precision: Int = 0,
    val scale: Int = 0,
    val collation: Collation? = null,
)

/** Die Typen, die der RPC-Parser liest (Typliste in `SPEC-003`, ohne PLP); `code` ist die Typkennung der TYPE_INFO. */
enum class RpcTypeKind(val code: Int) {
    GUID(RpcTypeCode.GUID),
    INTN(RpcTypeCode.INTN),
    DATEN(RpcTypeCode.DATEN),
    TIMEN(RpcTypeCode.TIMEN),
    DATETIME2N(RpcTypeCode.DATETIME2N),
    DATETIMEOFFSETN(RpcTypeCode.DATETIMEOFFSETN),
    BITN(RpcTypeCode.BITN),
    DECIMALN(RpcTypeCode.DECIMALN),
    NUMERICN(RpcTypeCode.NUMERICN),
    FLTN(RpcTypeCode.FLTN),
    MONEYN(RpcTypeCode.MONEYN),
    DATETIMN(RpcTypeCode.DATETIMN),
    BIGVARBINARY(RpcTypeCode.BIGVARBINARY),
    BIGVARCHAR(RpcTypeCode.BIGVARCHAR),
    BIGBINARY(RpcTypeCode.BIGBINARY),
    BIGCHAR(RpcTypeCode.BIGCHAR),
    NVARCHAR(RpcTypeCode.NVARCHAR),
    NCHAR(RpcTypeCode.NCHAR),
}

/**
 * Wert eines Parameters. Der Parser deutet nur `INTN`, `BITN` und die Unicode-Texttypen; jeder
 * andere Typ liefert seine Wertbytes unverändert als [RawValue] (Festlegung: die Deutung gehört der
 * Datentypen-Erweiterung bzw. dem Backend-Vertrag, nicht diesem Parser). Die Textdarstellung
 * nennt nie den Inhalt: Parameter sind Nutzerdaten.
 */
sealed interface RpcValue {
    /** NULL-Sentinel des Typs. */
    data object NullValue : RpcValue

    /** `INTN`: 1 Byte vorzeichenlos (`tinyint`), 2, 4 und 8 Byte vorzeichenbehaftet. */
    class IntegerValue(val value: Long) : RpcValue {
        override fun toString(): String = "RpcValue.IntegerValue(verborgen)"
    }

    class BitValue(val value: Boolean) : RpcValue {
        override fun toString(): String = "RpcValue.BitValue(verborgen)"
    }

    /** `NVARCHAR` und `NCHAR`: UTF-16-Einheiten unverändert übernommen, auch unpaarige Surrogate. */
    class TextValue(val value: String) : RpcValue {
        override fun toString(): String = "RpcValue.TextValue(chars=${value.length})"
    }

    /** Wertbytes ohne Längenpräfix, unverändert. */
    class RawValue(val bytes: ByteArray) : RpcValue {
        override fun toString(): String = "RpcValue.RawValue(bytes=${bytes.size})"
    }
}

/**
 * Ein Parameter einer RPC-Nachricht. `name` ist leer bei einem Parameter ohne Namen; `status` ist
 * das Statusbyte unverändert (Bit 0 `fByRefValue` = OUTPUT, Bit 1 `fDefaultValue`).
 */
class RpcParameter(val name: String, val status: Int, val type: RpcType, val value: RpcValue) {
    /** `fByRefValue`: OUTPUT bzw. InputOutput. */
    val output: Boolean get() = status and STATUS_OUTPUT != 0

    /** `fDefaultValue`: der Client verlangt den Standardwert des Parameters. */
    val useDefault: Boolean get() = status and STATUS_DEFAULT != 0

    override fun toString(): String = "RpcParameter(name=$name, output=$output, type=$type, value=$value)"

    companion object {
        const val STATUS_OUTPUT = 0x01
        const val STATUS_DEFAULT = 0x02
    }
}

/**
 * Die erkannte Form der RPC-Nachricht (Katalog `SPEC-030`). Text und Deklarationsliste von
 * `sp_executesql` und `sp_prepexec` stehen getrennt von den Wertparametern in `parameters`.
 * Textdarstellungen nennen nur Längen: SQL-Text kann Literale tragen.
 */
sealed interface RpcCall {
    /** `sp_executesql` (ProcID 10): `statement`, `declarations`, danach die Wertparameter. */
    class ExecuteSql(val statement: String, val declarations: String, val parameters: List<RpcParameter>) : RpcCall {
        override fun toString(): String =
            "ExecuteSql(statementChars=${statement.length}, declarationChars=${declarations.length}, " +
                "parameters=$parameters)"
    }

    /**
     * `sp_prepexec` (ProcID 13): `handle` ist der Wert des OUTPUT-Parameters 0 (beim Erstaufruf −1),
     * dann Deklarationsliste und Text, danach die Wertparameter.
     */
    class PrepExec(
        val handle: Int,
        val declarations: String,
        val statement: String,
        val parameters: List<RpcParameter>,
    ) : RpcCall {
        override fun toString(): String = "PrepExec(handle=$handle, declarationChars=${declarations.length}, " +
            "statementChars=${statement.length}, parameters=$parameters)"
    }

    /** `sp_execute` (ProcID 12): `handle` ist der Wert des Parameters 0, danach die Wertparameter. */
    class Execute(val handle: Int, val parameters: List<RpcParameter>) : RpcCall {
        override fun toString(): String = "Execute(handle=$handle, parameters=$parameters)"
    }

    /** RPC per Prozedurname, wie der Client ihn gesendet hat; der Name wird nicht gedeutet. */
    class ByName(val name: String, val parameters: List<RpcParameter>) : RpcCall {
        override fun toString(): String = "ByName(name=$name, parameters=$parameters)"
    }

    /**
     * Eine ProcID außerhalb des Katalogs. Die Parameter sind nicht gelesen; die Antwort legt der
     * Parser nicht fest.
     */
    data class UnknownProcedure(val procId: Int) : RpcCall
}

/**
 * Das Ergebnis des RPC-Parsers: Felder und Form, keine Entscheidung. `headerTypes` sind die
 * Kennungen der ALL_HEADERS-Header, `optionFlags` die Optionsflags unverändert (Bit 0
 * `fWithRecomp`, Bit 1 `fNoMetaData`, Bit 2 `fReuseMetaData`).
 */
class RpcRequest(val headerTypes: List<Int>, val optionFlags: Int, val call: RpcCall) {
    override fun toString(): String = "RpcRequest(headerTypes=$headerTypes, optionFlags=0x" +
        "${Integer.toHexString(optionFlags)}, call=$call)"
}
