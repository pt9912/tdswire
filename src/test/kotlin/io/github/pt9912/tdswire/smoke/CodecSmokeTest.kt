package io.github.pt9912.tdswire.smoke

import io.github.pt9912.tdswire.protocol.login7.Login7Parser
import io.github.pt9912.tdswire.protocol.login7.Login7Request
import io.github.pt9912.tdswire.protocol.prelogin.EncryptionMode
import io.github.pt9912.tdswire.protocol.prelogin.PreloginCodec
import io.github.pt9912.tdswire.protocol.prelogin.PreloginMessage
import io.github.pt9912.tdswire.protocol.prelogin.PreloginOption
import io.github.pt9912.tdswire.protocol.sqlbatch.SqlBatchParser
import io.github.pt9912.tdswire.protocol.sqlbatch.SqlBatchRequest
import io.github.pt9912.tdswire.protocol.token.ColMetadataToken
import io.github.pt9912.tdswire.protocol.token.Collation
import io.github.pt9912.tdswire.protocol.token.ColumnDef
import io.github.pt9912.tdswire.protocol.token.DoneKind
import io.github.pt9912.tdswire.protocol.token.DoneStatus
import io.github.pt9912.tdswire.protocol.token.DoneToken
import io.github.pt9912.tdswire.protocol.token.EnvChange
import io.github.pt9912.tdswire.protocol.token.InfoToken
import io.github.pt9912.tdswire.protocol.token.LoginAck
import io.github.pt9912.tdswire.protocol.token.ReturnStatusToken
import io.github.pt9912.tdswire.protocol.token.ReturnValueToken
import io.github.pt9912.tdswire.protocol.token.RowToken
import io.github.pt9912.tdswire.protocol.token.ServerMessage
import io.github.pt9912.tdswire.protocol.token.TdsType
import io.github.pt9912.tdswire.protocol.token.TdsValue
import io.github.pt9912.tdswire.protocol.token.Token
import io.github.pt9912.tdswire.protocol.token.TokenEncoder
import io.github.pt9912.tdswire.protocol.wire.ProtocolResult
import io.github.pt9912.tdswire.testsupport.Login7Builder
import io.github.pt9912.tdswire.testsupport.TokenDecoder
import io.github.pt9912.tdswire.testsupport.chunked
import io.github.pt9912.tdswire.testsupport.hex
import io.github.pt9912.tdswire.transport.framing.FramingLimits
import io.github.pt9912.tdswire.transport.framing.FramingResult
import io.github.pt9912.tdswire.transport.framing.MessageAssembler
import io.github.pt9912.tdswire.transport.framing.PacketType
import io.github.pt9912.tdswire.transport.framing.PacketWriter
import io.github.pt9912.tdswire.transport.framing.TdsMessage
import kotlin.random.Random
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull

/**
 * Smoke: Nachrichten laufen durch Nachrichten-Codec und Paket-Codec hin und zurück,
 * zerlegt in kleine Pakete und byteweise wieder angenommen. Das belegt, dass beide Codecs
 * zusammenpassen; die Protokolltreue belegen die Handvektoren der Einzeltests, nicht dieser Test.
 */
class CodecSmokeTest {
    private val latin = Collation.LATIN1_GENERAL_CI_AS

    private fun packets(type: PacketType, payload: ByteArray, packetSize: Int): ByteArray {
        val result = PacketWriter.split(type, payload, packetSize)
        val list = assertIs<FramingResult.Ok<List<ByteArray>>>(result).value
        return list.fold(ByteArray(0)) { acc, packet -> acc + packet }
    }

    private fun receive(wire: ByteArray, chunks: List<ByteArray>, limits: FramingLimits): TdsMessage {
        val assembler = MessageAssembler(limits)
        val messages = chunks.flatMap { chunk ->
            val step = assembler.feed(chunk)
            assertNull(step.error)
            step.messages
        }
        assertNull(assembler.endOfInput().error)
        assertEquals(wire.size, chunks.sumOf { it.size })
        return messages.single()
    }

    /** Eine Nachricht geht in Pakete, die Pakete kommen byteweise und in zufälligen Stücken an. */
    private fun transfer(type: PacketType, payload: ByteArray, packetSize: Int, limits: FramingLimits): TdsMessage {
        val wire = packets(type, payload, packetSize)
        val bytewise = receive(wire, wire.map { byteArrayOf(it) }, limits)
        val random = Random(packetSize + payload.size)
        val ragged = receive(wire, chunked(wire, generateSequence { random.nextInt(1, 40) }), limits)
        assertEquals(bytewise, ragged)
        assertEquals(type, bytewise.type)
        return bytewise
    }

    private fun tokensToBytes(tokens: List<Token>): ByteArray {
        val encoder = TokenEncoder()
        tokens.forEach { assertIs<ProtocolResult.Ok<Unit>>(encoder.write(it), "$it") }
        return encoder.toByteArray()
    }

