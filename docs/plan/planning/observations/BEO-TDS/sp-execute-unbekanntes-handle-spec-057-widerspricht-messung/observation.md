# `SPEC-057` sendet für `sp_execute` mit unbekanntem Handle 50009, der echte SQL Server 8179 mit State 4

**Sub-Area:** `*` (Spezifikation, Fehlercodes, Prepared-Handles)

Die Messung M-6 (`docs/messungen/referenzabgleich.md`, am Referenzclient gegen den echten SQL Server, 2022 und 2025, Einzelmessung je Instanz) ergibt für `sp_execute` mit einem Handle, das die Sitzung nicht hält, `Number 8179, Class 16, State 4` mit dem Text „Could not find prepared statement with handle 999999.“ bei offener Verbindung. `SPEC-057` (`TDS_UNKNOWN_HANDLE`) sagt 50009 und State 1. Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte SQL Server die Referenz; die Angleichung braucht eine neue ADR (Re-Evaluierungs-Trigger von [ADR-0037](../../../../adr/0037-sp-unprepare-unbekanntes-handle-ergibt-fehler-8179.md) Offen 2), `Accepted`-ADRs bleiben unverändert.
