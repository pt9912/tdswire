using System.Data;
using System.Diagnostics;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Mitschnitt;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Der Mitschnitt-Lauf des Referenzabgleichs (SPEC-045, `make vergleichslauf-d LAUF=aufnahme-2022|aufnahme-2025|mitschnitt-2022|
/// mitschnitt-2025`). Die Aufnahme führt je Szenario eine Verbindung des Referenzclients über den mitschneidenden Proxy
/// (<see cref="Aufnahme"/>, Loopback im Container des Clients) gegen die Referenzinstanz und legt die Anfragen und Antworten ab
/// (<see cref="MitschnittDatei"/>). Verbunden wird mit `Encrypt=false`: die Anmeldung läuft verschlüsselt und bleibt außerhalb der
/// Ablage, die Anfragen laufen im Klartext. Grenze: Der Mitschnitt zeigt nur, was der Client dieser Version auf diese
/// Anfragen sendet und die Instanz in diesem Lauf antwortet; er sagt nichts über TLS, TDS 8.0, andere Clients oder andere Läufe.
/// </summary>
internal static class MitschnittZiel
{
    public static string Umgebung(string name) =>
        Environment.GetEnvironmentVariable(name) ?? throw new InvalidOperationException(name + " fehlt");

    public static (string Host, int Port) Ziel()
    {
        var ziel = Umgebung("TDSWIRE_ZIEL");
        var trenner = ziel.LastIndexOf(':');
        return (ziel[..trenner], int.Parse(ziel[(trenner + 1)..], System.Globalization.CultureInfo.InvariantCulture));
    }

    public static string KlartextVerbindung(int port, string benutzer, string kennwort, string? datenbank) =>
        new SqlConnectionStringBuilder
        {
            DataSource = $"tcp:127.0.0.1,{port}",
            UserID = benutzer,
            Password = kennwort,
            Encrypt = SqlConnectionEncryptOption.Optional,
            Pooling = false,
            ConnectRetryCount = 1,
            ConnectTimeout = 15,
            CommandTimeout = 60,
            InitialCatalog = datenbank ?? "",
        }.ConnectionString;
}

[Trait("Kategorie", "MitschnittAufnahme")]
public class MitschnittAufnahmeTests
{
    private const string Datenbank = "tdswire_live";
    private readonly ITestOutputHelper _ausgabe;

    public MitschnittAufnahmeTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static void SchemaAnlegen()
    {
        using var strom = Assembly.GetExecutingAssembly().GetManifestResourceStream("testschema.sql")
            ?? throw new InvalidOperationException("testschema.sql fehlt im Assembly");
        using var leser = new StreamReader(strom, Encoding.UTF8);
        var staepel = Regex.Split(leser.ReadToEnd(), @"^GO\s*$", RegexOptions.Multiline).Where(s => !string.IsNullOrWhiteSpace(s));
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        foreach (var stapel in staepel)
        {
            using var befehl = new SqlCommand(stapel, verbindung);
            befehl.ExecuteNonQuery();
        }
    }

