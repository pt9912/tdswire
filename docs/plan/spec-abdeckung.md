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

Stand: 23 von 51 Festlegungen mit mindestens einem bestandenen Test, 7 Gate-Festlegungen ungemessen, 21 ohne Nachweis.

## Matrix

| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `SPEC-001` | Zustandsautomat einer Verbindung | 14 | 0 | 0 | 0 | HandshakeDriverTest, HandshakeMatrixTest | mindestens ein bestandener getaggter Test |
| `SPEC-002` | Interner Query-Vertrag | 1 | 0 | 0 | 5 | BackendModelTest | mindestens ein bestandener getaggter Test |
| `SPEC-003` | Typmapping (initialer Mindestkatalog) | 45 | 0 | 0 | 0 | BackendModelTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, RpcFailureTest, RpcTypesTest, DecimalValuesTest, TokenEncoderTest, TokenRoundtripTest | mindestens ein bestandener getaggter Test |
| `SPEC-004` | Testprofil | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-028` | PRELOGIN, TLS und LOGIN7: Zustands- und Antwortvertrag | 109 | 2 | 0 | 0 | Login7ParserTest, PreloginCodecTest, HandshakeDriverTest, HandshakeMatrixTest, HandshakeTransportTest, LoginAdmissionTest, LoginResponseTest, PreloginNegotiationTest, SqlBatchParserTest, TokenEncoderTest, TokenRoundtripTest, CodecSmokeTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, PacketWriterTest, ListenerSmokeTest, MessageReaderTest, MessageWriterTest, TlsDataPhaseTest, TlsLoopbackTest, TlsNegativeTest, TlsTicketFlagTest | mindestens ein bestandener getaggter Test |
| `SPEC-029` | ATTENTION und Wiederverwendung der Verbindung | 1 | 0 | 0 | 0 | MessageAssemblerTest | mindestens ein bestandener getaggter Test |
| `SPEC-030` | RPC-Varianten und Parameter-Metadaten | 14 | 1 | 0 | 0 | RpcCatalogFormsTest, RpcFailureTest, RpcTypesTest, TokenEncoderTest, CodecSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-031` | Streaming-Fehler und Atomarität | 5 | 0 | 0 | 3 | ColumnMappingTest, MappingFuzzTest, ValueMappingTest | mindestens ein bestandener getaggter Test |
| `SPEC-035` | Tabellenbeschreibung und Zeilenquelle des Simulations-Backends | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-036` | Erkannte Anweisungsformen des Simulations-Backends (vorläufig) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-043` | Startprüfung: TLS-Konfiguration und Port | 12 | 0 | 0 | 0 | ListenerStartTest, TlsStartProbeTest | mindestens ein bestandener getaggter Test |
| `SPEC-044` | Geordnetes Beenden | 11 | 0 | 0 | 0 | ListenerLifecycleTest, ListenerShutdownTest | mindestens ein bestandener getaggter Test |
| `SPEC-045` | Referenzabgleich: Ablauf und Ausnahmeliste (vorläufig) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-005` | `LISTEN_PORT` | 4 | 0 | 0 | 0 | ListenerLifecycleTest, ListenerSmokeTest, ListenerStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 10 | 0 | 0 | 0 | PacketHeaderTest, PacketWriterTest | mindestens ein bestandener getaggter Test |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 5 | 0 | 0 | 0 | HandshakeTransportTest, MessageAssemblerTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-008` | `LOGIN_TIMEOUT` | 16 | 0 | 0 | 0 | AuthExecutorTest, HandshakeDriverTest, ListenerLifecycleTest, ListenerSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` | 9 | 0 | 0 | 0 | HandshakeTransportTest, PreloginNegotiationTest, SocketChannelLoopbackTest, MessageAssemblerTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-047` | `SHUTDOWN_GRACE` | 3 | 0 | 0 | 0 | ListenerShutdownTest | mindestens ein bestandener getaggter Test |
| `SPEC-009` | `QUERY_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-010` | `ENCRYPTION_POLICY` | 6 | 0 | 0 | 0 | HandshakeDriverTest, PreloginNegotiationTest | mindestens ein bestandener getaggter Test |
| `SPEC-033` | `CANCEL_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-034` | `MAX_CONNECTIONS` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | 5 | 0 | 0 | 0 | PreloginCodecTest, TokenEncoderTest, MessageAssemblerTest, PacketHeaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-012` | `TDS_LOGIN_FAILED` | 14 | 0 | 0 | 0 | UserListAuthAdapterTest, HandshakeDriverTest, HandshakeTransportTest, LoginAdmissionTest, LoginResponseTest | mindestens ein bestandener getaggter Test |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | 19 | 0 | 0 | 0 | ColumnMappingTest, MappingFuzzTest, ValueMappingTest, DecimalValuesTest, TokenEncoderTest | mindestens ein bestandener getaggter Test |
| `SPEC-015` | `TDS_BACKEND_ERROR` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-016` | `TDS_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | 2 | 0 | 0 | 0 | HandshakeDriverTest | mindestens ein bestandener getaggter Test |
| `SPEC-049` | `TDS_SHUTDOWN` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-017` | `tds.connection` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-018` | `tds.query` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-019` | `backend.execute` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-020` | Microsoft TDS | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-021` | .NET SQL-Client | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-022` | Query-Backend | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-023` | TLS | 9 | 0 | 0 | 0 | TlsLoopbackTest, TlsNegativeTest | mindestens ein bestandener getaggter Test |
| `SPEC-051` | Authentifizierungsadapter | 29 | 0 | 0 | 0 | AuthPrimitivesTest, AuthTextRepresentationTest, UserListAuthAdapterTest, UserListParserTest | mindestens ein bestandener getaggter Test |
| `SPEC-024` | Unit-Test-Gate | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-025` | Integrationstest-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-026` | `make gates` | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-027` | Fuzz-/Property-Tests | 17 | 0 | 0 | 0 | Login7ParserTest, PreloginCodecTest, RpcFailureTest, RpcFuzzTest, SqlBatchParserTest, TokenRoundtripTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, RpcAssemblyTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
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

Keine.
