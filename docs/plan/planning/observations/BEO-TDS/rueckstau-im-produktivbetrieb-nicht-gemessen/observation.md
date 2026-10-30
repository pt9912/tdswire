# Rückstau im Produktivbetrieb und unter Last ist nicht gemessen

**Sub-Area:** `*` (Transport, Build und Gates)

Der Rückstau des Transports ist am Speicher-Kanal des Tests (Kapazität 1024, Nachricht 100000 Bytes) deterministisch belegt und am Socket nur als Suspendieren auf einer Maschine (Linux, Loopback, 32 MiB, 500 ms). Der Spike maß 100 MiB; im Produktivcode unter Last mit vielen Verbindungen ist er ungemessen, und jede Aussage darüber trüge keinen Beleg.
