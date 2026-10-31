**Vorgang:** slice-fehler-zustandsuebergang
**Fund:** `RESETCONNECTION` wertet der Automat nicht aus, und das Verhalten des Pools bei einer vom Server nach einem Fehler geschlossenen Verbindung ist nicht gemessen; die Fehlerfolge dieses Slice gilt ohne Pooling und für TDS 7.4. Träger bleibt `slice-qa-kompatibilitaet`.
