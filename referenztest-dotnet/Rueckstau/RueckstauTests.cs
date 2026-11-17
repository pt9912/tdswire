using System.Collections.Concurrent;
using System.Data.Common;
using System.Diagnostics;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Lasttest;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Rueckstau;

/// <summary>
/// Die Rückstau-Messung am Referenzclient (Microsoft.Data.SqlClient 7.1.1) über die Prozessgrenze (TDS-QA-04, ADR-0006;
/// `tools/rueckstau.sh`, kein Gate): Szenario `Leser` (ein oder mehrere langsame Leser unter schnellen), `Blockierend`
/// (Backend wartet je Zeile), `Abbruch` (Cancel bei wartendem Backend) und `Import` (Massenladen gegen einen langsamen
/// Stapel-Callback oder mit langsamem Sender). Der Server ist der Prozess `RueckstauServerKt`; seine Zähler kommen über
/// `SELECT * FROM rueckstau_stand`. Parameter: RS_*; Ziel: TDSWIRE_ZIEL. Jede Messung ist eine Zeile `rueckstau: ...`.
/// Belegt: jede Sitzung erreicht alle Zeilen (letzte Zeile gleich dem Erzeuger dieser Seite). Nicht belegt: ein Grenzwert,
/// ein Netz zwischen Hosts, andere Clients.
/// </summary>
[Trait("Kategorie", "Rueckstau")]
public class RueckstauTests
{
    private const int Batch = 1000;
    private readonly ITestOutputHelper _ausgabe;

    public RueckstauTests(ITestOutputHelper ausgabe)
    {
        _ausgabe = ausgabe;
        // Der Thread-Pool des Clients soll die Sitzungen nicht aufstauen: die Messung gilt dem Server, nicht dem Anlauf des Pools.
        ThreadPool.SetMinThreads(1024, 1024);
    }

    private static string Umg(string name, string vorgabe) => Environment.GetEnvironmentVariable(name) ?? vorgabe;

    private static long Zahl(string name, long vorgabe) => long.Parse(Umg(name, vorgabe.ToString()));

    private static SqlConnection Verbinden(int timeoutSekunden = 600)
    {
        var ziel = Environment.GetEnvironmentVariable("TDSWIRE_ZIEL")
            ?? throw new InvalidOperationException("TDSWIRE_ZIEL fehlt (host:port)");
        var trenner = ziel.LastIndexOf(':');
        var verbindung = new SqlConnection($"Server=tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]};User ID=alice;Password=secret;"
            + $"Encrypt=false;Pooling=false;ConnectRetryCount=0;Connect Timeout=60;Command Timeout={timeoutSekunden}");
        verbindung.Open();
        return verbindung;
    }

    /// <summary>
    /// Startet <paramref name="anzahl"/> Threads sofort, je einen je Sitzung (kein Thread-Pool: dessen Anlauf würde die
    /// Sitzungen nacheinander starten); <see cref="Warten"/> wartet auf alle und wirft die Fehler gesammelt.
    /// </summary>
    private sealed class Gruppe
    {
        private readonly List<Thread> _threads = new();
        private readonly ConcurrentBag<Exception> _fehler = new();

        public Gruppe(int anzahl, Action<int> körper)
        {
            for (var i = 0; i < anzahl; i++)
            {
                var index = i;
                var thread = new Thread(
                    () =>
                    {
                        try
                        {
                            körper(index);
                        }
                        catch (Exception e)
                        {
                            _fehler.Add(e);
                        }
                    },
                    512 * 1024);
                _threads.Add(thread);
                thread.Start();
            }
        }

        public void Warten()
        {
            foreach (var thread in _threads)
            {
                thread.Join();
            }

            if (!_fehler.IsEmpty)
            {
                throw new AggregateException(_fehler);
            }
        }
    }

    private void Zeile(string text) => _ausgabe.WriteLine("rueckstau: " + text);

    private sealed record Stand(long Erzeugt, long Importiert, int Offen, long Beendet, long Abgebrochen, int Threads, int Spitze, long HeapMb);

