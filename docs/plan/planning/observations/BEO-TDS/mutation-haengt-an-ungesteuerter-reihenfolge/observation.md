# Eine Mutation hängt an einer Reihenfolge, die der Test nicht steuert, und überlebt als Prüfstein

**Sub-Area:** `*` (Tests, Verifikation)

Ein Prüfstein für einen Wächter wählt ein Ziel nach einer Nummer (hier: die Verbindung 120 schließt ihren Kanal nicht), die von der Annahmereihenfolge nebenläufiger Threads abhängt, die der Test nicht steuert. Je nachdem, welcher Fall die Nummer trägt, schließt der Treiber den Kanal ohnehin, und die Mutation überlebt. Als Beleg für den Wächter taugt nur eine reihenfolgeunabhängige Form (hier: die 100. Instanz des Adapters schließt nie). Wer die erste Form in einer Notiz als belegt führt, muss die Form nennen. Verwandt, aber eigener Mechanismus: `BEO-TDS/zeile-oder-zweig-ohne-unterscheidenden-test` (dort fehlt der Test zur Zeile, hier fehlt die Reproduzierbarkeit der Mutation).
