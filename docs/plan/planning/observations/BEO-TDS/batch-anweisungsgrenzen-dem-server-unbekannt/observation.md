# `SPEC-044` Nr. 3: von den übrigen Anweisungen eines Batches läuft nach dem Beginn nichts mehr — der Server kennt keine Anweisungsgrenzen

**Sub-Area:** `*` (Spezifikation, Listener)

Der Server reicht den Batch-Text unverändert an das Backend und kennt keine Anweisungsgrenzen darin; ein Backend, das mehrere Anweisungen ausführt, erfährt vom Beginn nur über das Cancel nach der Frist. Die Zusage steht unverändert in `SPEC-044` Nr. 3, der Satz „Nicht gebaut“ nennt den Grund. Verwandt: `BEO-TDS/fa-10-mehrteilige-antwort-ohne-backend-traeger` (Backend-Seite mehrteiliger Antworten).
