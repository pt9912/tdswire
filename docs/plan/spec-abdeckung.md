# Abdeckung der Festlegungen der Spezifikation durch Tests

Erzeugt von `make spec-abdeckung` aus den `@spec`-Tags im KDoc der Testfunktionen unter `src/test`, `src/integrator` und `testserver`,
aus den `// @spec`-Kommentaren der C#-Tests unter `referenztest-dotnet`
und den Berichten des Testlaufs, der diese Matrix erzeugt hat (JUnit-XML des Gradle-Laufs, TRX der Stage `test-bericht` des .NET-Gerüsts). Die Festlegungen sind die
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

**Konvention des C#-Tags:** eine Kommentarzeile `// @spec SPEC-<NNN> [SPEC-<NNN> ...]` über der Testmethode; Attributzeilen
(`[Fact]`, `[Theory]`, `[InlineData(...)]`), Leer- und Kommentarzeilen dazwischen halten ihn, jede andere Zeile verwirft ihn.
Der Bericht der C#-Tests ist der der Stage `test-bericht` des .NET-Gerüsts (COPY-Quellen und DOTNET_TEST_ARGS wie die Stage `test`; weitere Flags in deren RUN-Zeile sind nicht gekoppelt); ein getaggter C#-Test, den sie nicht
ausführt (die zwei Tests des Abspielens, die nur in `make vergleichslauf-d` laufen), steht als „nicht gelaufen“.

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
Die Art folgt dem Verzeichnis des Tests: `smoke/` (C#: `Smoke/`) Smoke-Test, `e2e/` E2E-Test, sonst Modul-Test.

Stand: 45 von 58 Festlegungen mit mindestens einem bestandenen Test, 4 Gate-Festlegungen ungemessen, 9 ohne Nachweis.

## Matrix

| Festlegung | Titel | Modul-Test | Smoke-Test | E2E-Test | Nicht bestanden | Belegt durch | Urteil |
| --- | --- | --- | --- | --- | --- | --- | --- |
| `SPEC-001` | Zustandsautomat einer Verbindung | 53 | 0 | 0 | 0 | AttentionCancelTest, DispatchFailureTest, DispatchFateTest, PreparedHandlesDispatchTest, PreparedHandlesTest, RequestSequenceTest, ResetConnectionDispatchTest, RpcDispatchTest, SqlBatchDispatchTest, StreamingDispatchTest, UnprepareDispatchTest, AttentionHandshakeTest, HandshakeDriverTest, HandshakeMatrixTest, RequestWiringTest, Tds8WiringTest, Tds8ProcessTest | mindestens ein bestandener getaggter Test |
| `SPEC-002` | Interner Query-Vertrag | 18 | 0 | 0 | 5 | BackendModelTest, AnswerFormsDispatchTest, HandleReturningBackendTest, PreparedHandlesDispatchTest, RequestSequenceTest, RpcBinderTest, RpcDispatchTest, SqlBatchDispatchTest, ResponseDecoderTest, ResponseMappingTest, SimulationBackendSelectTest | mindestens ein bestandener getaggter Test |
| `SPEC-003` | Typmapping (initialer Mindestkatalog) | 54 | 0 | 0 | 0 | BackendModelTest, RpcBinderTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, RpcFailureTest, RpcTypesTest, DecimalValuesTest, TokenEncoderTest, TokenRoundtripTest, NameFoldTest | mindestens ein bestandener getaggter Test |
| `SPEC-004` | Testprofil | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-028` | PRELOGIN, TLS und LOGIN7: Zustands- und Antwortvertrag | 131 | 2 | 0 | 0 | DispatchFailureTest, StreamingDispatchTest, Login7ParserTest, PreloginCodecTest, HandshakeDriverTest, HandshakeMatrixTest, HandshakeTransportTest, LoginAdmissionTest, LoginResponseTest, PreloginNegotiationTest, SqlBatchParserTest, TokenEncoderTest, TokenRoundtripTest, Tds8WiringTest, Tds8DefenseTest, TlsWiringTest, CodecSmokeTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, SocketChannelLoopbackTest, SwitchableChannelTest, MessageAssemblerTest, PacketHeaderTest, PacketWriterTest, ListenerSmokeTest, MessageReaderTest, MessageStreamTest, MessageWriterTest, NetInboundTest, TlsDataPhaseTest, TlsLoopbackTest, TlsNegativeTest, TlsSessionTicketTest | mindestens ein bestandener getaggter Test |
| `SPEC-029` | ATTENTION und Wiederverwendung der Verbindung | 34 | 0 | 0 | 0 | AttentionCancelTest, AttentionWireTest, CancelTimeoutTest, DisconnectTest, QueryTimeoutTest, ShutdownDispatchTest, StreamingDispatchTest, ResponseFailureTest, ResponseMappingTest, AbbruchWiringTest, ConnectionLeakTest, ShutdownWiringTest, SimulationBackendChangeTest, SimulationBackendSourceTest, MessageAssemblerTest | mindestens ein bestandener getaggter Test |
| `SPEC-030` | RPC-Varianten und Parameter-Metadaten | 100 | 1 | 0 | 0 | FlaecheEmbeddingTest, HandleLimitEmbeddingTest, AnswerFormsDispatchTest, HandleReturningBackendTest, PreparedHandlesDispatchTest, PreparedHandlesTest, QueryTimeoutTest, RequestSequenceTest, ResetConnectionDispatchTest, RpcBinderTest, RpcDispatchTest, RpcPositionalNamesTest, UnprepareBatchTest, UnprepareDispatchTest, ErrorMapperTest, ResponseMeasuredTest, ResponseRpcTest, RpcCatalogFormsTest, RpcFailureTest, RpcTypesTest, TokenEncoderTest, CodecSmokeTest | mindestens ein bestandener getaggter Test |
| `SPEC-031` | Streaming-Fehler und Atomarität | 112 | 3 | 0 | 4 | FlaecheEmbeddingTest, BackendModelTest, AnswerFormsDispatchTest, AttentionCancelTest, AttentionWireTest, DisconnectTest, DispatchClosingTest, DispatchFailureTest, DispatchFateTest, QueryTimeoutTest, ShutdownDispatchTest, SqlBatchDispatchTest, StreamingDispatchTest, ErrorMapperTest, ResponseDecoderTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseMappingTest, ResponseMeasuredTest, ResponseOrderTest, ResponseRpcTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, ConnectionLeakTest, RequestWiringTest, SynchronitaetWiringTest, SimulationBackendSourceTest, SimulationMemoryTest, StreamingSmokeTest, MessageStreamTest | mindestens ein bestandener getaggter Test |
| `SPEC-035` | Tabellenbeschreibung und Zeilenquelle des Simulations-Backends | 93 | 0 | 0 | 0 | EmbeddingTest, FlaecheEmbeddingTest, SimulationWiringTest, TestserverSimulationProcessTest, SimModelTest, SimulationBackendCatalogTest, SimulationBackendChangeTest, SimulationBackendSelectTest, SimValuesTest, ConfigSimulationFileTest, SimDescriptionReaderTest, TableStoreTest, TestserverStartTest, YamlTreeReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-036` | Erkannte Anweisungsformen des Simulations-Backends (vorläufig) | 91 | 0 | 0 | 0 | FlaecheEmbeddingTest, CatalogFormsTest, FixedFormsTest, SimModelTest, SimulationBackendCatalogTest, SimulationBackendFixedTest, SimulationBackendSelectTest, SimulationBackendStructureTest, SimValuesTest, StatementFormsTest, StatementOracleTest, StatementPlannerTest, StatementScannerTest, NameFoldTest | mindestens ein bestandener getaggter Test |
| `SPEC-043` | Startprüfung: TLS-Konfiguration und Port | 40 | 0 | 0 | 0 | TelemetryEmbeddingTest, TelemetryFelderEmbeddingTest, BootstrapTest, SecurityConfigNegativeTest, ServerSmokeTest, TdsServerLifecycleTest, TelemetryRedactionTest, TestserverLogProcessTest, TlsHandshakeNegativeTest, IoGroupTest, ListenerStartTest, TlsStartProbeTest, ConfigLoaderTest, LogAdapterTest, LogStartTest, TelemetryJsonTest, TestserverStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-044` | Geordnetes Beenden | 44 | 0 | 0 | 0 | EmbeddingTest, ShutdownDispatchTest, BootstrapTest, HandshakeWiringTest, ServerSmokeTest, ShutdownWiringTest, TdsServerLifecycleTest, ListenerAbortTest, ListenerLifecycleTest, ListenerPartialMessageTest, ListenerShutdownTest, ListenerTestBaseTest, MessageReaderMidMessageTest | mindestens ein bestandener getaggter Test |
| `SPEC-045` | Referenzabgleich: Ablauf und Ausnahmeliste (vorläufig) | 53 | 0 | 0 | 3 | MessungBelegTests, MessungEinheitTests, MitschnittEinheitTests | mindestens ein bestandener getaggter Test |
| `SPEC-005` | `LISTEN_PORT` (Eingang des Testservers) | 20 | 0 | 0 | 0 | ConfigValidationTest, BootstrapTest, ServerSmokeTest, ConnectionStormTest, ListenerLifecycleTest, ListenerRemoteAddressTest, ListenerSmokeTest, ListenerStartTest, ConfigLoaderTest, TestserverStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-006` | `MAX_TDS_PACKET_BYTES` | 16 | 0 | 0 | 0 | ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, PacketHeaderTest, PacketWriterTest | mindestens ein bestandener getaggter Test |
| `SPEC-007` | `MAX_MESSAGE_BYTES` | 9 | 0 | 0 | 0 | HandshakeTransportTest, ConnectionStormTest, FramingFuzzTest, MessageAssemblerTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-008` | `LOGIN_TIMEOUT` (Eingang des Testservers) | 35 | 0 | 0 | 0 | ConfigValidationTest, AuthExecutorTest, AuthPoolOccupationTest, HandshakeDriverTest, LoginThrottleDriverTest, LoginThrottleTimingTest, HandshakeWiringTest, ServerSmokeTest, ConnectionStormTest, ListenerLifecycleTest, ListenerSmokeTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-046` | `MAX_PRELOGIN_MESSAGE_BYTES` (Eingang des Testservers) | 20 | 0 | 0 | 0 | ConfigValidationTest, HandshakeTransportTest, PreloginNegotiationTest, HandshakeWiringTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, SocketChannelLoopbackTest, MessageAssemblerTest, MessageReaderTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-047` | `SHUTDOWN_GRACE` | 8 | 0 | 0 | 0 | ServerSmokeTest, ShutdownWiringTest, ListenerShutdownTest | mindestens ein bestandener getaggter Test |
| `SPEC-009` | `QUERY_TIMEOUT` | 8 | 0 | 0 | 0 | ConfigValidationTest, QueryTimeoutTest, AbbruchWiringTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-010` | `ENCRYPTION_POLICY` | 25 | 0 | 0 | 0 | EmbeddingTest, ConfigValidationTest, HandshakeDriverTest, PreloginNegotiationTest, BootstrapTest, HandshakeWiringTest, SecurityConfigNegativeTest, ServerSmokeTest, Tds8WiringTest, Tds8DefenseTest, TlsHandshakeNegativeTest, TlsWiringTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-033` | `CANCEL_TIMEOUT` | 12 | 0 | 0 | 0 | ConfigValidationTest, CancelTimeoutTest, QueryTimeoutTest, ShutdownDispatchTest, ShutdownWiringTest, ListenerAbortTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-034` | `MAX_CONNECTIONS` | 4 | 0 | 0 | 0 | ListenerLimitTest | mindestens ein bestandener getaggter Test |
| `SPEC-061` | `MAX_PREPARED_HANDLES` | 3 | 0 | 0 | 0 | HandleLimitEmbeddingTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-053` | Drosselung wiederholter Anmeldeversuche | 23 | 0 | 0 | 0 | LoginThrottleDriverTest, LoginThrottleTest, LoginThrottleTimingTest, LoginThrottleWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-011` | `TDS_PROTOCOL_ERROR` | 9 | 0 | 0 | 0 | DispatchFailureTest, RpcDispatchTest, ErrorMapperTest, PreloginCodecTest, TokenEncoderTest, MessageAssemblerTest, PacketHeaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-012` | `TDS_LOGIN_FAILED` | 36 | 0 | 0 | 0 | TelemetryEmbeddingTest, TelemetryFelderEmbeddingTest, UserListAuthAdapterTest, TelemetryEventTest, ErrorMapperTest, HandshakeDriverTest, HandshakeTransportTest, LoginAdmissionTest, LoginPathTextTest, LoginResponseTest, LoginThrottleDriverTest, LoginLogInspectionTest, LoginThrottleWiringTest, ServerSmokeTest, TelemetryRedactionTest, TestserverLogProcessTest, LogStartTest, TelemetryJsonTest | mindestens ein bestandener getaggter Test |
| `SPEC-013` | `TDS_UNSUPPORTED_RPC` | 12 | 0 | 0 | 0 | PreparedHandlesDispatchTest, RequestSequenceTest, RpcBinderTest, RpcDispatchTest, UnprepareDispatchTest, ErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-014` | `TDS_UNSUPPORTED_TYPE` | 27 | 0 | 0 | 0 | AnswerFormsDispatchTest, DispatchFailureTest, ErrorMapperTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseMappingTest, ColumnMappingTest, MappingFuzzTest, ValueMappingTest, DecimalValuesTest, TokenEncoderTest | mindestens ein bestandener getaggter Test |
| `SPEC-015` | `TDS_BACKEND_ERROR` | 6 | 0 | 0 | 0 | ErrorMapperTest, ResponseErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-016` | `TDS_TIMEOUT` | 12 | 0 | 0 | 0 | CancelTimeoutTest, DispatchFailureTest, QueryTimeoutTest, AbbruchWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-037` | `TDS_UNSUPPORTED_SQL` | 9 | 0 | 0 | 0 | ErrorMapperTest, SimulationCodesTest, SimulationBackendChangeTest, SimulationBackendFixedTest, SimulationBackendSelectTest, StatementPlannerTest | mindestens ein bestandener getaggter Test |
| `SPEC-038` | `TDS_SIM_UNKNOWN_OBJECT` | 8 | 0 | 0 | 0 | SimulationCodesTest, SimulationBackendChangeTest, SimulationBackendFixedTest, SimulationBackendSelectTest, StatementPlannerTest | mindestens ein bestandener getaggter Test |
| `SPEC-039` | `TDS_SIM_INVALID_VALUE` | 14 | 0 | 0 | 0 | ErrorMapperTest, SimulationCodesTest, SimulationBackendChangeTest, SimulationBackendSelectTest, SimValuesTest | mindestens ein bestandener getaggter Test |
| `SPEC-048` | `TDS_INTERNAL_ERROR` | 43 | 0 | 0 | 0 | AnswerFormsDispatchTest, DispatchClosingTest, DispatchFailureTest, DispatchFateTest, RpcBinderTest, RpcDispatchTest, StreamingDispatchTest, ErrorMapperTest, ResponseErrorMapperTest, ResponseFailureTest, ResponseOrderTest, ResponseRpcTest, HandshakeDriverTest, HandshakeWiringTest, LoginLogInspectionTest, RequestWiringTest, SynchronitaetWiringTest, TelemetryRedactionTest, TelemetryWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-049` | `TDS_SHUTDOWN` | 5 | 0 | 0 | 0 | ShutdownDispatchTest, ShutdownWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-057` | `TDS_UNKNOWN_HANDLE` | 7 | 0 | 0 | 0 | PreparedHandlesDispatchTest, RequestSequenceTest, ResetConnectionDispatchTest, ErrorMapperTest | mindestens ein bestandener getaggter Test |
| `SPEC-058` | `TDS_PREPARE_LIMIT` | 10 | 0 | 0 | 0 | HandleLimitEmbeddingTest, PreparedHandlesDispatchTest, PreparedHandlesTest, ResetConnectionDispatchTest, UnprepareDispatchTest, ErrorMapperTest, ConfigLoaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-060` | `TDS_SIM_UNKNOWN_PREPARED_HANDLE` | 7 | 0 | 0 | 0 | UnprepareDispatchTest, ErrorMapperTest, RequestWiringTest | mindestens ein bestandener getaggter Test |
| `SPEC-017` | `tds.connection` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-018` | `tds.query` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-019` | `backend.execute` | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-020` | Microsoft TDS | 3 | 0 | 0 | 0 | Tds8WiringTest, Tds8ProcessTest | mindestens ein bestandener getaggter Test |
| `SPEC-021` | .NET SQL-Client | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-022` | Query-Backend | 4 | 0 | 0 | 0 | EmbeddingTest, QueryTimeoutTest | mindestens ein bestandener getaggter Test |
| `SPEC-023` | TLS | 16 | 0 | 0 | 0 | TlsHandshakeNegativeTest, TlsLoopbackTest, TlsNegativeTest, TlsRenegotiationTest, TlsSessionTicketTest, TlsStartProbeTest | mindestens ein bestandener getaggter Test |
| `SPEC-051` | Authentifizierungsadapter | 48 | 0 | 0 | 0 | FlaecheEmbeddingTest, AuthPrimitivesTest, AuthTextRepresentationTest, AuthTimingTest, UserListAuthAdapterTest, UserListParserTest, ConfigValidationTest, AuthPoolOccupationTest, LoginThrottleDriverTest, SecurityConfigNegativeTest, ConfigLoaderTest, ConfigUsersFileTest, TestserverStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-059` | Einstieg des Integrators | 26 | 0 | 0 | 0 | EmbeddingTest, FlaecheEmbeddingTest, HandleLimitEmbeddingTest, TelemetryEmbeddingTest, TelemetryFelderEmbeddingTest, TelemetryEventTest, RequestWiringTest, TdsServerLifecycleTest, TelemetryTest | mindestens ein bestandener getaggter Test |
| `SPEC-024` | Unit-Test-Gate | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-025` | Integrationstest (E2E-Durchgang) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-026` | `make gates` | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-027` | Fuzz-/Property-Tests | 25 | 0 | 0 | 0 | Login7ParserTest, PreloginCodecTest, RpcFailureTest, RpcFuzzTest, SqlBatchParserTest, TokenRoundtripTest, ServerSmokeTest, ConnectionStormTest, FramingFuzzTest, HandshakeFuzzTest, StreamCutoffTest, SocketChannelLoopbackTest, MessageAssemblerTest, PacketHeaderTest, RpcAssemblyTest, MessageReaderTest | mindestens ein bestandener getaggter Test |
| `SPEC-040` | Simulations-Tests (`make simulations-integration` im E2E-Durchgang) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-041` | Static-Analysis-Gate (`make detekt`, `make detekt-selbsttest`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-042` | Build-Gate (`make build`) | 0 | 0 | 0 | 0 | — | Gate-Festlegung, ungemessen |
| `SPEC-052` | Bibliotheks-Artefakt und Lizenz (E2E-Durchgang: `make laufzeittest` mit dem Testserver; der Tag `@spec` nennt nur den Lizenztext) | 30 | 0 | 0 | 0 | LizenzTest, TestserverLogProcessTest, LogAdapterTest, LogLevelSettingTest, LogStartTest, TelemetryJsonTest, TestserverStartTest | mindestens ein bestandener getaggter Test |
| `SPEC-050` | Referenzabgleich (vorläufig, E2E-Durchgang) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |
| `SPEC-032` | Kompatibilitätstest (E2E-Durchgang) | 0 | 0 | 0 | 0 | — | OHNE NACHWEIS |

## Getaggt, aber ohne bestandenes Ergebnis

| Festlegung | Test | Grund |
| --- | --- | --- |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Ergebnis kommt vollstaendig und in Reihenfolge an` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Leeres Ergebnis traegt Metadaten und keine Zeile` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Fehler nach Zeilen ist ein Failure-Ereignis im Strom` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..Fehler beim Start wirft nicht und erscheint als einziges Failure im Strom` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-002` | `io.github.pt9912.tdswire.port..ResultStart kommt vor der ersten Zeile` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..Generator laeuft nicht ueber die Abnahme hinaus` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..Generator laeuft waehrend der Wartezeit des Lesers nicht voraus` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..close beendet den laufenden Strom an der Zeilenquelle` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-031` | `io.github.pt9912.tdswire.port..cancel beendet den laufenden Strom an der Zeilenquelle` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-045` | `TdsWire.Referenztests.Vergleich.Lauf.Die_Messreihe_laeuft_ueber_den_TlsProxy_und_die_Ablage_ist_frei_von_Zugangsdaten` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-045` | `TdsWire.Referenztests.Vergleich.MitschnittAufnahmeTests.Die_Szenarien_laufen_ueber_den_Proxy_und_die_Ablage_ist_frei_von_Kennwort` | nicht gelaufen (kein Ergebnis im Bericht) |
| `SPEC-045` | `TdsWire.Referenztests.Vergleich.MitschnittAbspielenTests.Die_Mitschnitte_laufen_gegen_den_Server_und_beide_Seiten_liegen_als_Ausgabe_vor` | nicht gelaufen (kein Ergebnis im Bericht) |

## Tags auf unbekannte Festlegungen

| Tag | Test |
| --- | --- |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionCancelTest.ATTENTION in RUNNING loest einen Cancel aus und wird mit DONE ATTN allein beantwortet` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionCancelTest.ATTENTION in RUNNING loest einen Cancel aus und wird mit DONE ATTN allein beantwortet` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionCancelTest.ATTENTION in STREAMING stoppt die Zeilen und schliesst mit einem DONE ATTN ab` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionCancelTest.ATTENTION erreicht ein blockierendes Backend` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionCancelTest.Eine Ausnahme beim Mitlesen bricht den Lauf des Backends ab` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.AttentionWireTest.ATTENTION mitten in einem Paket laesst kein halbes Paket und endet mit DONE ATTN` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.CancelTimeoutTest.Ein Backend, das den Cancel ignoriert, fuehrt nach CANCEL_TIMEOUT zum Schliessen` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in RUNNING bricht die Anfrage ab und schreibt nichts` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in RUNNING bricht die Anfrage ab und schreibt nichts` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in STREAMING schliesst den Strom ohne weiteren Schreibvorgang` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in STREAMING schliesst den Strom ohne weiteren Schreibvorgang` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Lesefehler in STREAMING wirkt wie das Ende der Gegenstelle` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Lesefehler in STREAMING wirkt wie das Ende der Gegenstelle` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in CANCELLING verhindert die Bestaetigung und einen zweiten Cancel` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle in CANCELLING verhindert die Bestaetigung und einen zweiten Cancel` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle im Leerlauf beendet die Schleife ohne Backend-Aufruf` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle im Leerlauf beendet die Schleife ohne Backend-Aufruf` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DisconnectTest.Ende der Gegenstelle nach der beantworteten Anfrage ruft kein cancel` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Eine Folge offener Fehler vermischt keine Antworten` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Ein Fehler der Class 20 nach Teilzeilen schliesst nach genau einer Fehlerantwort` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.protocol.dispatch.DispatchFateTest.Ein nicht abschliessbarer Strom schliesst ohne Antwort und ohne weiteres Token` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.ResetConnectionDispatchTest.Das Bit im Status des ersten Pakets erreicht den Sitzungsrahmen ueber den Transport` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.ShutdownDispatchTest.Eine Anfrage nach dem Beginn des Beendens wird mit TDS_SHUTDOWN beantwortet` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.ShutdownDispatchTest.Nach der regulaeren Antwort bleibt die Verbindung offen und eine spaetere Anfrage erhaelt TDS_SHUTDOWN` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.dispatch.ShutdownDispatchTest.Der Ablauf der Frist bricht die Anfrage wie ATTENTION ab und beendet die Verbindung` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.session.AttentionHandshakeTest.ATTENTION in PRELOGIN schliesst ohne Antwortbytes` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.session.AttentionHandshakeTest.ATTENTION in PRELOGIN nach TLS ab dem ersten Byte schliesst ohne Antwortbytes` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.session.AttentionHandshakeTest.ATTENTION in LOGIN7 schliesst ohne weitere Antwortbytes` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.protocol.session.AttentionHandshakeTest.ATTENTION ist in jedem Zustand des Handshakes ein Schliessen ohne Antwort` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.AbbruchWiringTest.ATTENTION ueber den Socket wird mit DONE ATTN beantwortet und die Verbindung bleibt offen` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.runtime.AbbruchWiringTest.ATTENTION ueber den Socket wird mit DONE ATTN beantwortet und die Verbindung bleibt offen` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.AbbruchWiringTest.QUERY_TIMEOUT aus der Umgebung endet in TDS_TIMEOUT bei offener Verbindung` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ConnectionLeakTest.viele Abbrueche in jedem Zustand lassen weder Strom noch Verbindung noch Thread zurueck` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.runtime.ConnectionLeakTest.viele Abbrueche in jedem Zustand lassen weder Strom noch Verbindung noch Thread zurueck` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ServerSmokeTest.Der Prozess-Stop beendet eine ruhende Verbindung und bricht die laufende Anfrage nach der Frist ab` |
| `TDS-FA-01` | `io.github.pt9912.tdswire.runtime.ServerSmokeTest.Ein beschaedigtes PRELOGIN schliesst ohne Antwort und der Prozess bleibt erreichbar` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.runtime.ServerSmokeTest.Ein beschaedigtes PRELOGIN schliesst ohne Antwort und der Prozess bleibt erreichbar` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ShutdownWiringTest.der Stop wartet auf die laufende Anfrage, eine spaetere Anfrage erhaelt TDS_SHUTDOWN` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ShutdownWiringTest.eine Anfrage nach dem Beginn erhaelt TDS_SHUTDOWN und danach endet die Verbindung` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ShutdownWiringTest.nach Ablauf der Frist wird die Anfrage abgebrochen und die Verbindung endet` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.runtime.ShutdownWiringTest.eine halbe Nachricht haelt die Verbindung offen und erhaelt nach dem Rest TDS_SHUTDOWN` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.runtime.SynchronitaetWiringTest.Anfragen hinter einem Fehler der Class 16 erhalten je genau ihre eigene Antwort` |
| `TDS-QA-07` | `io.github.pt9912.tdswire.runtime.SynchronitaetWiringTest.Eine Anfrage hinter einem Fehler der Class 20 bleibt unbeantwortet und ohne Backend` |
| `TDS-FA-09` | `io.github.pt9912.tdswire.runtime.TelemetryWiringTest.Ein Handler mit Ausnahme ergibt CONNECTION_FAILED ohne Sitzung und mit unbekanntem Profil` |
| `TDS-FA-09` | `io.github.pt9912.tdswire.runtime.TelemetryWiringTest.Eine Verbindung ohne Ausnahme des Handlers ergibt kein CONNECTION_FAILED` |
| `TDS-FA-09` | `io.github.pt9912.tdswire.runtime.TelemetryWiringTest.Ein gescheitertes Annehmen ergibt ACCEPT_FAILED und der Server nimmt danach wieder an` |
| `TDS-FA-07` | `io.github.pt9912.tdswire.transport.listener.ListenerLimitTest.genau MAX_CONNECTIONS Verbindungen sind im Handler, die naechste wird abgewiesen` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.transport.listener.ListenerLimitTest.genau MAX_CONNECTIONS Verbindungen sind im Handler, die naechste wird abgewiesen` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.transport.listener.ListenerLimitTest.eine bestehende Verbindung bleibt bedienbar, wenn eine weitere abgewiesen wird` |
| `TDS-QA-02` | `io.github.pt9912.tdswire.transport.listener.ListenerLimitTest.der Verbindungssturm ueber der Grenze nimmt genau MAX_CONNECTIONS auf` |
