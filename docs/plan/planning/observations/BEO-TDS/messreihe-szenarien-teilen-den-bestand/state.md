**Stand:** offen — 1×, unter der Schwelle, keine Regel. Behoben für das Szenario `rpc-batch-fehler` (eigene Tabelle, Prüfung im Beleg); die übrigen Szenarien teilen `wire_dml` weiter.

**Nachführung slice-mehrfach-rpc-messluecken (2026-11-17):** Die zehn neuen Szenarien legen je eine eigene Tabelle an; kein weiteres Auftreten, der Zähler bleibt 1×. Die älteren Szenarien teilen `wire_dml` weiter.
