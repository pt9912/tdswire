# Zwei Definitionen desselben Server-Artefakts stehen im Dockerfile nebeneinander

**Sub-Area:** `*` (Docker-Stages)

Neben der Stage `runtime` (Jar, Bibliotheken, JRE) besteht die Stage `build` (Distroless static, nur `tdswire.jar`, ohne `lib/`); das Jar läuft dort nicht allein. Die Vereinheitlichung auf ein Artefakt ist nicht erfolgt, und kein Gate hält die beiden gegeneinander; Folge-Slices, die ein Image brauchen (Referenzabgleich), müssen wählen.
