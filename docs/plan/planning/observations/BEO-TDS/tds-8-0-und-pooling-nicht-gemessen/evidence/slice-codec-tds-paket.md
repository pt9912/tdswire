**Vorgang:** slice-codec-tds-paket
**Fund:** Der SQL_BATCH-Parser des Codecs ist als Festlegung für TDS 7.2 und höher geschrieben (das Clientprofil nennt 7.4 und 8.0); gemessen ist nur TDS 7.4 ohne Pool. Mehrpaketiges LOGIN7 und Pooling mit `sp_reset_connection` (der Paket-Codec meldet das Flag nur) sind ungemessen. Träger bleibt `slice-qa-kompatibilitaet`.
