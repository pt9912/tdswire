using System.Collections.Concurrent;
using System.Diagnostics;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Lasttest;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Heap;

/// <summary>
/// Die Heap-Messung am Referenzclient (Microsoft.Data.SqlClient 7.1.1) über die Prozessgrenze (TDS-QA-04, ADR-0006;
/// `tools/heap-abschluss.sh`, kein Gate): HS_N Sitzungen lesen gleichzeitig je HS_ZEILEN Zeilen von `ref_zeilen` bis zum Ende
/// (Muster 1 des Referenz-Workloads). Der Server ist der Prozess `HeapAbschlussServerKt`; er nimmt alle 500 ms eine Stichprobe
/// des Live-Heaps nach einer Speicherbereinigung und liefert das Fenster seit dem letzten Lesen über `SELECT * FROM heap_stand`.
/// Der Test liest das Fenster vor dem Strom (Leerlauf, beginnt das Fenster) und danach und schreibt je Wiederholung eine Zeile
/// `heap: lauf ...`. Parameter: HS_N, HS_ZEILEN, HS_SEED, HS_WDH; Ziel: TDSWIRE_ZIEL. Belegt: jede Sitzung erreicht alle
/// Zeilen (letzte Zeile gleich dem Erzeuger dieser Seite). Nicht belegt: eine Spitze des Heaps zwischen zwei Stichproben.
/// </summary>
[Trait("Kategorie", "Heap")]
public class HeapTests
{
    private readonly ITestOutputHelper _ausgabe;

    public HeapTests(ITestOutputHelper ausgabe)
    {
        _ausgabe = ausgabe;
        ThreadPool.SetMinThreads(1024, 1024);
    }

    private static string Umg(string name, string vorgabe) => Environment.GetEnvironmentVariable(name) ?? vorgabe;

    private static long Zahl(string name, long vorgabe) => long.Parse(Umg(name, vorgabe.ToString()));

    private static SqlConnection Verbinden()
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        var verbindung = new SqlConnection($"Server=tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]};User ID=alice;Password=secret;"
            + "Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=60;Command Timeout=3600");
        verbindung.Open();
        return verbindung;
    }

    /// <summary>Das Fenster der Stichproben seit dem letzten Lesen (Spalten der Tabelle `heap_stand`).</summary>
    private sealed record Fenster(long Offen, long Leer, long MinKb, long MedianKb, long P95Kb, long MaxKb, long LeerMaxKb, long GcMs);

    private static Fenster LiesFenster(SqlConnection verbindung)
    {
        using var befehl = new SqlCommand("SELECT * FROM heap_stand", verbindung);
        using var leser = befehl.ExecuteReader();
        Assert.True(leser.Read());
        return new Fenster(leser.GetInt64(0), leser.GetInt64(1), leser.GetInt64(2), leser.GetInt64(3), leser.GetInt64(4), leser.GetInt64(5), leser.GetInt64(6), leser.GetInt64(7));
    }

    /// <summary>Eine Sitzung, die alle Zeilen bis zum Ende liest; prüft Anzahl und letzte Zeile.</summary>
    private static void LiesAlles(SqlConnection verbindung, long zeilen, long seed)
    {
        long gelesen = 0;
        object?[]? letzte = null;
        using var befehl = new SqlCommand("SELECT * FROM ref_zeilen", verbindung) { CommandTimeout = 3600 };
        using var leser = befehl.ExecuteReader();
        var werte = new object[leser.FieldCount];
        while (leser.Read())
        {
            gelesen++;
            if (gelesen == zeilen)
            {
                leser.GetValues(werte);
                letzte = werte.Select(w => w is DBNull ? null : w).ToArray();
            }
        }

        Assert.Equal(zeilen, gelesen);
        Assert.Equal(ReferenzZeilen.Zeile(seed, zeilen), letzte);
    }

    /// <summary>
    /// HS_N Sitzungen lesen gleichzeitig HS_ZEILEN Zeilen, HS_WDH mal hintereinander im selben Serverprozess. Je Wiederholung
    /// steht das Fenster der Stichproben des Servers in der Zeile; der Leerlauf davor (`leerlauf_max_kb`) ist der größte
    /// Heap der Stichproben ohne offenen Strom seit dem vorigen Lesen.
    /// </summary>
    [Fact]
    public void Strom()
    {
        var n = (int)Zahl("HS_N", 1);
        var zeilen = Zahl("HS_ZEILEN", 10_000_000);
        var seed = Zahl("HS_SEED", 42);
        var wiederholungen = (int)Zahl("HS_WDH", 1);
        using var probe = Verbinden();
        var sitzungen = new SqlConnection[n];
        Parallel.For(0, n, new ParallelOptions { MaxDegreeOfParallelism = 16 }, i => sitzungen[i] = Verbinden());
        try
        {
            for (var w = 1; w <= wiederholungen; w++)
            {
                var leerlauf = LiesFenster(probe);
                var fehler = new ConcurrentBag<Exception>();
                var uhr = Stopwatch.StartNew();
                var threads = Enumerable.Range(0, n).Select(i => new Thread(
                    () =>
                    {
                        try
                        {
                            LiesAlles(sitzungen[i], zeilen, seed);
                        }
                        catch (Exception e)
                        {
                            fehler.Add(e);
                        }
                    },
                    512 * 1024)).ToList();
                threads.ForEach(t => t.Start());
                threads.ForEach(t => t.Join());
                var ms = Math.Max(1, (long)uhr.Elapsed.TotalMilliseconds);
                var fenster = LiesFenster(probe);
                _ausgabe.WriteLine($"heap: lauf n {n} zeilen {zeilen} wdh {w} von {wiederholungen} dauer_ms {ms} zeilen_je_s_gesamt {n * zeilen * 1000 / ms}"
                    + $" proben_offen {fenster.Offen} proben_leer {fenster.Leer} min_kb {fenster.MinKb} median_kb {fenster.MedianKb} p95_kb {fenster.P95Kb} max_kb {fenster.MaxKb}"
                    + $" leerlauf_max_kb {leerlauf.LeerMaxKb} gc_ms {fenster.GcMs}");
                if (!fehler.IsEmpty)
                {
                    throw new AggregateException(fehler);
                }
            }
        }
        finally
        {
            foreach (var s in sitzungen)
            {
                s.Dispose();
            }
        }
    }
}
