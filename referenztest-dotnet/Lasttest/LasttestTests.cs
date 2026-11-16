using System.Diagnostics;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Lasttest;

/// <summary>
/// Der Lasttest des Referenz-Workloads am Referenzclient (Microsoft.Data.SqlClient 7.1.1) über die Prozessgrenze
/// (TDS-QA-04; `make lasttest`, Stufe 9 von `make e2e`, kein Gate): Export (`SELECT * FROM ref_zeilen`, ein Strom bis zum
/// Ende gelesen) und Import (`SqlBulkCopy`, BatchSize 1000) gegen den Serverprozess der Dockerfile-Stage `lasttestserver`
/// (Aufbau B: eigenes Backend, Aufbau C: Simulations-Backend). Aufbau, Zeilenzahl, Seed und Wiederholungen stehen in
/// LASTTEST_AUFBAU, LASTTEST_ZEILEN, LASTTEST_SEED, LASTTEST_WDH; das Ziel in TDSWIRE_ZIEL. Je Lauf eine Zeile
/// `lasttest: export|import lauf n zeilen z ms t ...`; Lauf 0 ist der Warmlauf. Die Bytes sind die Zähler der
/// Netzwerkschnittstelle des Containers (mit IP- und TCP-Kopf), nicht die Länge der TDS-Pakete.
/// Belegt: der Lauf liest alle Zeilen, die erste und die letzte gleich dem Erzeuger dieser Seite; der Import erreicht alle
/// Zeilen (das Backend zählt, der Server schreibt `lasttest: import <Summe>`). Aufbau B nimmt kein Massenladen an: der
/// Import endet dort mit einem Fehler, den der Test festhält.
/// Nicht belegt: ein Grenzwert (der Test misst, er prüft keine Schwelle), Streuung über mehrere Tage, mehrere Sitzungen,
/// p95 je Anfrage, TLS und TDS 8.0, ein anderer Client.
/// </summary>
[Trait("Kategorie", "Lasttest")]
public class LasttestTests
{
    private const int Batch = 1000;
    private readonly ITestOutputHelper _ausgabe;

    public LasttestTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static string Umgebung(string name, string vorgabe) => Environment.GetEnvironmentVariable(name) ?? vorgabe;

    private static SqlConnection Verbinden()
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        var verbindung = new SqlConnection($"Server=tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]};User ID=alice;Password=secret;"
            + "Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=15;Command Timeout=600");
        verbindung.Open();
        return verbindung;
    }

    private static long Zaehler(string datei)
    {
        foreach (var schnittstelle in Directory.GetDirectories("/sys/class/net").Where(p => Path.GetFileName(p) != "lo"))
        {
            var pfad = Path.Combine(schnittstelle, "statistics", datei);
            if (File.Exists(pfad))
            {
                return long.Parse(File.ReadAllText(pfad).Trim());
            }
        }

        return -1;
    }

    [Fact]
    public void Export()
    {
        var seed = long.Parse(Umgebung("LASTTEST_SEED", "42"));
        var zeilen = long.Parse(Umgebung("LASTTEST_ZEILEN", "100000"));
        var wiederholungen = int.Parse(Umgebung("LASTTEST_WDH", "5"));
        using var verbindung = Verbinden();
        for (var lauf = 0; lauf <= wiederholungen; lauf++)
        {
            var rx0 = Zaehler("rx_bytes");
            var uhr = Stopwatch.StartNew();
            long gelesen = 0;
            double erste = -1;
            object?[]? ersteWerte = null;
            object?[]? letzteWerte = null;
            using (var befehl = new SqlCommand("SELECT * FROM ref_zeilen", verbindung))
            using (var leser = befehl.ExecuteReader())
            {
                var werte = new object[leser.FieldCount];
                while (leser.Read())
                {
                    if (gelesen == 0)
                    {
                        erste = uhr.Elapsed.TotalMilliseconds;
                    }

                    gelesen++;
                    if (gelesen == 1 || gelesen == zeilen)
                    {
                        leser.GetValues(werte);
                        var kopie = werte.Select(w => w is DBNull ? null : w).ToArray();
                        if (gelesen == 1)
                        {
                            ersteWerte = kopie;
                        }

                        if (gelesen == zeilen)
                        {
                            letzteWerte = kopie;
                        }
                    }
                }
            }

            uhr.Stop();
            var rx = Zaehler("rx_bytes") - rx0;
            _ausgabe.WriteLine($"lasttest: export lauf {lauf} zeilen {gelesen} ms {(long)uhr.Elapsed.TotalMilliseconds} erste_zeile_ms {(long)erste} rx_bytes {rx}");
            Assert.Equal(zeilen, gelesen);
            Assert.Equal(ReferenzZeilen.Zeile(seed, 1), ersteWerte);
            Assert.Equal(ReferenzZeilen.Zeile(seed, zeilen), letzteWerte);
        }
    }

    [Fact]
    public void Import()
    {
        var seed = long.Parse(Umgebung("LASTTEST_SEED", "42"));
        var zeilen = long.Parse(Umgebung("LASTTEST_ZEILEN", "100000"));
        var wiederholungen = int.Parse(Umgebung("LASTTEST_WDH", "5"));
        var aufbau = Umgebung("LASTTEST_AUFBAU", "C");
        using var verbindung = Verbinden();
        for (var lauf = 0; lauf <= wiederholungen; lauf++)
        {
            var tx0 = Zaehler("tx_bytes");
            var uhr = Stopwatch.StartNew();
            using var kopierer = new SqlBulkCopy(verbindung) { DestinationTableName = "ref_zeilen", BatchSize = Batch, BulkCopyTimeout = 600 };
            for (var i = 0; i < ReferenzZeilen.Spalten; i++)
            {
                kopierer.ColumnMappings.Add(i, i);
            }

            using var quelle = new ZeilenLeser(seed, zeilen);
            if (aufbau == "B")
            {
                var fehler = Assert.ThrowsAny<Exception>(() => kopierer.WriteToServer(quelle));
                _ausgabe.WriteLine($"lasttest: import aufbau B abgewiesen: {fehler.GetType().Name} Number {(fehler as SqlException)?.Number} Meldung '{fehler.Message.Replace("\n", " ")}'");
                return;
            }

            kopierer.WriteToServer(quelle);
            uhr.Stop();
            var tx = Zaehler("tx_bytes") - tx0;
            _ausgabe.WriteLine($"lasttest: import lauf {lauf} zeilen {zeilen} ms {(long)uhr.Elapsed.TotalMilliseconds} tx_bytes {tx}");
        }
    }
}
