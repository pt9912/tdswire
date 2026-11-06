using System.Data;
using System.Diagnostics;
using System.Globalization;
using System.Reflection;
using System.Text;
using System.Text.RegularExpressions;
using Microsoft.Data.SqlClient;
using Xunit;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Der Live-Lauf des Referenzabgleichs (SPEC-045, `make vergleichslauf-d LAUF=live-2022|live-2025`): dasselbe Szenario läuft
/// nacheinander gegen zwei Ziele, die Umgebung wählt das Ziel (TDSWIRE_ZIEL als host:port, TDSWIRE_LIVE_BENUTZER,
/// TDSWIRE_LIVE_KENNWORT; TDSWIRE_LIVE_AUFBAU=1 legt vorher das Testschema `testschema.sql` an und wählt die Datenbank
/// TDSWIRE_LIVE_DATENBANK, so bei der Referenzinstanz). Je Schritt entsteht eine Zeile `Kennung TAB Ergebnis` in der Datei
/// TDSWIRE_LIVE_AUSGABE; das Skript `tools/vergleichslauf-d.sh` vergleicht die beiden Dateien mit der Ausnahmeliste
/// `live-ausnahmen.tsv`. Dieser Test urteilt nicht über Gleichheit: er besteht, wenn alle Schritte eine Zeile geliefert haben.
/// Das Ergebnis eines Schritts ist, was der Client ausgibt (Spalten, Typnamen, Werte, Ausnahmen mit Nummer, Klasse, State und
/// Text); Dauern und vom Server vergebene Werte (Versionstext, Name des Benutzers in der Meldung) stehen nur als
/// „vorhanden“ oder normiert darin. Nicht belegt: die Tokenfolge und die Statusbits auf dem Draht (dafür der Mitschnitt-Lauf),
/// OUTPUT und RETURNSTATUS von Prozeduren (das Simulations-Backend führt keine), TDS 8.0.
/// </summary>
[Trait("Kategorie", "Live")]
public class LiveSzenarioTests
{
    private const int ZeilenVorDemAbbruch = 50;

    private static readonly CultureInfo Invariant = CultureInfo.InvariantCulture;

    private readonly List<string> _zeilen = new();

    private static string Umgebung(string name) =>
        Environment.GetEnvironmentVariable(name) ?? throw new InvalidOperationException(name + " fehlt");

    private static string? Optional(string name) => Environment.GetEnvironmentVariable(name);

    private static SqlConnectionStringBuilder Zeichenfolge(string benutzer, string kennwort, string? datenbank)
    {
        var ziel = Umgebung("TDSWIRE_ZIEL");
        var trenner = ziel.LastIndexOf(':');
        var zeichenfolge = new SqlConnectionStringBuilder
        {
            DataSource = $"tcp:{ziel[..trenner]},{ziel[(trenner + 1)..]}",
            UserID = benutzer,
            Password = kennwort,
            Encrypt = SqlConnectionEncryptOption.Mandatory,
            HostNameInCertificate = "localhost",
            Pooling = false,
            ConnectRetryCount = 0,
            ConnectTimeout = 15,
            CommandTimeout = 60,
        };
        if (!string.IsNullOrEmpty(datenbank)) zeichenfolge.InitialCatalog = datenbank;
        return zeichenfolge;
    }

    private static string Benutzer => Umgebung("TDSWIRE_LIVE_BENUTZER");

    private static string Kennwort => Umgebung("TDSWIRE_LIVE_KENNWORT");

    private static SqlConnection Oeffnen(string? kennwort = null, string? benutzer = null)
    {
        var verbindung = new SqlConnection(
            Zeichenfolge(benutzer ?? Benutzer, kennwort ?? Kennwort, Optional("TDSWIRE_LIVE_DATENBANK")).ConnectionString);
        verbindung.Open();
        return verbindung;
    }

    // ---- Schema der Referenzinstanz ---------------------------------------------------------------------------------

    private static void SchemaAnlegen()
    {
        var assembly = Assembly.GetExecutingAssembly();
        using var strom = assembly.GetManifestResourceStream("testschema.sql")
            ?? throw new InvalidOperationException("testschema.sql fehlt im Assembly");
        using var leser = new StreamReader(strom, Encoding.UTF8);
        var staepel = Regex.Split(leser.ReadToEnd(), @"^GO\s*$", RegexOptions.Multiline)
            .Where(s => !string.IsNullOrWhiteSpace(s));
        // Ohne InitialCatalog: die Datenbank entsteht in diesem Lauf, `USE` im Skript wechselt in sie.
        using var verbindung = new SqlConnection(Zeichenfolge(Benutzer, Kennwort, null).ConnectionString);
        verbindung.Open();
        foreach (var stapel in staepel)
        {
            using var befehl = new SqlCommand(stapel, verbindung);
            befehl.ExecuteNonQuery();
        }
    }