    private static Stand LiesStand(SqlConnection verbindung, bool bereinigen = false)
    {
        using var befehl = new SqlCommand(bereinigen ? "SELECT * FROM rueckstau_stand_gc" : "SELECT * FROM rueckstau_stand", verbindung);
        using var leser = befehl.ExecuteReader();
        Assert.True(leser.Read());
        return new Stand(leser.GetInt64(0), leser.GetInt64(1), leser.GetInt32(2), leser.GetInt64(3), leser.GetInt64(4), leser.GetInt32(5), leser.GetInt32(6), leser.GetInt64(7));
    }

    private static string Angaben(long zeilen, string modus, long block, long jede) =>
        $" /*zeilen={zeilen},modus={modus},block={block},jede={jede}*/";

    private static long Perzentil(List<double> werte, double p)
    {
        if (werte.Count == 0)
        {
            return -1;
        }

        var sortiert = werte.OrderBy(w => w).ToList();
        return (long)Math.Round(sortiert[Math.Min(sortiert.Count - 1, (int)Math.Ceiling(p * sortiert.Count) - 1)] * 1000000);
    }

    /// <summary>Eine Sitzung, die alle Zeilen bis zum Ende liest; prüft Anzahl und letzte Zeile, liefert die Dauer in ms.</summary>
    private static double LiesAlles(SqlConnection verbindung, string sql, long zeilen, long seed)
    {
        var uhr = Stopwatch.StartNew();
        long gelesen = 0;
        object?[]? letzte = null;
        using (var befehl = new SqlCommand(sql, verbindung))
        using (var leser = befehl.ExecuteReader())
        {
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
        }

        Assert.Equal(zeilen, gelesen);
        Assert.Equal(ReferenzZeilen.Zeile(seed, zeilen), letzte);
        return uhr.Elapsed.TotalMilliseconds;
    }

    private static SqlConnection[] ÖffneViele(int anzahl)
    {
        var ergebnis = new SqlConnection[anzahl];
        Parallel.For(0, anzahl, new ParallelOptions { MaxDegreeOfParallelism = 16 }, i => ergebnis[i] = Verbinden());
        return ergebnis;
    }

    /// <summary>Misst während <paramref name="arbeit"/> die Antwortzeit einer billigen Anfrage einer eigenen Sitzung (Nachbar).</summary>
    private (List<double> Zeiten, int Fehler) MitNachbar(SqlConnection nachbar, Action arbeit)
    {
        var zeiten = new List<double>();
        var fehler = 0;
        using var stopp = new CancellationTokenSource();
        var probe = Task.Run(() =>
        {
            while (!stopp.IsCancellationRequested)
            {
                var uhr = Stopwatch.StartNew();
                try
                {
                    LiesStand(nachbar);
                    zeiten.Add(uhr.Elapsed.TotalSeconds);
                }
                catch (Exception)
                {
                    fehler++;
                }

                Thread.Sleep(50);
            }
        });
        try
        {
            arbeit();
        }
        finally
        {
            stopp.Cancel();
            probe.Wait();
        }

        return (zeiten, fehler);
    }

    private void NachbarZeile(string name, List<double> zeiten, int fehler) =>
        Zeile($"{name} nachbar n {zeiten.Count} fehler {fehler} p50_us {Perzentil(zeiten, 0.5)} p95_us {Perzentil(zeiten, 0.95)} max_us {Perzentil(zeiten, 1.0)}");

