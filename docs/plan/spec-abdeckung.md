# Abdeckung der Festlegungen der Spezifikation durch Tests

Erzeugt von `make spec-abdeckung` aus den `@spec`-Tags im KDoc der Testfunktionen unter `src/test`
und den JUnit-Berichten des Testlaufs, der diese Matrix erzeugt hat. Die Festlegungen sind die
`SPEC-<NNN>`-Überschriften und die Tabellenzeilen mit `SPEC-<NNN>` in der ersten Zelle von
[`spec/spezifikation.md`](../../spec/spezifikation.md).

Die Matrix belegt den Stand **jenes Testlaufs**, nicht eines Commits: sie nennt keinen Commit-Hash,
damit der Erzeuger deterministisch bleibt. Wer ihren Stand braucht, erzeugt sie neu.

**Konvention des Tags:** eine KDoc-Zeile `* @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testfunktion,
Kennungen durch Leerzeichen getrennt (ein Komma macht die Kennung unbekannt). Mehrere `@spec`-Zeilen
untereinander im selben Block sind erlaubt und addieren sich. Der Tag haftet an der nächsten `fun`
(Modifikatoren nur `private`/`internal`; bei `suspend fun` oder `override fun` haftet er an der
nächsten passenden `fun`); ein Einzeilen-KDoc `/** @spec ... */` wird nicht gelesen; ein neuer
KDoc-Block oder eine Klasse verwirft einen offenen Tag.

Ein **Nachweis** ist ein Test, dessen Tag die Kennung nennt und der im Bericht desselben Laufs
bestanden steht (Klasse und Methodenname stimmen überein); das Urteil lautet „mindestens ein
bestandener getaggter Test“. Ein getaggter Test, der nicht gelaufen, rot oder übersprungen ist,
zählt nicht und steht im Abschnitt „Getaggt, aber ohne bestandenes Ergebnis“. Ein getaggter Test,
zu dem der Bericht keinen gleichnamigen Testfall führt, steht als „nicht gelaufen“; belegbar ist
das nur gegen einen Bericht, der den Namen nicht kennt. Kommt ein Testfall in mehreren Berichten
vor, zählt das schlechteste Ergebnis. **Grenze:** der Tag nennt die Festlegung, die der Autor
meint; ob die Assertions sie inhaltlich belegen, urteilt der Leser. Eine Zeile `<failure` oder
`<error` in der Ausgabe eines Testfalls färbt ihn rot.

Ein Gate als Nachweis (ein Make-Target statt eines Tests) kennt der Erzeuger nicht: eine Festlegung,
deren Titelzelle ein `make`-Target nennt oder die „Gate noch einzurichten“ trägt, steht ohne
bestandenen Test als „Gate-Festlegung, ungemessen“ und ist weder Nachweis noch Waise. Nennt
irgendein Test ihre Kennung, steht sie als Nachweis da und die Klasse ist nicht mehr zu sehen.
Die Art folgt dem Verzeichnis des Tests: `smoke/` Smoke-Test, `e2e/` E2E-Test, sonst Modul-Test.

Stand: 30 von 51 Festlegungen mit mindestens einem bestandenen Test, 7 Gate-Festlegungen ungemessen, 14 ohne Nachweis.

## Matrix

| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `SPEC-001` | Zustandsautomat einer Verbindung | 29 | 0 | 0 | 0 | DispatchFailureTest, DispatchFateTest, RpcDispatchTest, SqlBatchDispatchTest, StreamingDispatchTest, HandshakeDriverTest, HandshakeMatrixTest, RequestWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-002` | Interner Query-Vertrag | 11 | 0 | 0 | 5 | BackendModelTest, RpcBinderTest, RpcDispatchTest, SqlBatchDispatchTest, ResponseDecoderTest, ResponseMappingTest | mindestens ein bestandener getaggter Test |
| `SPEC-003` | Typmapping (initialer Mindestkatalog) | 53 | 0 | 0 | 0 | BackendModelTest, RpcBinderTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, RpcFailureTest, RpcTypesTest, DecimalValuesTest, TokenEncoderTest, TokenRoundtripTest | mindestens ein bestandener getaggter Test |
| `SPEC-004` | Testprofil | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-028` | PRELOGIN, TLS und LOGIN7: Zustands- und Antwortvertrag | 128 | 2 | 0 | 0 | DispatchFailureTest, StreamingDispatchTest, Login7ParserTest, PreloginCodecTest, HandshakeDriverTest, HandshakeMatrixTest, HandshakeTransportTest, LoginAdmissionTest, LoginResponseTest, PreloginNegotiationTest, SqlBatchParserTest, TokenEncoderTest, TokenRoundtripTest, BootstrapTest, ServerSmokeTest, TlsWiringTest, CodecSmokeTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, SocketChannelLoopbackTest, SwitchableChannelTest, MessageAssemblerTest, PacketHeaderTest, PacketWriterTest, ListenerSmokeTest, MessageReaderTest, MessageStreamTest, MessageWriterTest, TlsDataPhaseTest, TlsLoopbackTest, TlsNegativeTest, TlsTicketFlagTest | mindestens ein bestandener getaggter Test |
| `SPEC-029` | ATTENTION und Wiederverwendung der Verbindung | 6 | 0 | 0 | 0 | StreamingDispatchTest, ResponseFailureTest, ResponseMappingTest, MessageAssemblerTest | mindestens ein bestandener getaggter Test |
| `SPEC-030` | RPC-Varianten und Parameter-Metadaten | 29 | 1 | 0 | 0 | RpcBinderTest, RpcDispatchTest, RpcCatalogFormsTest, RpcFailureTest, RpcTypesTest, TokenEncoderTest, CodecSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-031` | Streaming-Fehler und Atomarität | 72 | 3 | 0 | 3 | DispatchClosingTest, DispatchFailureTest, DispatchFateTest, SqlBatchDispatchTest, StreamingDispatchTest, ErrorMapperTest, ResponseDecoderTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseMappingTest, ResponseOrderTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, RequestWiringTest, StreamingSmokeTest, MessageStreamTest | mindestens ein bestandener getaggter Test |
| `SPEC-035` | Tabellenbeschreibung und Zeilenquelle des Simulations-Backends | 30 | 0 | 0 | 0 | SimDescriptionReaderTest, SimModelTest | mindestens ein bestandener getaggter Test |
| `SPEC-036` | Erkannte Anweisungsformen des Simulations-Backends (vorläufig) | 47 | 0 | 0 | 0 | StatementFormsTest, StatementOracleTest, StatementPlannerTest, StatementScannerTest | mindestens ein bestandener getaggter Test |
| `SPEC-043` | Startprüfung: TLS-Konfiguration und Port | 20 | 0 | 0 | 0 | BootstrapTest, ServerSmokeTest, IoGroupTest, ListenerStartTest, TlsStartProbeTest | mindestens ein bestandener getaggter Test |
| `SPEC-044` | Geordnetes Beenden | 16 | 0 | 0 | 0 | BootstrapTest, HandshakeWiringTest, ServerSmokeTest, ListenerLifecycleTest, ListenerShutdownTest, ListenerTestBaseTest | mindestens ein bestandener getaggter Test |
| `SPEC-045` | Referenzabgleich: Ablauf und Ausnahmeliste (vorläufig) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-005` | `LISTEN_PORT` | 15 | 0 | 0 | 0 | ConfigLoaderTest, BootstrapTest, ServerSmokeTest, ConnectionStormTest, ListenerLifecycleTest, ListenerSmokeTest, ListenerStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 16 | 0 | 0 | 0 | ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, PacketHeaderTest, PacketWriterTest | mindestens ein bestandener getaggter Test |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 9 | 0 | 0 | 0 | HandshakeTransportTest, ConnectionStormTest, FramingFuzzTest, MessageAssemblerTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-008` | `LOGIN_TIMEOUT` | 27 | 0 | 0 | 0 | ConfigLoaderTest, AuthExecutorTest, HandshakeDriverTest, HandshakeWiringTest, ServerSmokeTest, ConnectionStormTest, ListenerLifecycleTest, ListenerSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` | 18 | 0 | 0 | 0 | ConfigLoaderTest, HandshakeTransportTest, PreloginNegotiationTest, HandshakeWiringTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, SocketChannelLoopbackTest, MessageAssemblerTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-047` | `SHUTDOWN_GRACE` | 3 | 0 | 0 | 0 | ListenerShutdownTest | mindestens ein bestandener getaggter Test |
| `SPEC-009` | `QUERY_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-010` | `ENCRYPTION_POLICY` | 14 | 0 | 0 | 0 | ConfigLoaderTest, HandshakeDriverTest, PreloginNegotiationTest, BootstrapTest, HandshakeWiringTest, ServerSmokeTest, TlsWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-033` | `CANCEL_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-034` | `MAX_CONNECTIONS` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | 8 | 0 | 0 | 0 | DispatchFailureTest, ErrorMapperTest, PreloginCodecTest, TokenEncoderTest, MessageAssemblerTest, PacketHeaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-012` | `TDS_LOGIN_FAILED` | 17 | 0 | 0 | 0 | UserListAuthAdapterTest, ErrorMapperTest, HandshakeDriverTest, HandshakeTransportTest, LoginAdmissionTest, LoginResponseTest, ServerSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | 4 | 0 | 0 | 0 | RpcDispatchTest, ErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | 26 | 0 | 0 | 0 | DispatchFailureTest, ErrorMapperTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseMappingTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, DecimalValuesTest, TokenEncoderTest | mindestens ein bestandener getaggter Test |
| `SPEC-015` | `TDS_BACKEND_ERROR` | 6 | 0 | 0 | 0 | ErrorMapperTest, ResponseErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-016` | `TDS_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | 5 | 0 | 0 | 0 | ErrorMapperTest, SimulationCodesTest, StatementPlannerTest | mindestens ein bestandener getaggter Test |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | 4 | 0 | 0 | 0 | SimulationCodesTest, StatementPlannerTest | mindestens ein bestandener getaggter Test |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | 1 | 0 | 0 | 0 | ErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | 31 | 0 | 0 | 0 | DispatchClosingTest, DispatchFailureTest, DispatchFateTest, RpcBinderTest, RpcDispatchTest, StreamingDispatchTest, ErrorMapperTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseOrderTest, HandshakeDriverTest, HandshakeWiringTest, RequestWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-049` | `TDS_SHUTDOWN` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-017` | `tds.connection` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-018` | `tds.query` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-019` | `backend.execute` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-020` | Microsoft TDS | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-021` | .NET SQL-Client | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-022` | Query-Backend | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-023` | TLS | 10 | 0 | 0 | 0 | TlsLoopbackTest, TlsNegativeTest, TlsStartProbeTest | mindestens ein bestandener getaggter Test |
| `SPEC-051` | Authentifizierungsadapter | 40 | 0 | 0 | 0 | AuthPrimitivesTest, AuthTextRepresentationTest, UserListAuthAdapterTest, UserListParserTest, ConfigLoaderTest, ConfigUsersFileTest, BootstrapTest | mindestens ein bestandener getaggter Test |
| `SPEC-024` | Unit-Test-Gate | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-025` | Integrationstest-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-026` | `make gates` | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-027` | Fuzz-/Property-Tests | 24 | 0 | 0 | 0 | Login7ParserTest, PreloginCodecTest, RpcFailureTest, RpcFuzzTest, SqlBatchParserTest, TokenRoundtripTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, RpcAssemblyTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-040` | Simulations-Test-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-042` | Build-Gate (`make build`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-050` | Referenzabgleich-Gate (vorläufig) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-032` | Kompatibilitätstest-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |

## Getaggt, aber ohne bestandenes Ergebnis

| Festlegung | Test | Grund |
| --- | --- | --- |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Ergebnis kommt vollstaendig und in Reihenfolge an` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Leeres Ergebnis traegt Metadaten und keine Zeile` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Fehler nach Zeilen ist ein Failure-Ereignis im Strom` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Fehler beim Start wirft nicht und erscheint als einziges Failure im Strom` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..ResultStart kommt vor der ersten Zeile` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..Generator laeuft nicht ueber die Abnahme hinaus` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..close beendet den laufenden Strom an der Zeilenquelle` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..cancel beendet den laufenden Strom an der Zeilenquelle` | nicht gelaufen (kein Ergebnis im Bericht) |

## Tags auf unbekannte Festlegungen

| Tag | Test |
| --- | --- |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Eine Folge offener Fehler vermischt keine Antworten` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Ein Fehler der Class 20 nach Teilzeilen schliesst nach genau einer Fehlerantwort` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Ein nicht abschliessbarer Strom schliesst ohne Antwort und ohne weiteres Token` |
