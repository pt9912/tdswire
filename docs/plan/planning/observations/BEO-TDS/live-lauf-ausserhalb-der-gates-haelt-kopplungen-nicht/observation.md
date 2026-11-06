# Der Live-Lauf ist kein Gate, und zwei Kopplungen, an denen er hängt, hält kein Sensor

**Sub-Area:** `*` (Referenzabgleich, Docker-Stages, Testschema)

Der Live-Lauf (`make vergleichslauf-d LAUF=live`) ist nach [ADR-0022](../../../../adr/0022-e2e-laeufe-nicht-in-den-gates.md) kein Gate. Zwei Kopplungen bleiben deshalb in `make gates` ungeprüft: (1) das Schema der Referenzinstanz (`referenztest-dotnet/Vergleich/testschema.sql`) und die Tabellen im Code des Integrators (`KompatibilitaetsServer.kt`) werden getrennt gepflegt; eine Änderung an Precision, Länge oder Nullbarkeit auf nur einer Seite fällt nur auf, wenn `LAUF=live` läuft und ein Wertschritt sie liest (die Schritte `schema-*` wären der Sensor, scheitern aber am Server, `BEO-TDS/schemaonly-keyinfo-fmtonly-ohne-traeger`); (2) der Lauf baut die Dockerfile-Stage `kompatibilitaetsserver` und liest `/home/gradle/server.pem`; ein Umbau der Stage ändert den Lauf, ohne dass ein Gate rot wird.
