# Eine Anforderung an den Zugangsweg eines Integrators hat keinen Slice, der ihn öffentlich macht

**Sub-Area:** `*` (Runtime, Backend-Anbindung)

[TDS-FA-08](../../../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung) (dokumentierte Integrationsschnittstelle), [TDS-RB-04](../../../../../../spec/lastenheft.md#tds-rb-04--externes-backend) (austauschbares Backend) und [TDS-FA-11](../../../../../../spec/lastenheft.md#tds-fa-11--mitgeliefertes-simulations-backend) (vom Integrator bereitgestellte Zeilenquelle) setzen voraus, dass ein Integrator den Server mit eigenem `QueryBackend` und eigenen Zeilenquellen starten kann. Im Stand des Repos ist `QueryBackend` `public`, der Start (`Bootstrap`, Verdrahtung, `RequestSource`, `HandlerSetup`) `internal`, und `main()` startet nur ohne Backend oder mit `SIMULATION_DESCRIPTION_FILE`; keine Slice-Datei trug den öffentlichen Einstieg.

Träger seit der Anlage dieser Beobachtung: [slice-integrations-einstieg](../../../next/slice-integrations-einstieg.md).

## Benannt, nicht gezählt

Das geschlossene Zeitdokument [welle-backend-streaming-results](../../../done/welle-backend-streaming-results.md) führt den Port ([TDS-FA-08](../../../../../../spec/lastenheft.md#tds-fa-08--backend-anbindung)) als belegt und bleibt unverändert; es nennt den Einstieg nicht. Die Beobachtung steht deshalb hier, nicht dort.
