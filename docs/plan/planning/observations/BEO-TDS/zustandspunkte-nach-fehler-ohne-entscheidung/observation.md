# Drei Zustandspunkte nach einem Fehler sind offen und hängen an keinem Slice

**Sub-Area:** `*` (Sitzungsrahmen, Fehlerpfad)

[ADR-0004](../../../../adr/0004-session-zustandsautomat.md) und [ADR-0007](../../../../adr/0007-fehler-und-attention.md) führen offen: die Antwort auf einen unzulässigen Übergang, den Sitzungszustand nach einem Fehler (offene Transaktion, R08 und R09 der Messung) und das Kriterium, ab dem der Strom „nicht mehr sicher abschließbar“ ist (SPEC-031 in der Spezifikation). Als Träger nennen beide ADRs „die Slices der Welle `welle-fehler-abbruch`“; mit der Closure von `slice-fehler-zustandsuebergang` führt keine Slice-Datei die drei Punkte mehr. `slice-attention-cancel-timeout` schließt die Antwort auf unzulässige Übergänge ausdrücklich aus seinem Umfang aus, `slice-abbruch-ressourcen-shutdown` führt nur den Zeitpunkt des Schließens nach `TDS_SHUTDOWN`.

Stand im Code (Setzungen, keine Entscheidungen): ein unzulässiger Übergang schließt ohne Antwort (`HandshakeMatrix`), der Sitzungszustand wird nach einem Fehler nicht angefasst (dass der Port keine Transaktion kennt, belegt kein Test), „nicht abschließbar“ heißt: die Senke nimmt ein Token nicht an (`ResponseSequencer`).

Grund für „weiter offen“: Die Entscheidung des Nutzers steht aus; ein Slice ohne Entscheidung hätte keinen Gegenstand, der sich bauen lässt. Fällt die Entscheidung, entsteht der Träger (Entscheidungs-Slice oder Ergänzung eines vorhandenen Slice) und diese Beobachtung geht auf `geplant`. `RESETCONNECTION` hat einen eigenen Träger: `BEO-TDS/tds-8-0-und-pooling-nicht-gemessen`. Verwandt, aber eigener Mechanismus: `BEO-TDS/offene-punkte-ohne-traeger` (dort fehlt der Träger beim Entscheidungs-Slice einer `Accepted`-ADR, hier beim letzten Slice, der den Punkt führt).
