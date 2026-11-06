# Die Anmeldung und ENVCHANGE nach der Anmeldung stehen in keinem Mitschnitt

**Sub-Area:** `*` (Referenzabgleich, Mitschnitt-Lauf)

[SPEC-045](../../../../../../spec/spezifikation.md#spec-045--referenzabgleich-ablauf-und-ausnahmeliste-vorläufig) lässt den Mitschnitt-Lauf auch ENVCHANGE vergleichen. Bei `Encrypt=false` laufen PRELOGIN-Vorspann, TLS-Handschlag, LOGIN7 und die Anmeldeantwort verschlüsselt und gelangen nicht in die Ablage; die Pakettypen der 34 Dateien sind nur Batch, RPC und ATTENTION. Ein ENVCHANGE nach der Anmeldung (Datenbank, Sprache, Paketgröße) ist damit am Draht nicht gemessen; die Anmeldung trägt nur der Live-Lauf, am Ausgang des Clients.
