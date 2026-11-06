# Die Obergrenze von 100 gemeldeten Nummern je Unprepare-Batch weicht vom echten Server ab

**Sub-Area:** `*` (Spezifikation, Fehlercodes, Prepared-Handles)

Die Batch-Freigabe `EXEC sp_unprepare <n>[;…]` meldet nach `SPEC-060` höchstens die ersten 100 nicht gehaltenen Nummern eines Batches (ERROR und Log-Eintrag je Nummer). Das ist eine Setzung zugunsten von [TDS-QA-02](../../../../../../spec/lastenheft.md#tds-qa-02--stabilität) (Verstärkung einer Anfrage nach oben begrenzt). Der echte SQL Server meldet nach Messzeile M-7 jede unbekannte Nummer (zwei Unbekannte, zwei Fehler); ob und ab welcher Zahl er begrenzt, ist nicht gemessen. Maßstab des Nutzers: die Spezifikation ist Zielbild, der echte Server die Referenz; die Abweichung ist Schuld mit Auflösungs-Trigger, nicht Absicht.
