**Vorgang:** slice-resetconnection-zuruecksetzen
**Fund:** SPEC-001 und das KDoc von PacketStatus sagen, ein Paket nur mit `0x10` setze nichts zurück; die Mutation `0x10` wie `0x08` bleibt grün (Verifikation V7, L-1), `PacketHeaderTest` prüft nur den Konstantenwert. Die Aussage ist als Festlegung gekennzeichnet, ein Test, der sie festhält, fehlt.