    /// <summary>
    /// RS_N Sitzungen, davon RS_LANGSAM langsame: sie lesen eine Zeile und halten den Strom an, bis alle schnellen Sitzungen
    /// fertig sind und RS_NACH_MS vergangen sind. Zuvor läuft dieselbe Zahl schneller Sitzungen allein (Basis). Der Vorlauf
    /// ist die Zahl der Zeilen, die das Backend mehr übergeben hat, als die Anwendung gelesen hat.
    /// </summary>
    [Fact]
    public void Leser()
    {
        var n = (int)Zahl("RS_N", 1);
        var langsam = (int)Zahl("RS_LANGSAM", 1);
        var zeilen = Zahl("RS_ZEILEN", 100000);
        var seed = Zahl("RS_SEED", 42);
        var nach = Zahl("RS_NACH_MS", 1500);
        var sql = "SELECT * FROM ref_zeilen" + Angaben(zeilen, "suspendierend", 0, 1);
        var schnell = n - langsam;
        var frist = Umg("RS_ERWARTE_FRIST", "0") == "1";
        var fristEnde = new string[langsam];
        using var probe = Verbinden();
        var sitzungen = ÖffneViele(n);
        try
        {
            double basisMs = -1;
            if (schnell > 0)
            {
                var uhr = Stopwatch.StartNew();
                new Gruppe(schnell, i => LiesAlles(sitzungen[i], sql, zeilen, seed)).Warten();
                basisMs = uhr.Elapsed.TotalMilliseconds;
            }

            var s0 = LiesStand(probe);
            var dauern = new ConcurrentBag<double>();
            var bereit = new CountdownEvent(langsam);
            var schnellFertig = new ManualResetEventSlim(false);
            var gesamt = Stopwatch.StartNew();
            var langsameAufgaben = new Gruppe(langsam, k =>
            {
                using var befehl = new SqlCommand(sql, sitzungen[schnell + k]);
                using var leser = befehl.ExecuteReader();
                Assert.True(leser.Read());
                bereit.Signal();
                Assert.True(schnellFertig.Wait(TimeSpan.FromMinutes(10)));
                long gelesen = 1;
                try
                {
                    while (leser.Read())
                    {
                        gelesen++;
                    }
                }
                catch (SqlException e) when (frist)
                {
                    fristEnde[k] = $"{e.Message.Replace("\n", " ")} nach {gelesen} Zeilen";
                    return;
                }

                Assert.Equal(zeilen, gelesen);
            });
            bereit.Wait();
            double schnellMs = -1;
            if (schnell > 0)
            {
                var uhr = Stopwatch.StartNew();
                new Gruppe(schnell, i => dauern.Add(LiesAlles(sitzungen[i], sql, zeilen, seed))).Warten();
                schnellMs = uhr.Elapsed.TotalMilliseconds;
            }

            Thread.Sleep((int)nach);
            var s1 = LiesStand(probe);
            Thread.Sleep((int)nach);
            var s2 = LiesStand(probe);
            var weiter = Stopwatch.StartNew();
            schnellFertig.Set();
            langsameAufgaben.Warten();
            var weiterMs = (long)weiter.Elapsed.TotalMilliseconds;
            gesamt.Stop();
            var s3 = LiesStand(probe, bereinigen: true);
            var vorlaufSumme = s1.Erzeugt - s0.Erzeugt - (long)schnell * zeilen - langsam;
            var vorlaufPlateau = s2.Erzeugt - s1.Erzeugt;
            var sortiert = dauern.OrderBy(d => d).ToList();
            var mediandauer = sortiert.Count == 0 ? -1 : (long)sortiert[sortiert.Count / 2];
            var maxdauer = sortiert.Count == 0 ? -1 : (long)sortiert[^1];
            Zeile($"leser n {n} langsam {langsam} zeilen {zeilen} basis_ms {(long)basisMs} schnell_ms {(long)schnellMs} schnell_median_sitzung_ms {mediandauer} schnell_max_sitzung_ms {maxdauer}"
                + $" basis_zeilen_je_s {(basisMs > 0 ? (long)(schnell * zeilen * 1000 / basisMs) : -1)} schnell_zeilen_je_s {(schnellMs > 0 ? (long)(schnell * zeilen * 1000 / schnellMs) : -1)}"
                + $" vorlauf_summe {vorlaufSumme} vorlauf_je_langsam {vorlaufSumme / langsam} vorlauf_byte_je_langsam_gesch {vorlaufSumme / langsam * 329} plateau_zuwachs {vorlaufPlateau}"
                + $" offen {s1.Offen} threads {s1.Threads} spitze_threads {s1.Spitze} heap_benutzt_mb {s1.HeapMb} heap_nach_gc_mb {s3.HeapMb} offen_ende {s3.Offen} beendet_delta {s3.Beendet - s0.Beendet} abgebrochen_delta {s3.Abgebrochen - s0.Abgebrochen} weiterlesen_ms {weiterMs} frist_ende '{string.Join(";", fristEnde.Select(f => f ?? "-"))}' gesamt_ms {(long)gesamt.Elapsed.TotalMilliseconds}");
            Assert.Equal(0, s3.Offen);
        }
        finally
        {
            foreach (var s in sitzungen)
            {
                s.Dispose();
            }
        }
    }

