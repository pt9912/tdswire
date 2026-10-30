**Vorgang:** slice-codec-rpc-parser
**Fund:** Der RPC-Parser gilt als Festlegung für TDS 7.2 und höher; der Katalog (`sp_executesql`, `sp_prepexec`, `sp_execute`, RPC per Prozedurname) ist nur unter TDS 7.4 ohne TLS und mit `Pooling=false` am Spike-Testserver gemessen. Unter TDS 8.0 und Pooling (`sp_reset_connection`, `sp_unprepare`) ist nichts gemessen. Träger bleibt `slice-qa-kompatibilitaet`.
