using System.Diagnostics;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Lasttest;

/// <summary>
/// Der Lasttest des Referenz-Workloads am Referenzclient (Microsoft.Data.SqlClient 7.1.1) über die Prozessgrenze
/// (TDS-QA-04; `make lasttest`, Stufe 9 von `make e2e`, kein Gate) gegen den Serverprozess der Dockerfile-Stage
/// `lasttestserver` (Aufbau B: eigenes Backend, Aufbau C: Simulations-Backend). Aufbau, Zeilenzahl, Seed, Wiederholungen,
/// Sitzungen und Phase stehen in LASTTEST_AUFBAU, LASTTEST_ZEILEN, LASTTEST_SEED, LASTTEST_WDH, LASTTEST_SITZUNGEN
/// (Vorgabe 1) und LASTTEST_PHASE; das Ziel in TDSWIRE_ZIEL. Je Lauf eine Zeile; Lauf 0 ist der Warmlauf. Die Bytes sind
/// die Zähler der Netzwerkschnittstelle des Containers (mit IP- und TCP-Kopf), nicht die Länge der TDS-Pakete.
/// Phase `export` (Vorgabe: Test `Export`, `SELECT * FROM ref_zeilen`, ein Strom je Sitzung bis zum Ende gelesen) und
/// `import` (Test `Import`, `SqlBulkCopy`, BatchSize 1000), jede der N Sitzungen auf eigener Verbindung und eigenem Thread, alle gemeinsam
/// gestartet; Zeile `lasttest: export|import lauf n zeilen z ms t ... sitzungen N`, `ms` von der Freigabe bis zur letzten
/// Sitzung, `zeilen` je Sitzung. Phase `punkt-export` und `punkt-import` (Test `Punkt`): eine Sitzung fragt `SELECT * FROM
/// ref_zeilen WHERE Id = @id` (RPC mit Parameter), die übrigen N - 1 streamen den Export oder laden den Import ohne Pause,
/// bis die Messung endet; Zeile `lasttest: punkt lauf n anfragen a p50_us x p95_us y p99_us z ...` aus der Zeit je Anfrage
/// am Client (Mikrosekunden).
/// Belegt: der Lesen jeder Sitzung liefert alle Zeilen, die erste und die letzte gleich dem Erzeuger dieser Seite; jede
/// Punktabfrage liefert genau die Zeile der Id aus dem Erzeuger; der Import erreicht alle Zeilen (das Backend zählt, der
/// Server schreibt `lasttest: import <Summe>`, in Aufbau B und C).
/// Nicht belegt: ein Grenzwert (der Test misst, er prüft keine Schwelle), Streuung über mehrere Tage, Rückstau, TLS und
/// TDS 8.0, ein anderer Client.
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

    private static bool Phase(string name) => Umgebung("LASTTEST_PHASE", "export") == name;

    private static SqlConnection[] VerbindenAlle(int anzahl) => Enumerable.Range(0, anzahl).Select(_ => Verbinden()).ToArray();

    /// <summary>
    /// Lässt `anzahl` Sitzungen je auf einem eigenen Thread (kein ThreadPool: er legte nur wenige Threads gleichzeitig an)
    /// den Rumpf `arbeit(sitzung)` ausführen; `Lauf` gibt sie gemeinsam frei und misst bis zur letzten. Ein Fehler einer
    /// Sitzung hält die Sperre nicht an, er wird nach dem Lauf geworfen.
    /// </summary>
    private sealed class Gruppe : IDisposable
    {
        private readonly Barrier _start;
        private readonly Barrier _ende;
        private readonly List<Thread> _threads = new();
        private readonly List<Exception> _fehler = new();
        private bool _stopp;
        private Action<int>? _arbeit;

        public Gruppe(int anzahl)
        {
            _start = new Barrier(anzahl + 1);
            _ende = new Barrier(anzahl + 1);
            for (var i = 0; i < anzahl; i++)
            {
                var sitzung = i;
                var thread = new Thread(() => Schleife(sitzung)) { IsBackground = true };
                _threads.Add(thread);
                thread.Start();
            }
        }

        private void Schleife(int sitzung)
        {
            while (true)
            {
                _start.SignalAndWait();
                if (_stopp)
                {
                    return;
                }

                try
                {
                    _arbeit!(sitzung);
                }
                catch (Exception e)
                {
                    lock (_fehler)
                    {
                        _fehler.Add(e);
                    }
                }

                _ende.SignalAndWait();
            }
        }

        public double Lauf(Action<int> arbeit)
        {
            _arbeit = arbeit;
            _start.SignalAndWait();
            var uhr = Stopwatch.StartNew();
            _ende.SignalAndWait();
            uhr.Stop();
            lock (_fehler)
            {
                if (_fehler.Count > 0)
                {
                    throw new AggregateException(_fehler.ToArray());
                }
            }

            return uhr.Elapsed.TotalMilliseconds;
        }

        public void Dispose()
        {
            _stopp = true;
            _start.SignalAndWait();
            foreach (var thread in _threads)
            {
                thread.Join();
            }
        }
    }

    private static long Median(IEnumerable<double> werte)
    {
        var sortiert = werte.OrderBy(w => w).ToArray();
        return sortiert.Length == 0 ? -1 : (long)sortiert[sortiert.Length / 2];
    }

    [Fact]
    public void Export()
    {
        if (!Phase("export"))
        {
            return;
        }

        var seed = long.Parse(Umgebung("LASTTEST_SEED", "42"));
        var zeilen = long.Parse(Umgebung("LASTTEST_ZEILEN", "100000"));
        var wiederholungen = int.Parse(Umgebung("LASTTEST_WDH", "5"));
        var sitzungen = int.Parse(Umgebung("LASTTEST_SITZUNGEN", "1"));
        var verbindungen = VerbindenAlle(sitzungen);
        var erste = new double[sitzungen];
        var gelesen = new long[sitzungen];
        var ersteWerte = new object?[sitzungen][];
        var letzteWerte = new object?[sitzungen][];
        using var gruppe = new Gruppe(sitzungen);
        try
        {
            for (var lauf = 0; lauf <= wiederholungen; lauf++)
            {
                var rx0 = Zaehler("rx_bytes");
                var ms = gruppe.Lauf(sitzung =>
                {
                    var uhr = Stopwatch.StartNew();
                    long n = 0;
                    erste[sitzung] = -1;
                    using var befehl = new SqlCommand("SELECT * FROM ref_zeilen", verbindungen[sitzung]);
                    using var leser = befehl.ExecuteReader();
                    var werte = new object[leser.FieldCount];
                    while (leser.Read())
                    {
                        if (n == 0)
                        {
                            erste[sitzung] = uhr.Elapsed.TotalMilliseconds;
                        }

                        n++;
                        if (n == 1 || n == zeilen)
                        {
                            leser.GetValues(werte);
                            var kopie = werte.Select(w => w is DBNull ? null : w).ToArray();
                            if (n == 1)
                            {
                                ersteWerte[sitzung] = kopie;
                            }

                            if (n == zeilen)
                            {
                                letzteWerte[sitzung] = kopie;
                            }
                        }
                    }

                    gelesen[sitzung] = n;
                });
                var rx = Zaehler("rx_bytes") - rx0;
                _ausgabe.WriteLine($"lasttest: export lauf {lauf} zeilen {zeilen} ms {(long)ms} erste_zeile_ms {Median(erste)} rx_bytes {rx} sitzungen {sitzungen}");
                for (var sitzung = 0; sitzung < sitzungen; sitzung++)
                {
                    Assert.Equal(zeilen, gelesen[sitzung]);
                    Assert.Equal(ReferenzZeilen.Zeile(seed, 1), ersteWerte[sitzung]);
                    Assert.Equal(ReferenzZeilen.Zeile(seed, zeilen), letzteWerte[sitzung]);
                }
            }
        }
        finally
        {
            foreach (var verbindung in verbindungen)
            {
                verbindung.Dispose();
            }
        }
    }

    [Fact]
    public void Import()
    {
        if (!Phase("import"))
        {
            return;
        }

        var seed = long.Parse(Umgebung("LASTTEST_SEED", "42"));
        var zeilen = long.Parse(Umgebung("LASTTEST_ZEILEN", "100000"));
        var wiederholungen = int.Parse(Umgebung("LASTTEST_WDH", "5"));
        var sitzungen = int.Parse(Umgebung("LASTTEST_SITZUNGEN", "1"));
        var verbindungen = VerbindenAlle(sitzungen);
        using var gruppe = new Gruppe(sitzungen);
        try
        {
            for (var lauf = 0; lauf <= wiederholungen; lauf++)
            {
                var tx0 = Zaehler("tx_bytes");
                var ms = gruppe.Lauf(sitzung =>
                {
                    using var kopierer = Kopierer(verbindungen[sitzung]);
                    using var quelle = new ZeilenLeser(seed, zeilen);
                    kopierer.WriteToServer(quelle);
                });
                var tx = Zaehler("tx_bytes") - tx0;
                _ausgabe.WriteLine($"lasttest: import lauf {lauf} zeilen {zeilen} ms {(long)ms} tx_bytes {tx} sitzungen {sitzungen}");
            }
        }
        finally
        {
            foreach (var verbindung in verbindungen)
            {
                verbindung.Dispose();
            }
        }
    }

    private static SqlBulkCopy Kopierer(SqlConnection verbindung)
    {
        var kopierer = new SqlBulkCopy(verbindung) { DestinationTableName = "ref_zeilen", BatchSize = Batch, BulkCopyTimeout = 600 };
        for (var i = 0; i < ReferenzZeilen.Spalten; i++)
        {
            kopierer.ColumnMappings.Add(i, i);
        }

        return kopierer;
    }

    private static long Rang(long[] sortiert, double anteil) => sortiert[Math.Max(0, (int)Math.Ceiling(anteil * sortiert.Length) - 1)];

    /// <summary>
    /// Die Punktabfrage unter Last (Muster 3 des Workloads): die Sitzung 0 fragt je Anfrage eine andere `Id` (fester Seed),
    /// die Sitzungen 1 bis N - 1 streamen den Export oder laden den Import wiederholt, bis die Messung endet. p50, p95 und
    /// p99 sind Ränge (aufgerundet, Nearest-Rank) der Zeiten je Anfrage eines Laufs von `LASTTEST_ANFRAGEN` Anfragen (Vorgabe
    /// 1000), gemessen von `ExecuteReader` bis zum Ende des Lesens. Die Last läuft über den Warmlauf und alle Läufe.
    /// </summary>
    [Fact]
    public void Punkt()
    {
        var phase = Umgebung("LASTTEST_PHASE", "export");
        if (phase != "punkt-export" && phase != "punkt-import")
        {
            return;
        }

        var seed = long.Parse(Umgebung("LASTTEST_SEED", "42"));
        var zeilen = long.Parse(Umgebung("LASTTEST_ZEILEN", "100000"));
        var wiederholungen = int.Parse(Umgebung("LASTTEST_WDH", "5"));
        var sitzungen = int.Parse(Umgebung("LASTTEST_SITZUNGEN", "1"));
        var anfragen = int.Parse(Umgebung("LASTTEST_ANFRAGEN", "1000"));
        var import = phase == "punkt-import";
        var verbindungen = VerbindenAlle(sitzungen);
        var halt = false;
        var angelaufen = new CountdownEvent(sitzungen - 1);
        var lastZeilen = 0L;
        var lastFehler = new List<Exception>();
        var lastThreads = new List<Thread>();
        for (var i = 1; i < sitzungen; i++)
        {
            var verbindung = verbindungen[i];
            var thread = new Thread(() =>
            {
                var gemeldet = false;
                try
                {
                    while (!Volatile.Read(ref halt))
                    {
                        if (import)
                        {
                            using var kopierer = Kopierer(verbindung);
                            using var quelle = new ZeilenLeser(seed, zeilen, () =>
                            {
                                if (!gemeldet)
                                {
                                    gemeldet = true;
                                    angelaufen.Signal();
                                }

                                return Volatile.Read(ref halt);
                            });
                            kopierer.WriteToServer(quelle);
                            Interlocked.Add(ref lastZeilen, quelle.Gelesen);
                        }
                        else
                        {
                            using var befehl = new SqlCommand("SELECT * FROM ref_zeilen", verbindung);
                            using var leser = befehl.ExecuteReader();
                            long n = 0;
                            while (!Volatile.Read(ref halt) && leser.Read())
                            {
                                if (!gemeldet)
                                {
                                    gemeldet = true;
                                    angelaufen.Signal();
                                }

                                n++;
                            }

                            Interlocked.Add(ref lastZeilen, n);
                            if (Volatile.Read(ref halt))
                            {
                                befehl.Cancel();
                            }
                        }
                    }
                }
                catch (Exception e)
                {
                    if (!Volatile.Read(ref halt))
                    {
                        lock (lastFehler)
                        {
                            lastFehler.Add(e);
                        }

                        if (!gemeldet)
                        {
                            angelaufen.Signal();
                        }
                    }
                }
            })
            { IsBackground = true };
            lastThreads.Add(thread);
            thread.Start();
        }

        try
        {
            Assert.True(angelaufen.Wait(TimeSpan.FromSeconds(120)), "die Last-Sitzungen laufen nicht an");
            var uhrGesamt = Stopwatch.StartNew();
            using var befehl = new SqlCommand("SELECT * FROM ref_zeilen WHERE Id = @id", verbindungen[0]);
            var parameter = befehl.Parameters.Add("@id", System.Data.SqlDbType.BigInt);
            for (var lauf = 0; lauf <= wiederholungen; lauf++)
            {
                var zeiten = new long[anfragen];
                var ids = new long[anfragen];
                var treffer = new object?[anfragen][];
                var zeilen0 = Interlocked.Read(ref lastZeilen);
                var uhrLauf = Stopwatch.StartNew();
                for (var i = 0; i < anfragen; i++)
                {
                    ids[i] = 1 + (unchecked((seed + lauf * 7919L + i) * 2654435761L) & long.MaxValue) % zeilen;
                    parameter.Value = ids[i];
                    var t0 = Stopwatch.GetTimestamp();
                    using (var leser = befehl.ExecuteReader())
                    {
                        var werte = new object[leser.FieldCount];
                        var n = 0;
                        while (leser.Read())
                        {
                            leser.GetValues(werte);
                            treffer[i] = werte.Select(w => w is DBNull ? null : w).ToArray();
                            n++;
                        }

                        zeiten[i] = (Stopwatch.GetTimestamp() - t0) * 1_000_000L / Stopwatch.Frequency;
                        Assert.Equal(1, n);
                    }
                }

                uhrLauf.Stop();
                var sortiert = zeiten.OrderBy(z => z).ToArray();
                _ausgabe.WriteLine($"lasttest: punkt lauf {lauf} anfragen {anfragen} p50_us {Rang(sortiert, 0.50)} p95_us {Rang(sortiert, 0.95)} p99_us {Rang(sortiert, 0.99)} "
                    + $"min_us {sortiert[0]} max_us {sortiert[^1]} lauf_ms {(long)uhrLauf.Elapsed.TotalMilliseconds} last_zeilen {Interlocked.Read(ref lastZeilen) - zeilen0} sitzungen {sitzungen} last {(import ? "import" : "export")}");
                for (var i = 0; i < anfragen; i++)
                {
                    Assert.Equal(ReferenzZeilen.Zeile(seed, ids[i]), treffer[i]);
                }

                lock (lastFehler)
                {
                    Assert.Empty(lastFehler);
                }
            }

            uhrGesamt.Stop();
            _ausgabe.WriteLine($"lasttest: punkt gesamt ms {(long)uhrGesamt.Elapsed.TotalMilliseconds} last_zeilen {Interlocked.Read(ref lastZeilen)} sitzungen {sitzungen} last {(import ? "import" : "export")}");
        }
        finally
        {
            Volatile.Write(ref halt, true);
            foreach (var thread in lastThreads)
            {
                thread.Join(TimeSpan.FromSeconds(60));
            }

            foreach (var verbindung in verbindungen)
            {
                verbindung.Dispose();
            }
        }
    }
}
