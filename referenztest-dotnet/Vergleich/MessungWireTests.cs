using System.Data;
using System.Diagnostics;
using System.Reflection;
using System.Runtime.InteropServices;
using System.Security.Cryptography;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using TdsWire.Referenztests.Mitschnitt;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Die Messreihe am Draht des Slice slice-qa-vergleichslauf-d-messungen (SPEC-045, `make vergleichslauf-d LAUF=wire-2022|wire-2025`):
/// Fehler im RPC, Stellung von COLMETADATA hinter RETURNVALUE, Ordinal der RETURNVALUE mit mehreren Ausgabewerten, Befehlskennung
/// `curCmd` für Abschlüsse mit Zeilenzahl, ENVCHANGE bei RESETCONNECTION, Anmeldung und SESSIONSTATE mit `ConnectRetryCount=0` und `1`.
/// Jedes Szenario führt eine Verbindung des Referenzclients über den TLS-beendenden Proxy (<see cref="TlsAufnahme"/>) gegen die
/// Referenzinstanz und legt PRELOGIN, LOGIN7 (redigiert) und alle Nachrichten ab (<see cref="MessDatei"/>). Verbunden wird mit
/// `Encrypt=Mandatory`; der Client prüft das Zertifikat des Proxys nicht (`TrustServerCertificate=true` nur auf dieser Strecke,
/// Setzung), der Proxy prüft das des Servers. Grenze: Einzelmessung je Szenario und Instanz; der Client ist `Microsoft.Data.SqlClient`
/// in der angegebenen Version, ein anderer Client fragt anders; TLS 1.2 ist gesetzt.
/// </summary>
[Trait("Kategorie", "MessungWire")]
public class MessungWireTests
{
    private const string Datenbank = "tdswire_live";
    private readonly ITestOutputHelper _ausgabe;

    public MessungWireTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    // Zusätzliche Objekte der Messreihe, neben dem Testschema (Vergleich/testschema.sql, unverändert).
    private static readonly string[] Aufbau =
    {
        "CREATE TABLE wire_dml (Id int NOT NULL PRIMARY KEY, [Text] nvarchar(10) NULL)",
        "INSERT INTO wire_dml (Id, [Text]) VALUES (100, N'alt')",
        "CREATE PROCEDURE wire_proc_ausgabe @a int OUTPUT, @b int OUTPUT AS BEGIN SET @b = 20; SET @a = 10; SELECT 1 AS x; END",
        "CREATE PROCEDURE wire_proc_zwei @a int OUTPUT, @w int, @b int OUTPUT AS BEGIN SET @b = 2; SET @a = 1; SELECT @w AS w; END",
        "CREATE PROCEDURE wire_proc_ergebnis @o int OUTPUT AS BEGIN SELECT 1 AS x; SET @o = 5; SELECT 2 AS y; END",
        "CREATE PROCEDURE wire_proc_innen @o int OUTPUT AS BEGIN SET @o = 7; SELECT 1 AS innen; END",
        "CREATE PROCEDURE wire_proc_aussen @o int OUTPUT AS BEGIN EXEC wire_proc_innen @o OUTPUT; SELECT 2 AS aussen; END",
        "CREATE PROCEDURE wire_proc_fehler1 AS BEGIN SELECT 1/0 AS a; SELECT 2 AS b; END",
        "CREATE PROCEDURE wire_proc_fehler2 AS BEGIN SELECT 1 AS a; RAISERROR('wire', 16, 1); SELECT 2 AS b; END",
        "CREATE PROCEDURE wire_proc_fehler3 @o int OUTPUT AS BEGIN SET @o = 5; SELECT 1/0 AS a; END",
        "CREATE PROCEDURE wire_proc_dml @i int AS BEGIN INSERT INTO wire_dml (Id, [Text]) VALUES (@i, N'p'); UPDATE wire_dml SET [Text] = N'q' WHERE Id = @i; DELETE FROM wire_dml WHERE Id = @i; END",
        "CREATE PROCEDURE wire_proc_dml_ergebnis @i int AS BEGIN SELECT 1 AS x; INSERT INTO wire_dml (Id, [Text]) VALUES (@i, N'p'); DELETE FROM wire_dml WHERE Id = @i; END",
    };

