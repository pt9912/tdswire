**Vorgang:** slice-handshake-login
**Fund:** Das mehrpaketige, byteweise zerstückelte LOGIN7 ist ein von Hand aus MS-TDS abgeleiteter Vektor (Festlegung); ob ein Client LOGIN7 über mehrere Pakete sendet, ist nicht bekannt. Die TDS-8.0-Reihenfolge (TLS vor PRELOGIN) ist Festlegung, `ResetConnection` wertet der Automat nicht aus. Träger bleibt `slice-qa-kompatibilitaet`.
