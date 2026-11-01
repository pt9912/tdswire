# Dispose eines verworfenen Lesers verschluckt den Fehler der Quelle im Referenzclient

**Sub-Area:** `*` (Tests, Kompatibilität)

Wird ein Leser nach einer Zeile verworfen, hinter der der Strom einen Fehler trägt, meldet Microsoft.Data.SqlClient 7.1.1 den Quellfehler beim `Dispose` nicht. Der Test legt dieses Clientverhalten fest und benennt es im KDoc; ein Versionssprung des Clients kann ihn rot machen, ohne dass der Server sich geändert hat. Ein roter Test dieser Art ist ein Befund über den Client, kein Fehler des Servers.