    /// <summary>
    /// RS_N Sitzungen lesen je RS_ZEILEN Zeilen; das Backend wartet vor jeder RS_JEDE-ten Zeile RS_BLOCK_MS (Art RS_MODUS).
    /// Eine eigene Sitzung fragt währenddessen die Zähler ab (Nachbar); ihre Antwortzeit ohne Last steht daneben.
    /// </summary>
    [Fact]
    public void Blockierend()
    {
        var n = (int)Zahl("RS_N", 8);
        var zeilen = Zahl("RS_ZEILEN", 200);
        var seed = Zahl("RS_SEED", 42);
        var modus = Umg("RS_MODUS", "unterbrechbar");
        var block = Zahl("RS_BLOCK_MS", 10);
        var jede = Zahl("RS_JEDE", 1);
        var sql = "SELECT * FROM ref_zeilen" + Angaben(zeilen, modus, block, jede);
        using var nachbar = Verbinden();
        var sitzungen = ÖffneViele(n);
        try
        {
            var leer = MitNachbar(nachbar, () => Thread.Sleep(2000));
            NachbarZeile($"blockierend modus {modus} ohne_last", leer.Zeiten, leer.Fehler);
            var s0 = LiesStand(nachbar);
            var dauern = new ConcurrentBag<double>();
            var starts = new ConcurrentBag<double>();
            var uhr = Stopwatch.StartNew();
            var mit = MitNachbar(nachbar, () =>
                new Gruppe(n, i =>
                {
                    starts.Add(uhr.Elapsed.TotalMilliseconds);
                    dauern.Add(LiesAlles(sitzungen[i], sql, zeilen, seed));
                }).Warten());
            var gesamtMs = (long)uhr.Elapsed.TotalMilliseconds;
            var s1 = LiesStand(nachbar);
            var sortiert = dauern.OrderBy(d => d).ToList();
            NachbarZeile($"blockierend modus {modus} mit_last", mit.Zeiten, mit.Fehler);
            Zeile($"blockierend modus {modus} n {n} zeilen {zeilen} block_ms {block} jede {jede} gesamt_ms {gesamtMs} sitzung_median_ms {(long)sortiert[sortiert.Count / 2]} sitzung_max_ms {(long)sortiert[^1]} start_max_ms {(long)starts.Max()}"
                + $" erwartet_je_sitzung_ms {zeilen / jede * block} threads_vorher {s0.Threads} spitze_threads {s1.Spitze} threads_nachher {s1.Threads} offen_nachher {s1.Offen} beendet_delta {s1.Beendet - s0.Beendet}");
        }
        finally
        {
            foreach (var s in sitzungen)
            {
                s.Dispose();
            }
        }
    }