    // ---- Ergebnis eines Schritts als Text ---------------------------------------------------------------------------

    private static string Wert(object? wert) => wert switch
    {
        null or DBNull => "NULL",
        decimal d => d.ToString(Invariant),
        DateTime t => t.ToString("yyyy-MM-ddTHH:mm:ss.fffffff", Invariant),
        TimeSpan s => s.ToString("c", Invariant),
        bool b => b ? "1" : "0",
        string s => "'" + s + "'",
        _ => Convert.ToString(wert, Invariant) ?? "?",
    };

    private static string Ausnahme(SqlException a)
    {
        var text = a.Message.Replace("\r", " ").Replace("\n", " ").Replace("\t", " ");
        // Der Name des Benutzers in der Meldung gehört dem Server und der Zeile des Benutzers, nicht dem Verhalten.
        text = text.Replace("'" + Benutzer + "'", "'<Benutzer>'").Replace("'tdswire_unbekannt'", "'<Benutzer>'");
        return $"SqlException Number {a.Number}, Class {a.Class}, State {a.State}, Meldung '{text}'";
    }

    private static string Spalten(SqlDataReader leser) => string.Join(
        ",", Enumerable.Range(0, leser.FieldCount).Select(i => $"{leser.GetName(i)}:{leser.GetDataTypeName(i)}/{leser.GetFieldType(i).Name}"));

    private static string Zeile(SqlDataReader leser) => "(" + string.Join(
        ",", Enumerable.Range(0, leser.FieldCount).Select(i => Wert(leser.IsDBNull(i) ? null : leser.GetValue(i)))) + ")";

    /// <summary>Alle Ergebnismengen: Spalten, Zeilenzahl und Zeilen; Teile durch ` || ` getrennt.</summary>
    private static string Lesen(SqlDataReader leser, bool nurZaehlen = false)
    {
        var teile = new List<string>();
        do
        {
            var zeilen = new List<string>();
            while (leser.Read()) zeilen.Add(nurZaehlen ? $"laenge={leser.GetString(1).Length}" : Zeile(leser));
            teile.Add($"spalten=[{Spalten(leser)}] zeilen={zeilen.Count} [{string.Join(";", zeilen)}]");
        }
        while (leser.NextResult());
        return string.Join(" || ", teile);
    }

    private static string Abfrage(SqlConnection verbindung, string text, Action<SqlParameterCollection>? parameter = null, bool nurZaehlen = false)
    {
        using var befehl = new SqlCommand(text, verbindung);
        parameter?.Invoke(befehl.Parameters);
        using var leser = befehl.ExecuteReader();
        return Lesen(leser, nurZaehlen);
    }

    private static string Folgeabfrage(SqlConnection verbindung)
    {
        if (verbindung.State != ConnectionState.Open) return "Verbindung " + verbindung.State;
        try
        {
            using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
            return "Folgeabfrage " + Wert(befehl.ExecuteScalar());
        }
        catch (SqlException a)
        {
            return "Folgeabfrage " + Ausnahme(a);
        }
    }

    /// <summary>Liest bis zu <paramref name="anzahl"/> Zeilen und gibt zurück, wie viele angekommen sind.</summary>
    private static int LiesZeilen(SqlDataReader leser, int anzahl)
    {
        var gelesen = 0;
        while (gelesen < anzahl && leser.Read()) gelesen++;
        return gelesen;
    }

    private void Schritt(string kennung, Func<string> arbeit)
    {
        string ergebnis;
        try
        {
            ergebnis = arbeit();
        }
        catch (SqlException a)
        {
            ergebnis = Ausnahme(a);
        }
        catch (Exception a) when (a is InvalidOperationException or InvalidCastException or IOException)
        {
            ergebnis = "Ausnahme " + a.GetType().Name;
        }

        _zeilen.Add(kennung + "\t" + ergebnis.Replace("\r", " ").Replace("\n", " ").Replace("\t", " "));
    }

    // ---- Das Szenario -----------------------------------------------------------------------------------------------