    /**
     * Die PRELOGIN-Anfrage (Handvektor) und die Antwort laufen durch den Paket-Codec (Packet Size 16,
     * Grenze vor dem Login) und durch den PRELOGIN-Codec.
     *
     * @spec SPEC-028
     */
    @Test
    fun `PRELOGIN-Anfrage und -Antwort`() {
        val request = hex(
            "00 00 1A 00 06  01 00 20 00 01  02 00 21 00 01  03 00 22 00 04  04 00 26 00 01  FF " +
                "0B 00 00 00 00 00  00  00  00 00 05 39  00",
        )
        val received = transfer(PacketType.PRELOGIN, request, PACKET_SIZE_SMALL, FramingLimits.preLogin())
        val parsed = assertIs<ProtocolResult.Ok<PreloginMessage>>(PreloginCodec.parse(received.payload)).value
        assertEquals(EncryptionMode.OFF, parsed.encryption)

        val response = PreloginMessage(
            listOf(
                PreloginOption.version(11, 0, 0, 0),
                PreloginOption.encryption(EncryptionMode.REQUIRED),
                PreloginOption.instance(""),
                PreloginOption.mars(false),
            ),
        )
        val encoded = assertIs<ProtocolResult.Ok<ByteArray>>(PreloginCodec.encode(response)).value
        val back = transfer(PacketType.TABULAR_RESULT, encoded, PACKET_SIZE_SMALL, FramingLimits.preLogin())
        val reparsed = assertIs<ProtocolResult.Ok<PreloginMessage>>(PreloginCodec.parse(back.payload)).value
        assertEquals(response.options, reparsed.options)
    }

    /**
     * LOGIN7 geht in Paketen von 32 Byte durch den Paket-Codec und wird gelesen; die Login-Antwort
     * (LOGINACK, ENVCHANGE, INFO, DONE) geht als Tokenstrom in Paketen von 24 Byte zurück und wird
     * als dieselbe Tokenfolge gelesen.
     *
     * @spec SPEC-028
     */
    @Test
    fun `LOGIN7 und Login-Antwort`() {
        val login = Login7Builder().apply { password = "Geheim-123".toCharArray() }.build()
        val received = transfer(PacketType.LOGIN7, login, PACKET_SIZE_SMALL * 2, FramingLimits.preLogin())
        val request = assertIs<ProtocolResult.Ok<Login7Request>>(Login7Parser.parse(received.payload)).value
        assertEquals("Geheim-123", String(request.password.reveal()))
        assertEquals("sa", request.fields.userName)

        val response = listOf(
            EnvChange.Database("master", ""),
            InfoToken(ServerMessage(5701, 2, 0, "Changed database context to 'master'.", "tdswire", "", 1)),
            EnvChange.SqlCollation(latin, null),
            EnvChange.PacketSize(request.header.packetSize, 4096),
            LoginAck(1, request.header.tdsVersion, "tdswire", 0x01000000),
            DoneToken(DoneKind.DONE, DoneStatus.FINAL, 0, 0),
        )
        val back = transfer(PacketType.TABULAR_RESULT, tokensToBytes(response), PACKET_SIZE_TINY, FramingLimits())
        assertEquals(response, TokenDecoder(back.payload).decodeAll())
    }

    /**
     * SQL_BATCH (ALL_HEADERS und Text) geht durch den Paket-Codec und wird gelesen; die Antwort ist ein
     * Resultset mit 300 Zeilen, RETURNVALUE, RETURNSTATUS und DONEPROC in Paketen von 512 Byte.
     *
     * @spec SPEC-030
     */
    @Test
    fun `SQL_BATCH und Resultset mit RETURNSTATUS`() {
        val sql = "select 1"
        val batch = hex("16 00 00 00  12 00 00 00  02 00  00 00 00 00 00 00 00 00  01 00 00 00") +
            sql.toByteArray(Charsets.UTF_16LE)
        val received = transfer(PacketType.SQL_BATCH, batch, PACKET_SIZE_SMALL, FramingLimits())
        val parsed = assertIs<ProtocolResult.Ok<SqlBatchRequest>>(SqlBatchParser.parse(received.payload)).value
        assertEquals(sql, parsed.text)

        val name = ColumnDef("name", TdsType.NVarcharType(20, latin), nullable = true)
        val columns = listOf(ColumnDef("id", TdsType.Int4Type), name)
        val rows = List(ROWS) { RowToken(listOf(TdsValue.IntegerValue(it.toLong()), TdsValue.TextValue("zeile $it"))) }
        val output = ColumnDef("", TdsType.IntNType(4), nullable = true)
        val response = listOf(ColMetadataToken(columns)) + rows + listOf(
            DoneToken(DoneKind.DONE_IN_PROC, DoneStatus.MORE or DoneStatus.COUNT, 193, ROWS.toLong()),
            ReturnValueToken(0, "@o", 1, output, TdsValue.IntegerValue(7)),
            ReturnStatusToken(0),
            DoneToken(DoneKind.DONE_PROC, DoneStatus.FINAL, 0, 0),
        )
        val back = transfer(PacketType.TABULAR_RESULT, tokensToBytes(response), PACKET_SIZE_MEDIUM, FramingLimits())
        assertEquals(response, TokenDecoder(back.payload).decodeAll())
    }

    private companion object {
        const val PACKET_SIZE_TINY = 24
        const val PACKET_SIZE_SMALL = 16
        const val PACKET_SIZE_MEDIUM = 512
        const val ROWS = 300
    }
}