    private static void SchemaUndAufbau()
    {
        using var strom = Assembly.GetExecutingAssembly().GetManifestResourceStream("testschema.sql")
            ?? throw new InvalidOperationException("testschema.sql fehlt im Assembly");
        using var leser = new StreamReader(strom, Encoding.UTF8);
        var staepel = Regex.Split(leser.ReadToEnd(), @"^GO\s*$", RegexOptions.Multiline).Where(s => !string.IsNullOrWhiteSpace(s)).ToList();
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        foreach (var stapel in staepel)
        {
            using var befehl = new SqlCommand(stapel, verbindung);
            befehl.ExecuteNonQuery();
        }

        using (var wechsel = new SqlCommand("USE " + Datenbank, verbindung))
            wechsel.ExecuteNonQuery();
        foreach (var anweisung in Aufbau)
        {
            using var befehl = new SqlCommand(anweisung, verbindung);
            befehl.ExecuteNonQuery();
        }
    }

    private static string AufbauHash() =>
        "sha256:" + Convert.ToHexString(SHA256.HashData(Encoding.UTF8.GetBytes(string.Join("\n", Aufbau)))).ToLowerInvariant();

    // ---- Bausteine --------------------------------------------------------------------------------------------------

    private sealed class Lauf
    {
        public Lauf(string name, Func<int, bool, SqlConnection> oeffnen, Action<string> beobachtung, Func<string> proxy)
        {
            Proxy = proxy;
            Name = name;
            Oeffnen = oeffnen;
            Beobachtung = beobachtung;
        }

        public string Name { get; }

        public Func<int, bool, SqlConnection> Oeffnen { get; }

        public Action<string> Beobachtung { get; }

        /// <summary>Zustand des Proxys: weitere Verbindungen des Clients und Ende der ersten.</summary>
        public Func<string> Proxy { get; }
    }

    private static string Parameterwerte(SqlParameterCollection ps) =>
        string.Join(", ", ps.Cast<SqlParameter>().Where(p => p.Direction != ParameterDirection.Input)
            .Select(p => p.ParameterName + "=" + (p.Value is DBNull ? "NULL" : p.Value)));

    /// <summary>Führt einen Befehl bis zum Ende aus und beschreibt, was der Client sah: Mengen, Zeilen, Ausnahme (Ort, Nummer, Klasse, Zustand) und Ausgabewerte.</summary>
    private static string Ausfuehren(SqlConnection v, string text, Action<SqlParameterCollection>? parameter = null, CommandType art = CommandType.Text)
    {
        using var befehl = new SqlCommand(text, v) { CommandType = art };
        parameter?.Invoke(befehl.Parameters);
        var mengen = 0;
        var zeilen = 0;
        var ort = "ExecuteReader";
        string ergebnis;
        try
        {
            using (var leser = befehl.ExecuteReader())
            {
                ort = "Read";
                do
                {
                    mengen++;
                    while (leser.Read()) zeilen++;
                    ort = "NextResult";
                }
                while (leser.NextResult());
            }

            ergebnis = $"Mengen {mengen}, Zeilen {zeilen}, ohne Ausnahme";
        }
        catch (SqlException e)
        {
            ergebnis = $"Mengen {mengen}, Zeilen {zeilen}, Ausnahme bei {ort}: Number {e.Number}, Class {e.Class}, State {e.State}, Fehler {e.Errors.Count}";
        }

        var ausgabe = Parameterwerte(befehl.Parameters);
        return $"{ergebnis}; Verbindung {v.State}{(ausgabe.Length > 0 ? "; Parameter " + ausgabe : string.Empty)}";
    }