    /// <summary>
    /// Eine Sitzung liest, das Backend wartet vor der RS_JEDE-ten Zeile RS_BLOCK_MS; nach RS_ABBRUCH_NACH_MS ruft der Client
    /// Cancel. Gemessen wird die Zeit bis zum Ende des Lesens (Fehler oder Ende). RS_LAST schnelle Sitzungen lesen daneben,
    /// ein Nachbar fragt die Zähler ab.
    /// </summary>
    [Fact]
    public void Abbruch()
    {
        var modus = Umg("RS_MODUS", "unterbrechbar");
        var block = Zahl("RS_BLOCK_MS", 10000);
        var jede = Zahl("RS_JEDE", 3);
        var nachMs = (int)Zahl("RS_ABBRUCH_NACH_MS", 1500);
        var last = (int)Zahl("RS_LAST", 8);
        var lastZeilen = Zahl("RS_ZEILEN", 100000);
        var seed = Zahl("RS_SEED", 42);
        var sql = "SELECT * FROM ref_zeilen" + Angaben(1000, modus, block, jede);
        var lastSql = "SELECT * FROM ref_zeilen" + Angaben(lastZeilen, "suspendierend", 0, 1);
        using var nachbar = Verbinden();
        using var haengend = Verbinden();
        using var steuer = Verbinden();
        var lastSitzungen = ÖffneViele(last);
        try
        {
            var s0 = LiesStand(nachbar);
            long gelesen = 0;
            double abbruchBisEndeMs = -1;
            string ende = "kein-fehler";
            var mit = MitNachbar(nachbar, () =>
            {
                var befehl = new SqlCommand(sql, haengend);
                var leserAufgabe = Task.Run(() =>
                {
                    try
                    {
                        using var leser = befehl.ExecuteReader();
                        while (leser.Read())
                        {
                            gelesen++;
                        }
                    }
                    catch (Exception e)
                    {
                        ende = e.GetType().Name + ": " + e.Message.Replace("\n", " ");
                    }
                });
                var wartet = Stopwatch.StartNew();
                while (LiesStand(steuer).Offen < 1 && wartet.Elapsed < TimeSpan.FromSeconds(30))
                {
                    Thread.Sleep(10);
                }

                var lastAufgabe = new Gruppe(last, i => LiesAlles(lastSitzungen[i], lastSql, lastZeilen, seed));
                Thread.Sleep(nachMs);
                var gelesenVorCancel = Interlocked.Read(ref gelesen);
                var uhr = Stopwatch.StartNew();
                befehl.Cancel();
                Assert.True(leserAufgabe.Wait(TimeSpan.FromMinutes(2)));
                abbruchBisEndeMs = uhr.Elapsed.TotalMilliseconds;
                Zeile($"abbruch gelesen_vor_cancel {gelesenVorCancel}");
                lastAufgabe.Warten();
                befehl.Dispose();
            });
            var s1 = LiesStand(nachbar);
            Thread.Sleep((int)(block + 1500));
            var s2 = LiesStand(nachbar);
            NachbarZeile($"abbruch modus {modus} mit_last", mit.Zeiten, mit.Fehler);
            Zeile($"abbruch modus {modus} block_ms {block} jede {jede} cancel_nach_ms {nachMs} cancel_bis_ende_ms {(long)abbruchBisEndeMs} gelesen {gelesen} ende '{ende}' last_sitzungen {last}"
                + $" direkt_danach: threads {s1.Threads} offen {s1.Offen} abgebrochen_delta {s1.Abgebrochen - s0.Abgebrochen}; nach_block: threads {s2.Threads} offen {s2.Offen} abgebrochen_delta {s2.Abgebrochen - s0.Abgebrochen}");
        }
        finally
        {
            foreach (var s in lastSitzungen)
            {
                s.Dispose();
            }
        }
    }

    private sealed class ZaehlenderLeser : DbDataReaderDecorator
    {
        private long _gezogen;

        public long Gezogen => Interlocked.Read(ref _gezogen);
        private readonly int _pauseMs;

        public ZaehlenderLeser(ZeilenLeser innen, int pauseMs) : base(innen) => _pauseMs = pauseMs;

        public override bool Read()
        {
            var weiter = Innen.Read();
            if (weiter)
            {
                Interlocked.Increment(ref _gezogen);
                if (_pauseMs > 0)
                {
                    Thread.Sleep(_pauseMs);
                }
            }

            return weiter;
        }
    }

