# Abdeckung der Festlegungen der Spezifikation durch Tests

Erzeugt von `make spec-abdeckung` aus den `@spec`-Tags im KDoc der Testfunktionen unter `src/test`
und den JUnit-Berichten des Testlaufs, der diese Matrix erzeugt hat. Die Festlegungen sind die
`SPEC-<NNN>`-Überschriften und die Tabellenzeilen mit `SPEC-<NNN>` in der ersten Zelle von
[`spec/spezifikation.md`](../../spec/spezifikation.md).

Ein **Nachweis** ist ein Test, dessen Tag die Kennung nennt und der im Bericht desselben Laufs
bestanden steht (Klasse und Methodenname stimmen überein). Ein getaggter Test, der nicht gelaufen,
rot oder übersprungen ist, zählt nicht und steht im Abschnitt „Getaggt, aber ohne bestandenes
Ergebnis“. Ein umbenannter Test verliert seinen Nachweis, bis sein Name wieder zu einem Testfall
im Bericht passt. **Grenze:** der Tag nennt die Festlegung, die der Autor meint; ob die
Assertions sie inhaltlich belegen, urteilt der Leser. Ein Gate als Nachweis (ein Make-Target statt
eines Tests) kennt der Erzeuger nicht: eine Festlegung aus dem Abschnitt der Harness-Werkzeuge ohne
bestandenen Test steht als „Gate-Festlegung, ungemessen“ und ist weder Nachweis noch Waise. Die Art
folgt dem Verzeichnis des Tests: `smoke/` Smoke-Test, `e2e/` E2E-Test, sonst Modul-Test.

Stand: 12 von 51 Festlegungen mit mindestens einem bestandenen Test, 8 Gate-Festlegungen ungemessen, 31 ohne Nachweis.

## Matrix

| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `SPEC-001` | Zustandsautomat einer Verbindung | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-002` | Interner Query-Vertrag | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-003` | Typmapping (initialer Mindestkatalog) | 26 | 0 | 0 | 0 | RpcFailureTest, RpcTypesTest, TokenEncoderTest, TokenRoundtripTest | nachgewiesen |
| `SPEC-004` | Testprofil | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-028` | PRELOGIN, TLS und LOGIN7: Zustands- und Antwortvertrag | 53 | 2 | 0 | 0 | Login7ParserTest, PreloginCodecTest, SqlBatchParserTest, TokenEncoderTest, TokenRoundtripTest, CodecSmokeTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, PacketWriterTest, MessageReaderTest, MessageWriterTest | nachgewiesen |
| `SPEC-029` | ATTENTION und Wiederverwendung der Verbindung | 1 | 0 | 0 | 0 | MessageAssemblerTest | nachgewiesen |
| `SPEC-030` | RPC-Varianten und Parameter-Metadaten | 14 | 1 | 0 | 0 | RpcCatalogFormsTest, RpcFailureTest, RpcTypesTest, TokenEncoderTest, CodecSmokeTest | nachgewiesen |
| `SPEC-031` | Streaming-Fehler und Atomarität | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-035` | Tabellenbeschreibung und Zeilenquelle des Simulations-Backends | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-036` | Erkannte Anweisungsformen des Simulations-Backends (vorläufig) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-043` | Startprüfung: TLS-Konfiguration und Port | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-044` | Geordnetes Beenden | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-045` | Referenzabgleich: Ablauf und Ausnahmeliste (vorläufig) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-005` | `LISTEN_PORT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 10 | 0 | 0 | 0 | PacketHeaderTest, PacketWriterTest | nachgewiesen |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 3 | 0 | 0 | 0 | MessageAssemblerTest, MessageReaderTest | nachgewiesen |
| `SPEC-008` | `LOGIN_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` | 5 | 0 | 0 | 0 | SocketChannelLoopbackTest, MessageAssemblerTest, MessageReaderTest | nachgewiesen |
| `SPEC-047` | `SHUTDOWN_GRACE` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-009` | `QUERY_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-010` | `ENCRYPTION_POLICY` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-033` | `CANCEL_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-034` | `MAX_CONNECTIONS` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | 5 | 0 | 0 | 0 | PreloginCodecTest, TokenEncoderTest, MessageAssemblerTest, PacketHeaderTest | nachgewiesen |
| `SPEC-012` | `TDS_LOGIN_FAILED` | 1 | 0 | 0 | 0 | UserListAuthAdapterTest | nachgewiesen |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | 2 | 0 | 0 | 0 | TokenEncoderTest | nachgewiesen |
| `SPEC-015` | `TDS_BACKEND_ERROR` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-016` | `TDS_TIMEOUT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-049` | `TDS_SHUTDOWN` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-017` | `tds.connection` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-018` | `tds.query` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-019` | `backend.execute` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-020` | Microsoft TDS | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-021` | .NET SQL-Client | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-022` | Query-Backend | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-023` | TLS | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-051` | Authentifizierungsadapter | 29 | 0 | 0 | 0 | AuthPrimitivesTest, AuthTextRepresentationTest, UserListAuthAdapterTest, UserListParserTest | nachgewiesen |
| `SPEC-024` | Unit-Test-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-025` | Integrationstest-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-026` | `make gates` | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-027` | Fuzz-/Property-Tests | 17 | 0 | 0 | 0 | Login7ParserTest, PreloginCodecTest, RpcFailureTest, RpcFuzzTest, SqlBatchParserTest, TokenRoundtripTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, RpcAssemblyTest, MessageReaderTest | nachgewiesen |
| `SPEC-040` | Simulations-Test-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-042` | Build-Gate (`make build`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-050` | Referenzabgleich-Gate (vorläufig) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-032` | Kompatibilitätstest-Gate | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |

## Getaggt, aber ohne bestandenes Ergebnis

Keiner.

## Tags auf unbekannte Festlegungen

Keine.