    private static void Mit(Lauf l, SqlConnection v, string beschreibung, string text, Action<SqlParameterCollection>? parameter = null, CommandType art = CommandType.Text) =>
        l.Beobachtung($"{l.Name} {beschreibung}: {Ausfuehren(v, text, parameter, art)}");

    private static SqlParameter Ein(SqlParameterCollection ps, string name, int wert)
    {
        var p = ps.Add(name, SqlDbType.Int);
        p.Value = wert;
        return p;
    }

    private static SqlParameter Aus(SqlParameterCollection ps, string name)
    {
        var p = ps.Add(name, SqlDbType.Int);
        p.Direction = ParameterDirection.Output;
        return p;
    }

    private static SqlParameter EinAus(SqlParameterCollection ps, string name, object wert)
    {
        var p = ps.Add(name, SqlDbType.Int);
        p.Direction = ParameterDirection.InputOutput;
        p.Value = wert;
        return p;
    }

    private static void Folgeabfrage(Lauf l, SqlConnection v) => Mit(l, v, "Folgeabfrage", "SELECT Id FROM person WHERE Id = 1");

    // ---- Szenarien --------------------------------------------------------------------------------------------------

    private static readonly (string Name, int Crc, bool Pool, Action<Lauf, SqlConnection> Koerper)[] Szenarien = Erzeugen();

