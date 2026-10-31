# Die Dateien, die ein Test aus dem Arbeitsverzeichnis liest, stehen je Docker-Stage von Hand in einer Kopierliste

**Sub-Area:** `*` (Build und Gates)

Die Stages `test`, `spec-abdeckung` und `lock` des `Dockerfile` kopieren je ihre eigene Liste von Eingangsdateien. Ein neuer Test, der eine weitere Datei liest, bricht `make deps-lock` (Stage `lock`), ohne dass ein Gate es zeigt; so brach `LizenzTest` die Lock-Stage, bis `LICENSE` in der Liste stand. Es gibt keinen Sensor, der die drei Listen gegeneinander oder gegen die Dateien der Tests hält.
