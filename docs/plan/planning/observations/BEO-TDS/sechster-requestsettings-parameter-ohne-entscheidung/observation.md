# Der Abstand der Schnappschüsse ist der sechste Parameter von `RequestSettings`, und der alte Konstruktor entfällt

**Sub-Area:** `*` (Bibliothek, öffentliche Fläche, Telemetrie)

Der Abstand der Schnappschüsse (`metricsIntervalSeconds`) steht in `RequestSettings` (`ServerConfig.requests`), weil ein siebter Parameter von `ServerConfig` die Grenze `LongParameterList` bricht und ein eigener Typ ein neuer öffentlicher Name wäre. Folge: `RequestSettings` hat sechs Ganzzahl-Parameter (`maxMessageBytes` fünfter, `metricsIntervalSeconds` sechster); ein Aufruf mit Ganzzahlen nach Position vertauscht beide still, und aus Java entfällt der Konstruktor `RequestSettings(int, int, int, int)` (kein `@JvmOverloads`), ebenso ändern sich `copy` und `component5`. Die [Spezifikation](../../../../../../spec/spezifikation.md#5-metriken-und-tracing-felder) führt den Ort; vor `1.0` ist die Fläche nach `SPEC-059` änderbar.

Optionen: (a) so lassen, Aufrufe benannt (Stand); (b) `@JvmOverloads` auf dem Konstruktor; (c) eigener Typ `ObservabilitySettings` (neuer öffentlicher Name, Liste von `SPEC-059`, `make flaeche`).
