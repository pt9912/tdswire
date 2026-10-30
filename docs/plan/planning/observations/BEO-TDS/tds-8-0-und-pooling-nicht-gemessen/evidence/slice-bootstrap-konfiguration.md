**Vorgang:** slice-bootstrap-konfiguration
**Fund:** Der Serverprozess verdrahtet TLS nur im TDS-7.4-Pfad (gekapselt in Paketen vom Typ `0x12`); TLS ab dem ersten Byte (TDS 8.0) ist nicht verdrahtet (KDoc von `HandshakeConnectionHandler`), der Smoke läuft ohne TLS und ohne Pool.