    [Fact]
    public void Das_Szenario_liefert_je_Schritt_eine_Ausgabezeile()
    {
        if (Optional("TDSWIRE_LIVE_AUFBAU") == "1") SchemaAnlegen();

        Schritt("anmeldung", () =>
        {
            using var v = Oeffnen();
            return $"{v.State}; ServerVersion {(string.IsNullOrEmpty(v.ServerVersion) ? "fehlt" : "vorhanden")}; PacketSize {v.PacketSize}";
        });
        Schritt("anmeldung-falsches-kennwort", () => { using var v = Oeffnen("falsch-" + Kennwort); return "gelungen"; });
        Schritt("anmeldung-unbekannter-benutzer", () => { using var v = Oeffnen(benutzer: "tdswire_unbekannt"); return "gelungen"; });

        Schritt("select-spalten-und-null", () => { using var v = Oeffnen(); return Abfrage(v, "SELECT Id, Name, Age, Score FROM person WHERE Age = 36"); });
        Schritt("select-stern", () => { using var v = Oeffnen(); return Abfrage(v, "SELECT * FROM person WHERE Id = 2"); });
        Schritt("select-leer", () => { using var v = Oeffnen(); return Abfrage(v, "SELECT Id, Name FROM person WHERE Id = 99"); });
        Schritt("select-typen", () =>
        {
            using var v = Oeffnen();
            return Abfrage(v, "SELECT Id, Gross, Flag, Text, Preis, Tag, Uhr, Zeit FROM typen");
        });
        Schritt("select-breit", () => { using var v = Oeffnen(); return Abfrage(v, "SELECT Id, Text FROM breit", nurZaehlen: true); });
        Schritt("parameter-int", () =>
        {
            using var v = Oeffnen();
            return Abfrage(v, "SELECT Id, Name FROM person WHERE Age = @alter", p => p.Add("@alter", SqlDbType.Int).Value = 36);
        });
        Schritt("parameter-text", () =>
        {
            using var v = Oeffnen();
            return Abfrage(v, "SELECT Id FROM person WHERE Name = @name", p => p.Add("@name", SqlDbType.NVarChar, 40).Value = "Bob");
        });
        Schritt("mehrteilig", () =>
        {
            using var v = Oeffnen();
            return Abfrage(v, "SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2");
        });

        Schritt("fehler-vor-zeilen", () => FehlerSchritt("SELECT Id FROM gibtesnicht"));
        Schritt("anweisung-ohne-tabelle", () => FehlerSchritt("SELECT 2"));
        Schritt("bedingung-mit-or", () => FehlerSchritt("SELECT Id FROM person WHERE Id = 1 OR Id = 2"));
        Schritt("fehler-nach-zeilen", () =>
        {
            using var v = Oeffnen();
            using var befehl = new SqlCommand("SELECT Id, Text FROM kaputt", v);
            var gelesen = 0;
            string ausgang;
            try
            {
                using var leser = befehl.ExecuteReader();
                while (leser.Read()) gelesen++;
                ausgang = "keine Ausnahme";
            }
            catch (SqlException a)
            {
                ausgang = Ausnahme(a);
            }

            return $"{gelesen} Zeilen gelesen; {ausgang}; Zustand {v.State}; {Folgeabfrage(v)}";
        });
        Schritt("inputoutput-parameter", () =>
        {
            using var v = Oeffnen();
            using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @id", v);
            var p = befehl.Parameters.Add("@id", SqlDbType.Int);
            p.Direction = ParameterDirection.InputOutput;
            p.Value = 7;
            string ergebnis;
            try
            {
                using var leser = befehl.ExecuteReader();
                while (leser.Read()) { }
                ergebnis = "keine Ausnahme";
            }
            catch (SqlException a)
            {
                ergebnis = Ausnahme(a);
            }

            return $"{ergebnis}; Wert danach {Wert(p.Value)}";
        });

        Schritt("cancel-mit-folgeabfrage", () =>
        {
            using var v = Oeffnen();
            string ausgang;
            using (var befehl = new SqlCommand("SELECT Id, Text FROM laufend", v))
            using (var leser = befehl.ExecuteReader())
            {
                var vorher = LiesZeilen(leser, ZeilenVorDemAbbruch);
                befehl.Cancel();
                var uhr = Stopwatch.StartNew();
                var danach = 0;
                try
                {
                    while (leser.Read())
                    {
                        danach++;
                        if (uhr.Elapsed > TimeSpan.FromSeconds(30)) return "der Strom endet nach dem Abbruch nicht";
                    }

                    ausgang = "Strom endet ohne Ausnahme";
                }
                catch (SqlException a)
                {
                    ausgang = Ausnahme(a);
                }

                ausgang = $"{vorher} Zeilen vor dem Cancel; nach dem Cancel {(danach > 0 ? "weitere Zeilen" : "keine Zeile")}; {ausgang}";
            }

            return $"{ausgang}; Zustand {v.State}; {Folgeabfrage(v)}";
        });
        Schritt("cancel-im-leerlauf", () =>
        {
            using var v = Oeffnen();
            using var fertig = new SqlCommand("SELECT Id FROM person WHERE Id = 1", v);
            var wert = fertig.ExecuteScalar();
            fertig.Cancel();
            using var nie = new SqlCommand("SELECT Id FROM person WHERE Id = 2", v);
            nie.Cancel();
            return $"Cancel nach beendetem und vor nicht gestartetem Befehl ohne Ausnahme; erster Wert {Wert(wert)}; Zustand {v.State}; {Folgeabfrage(v)}";
        });

        Schritt("schema-person", () => SchemaSchritt("person", "Id, Name, Age, Score", CommandBehavior.SchemaOnly));
        Schritt("schema-typen", () => SchemaSchritt("typen", "Id, Gross, Flag, Text, Preis, Tag, Uhr, Zeit", CommandBehavior.SchemaOnly));
        Schritt("schema-person-schluessel", () => SchemaSchritt("person", "Id, Name, Age, Score", CommandBehavior.KeyInfo));

        // Schreibende Schritte zuletzt: die Referenzinstanz behält die Änderungen für den Rest des Laufs.
        Schritt("einfuegen-literal", () => Zeilenzahl("INSERT INTO person (Id, Name) VALUES (11, N'Di')"));
        Schritt("einfuegen-parameter-null", () =>
            Zeilenzahl("INSERT INTO person (Id, Name) VALUES (@id, @name)", p =>
            {
                p.Add("@id", SqlDbType.Int).Value = 10;
                p.Add("@name", SqlDbType.NVarChar, 40).Value = DBNull.Value;
            }));
        Schritt("aendern", () => Zeilenzahl("UPDATE person SET Age = 40 WHERE Id = 1"));
        Schritt("loeschen", () => Zeilenzahl("DELETE FROM person WHERE Id = 3"));

        var ausgabe = Umgebung("TDSWIRE_LIVE_AUSGABE");
        File.WriteAllLines(ausgabe, _zeilen, new UTF8Encoding(false));
        Assert.Equal(25, _zeilen.Count);
    }

