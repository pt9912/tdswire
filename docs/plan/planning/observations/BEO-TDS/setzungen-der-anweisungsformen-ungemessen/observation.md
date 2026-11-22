# Die Grenzen von `OR`, `SELECT <Ganzzahl>` und `WAITFOR DELAY` sind Setzung und am echten Server nicht gemessen

**Sub-Area:** `*` (Simulations-Backend, Spezifikation)

[ADR-0058](../../../../adr/0058-or-in-der-auswahl-ganzzahlkonstante-und-waitfor-delay-sind-erkannt.md) und `SPEC-036` kennzeichnen die Grenzen der drei Formen als Setzung; die Läufe vom 2026-11-22 tragen sie nicht: `OR` mit `IN` und `IS [NOT] NULL`, mehr als zwei Terme, `NULL` in der Folge; `SELECT <Ganzzahl>` für andere Werte als 2, die Grenzen von `Int32`, `SELECT 01` und `SELECT -0`, `SET FMTONLY ON SELECT 2`; `WAITFOR DELAY` mit regulärem Ende der Wartezeit und dem Bereich von `hh:mm:ss`; `QUERY_TIMEOUT` kürzer als die Wartezeit (nicht messbar, der echte Server kennt keine Frist des Servers). Die Aussagen stehen als Setzung, nicht als gemessen; eine Messung kann ihnen widersprechen.
