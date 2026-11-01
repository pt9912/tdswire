**Vorgang:** slice-betrieb-log-schnittstelle
**Fund:** Verifikation: `make gates` auf `2e6b7ba` zeigte alle Docker-Stufen als `CACHED`; der Lauf belegt „derselbe Eingang wie ein früherer grüner Lauf“, nicht einen frischen Testlauf. Den frischen Beleg (833 Tests grün) lieferten die Wegwerf-Kopien der Mutationen M9 und M10, die die Stage `test` ohne Cache bauten.
