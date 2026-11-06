# Die Prüfung der Mitschnitte sucht das Kennwort, nicht andere Kennungen der Instanz

**Sub-Area:** `*` (Referenzabgleich, Redaktion)

Die Redaktion und ihr Test suchen das Kennwort und den Prüfwert, die der Lauf kennt. Eine Kennung, die der Lauf nicht kennt (Servername der Instanz im ERROR-Token, Hostname, Container-ID), erkennt sie nicht; der Servername stand in 8 Dateien der ersten Aufnahme. Der Fall ist behoben (Ersetzung beim Ablegen, Einheitstest); die Klasse bleibt: jede neue Kennung im Strom, die nicht benannt ist, gelangt in die Ablage.
