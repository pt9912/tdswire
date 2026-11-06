# Das geordnete Beenden ist mit gesteuerter Uhr belegt, nicht unter Last mit realen Fristen

**Sub-Area:** `*` (Listener, Bootstrap)

Die Tests zu `SPEC-044` (Frist, Abbruch, `CANCEL_TIMEOUT`, Obergrenze) laufen mit gesteuerter Uhr; die Wanduhr-Anteile (`ShutdownWiringTest`, `ServerSmokeTest`) laufen mit kleinen Fristen auf einem Host. Ob `stop()` unter Last mit vielen Verbindungen und den Voreinstellungen (`SHUTDOWN_GRACE` 10 s, `CANCEL_TIMEOUT` 5 s, beide vorläufig) die Obergrenze hält, ist nicht gemessen.
