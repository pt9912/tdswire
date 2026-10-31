# Neuverhandlung in TLS 1.2 durch die Gegenstelle ist nicht begrenzt

**Sub-Area:** `*` (Transport, TLS-Schleife)

Die TLS-Schleife beantwortet eine Neuverhandlung der Gegenstelle in TLS 1.2 (JDK-Voreinstellung, mit einer JDK-Gegenstelle dreimal hintereinander getestet); eine Begrenzung ihrer Zahl gibt es nicht. Jede Neuverhandlung kostet den Server einen Handschlag. Die Rechenlast vieler Neuverhandlungen ist nicht gemessen, ebenso wenig das Verhalten eines Referenzclients.