    /// <summary>
    /// RS_N Sitzungen laden je RS_ZEILEN Zeilen mit SqlBulkCopy (BatchSize 1000) in das Simulations-Backend, dessen
    /// Stapel-Callback der Server nach RUECKSTAU_CALLBACK_* wartet; RS_SENDER_MS bremst den Sender je Zeile. Alle 100 ms
    /// vergleicht der Client die gezogenen Zeilen mit den verarbeiteten des Servers (Vorlauf des Senders).
    /// </summary>
    [Fact]
    public void Import()
    {
        var n = (int)Zahl("RS_N", 1);
        var zeilen = Zahl("RS_ZEILEN", 100000);
        var seed = Zahl("RS_SEED", 42);
        var sender = (int)Zahl("RS_SENDER_MS", 0);
        using var nachbar = Verbinden();
        var sitzungen = ÖffneViele(n);
        var leser = Enumerable.Range(0, n).Select(_ => new ZaehlenderLeser(new ZeilenLeser(seed, zeilen), sender)).ToArray();
        try
        {
            var s0 = LiesStand(nachbar);
            long maxVorlauf = 0;
            long probenAnzahl = 0;
            var uhr = Stopwatch.StartNew();
            var mit = MitNachbar(nachbar, () =>
            {
                using var stopp = new CancellationTokenSource();
                var sampler = Task.Run(() =>
                {
                    using var eigene = Verbinden();
                    while (!stopp.IsCancellationRequested)
                    {
                        var gezogen = leser.Sum(l => l.Gezogen);
                        var stand = LiesStand(eigene);
                        var vorlauf = gezogen - (stand.Importiert - s0.Importiert);
                        maxVorlauf = Math.Max(maxVorlauf, vorlauf);
                        probenAnzahl++;
                        Thread.Sleep(100);
                    }
                });
                try
                {
                    new Gruppe(n, i =>
                    {
                        using var kopierer = new SqlBulkCopy(sitzungen[i]) { DestinationTableName = "ref_zeilen", BatchSize = Batch, BulkCopyTimeout = 3600 };
                        for (var c = 0; c < ReferenzZeilen.Spalten; c++)
                        {
                            kopierer.ColumnMappings.Add(c, c);
                        }

                        kopierer.WriteToServer(leser[i]);
                    }).Warten();
                }
                finally
                {
                    stopp.Cancel();
                    sampler.Wait();
                }
            });
            var ms = (long)uhr.Elapsed.TotalMilliseconds;
            var s1 = LiesStand(nachbar, bereinigen: true);
            NachbarZeile("import mit_last", mit.Zeiten, mit.Fehler);
            Zeile($"import n {n} zeilen {zeilen} sender_ms_je_zeile {sender} gesamt_ms {ms} importiert_delta {s1.Importiert - s0.Importiert} erwartet {n * zeilen}"
                + $" max_vorlauf_zeilen {maxVorlauf} proben {probenAnzahl} spitze_threads {s1.Spitze} heap_nach_gc_mb {s1.HeapMb}");
            Assert.Equal(n * zeilen, s1.Importiert - s0.Importiert);
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

/// <summary>Reicht jeden Aufruf an den inneren Leser; die Ableitung überschreibt `Read`.</summary>
internal abstract class DbDataReaderDecorator : DbDataReader
{
    protected readonly ZeilenLeser Innen;

    protected DbDataReaderDecorator(ZeilenLeser innen) => Innen = innen;

    public override int FieldCount => Innen.FieldCount;
    public override bool HasRows => Innen.HasRows;
    public override bool IsClosed => Innen.IsClosed;
    public override int Depth => Innen.Depth;
    public override int RecordsAffected => Innen.RecordsAffected;
    public override object this[int ordinal] => Innen[ordinal];
    public override object this[string name] => Innen[name];
    public override bool NextResult() => Innen.NextResult();
    public override string GetName(int ordinal) => Innen.GetName(ordinal);
    public override int GetOrdinal(string name) => Innen.GetOrdinal(name);
    public override Type GetFieldType(int ordinal) => Innen.GetFieldType(ordinal);
    public override string GetDataTypeName(int ordinal) => Innen.GetDataTypeName(ordinal);
    public override object GetValue(int ordinal) => Innen.GetValue(ordinal);
    public override bool IsDBNull(int ordinal) => Innen.IsDBNull(ordinal);
    public override int GetValues(object[] values) => Innen.GetValues(values);
    public override bool GetBoolean(int ordinal) => Innen.GetBoolean(ordinal);
    public override long GetInt64(int ordinal) => Innen.GetInt64(ordinal);
    public override int GetInt32(int ordinal) => Innen.GetInt32(ordinal);
    public override decimal GetDecimal(int ordinal) => Innen.GetDecimal(ordinal);
    public override DateTime GetDateTime(int ordinal) => Innen.GetDateTime(ordinal);
    public override string GetString(int ordinal) => Innen.GetString(ordinal);
    public override byte GetByte(int ordinal) => throw new NotSupportedException();
    public override short GetInt16(int ordinal) => throw new NotSupportedException();
    public override float GetFloat(int ordinal) => throw new NotSupportedException();
    public override double GetDouble(int ordinal) => throw new NotSupportedException();
    public override Guid GetGuid(int ordinal) => throw new NotSupportedException();
    public override char GetChar(int ordinal) => throw new NotSupportedException();
    public override long GetBytes(int ordinal, long dataOffset, byte[]? buffer, int bufferOffset, int length) => throw new NotSupportedException();
    public override long GetChars(int ordinal, long dataOffset, char[]? buffer, int bufferOffset, int length) => throw new NotSupportedException();
    public override System.Collections.IEnumerator GetEnumerator() => throw new NotSupportedException();
}
