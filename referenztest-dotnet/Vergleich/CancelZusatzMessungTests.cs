using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Mitschnitt;
using Xunit;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Messung zum Zusatz „ Operation cancelled by user.“ in der Antwort auf `Cancel()` im Strom (A-7, SPEC-045, TDS-QA-01;
/// `make vergleichslauf-d LAUF=cancel-2022|cancel-2025`). Derselbe Schritt wie `cancel-mit-folgeabfrage` des Live-Laufs (50 Zeilen
/// von `laufend` lesen, `Cancel()`, den Rest lesen) läuft TDSWIRE_CANCEL_N-mal je Modus gegen ein Ziel (TDSWIRE_ZIEL als host:port,
/// TDSWIRE_CANCEL_BENUTZER, TDSWIRE_CANCEL_KENNWORT, TDSWIRE_CANCEL_NAME als Kennung des Ziels in den Zeilen; TDSWIRE_CANCEL_AUFBAU=1
/// legt vorher die Datenbank `tdswire_cancel` mit der Ansicht `laufend` aus `testschema.sql` an, falls nicht vorhanden, so bei der Referenzinstanz).
/// Modus `direkt`: der Client verbindet sich ohne Zwischenstation. Modus `relais`: zwischen Client und Ziel liegt ein
/// <see cref="RecordRelais"/> (Bytes unverändert, TLS nicht beendet), der den Strom je Richtung in TLS-Records zerlegt; er zeichnet
/// Länge und Ankunftszeit der Records auf, die der Server nach dem ATTENTION sendet. Die Modi `relais-einzeln`, `relais-verzoegert`
/// und `relais-buendeln` greifen zusätzlich in die Weitergabe der Antworten ein (<see cref="Eingriff"/>): Record für Record in je
/// einem Schreibvorgang; wie Einzeln, aber den Record der Länge TDSWIRE_CANCEL_ANTWORTLAENGE (Voreinstellung 50, die Länge des
/// `DONE[ATTN]` in diesem Aufbau) um TDSWIRE_CANCEL_VERZOEGERUNG_MS (Voreinstellung 20) verzögert; die Records ab dem ATTENTION
/// gehalten, bis der Record dieser Länge da ist, und in einem Schreibvorgang weitergegeben. Je Durchlauf entsteht eine Zeile
/// `durchlauf` (Zusatz, Zahl der Fehler der Ausnahme, Zeilen nach `Cancel()`, Dauer von `Cancel()` bis zum Ende, in den Modi mit
/// Relais die letzten Records), je Modus eine Zeile `summe` und je Muster der Records eine Zeile `muster` in der Datei
/// TDSWIRE_CANCEL_AUSGABE. Der Test urteilt nicht über die Häufigkeit: er besteht, wenn alle Durchläufe eine Zeile geliefert haben.
/// Nicht belegt: andere Clients, andere Zeilenzahlen vor dem Abbruch, andere Ansichten als `laufend`.
/// </summary>
[Trait("Kategorie", "MessungCancelZusatz")]
public class CancelZusatzMessungTests
{
    private const int ZeilenVorDemAbbruch = 50;
    private const string Zusatz = "Operation cancelled by user.";
    private const byte TlsDaten = 0x17;

    private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;

    private static string Umgebung(string name) =>
        Environment.GetEnvironmentVariable(name) ?? throw new InvalidOperationException(name + " fehlt");

    private static string Zeichenfolge(string host, int port, string? datenbank)
    {
        var zeichenfolge = new SqlConnectionStringBuilder
        {
            DataSource = $"tcp:{host},{port}",
            UserID = Umgebung("TDSWIRE_CANCEL_BENUTZER"),
            Password = Umgebung("TDSWIRE_CANCEL_KENNWORT"),
            Encrypt = SqlConnectionEncryptOption.Mandatory,
            HostNameInCertificate = "localhost",
            Pooling = false,
            ConnectRetryCount = 0,
            ConnectTimeout = 15,
            CommandTimeout = 60,
        };
        if (!string.IsNullOrEmpty(datenbank)) zeichenfolge.InitialCatalog = datenbank;
        return zeichenfolge.ConnectionString;
    }

    /// <summary>Legt die Datenbank mit der Ansicht `laufend` an; die Definition ist der Stapel aus `testschema.sql`.</summary>
    private static void SchemaAnlegen(string host, int port)
    {
        using var strom = Assembly.GetExecutingAssembly().GetManifestResourceStream("testschema.sql")
            ?? throw new InvalidOperationException("testschema.sql fehlt im Assembly");
        using var leser = new StreamReader(strom, Encoding.UTF8);
        var ansicht = Regex.Split(leser.ReadToEnd(), @"^GO\s*$", RegexOptions.Multiline)
            .Single(s => s.Contains("CREATE VIEW laufend AS", StringComparison.Ordinal))
            .Replace("CREATE VIEW laufend AS", "CREATE OR ALTER VIEW laufend AS", StringComparison.Ordinal);
        using (var verbindung = new SqlConnection(Zeichenfolge(host, port, null)))
        {
            verbindung.Open();
            using var befehl = new SqlCommand("IF DB_ID(N'tdswire_cancel') IS NULL CREATE DATABASE tdswire_cancel", verbindung);
            befehl.ExecuteNonQuery();
        }

        using (var verbindung = new SqlConnection(Zeichenfolge(host, port, "tdswire_cancel")))
        {
            verbindung.Open();
            using var befehl = new SqlCommand(ansicht, verbindung);
            befehl.ExecuteNonQuery();
        }
    }

    private sealed record Ergebnis(bool Zusatz, int Fehler, string Grund, int Vorher, int Danach, double DauerMs, string Folge, string Records, string Muster, long? AbstandUs);

    private static int EnvInt(string name, int vorgabe) =>
        int.TryParse(Environment.GetEnvironmentVariable(name), NumberStyles.Integer, Invariant, out var wert) ? wert : vorgabe;

    private static double Ms(long von, long bis) => (bis - von) * 1000.0 / Stopwatch.Frequency;

    private static string Text(string t) => t.Replace("\r", " ").Replace("\n", " ").Replace("\t", " ");

    private static Ergebnis Durchlauf(string host, int port, string modus)
    {
        var relais = modus != "direkt";
        var eingriff = modus switch
        {
            "relais-einzeln" => Eingriff.Einzeln,
            "relais-verzoegert" => Eingriff.Verzoegern,
            "relais-buendeln" => Eingriff.Buendeln,
            _ => Eingriff.Keiner,
        };
        using var relaisKette = relais
            ? new RecordRelais(host, port, eingriff, EnvInt("TDSWIRE_CANCEL_ANTWORTLAENGE", 50), EnvInt("TDSWIRE_CANCEL_VERZOEGERUNG_MS", 20))
            : null;
        var verbindungsHost = relais ? "127.0.0.1" : host;
        var verbindungsPort = relais ? relaisKette!.Port : port;
        using var v = new SqlConnection(Zeichenfolge(verbindungsHost, verbindungsPort, Environment.GetEnvironmentVariable("TDSWIRE_CANCEL_DATENBANK")));
        v.Open();
        bool zusatz = false;
        int fehler = 0, vorher = 0, danach = 0;
        string grund = "keine Ausnahme";
        long tCancel, tEnde;
        using (var befehl = new SqlCommand("SELECT Id, Text FROM laufend", v))
        using (var leser = befehl.ExecuteReader())
        {
            while (vorher < ZeilenVorDemAbbruch && leser.Read()) vorher++;
            relaisKette?.Scharf();
            tCancel = Stopwatch.GetTimestamp();
            befehl.Cancel();
            try
            {
                while (leser.Read())
                {
                    danach++;
                    if (Ms(tCancel, Stopwatch.GetTimestamp()) > 30000) throw new TimeoutException("der Strom endet nach dem Abbruch nicht");
                }
            }
            catch (SqlException a)
            {
                zusatz = a.Message.Contains(Zusatz, StringComparison.Ordinal);
                fehler = a.Errors.Count;
                grund = $"Number {a.Number}, Class {a.Class}, State {a.State}, Meldung '{Text(a.Message)}'";
            }

            tEnde = Stopwatch.GetTimestamp();
        }

        string folge;
        try
        {
            using var befehl = new SqlCommand("SELECT 1", v);
            folge = $"Folgeabfrage {befehl.ExecuteScalar()}, Zustand {v.State}";
        }
        catch (SqlException a)
        {
            folge = $"Folgeabfrage Number {a.Number}";
        }

        var records = "";
        var muster = "";
        long? abstand = null;
        if (relaisKette != null)
        {
            v.Close();
            (records, muster, abstand) = RecordsNachAttention(relaisKette, tCancel);
        }

        return new Ergebnis(zusatz, fehler, grund, vorher, danach, Ms(tCancel, tEnde), folge, records, muster, abstand);
    }

    /// <summary>
    /// Die TLS-Datenrecords, die der Server nach dem ATTENTION (dem ersten Record des Clients nach `Cancel()`) sendet: die letzten
    /// drei als `Länge@Abstand zum ATTENTION in µs`, dazu die Zahl der Records und als Muster die Längen der letzten drei. Der Abstand
    /// ist die Differenz der Lesezeiten des vorletzten und des drittletzten dieser Records (am Relais, nicht beim Client; die Folgeabfrage
    /// liefert den letzten, das `DONE[ATTN]` den vorletzten).
    /// </summary>
    private static (string Records, string Muster, long? AbstandUs) RecordsNachAttention(RecordRelais relais, long tCancel)
    {
        var attention = relais.VomClient().FirstOrDefault(r => r.Typ == TlsDaten && r.Ticks >= tCancel);
        if (attention == null) return ("kein Record des Clients nach Cancel", "ohne-attention", null);
        var server = relais.VomServer().Where(r => r.Typ == TlsDaten && r.Ticks >= attention.Ticks).ToList();
        var letzte = server.TakeLast(3).ToList();
        var records = $"attention@{(attention.Ticks - tCancel) * 1000000.0 / Stopwatch.Frequency:F0}us(laenge {attention.Laenge}) server-records={server.Count} letzte=" +
            string.Join(",", letzte.Select(r => $"{r.Laenge}@{(r.Ticks - attention.Ticks) * 1000000.0 / Stopwatch.Frequency:F0}us"));
        long? abstand = letzte.Count == 3 ? (long)((letzte[1].Ticks - letzte[0].Ticks) * 1000000.0 / Stopwatch.Frequency) : null;
        return (records, string.Join("+", letzte.Select(r => r.Laenge)), abstand);
    }

    [Fact]
    public void Cancel_im_Strom_mit_und_ohne_Zusatz_je_Modus()
    {
        var ziel = Umgebung("TDSWIRE_ZIEL");
        var trenner = ziel.LastIndexOf(':');
        var host = ziel[..trenner];
        var port = int.Parse(ziel[(trenner + 1)..], Invariant);
        var n = int.Parse(Umgebung("TDSWIRE_CANCEL_N"), Invariant);
        var name = Umgebung("TDSWIRE_CANCEL_NAME");
        if (Environment.GetEnvironmentVariable("TDSWIRE_CANCEL_AUFBAU") == "1") SchemaAnlegen(host, port);

        var zeilen = new List<string>();
        var modiEnv = Environment.GetEnvironmentVariable("TDSWIRE_CANCEL_MODI");
        var modi = (string.IsNullOrEmpty(modiEnv) ? "direkt,relais,relais-einzeln,relais-verzoegert,relais-buendeln" : modiEnv).Split(',');
        foreach (var modus in modi)
        {
            var relais = modus != "direkt";
            var ergebnisse = new List<Ergebnis>();
            for (var i = 1; i <= n; i++)
            {
                var e = Durchlauf(host, port, modus);
                ergebnisse.Add(e);
                zeilen.Add($"cancelzusatz: durchlauf {name} {modus} {i} zusatz={(e.Zusatz ? 1 : 0)} fehler={e.Fehler} vorher={e.Vorher} nach-cancel={e.Danach} " +
                    $"dauer-ms={e.DauerMs.ToString("F1", Invariant)} {e.Folge} {(relais ? e.Records + " " : "")}| {e.Grund}");
            }

            var mit = ergebnisse.Count(e => e.Zusatz);
            var abstaende = ergebnisse.Where(e => e.AbstandUs != null).Select(e => e.AbstandUs!.Value).OrderBy(a => a).ToList();
            var abstand = abstaende.Count == 0 ? "" : $" abstand-attn-us-min/median/max={abstaende[0]}/{abstaende[abstaende.Count / 2]}/{abstaende[^1]}";
            zeilen.Add($"cancelzusatz: summe {name} {modus} n={ergebnisse.Count} mit-zusatz={mit} ohne-zusatz={ergebnisse.Count - mit}{abstand}");
            if (relais)
            {
                foreach (var gruppe in ergebnisse.GroupBy(e => e.Muster).OrderBy(g => g.Key, StringComparer.Ordinal))
                {
                    zeilen.Add($"cancelzusatz: muster {name} {modus} letzte-records={gruppe.Key} mit-zusatz={gruppe.Count(e => e.Zusatz)} ohne-zusatz={gruppe.Count(e => !e.Zusatz)}");
                }
            }
        }

        File.WriteAllLines(Umgebung("TDSWIRE_CANCEL_AUSGABE"), zeilen, new UTF8Encoding(false));
        Assert.Equal(modi.Length * n, zeilen.Count(z => z.StartsWith("cancelzusatz: durchlauf ", StringComparison.Ordinal)));
    }
}
