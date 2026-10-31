# Ein `@spec`-Tag nennt eine Lastenheft-Kennung statt einer SPEC-Kennung

**Sub-Area:** `*` (Tests, Abdeckungsmatrix)

Ein Test trägt `@spec TDS-QA-07`. Die Abdeckungsmatrix kennt nur `SPEC-<NNN>` und führt das Tag unter „Tags auf unbekannte Festlegungen“; der Test zählt für keine Festlegung. Weder Review noch Verifikation fanden es, es steht nur in der erzeugten Matrix. `SPEC_ABDECKUNG_STRENG=1` bricht nur bei Festlegungen ohne Nachweis (`tools/spec-abdeckung.sh`), nicht bei unbekannten Tags; ein Sensor dafür ist nicht gebaut.
