# git notes append aus zwei Arbeitsbäumen verliert still Zeilen

**Sub-Area:** `*` (Werkzeug, Stempel)

[ADR-0024](../../../../adr/0024-lokale-stempel-zwei-klassen.md) Kontext 3 und Negativ 7 erwarten bei gleichzeitigem Schreiben der Note eine Sperre, bei der der Stempel ungesetzt bleibt. Gemessen (Git 2.43.0, ein Host, Einzelmessung): 60 `git notes append` aus zwei Arbeitsbäumen auf dieselbe Note gaben 60-mal Exit 0, in der Note blieben 39 Zeilen. Ein Stempel kann also still fehlen, obwohl der Aufruf Erfolg meldet; `tools/stempel.sh` serialisiert seine Aufrufe deshalb mit einer eigenen Sperre. Die ADR bleibt unverändert; ob sie eine Folge-Entscheidung braucht, ist offen.
