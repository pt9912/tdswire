# Die Folge einer Festlegung in einem Nachbar-Slice steht in keinem Plan

**Sub-Area:** `*` (Anmeldepfad und Transport)

Ein Slice trifft eine Festlegung (hier: wie der Anmeldepfad mit einer Abbruchausnahme des Adapters umgeht), und ihre Wirkung liegt im Code eines anderen, parallel laufenden Slice (der Listener ordnet jede `CancellationException` des Handlers als geordnetes Beenden ein). Weder der Plan des einen noch der des anderen nennt die Kopplung; sie fällt erst der Verifikation auf, die beide Dateien liest. Die Meldung an den Nachbar-Slice ist dann Handarbeit der Closure.