    private static string ServerName()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        using var befehl = new SqlCommand("SELECT @@SERVERNAME", verbindung);
        return (string)befehl.ExecuteScalar()!;
    }

    // ---- Szenarien --------------------------------------------------------------------------------------------------

    private static void Lesen(SqlConnection v, string text, Action<SqlParameterCollection>? parameter = null)
    {
        using var befehl = new SqlCommand(text, v);
        parameter?.Invoke(befehl.Parameters);
        using var leser = befehl.ExecuteReader();
        do
        {
            while (leser.Read())
            {
            }
        }
        while (leser.NextResult());
    }

    private static void LesenMitFehler(SqlConnection v, string text)
    {
        try
        {
            Lesen(v, text);
        }
        catch (SqlException)
        {
            // Der Fehler gehört zum Szenario; der Mitschnitt hält die Antwort fest.
        }
    }

    private static void Folgeabfrage(SqlConnection v)
    {
        if (v.State == ConnectionState.Open) LesenMitFehler(v, "SELECT Id FROM person WHERE Id = 1");
    }

    private static void BisZumEnde(SqlDataReader leser)
    {
        var uhr = Stopwatch.StartNew();
        try
        {
            while (leser.Read())
                if (uhr.Elapsed > TimeSpan.FromSeconds(30)) break;
        }
        catch (SqlException)
        {
            // Ausnahme nach dem Abbruch oder der Frist: Teil des Szenarios.
        }
    }

    private static void Zeilen(SqlDataReader leser, int anzahl)
    {
        for (var i = 0; i < anzahl && leser.Read(); i++)
        {
        }
    }

    private static readonly (string Name, Action<SqlConnection> Koerper)[] Szenarien =
    {
        ("batch-select", v => Lesen(v, "SELECT Id, Name, Age, Score FROM person WHERE Age = 36")),
        ("batch-leer", v => Lesen(v, "SELECT Id, Name FROM person WHERE Id = 99")),
        ("batch-typen", v => Lesen(v, "SELECT Id, Gross, Flag, Text, Preis, Tag, Uhr, Zeit FROM typen")),
        ("rpc-parameter", v => Lesen(v, "SELECT Id, Name FROM person WHERE Age = @alter", p => p.Add("@alter", SqlDbType.Int).Value = 36)),
        ("rpc-ausgabewert", v =>
        {
            using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @id", v);
            var p = befehl.Parameters.Add("@id", SqlDbType.Int);
            p.Direction = ParameterDirection.InputOutput;
            p.Value = 7;
            try
            {
                using var leser = befehl.ExecuteReader();
                while (leser.Read())
                {
                }
            }
            catch (SqlException)
            {
                // Der Fehler gehört zum Szenario.
            }
        }),
        ("prepare", v =>
        {
            using var befehl = new SqlCommand("SELECT Id, Name FROM person WHERE Age = @alter", v);
            befehl.Parameters.Add("@alter", SqlDbType.Int).Value = 36;
            try
            {
                befehl.Prepare();
                using var leser = befehl.ExecuteReader();
                while (leser.Read())
                {
                }
            }
            catch (SqlException)
            {
                // Der Fehler gehört zum Szenario.
            }
        }),
        ("mehrteilig", v => LesenMitFehler(v, "SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2")),
        ("fehler-vor-zeilen", v =>
        {
            LesenMitFehler(v, "SELECT Id FROM gibtesnicht");
            Folgeabfrage(v);
        }),
        ("fehler-nach-zeilen", v =>
        {
            LesenMitFehler(v, "SELECT Id, Text FROM kaputt");
            Folgeabfrage(v);
        }),
        ("batch-statement-abbrechend", v =>
        {
            LesenMitFehler(v, "SELECT 1/0; SELECT 2");
            Folgeabfrage(v);
        }),
        ("batch-batch-abbrechend", v =>
        {
            LesenMitFehler(v, "SELECT Id FROM gibtesnicht; SELECT 2");
            Folgeabfrage(v);
        }),
        ("cancel-streaming", v =>
        {
            using var befehl = new SqlCommand("SELECT Id, Text FROM laufend", v);
            using (var leser = befehl.ExecuteReader())
            {
                Zeilen(leser, 50);
                befehl.Cancel();
                BisZumEnde(leser);
            }

            Folgeabfrage(v);
        }),
        ("cancel-leerlauf", v =>
        {
            using var fertig = new SqlCommand("SELECT Id FROM person WHERE Id = 1", v);
            fertig.ExecuteScalar();
            fertig.Cancel();
            using var nie = new SqlCommand("SELECT Id FROM person WHERE Id = 2", v);
            nie.Cancel();
            Thread.Sleep(500);
            Folgeabfrage(v);
        }),
        ("timeout-wartend", v => Warten(v, timeoutSekunden: 1, abbruchNachMs: null)),
        ("cancel-wartend", v => Warten(v, timeoutSekunden: 30, abbruchNachMs: 1000)),
        ("cancel-kurz-vor-timeout", v => Warten(v, timeoutSekunden: 2, abbruchNachMs: 1900)),
        ("cancel-kurz-nach-timeout", v => Warten(v, timeoutSekunden: 2, abbruchNachMs: 2100)),
    };

    /// <summary>
    /// Ein Befehl, der nicht antwortet (`WAITFOR DELAY`), mit Frist <paramref name="timeoutSekunden"/> und optionalem Cancel()
    /// eines zweiten Threads nach <paramref name="abbruchNachMs"/> ms. So entstehen der Abbruch durch die Frist, durch Cancel()
    /// und beide kurz hintereinander (SPEC-040, Folgepflicht (a)); wie viele ATTENTION der Client sendet, zeigt der Mitschnitt.
    /// </summary>
    private static void Warten(SqlConnection v, int timeoutSekunden, int? abbruchNachMs)
    {
        using var befehl = new SqlCommand("WAITFOR DELAY '00:00:10'", v) { CommandTimeout = timeoutSekunden };
        Task? abbruch = abbruchNachMs is { } ms ? Task.Run(() =>
        {
            Thread.Sleep(ms);
            befehl.Cancel();
        }) : null;
        try
        {
            befehl.ExecuteNonQuery();
        }
        catch (SqlException)
        {
            // Abbruch durch Frist oder Cancel(): Teil des Szenarios.
        }

        abbruch?.Wait();
        Thread.Sleep(300);
        Folgeabfrage(v);
    }

    private static string Herkunft(string name) => Environment.GetEnvironmentVariable(name) ?? "nicht angegeben";

    // @spec SPEC-045
    [Fact]
    public void Die_Szenarien_laufen_ueber_den_Proxy_und_die_Ablage_ist_frei_von_Kennwort()
    {
        var ausgabe = MitschnittZiel.Umgebung("TDSWIRE_MITSCHNITT_AUSGABE");
        var kennwort = ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT");
        var (host, port) = MitschnittZiel.Ziel();
        Directory.CreateDirectory(ausgabe);
        SchemaAnlegen();
        var servername = ServerName();

        foreach (var (name, koerper) in Szenarien)
        {
            using var aufnahme = new Aufnahme(host, port);
            using var verbindung = new SqlConnection(MitschnittZiel.KlartextVerbindung(aufnahme.Port, ReferenzZiel.Benutzer, kennwort, Datenbank));
            verbindung.Open();
            var packetSize = verbindung.PacketSize;
            koerper(verbindung);
            var ereignisse = aufnahme.Anhalten();

            var datei = new MitschnittDatei { Szenario = name };
            datei.Herkunft.Add(new("instanz", Herkunft("TDSWIRE_HERKUNFT_INSTANZ")));
            datei.Herkunft.Add(new("image", Herkunft("TDSWIRE_HERKUNFT_IMAGE")));
            datei.Herkunft.Add(new("client", $"Microsoft.Data.SqlClient {typeof(SqlConnection).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion?.Split('+')[0]} auf {RuntimeInformation.FrameworkDescription}"));
            datei.Herkunft.Add(new("schema", "testschema.sql " + Herkunft("TDSWIRE_HERKUNFT_SCHEMA")));
            datei.Herkunft.Add(new("methode", "Proxy im Container des Clients, Encrypt=false (Anmeldung verschluesselt und nicht abgelegt, Anfragen im Klartext), PacketSize " + packetSize));
            datei.Herkunft.Add(new("aufnahme", DateTime.UtcNow.ToString("yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture)));
            // Der Servername (Name des Containers) steht in ERROR und INFO; er gehoert der Instanz, nicht dem Verhalten.
            datei.Ereignisse.AddRange(ereignisse.Select(e => e.Art == 'R' ? e with { Daten = Redaktion.NameErsetzen(e.Daten, servername) } : e));
            Assert.True(aufnahme.Schreiber.Klartext, $"{name}: kein Klartext-Abschnitt (Sitzung vollstaendig verschluesselt?)");
            Assert.NotEmpty(datei.Austausche());
            Assert.True(datei.Ereignisse.Sum(e => e.Daten.Length) <= 256 * 1024, $"{name}: der Mitschnitt ist groesser als 256 KiB");

            var text = datei.Serialisieren();
            Assert.Null(Redaktion.Treffer(text, kennwort));
            Assert.Null(Redaktion.Treffer(text, servername));
            _ = MitschnittDatei.Lesen(text);
            File.WriteAllText(Path.Combine(ausgabe, name + ".mitschnitt"), text, new UTF8Encoding(false));
            foreach (var (kennung, wert) in Ergebnis.AusMitschnitt(datei))
                _ausgabe.WriteLine($"messung: {kennung}\t{(wert.Length > 300 ? wert[..300] + "..." : wert)}");
        }
    }
}

[Trait("Kategorie", "MitschnittAbspielen")]
public class MitschnittAbspielenTests
{
    // @spec SPEC-045
    [Fact]
    public async Task Die_Mitschnitte_laufen_gegen_den_Server_und_beide_Seiten_liegen_als_Ausgabe_vor()
    {
        var stufe = MitschnittZiel.Umgebung("TDSWIRE_STUFE");
        var eingabe = Environment.GetEnvironmentVariable("TDSWIRE_MITSCHNITT_EINGABE")
            ?? Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "aufnahmen", stufe);
        var (host, port) = MitschnittZiel.Ziel();
        var benutzer = MitschnittZiel.Umgebung("TDSWIRE_LIVE_BENUTZER");
        var kennwort = MitschnittZiel.Umgebung("TDSWIRE_LIVE_KENNWORT");
        var dateien = Directory.GetFiles(eingabe, "*.mitschnitt").Order(StringComparer.Ordinal).ToList();
        Assert.NotEmpty(dateien);

        var referenz = new List<(string Kennung, string Text)>();
        var server = new List<(string Kennung, string Text)>();
        foreach (var pfad in dateien)
        {
            var datei = MitschnittDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
            referenz.AddRange(Ergebnis.AusMitschnitt(datei));

            using var abspieler = new Abspieler();
            var austausche = datei.Austausche();
            var lauf = abspieler.Abspielen(austausche, host, port);
            using (var verbindung = new SqlConnection(MitschnittZiel.KlartextVerbindung(abspieler.Port, benutzer, kennwort, null)))
            {
                verbindung.Open();
                var fertig = await Task.WhenAny(lauf, Task.Delay(TimeSpan.FromMinutes(2)));
                Assert.True(fertig == lauf, $"{datei.Szenario}: Abspielen blieb stehen");
            }

            var ergebnisse = await lauf;
            for (var i = 0; i < ergebnisse.Count; i++)
                server.AddRange(Ergebnis.Zeilen(datei.Szenario, i + 1, ergebnisse[i].Antworten, ergebnisse[i].Attention, ergebnisse[i].Problem));
        }

        File.WriteAllText(MitschnittZiel.Umgebung("TDSWIRE_MITSCHNITT_REFERENZ"), Ergebnis.Ausgabe(referenz), new UTF8Encoding(false));
        File.WriteAllText(MitschnittZiel.Umgebung("TDSWIRE_MITSCHNITT_SERVER"), Ergebnis.Ausgabe(server), new UTF8Encoding(false));
    }
}
