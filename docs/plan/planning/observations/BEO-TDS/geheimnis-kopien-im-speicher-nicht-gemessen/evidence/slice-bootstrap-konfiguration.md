**Vorgang:** slice-bootstrap-konfiguration
**Fund:** Das Kennwort des Keystore wird als `char[]` überschrieben, liegt aber vorher als `String` und `ByteArray` im Heap, ebenso der Text der Benutzerliste (Review F-7); keine Textdarstellung zeigt sie. Aus dem Code gelesen, kein Speicherabzug. Angenommen als Grenze; die Spezifikation sagt dazu nichts.
