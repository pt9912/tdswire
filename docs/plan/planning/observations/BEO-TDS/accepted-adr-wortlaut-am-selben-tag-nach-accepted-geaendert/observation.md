# Der Wortlaut einer ADR ändert sich nach dem Commit auf `Accepted` noch einmal

**Sub-Area:** `*` (ADRs)

`AGENTS.md` §3.5: eine ADR mit Status `Accepted` wird nicht inhaltlich überschrieben. [ADR-0033](../../../../adr/0033-callback-loesung-fuer-schreiben-struktur-und-massenladen.md) wurde in `fef4b5b` (2026-11-06 04:53) auf `Accepted` gesetzt; vier Minuten später (`680033f`, Verifikations-Findings V-3 und V-4) änderte ein Commit zwei Zeilen im Wortlaut: den Spike-Absatz (Rechner, Last und Digest ergänzt) und die Reihenfolge der Grenzen (1) bis (4). Die Änderungen sind nach Lektüre des Diffs Ergänzungen und Umordnung, keine Änderung der Entscheidung. Ob der Zeitraum zwischen `Accepted` und dem Merge als Fenster für solche Nachzüge gilt oder ob jede Änderung nach `Accepted` eine neue ADR verlangt, ist in `AGENTS.md` nicht ausdrücklich gesagt; **Lesart offen, Frage an den Nutzer**, vom Planner nicht gewertet.