    private static (string, int, bool, Action<Lauf, SqlConnection>)[] Erzeugen()
    {
        var s = new List<(string, int, bool, Action<Lauf, SqlConnection>)>
        {
            // ---- Punkt 1a: Fehler im RPC ----
            ("rpc-fehler-vor-zeilen", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Tabelle fehlt", "SELECT Id FROM gibtesnicht WHERE Id = @i", p => Ein(p, "@i", 1));
                Folgeabfrage(l, v);
            }),
            ("rpc-fehler-nach-zeilen", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Division durch null in der vierten Zeile", "SELECT Id, [Text] FROM kaputt WHERE Id >= @i", p => Ein(p, "@i", 1));
                Folgeabfrage(l, v);
            }),
            ("rpc-fehler-statement-abbrechend", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Division durch null, danach eine Anweisung", "SELECT 1/0 AS a; SELECT 2 AS b", p => Ein(p, "@i", 1));
                Folgeabfrage(l, v);
            }),
            ("rpc-fehler-batch-abbrechend", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Tabelle fehlt, danach eine Anweisung", "SELECT Id FROM gibtesnicht WHERE Id = @i; SELECT 2 AS b", p => Ein(p, "@i", 1));
                Folgeabfrage(l, v);
            }),
            ("rpc-fehler-ausgabewert", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Ausgabewert gesetzt, dann Fehler", "SET @o = 1; SELECT 1/0 AS a", p => Aus(p, "@o"));
                Folgeabfrage(l, v);
            }),
            ("proc-fehler-statement-abbrechend", 1, false, (l, v) =>
            {
                Mit(l, v, "Prozedur, Division durch null, danach eine Anweisung", "wire_proc_fehler1", null, CommandType.StoredProcedure);
                Folgeabfrage(l, v);
            }),
            ("proc-fehler-raiserror", 1, false, (l, v) =>
            {
                Mit(l, v, "Prozedur, RAISERROR Stufe 16 zwischen zwei Ergebnissen", "wire_proc_fehler2", null, CommandType.StoredProcedure);
                Folgeabfrage(l, v);
            }),
            ("proc-fehler-ausgabewert", 1, false, (l, v) =>
            {
                Mit(l, v, "Prozedur mit Ausgabewert, Fehler nach der Zuweisung", "wire_proc_fehler3", p => Aus(p, "@o"), CommandType.StoredProcedure);
                Folgeabfrage(l, v);
            }),

            // ---- Punkt 1b: COLMETADATA hinter RETURNVALUE ----
            ("colmetadata-hinter-returnvalue", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql, Zuweisung zwischen zwei Ergebnissen", "SELECT 1 AS x; SET @o = 5; SELECT 2 AS y", p => Aus(p, "@o"));
                Mit(l, v, "Prozedur, Zuweisung zwischen zwei Ergebnissen", "wire_proc_ergebnis", p => Aus(p, "@o"), CommandType.StoredProcedure);
                Mit(l, v, "Prozedur ruft Prozedur mit Ausgabewert, danach ein Ergebnis", "wire_proc_aussen", p => Aus(p, "@o"), CommandType.StoredProcedure);
            }),

            // ---- Punkt 1c: Ordinal der RETURNVALUE ----
            ("ordinal-executesql", 1, false, (l, v) =>
            {
                const string Text = "SET @a = 1; SET @b = 2; SELECT @w AS w";
                Mit(l, v, "@a OUT, @w IN, @b OUT", Text, p => { Aus(p, "@a"); Ein(p, "@w", 9); Aus(p, "@b"); });
                Mit(l, v, "@a OUT, @w IN (Ausgabe vor Wert, nur ein Ausgabewert)", "SET @a = 1; SELECT @w AS w", p => { Aus(p, "@a"); Ein(p, "@w", 9); });
                Mit(l, v, "@w IN, @a OUT, @b OUT", Text, p => { Ein(p, "@w", 9); Aus(p, "@a"); Aus(p, "@b"); });
                Mit(l, v, "@a OUT, @b OUT, Zuweisung in umgekehrter Reihenfolge", "SET @b = 2; SET @a = 1; SELECT 1 AS x", p => { Aus(p, "@a"); Aus(p, "@b"); });
                Mit(l, v, "@a INOUT mit NULL, @w IN", "SET @a = 3; SELECT @w AS w", p => { EinAus(p, "@a", DBNull.Value); Ein(p, "@w", 9); });
                Mit(l, v, "@a INOUT mit Wert, @b OUT, @w IN", Text, p => { EinAus(p, "@a", 4); Aus(p, "@b"); Ein(p, "@w", 9); });
            }),
            ("ordinal-prepexec", 1, false, (l, v) =>
            {
                using var befehl = new SqlCommand("SET @a = 1; SELECT @w AS w", v);
                Ein(befehl.Parameters, "@w", 9);
                Aus(befehl.Parameters, "@a");
                try
                {
                    befehl.Prepare();
                    for (var i = 0; i < 2; i++)
                    {
                        using var leser = befehl.ExecuteReader();
                        while (leser.Read())
                        {
                        }
                    }

                    l.Beobachtung($"{l.Name} Prepare mit @w IN, @a OUT, zwei Ausfuehrungen: ohne Ausnahme; Parameter {Parameterwerte(befehl.Parameters)}");
                }
                catch (SqlException e)
                {
                    l.Beobachtung($"{l.Name} Prepare mit @w IN, @a OUT: Ausnahme Number {e.Number}");
                }
            }),
            ("ordinal-prozedur", 1, false, (l, v) =>
            {
                Mit(l, v, "Prozedur @a OUT, @w IN, @b OUT mit Rueckgabestatus", "wire_proc_zwei", p =>
                {
                    var r = p.Add("@r", SqlDbType.Int);
                    r.Direction = ParameterDirection.ReturnValue;
                    Aus(p, "@a");
                    Ein(p, "@w", 9);
                    Aus(p, "@b");
                }, CommandType.StoredProcedure);
                Mit(l, v, "Prozedur @a OUT, @b OUT ohne Rueckgabeparameter", "wire_proc_ausgabe", p => { Aus(p, "@a"); Aus(p, "@b"); }, CommandType.StoredProcedure);
            }),

            // ---- Punkt 1c: curCmd ----
            ("dml-batch", 1, false, (l, v) =>
            {
                Mit(l, v, "INSERT", "INSERT INTO wire_dml (Id, [Text]) VALUES (1, N'a'), (2, N'b')");
                Mit(l, v, "UPDATE", "UPDATE wire_dml SET [Text] = N'c' WHERE Id <= 2");
                Mit(l, v, "DELETE", "DELETE FROM wire_dml WHERE Id <= 2");
                Mit(l, v, "INSERT, danach SELECT", "INSERT INTO wire_dml (Id, [Text]) VALUES (3, N'a'); SELECT Id FROM wire_dml WHERE Id = 3");
                Mit(l, v, "DELETE ohne Treffer", "DELETE FROM wire_dml WHERE Id = 999");
                Mit(l, v, "SET NOCOUNT ON, INSERT", "SET NOCOUNT ON; INSERT INTO wire_dml (Id, [Text]) VALUES (4, N'a'); SET NOCOUNT OFF");
                Mit(l, v, "EXEC Prozedur mit DML", "EXEC wire_proc_dml 50");
                Mit(l, v, "EXEC Prozedur mit Ergebnis und DML", "EXEC wire_proc_dml_ergebnis 51");
                Mit(l, v, "CREATE TABLE und DROP TABLE", "CREATE TABLE #t (a int); DROP TABLE #t");
                Mit(l, v, "SELECT INTO", "SELECT Id INTO #u FROM person; DROP TABLE #u");
            }),
            ("dml-rpc", 1, false, (l, v) =>
            {
                Mit(l, v, "sp_executesql INSERT", "INSERT INTO wire_dml (Id, [Text]) VALUES (@i, N'a'), (@i + 1, N'b')", p => Ein(p, "@i", 10));
                Mit(l, v, "sp_executesql UPDATE", "UPDATE wire_dml SET [Text] = N'c' WHERE Id >= @i", p => Ein(p, "@i", 10));
                Mit(l, v, "sp_executesql DELETE", "DELETE FROM wire_dml WHERE Id >= @i", p => Ein(p, "@i", 10));
                Mit(l, v, "Prozedur mit DML (DONEINPROC)", "wire_proc_dml", p => Ein(p, "@i", 60), CommandType.StoredProcedure);
                Mit(l, v, "Prozedur mit Ergebnis und DML", "wire_proc_dml_ergebnis", p => Ein(p, "@i", 61), CommandType.StoredProcedure);
            }),

            // ---- Punkt 2: ENVCHANGE bei RESETCONNECTION ----
            ("reset-ohne-zustand", 1, true, (l, v) =>
            {
                Mit(l, v, "erste Nutzung", "SELECT DB_NAME() AS db");
                var erste = v.ClientConnectionId;
                v.Close();
                using var zweite = l.Oeffnen(1, true);
                l.Beobachtung($"{l.Name} ClientConnectionId gleich: {erste == zweite.ClientConnectionId}; Proxy: {l.Proxy()}");
                Mit(l, zweite, "zweite Nutzung", "SELECT DB_NAME() AS db");
            }),
            ("reset-datenbank", 1, true, (l, v) =>
            {
                Mit(l, v, "USE master", "USE master");
                v.Close();
                using var zweite = l.Oeffnen(1, true);
                Mit(l, zweite, "zweite Nutzung", "SELECT DB_NAME() AS db");
            }),
            ("reset-sprache", 1, true, (l, v) =>
            {
                Mit(l, v, "SET LANGUAGE German", "SET LANGUAGE German");
                v.Close();
                using var zweite = l.Oeffnen(1, true);
                Mit(l, zweite, "zweite Nutzung", "SELECT @@LANGUAGE AS sprache");
            }),
            ("reset-mehrfach", 1, true, (l, v) =>
            {
                Mit(l, v, "SET DATEFORMAT dmy", "SET DATEFORMAT dmy");
                v.Close();
                using var zweite = l.Oeffnen(1, true);
                Mit(l, zweite, "zweite Nutzung", "SELECT @@LANGUAGE AS sprache");
                zweite.Close();
                using var dritte = l.Oeffnen(1, true);
                Mit(l, dritte, "dritte Nutzung", "SELECT DB_NAME() AS db");
            }),
        };

        // ---- Punkt 3: Anmeldung und SESSIONSTATE, mit ConnectRetryCount 0 und 1 ----
        foreach (var crc in new[] { 0, 1 })
        {
            var c = crc;
            s.Add(($"anmeldung-crc{c}", c, false, (l, v) => Mit(l, v, "SELECT 1", "SELECT 1 AS eins")));
            s.Add(($"sitzung-crc{c}", c, false, (l, v) =>
            {
                Mit(l, v, "SET DATEFORMAT dmy", "SET DATEFORMAT dmy");
                Mit(l, v, "SET LANGUAGE German", "SET LANGUAGE German");
                Mit(l, v, "USE master", "USE master");
                Mit(l, v, "SELECT nach Zustandswechseln", "SELECT 1 AS eins");
                Mit(l, v, "USE zurueck", "USE " + Datenbank);
                using var befehl = new SqlCommand("SELECT Id, [Text] FROM laufend", v);
                using (var leser = befehl.ExecuteReader())
                {
                    for (var i = 0; i < 50 && leser.Read(); i++)
                    {
                    }

                    befehl.Cancel();
                    var uhr = Stopwatch.StartNew();
                    try
                    {
                        while (leser.Read() && uhr.Elapsed < TimeSpan.FromSeconds(30))
                        {
                        }
                    }
                    catch (SqlException e)
                    {
                        l.Beobachtung($"{l.Name} Cancel nach 50 Zeilen: Ausnahme Number {e.Number}, Class {e.Class}");
                    }
                }

                Folgeabfrage(l, v);
            }));
        }

        return s.ToArray();
    }

    private static string Herkunft(string name) => Environment.GetEnvironmentVariable(name) ?? "nicht angegeben";

    private static string ServerName()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));
        using var befehl = new SqlCommand("SELECT @@SERVERNAME", verbindung);
        return (string)befehl.ExecuteScalar()!;
    }

    // @spec SPEC-045
    [Fact]
    public void Die_Messreihe_laeuft_ueber_den_TlsProxy_und_die_Ablage_ist_frei_von_Zugangsdaten()
    {
        var ausgabe = MitschnittZiel.Umgebung("TDSWIRE_MESSUNG_AUSGABE");
        var kennwort = ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT");
        var (host, port) = MitschnittZiel.Ziel();
        Directory.CreateDirectory(ausgabe);
        SchemaUndAufbau();
        var servername = ServerName();
        var rechner = Environment.MachineName;
        var kennungen = new HashSet<string>();

        // TDSWIRE_MESSUNG_NUR: Namen der Szenarien, durch Komma getrennt (Teilmenge für die Fehlersuche; leer: alle).
        var nur = (Environment.GetEnvironmentVariable("TDSWIRE_MESSUNG_NUR") ?? string.Empty).Split(',', StringSplitOptions.RemoveEmptyEntries);
        foreach (var (name, crc, pool, koerper) in Szenarien)
        {
            Assert.True(kennungen.Add(name), "doppeltes Szenario " + name);
            if (nur.Length > 0 && !nur.Contains(name)) continue;
            using var aufnahme = new TlsAufnahme(host, port, "localhost");
            SqlConnection Oeffnen(int c, bool p)
            {
                var v = new SqlConnection(new SqlConnectionStringBuilder
                {
                    DataSource = $"tcp:127.0.0.1,{aufnahme.Port}",
                    UserID = ReferenzZiel.Benutzer,
                    Password = kennwort,
                    Encrypt = SqlConnectionEncryptOption.Mandatory,
                    TrustServerCertificate = true,
                    Pooling = p,
                    MaxPoolSize = 1,
                    ConnectRetryCount = c,
                    ConnectTimeout = 30,
                    CommandTimeout = 60,
                    InitialCatalog = Datenbank,
                }.ConnectionString);
                try
                {
                    v.Open();
                }
                catch (SqlException e)
                {
                    _ausgabe.WriteLine($"messung: client {name} Open scheitert: Number {e.Number}; Proxy: weitere Verbindungen {aufnahme.WeitereVerbindungen}, erste endete: {aufnahme.Ende ?? "laeuft"}, Problem: {aufnahme.Problem ?? "-"}");
                    throw;
                }

                return v;
            }

            var beobachtungen = new List<string>();
            var lauf = new Lauf(name, Oeffnen, beobachtungen.Add, () => $"weitere Verbindungen {aufnahme.WeitereVerbindungen}, erste endete: {aufnahme.Ende ?? "laeuft"}");
            string packetSize;
            using (var verbindung = Oeffnen(crc, pool))
            {
                packetSize = verbindung.PacketSize.ToString(System.Globalization.CultureInfo.InvariantCulture);
                koerper(lauf, verbindung);
                Assert.Null(aufnahme.Problem);
                var nachrichten = aufnahme.Anhalten();

                var datei = new MessDatei { Szenario = name };
                datei.Herkunft.Add(new("instanz", Herkunft("TDSWIRE_HERKUNFT_INSTANZ")));
                datei.Herkunft.Add(new("image", Herkunft("TDSWIRE_HERKUNFT_IMAGE")));
                datei.Herkunft.Add(new("client", $"Microsoft.Data.SqlClient {typeof(SqlConnection).Assembly.GetCustomAttribute<AssemblyInformationalVersionAttribute>()?.InformationalVersion?.Split('+')[0]} auf {RuntimeInformation.FrameworkDescription}"));
                datei.Herkunft.Add(new("schema", "testschema.sql " + Herkunft("TDSWIRE_HERKUNFT_SCHEMA") + ", Aufbau der Messreihe " + AufbauHash()));
                datei.Herkunft.Add(new("methode", $"TLS-beendender Proxy im Container des Clients, Encrypt=Mandatory, TLS 1.2, ConnectRetryCount={crc}, Pooling={(pool ? "an" : "aus")}, PacketSize {packetSize}; LOGIN7 redigiert (Rechnername, Benutzer, Kennwort, Servername, SSPI, ClientID, Prozess-ID)"));
                datei.Herkunft.Add(new("aufnahme", DateTime.UtcNow.ToString("yyyy-MM-dd", System.Globalization.CultureInfo.InvariantCulture)));
                // Der Servername (Name des Containers) steht in ERROR und INFO; er gehoert der Instanz, nicht dem Verhalten.
                datei.Nachrichten.AddRange(nachrichten.Select(n => n.Richtung == 's' ? n with { Daten = Redaktion.NameErsetzen(n.Daten, servername) } : n));
                Assert.True(datei.Nachrichten.Count >= 4, $"{name}: weniger als vier Nachrichten");
                Assert.Contains(datei.Nachrichten, n => n.Typ == TdsPaket.Login7);
                Assert.True(datei.Nachrichten.Sum(n => n.Daten.Length) <= 256 * 1024, $"{name}: die Messung ist groesser als 256 KiB");
                Assert.Null(datei.Treffer(kennwort, servername, rechner));
                var text = datei.Serialisieren();
                _ = MessDatei.Lesen(text);
                File.WriteAllText(Path.Combine(ausgabe, name + ".messung"), text, new UTF8Encoding(false));
                foreach (var b in beobachtungen) _ausgabe.WriteLine("messung: client " + b);
                foreach (var (kennung, wert) in MessAuswertung.Zeilen(datei))
                    _ausgabe.WriteLine($"messung: {kennung}\t{(wert.Length > 1500 ? wert[..1500] + "..." : wert)}");
            }

            SqlConnection.ClearAllPools();
        }
    }
}