    private static string FehlerSchritt(string text)
    {
        using var v = Oeffnen();
        using var befehl = new SqlCommand(text, v);
        string ergebnis;
        try
        {
            befehl.ExecuteReader().Dispose();
            ergebnis = "keine Ausnahme";
        }
        catch (SqlException a)
        {
            ergebnis = Ausnahme(a);
        }

        return $"{ergebnis}; Zustand {v.State}; {Folgeabfrage(v)}";
    }

    private static string Zeilenzahl(string text, Action<SqlParameterCollection>? parameter = null)
    {
        using var v = Oeffnen();
        using var befehl = new SqlCommand(text, v);
        parameter?.Invoke(befehl.Parameters);
        return "ExecuteNonQuery " + befehl.ExecuteNonQuery();
    }

    private static string SchemaSchritt(string tabelle, string spaltenliste, CommandBehavior verhalten)
    {
        using var v = Oeffnen();
        using var befehl = new SqlCommand("SELECT " + spaltenliste + " FROM " + tabelle, v);
        using var leser = befehl.ExecuteReader(verhalten);
        var schema = leser.GetSchemaTable() ?? throw new InvalidOperationException("kein Schema");
        var spalten = schema.Rows.Cast<DataRow>().Select(z =>
        {
            var teile = $"{z["ColumnName"]}:{z["DataTypeName"]}({z["ColumnSize"]},{z["NumericPrecision"]},{z["NumericScale"]}) NULL={z["AllowDBNull"]}";
            return verhalten == CommandBehavior.KeyInfo ? $"{teile} Schluessel={z["IsKey"]} Tabelle={z["BaseTableName"]}" : teile;
        });
        return string.Join("; ", spalten);
    }
}
