using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Die Szenarien des Referenzclients (Microsoft.Data.SqlClient 7.1.1) gegen den Serverprozess mit Simulations-Backend
/// (`make kompatibilitaet`, Stufe 4 von `make e2e`, ADR-0019, TDS-QA-01): Anmeldung, SELECT, parametrisierte Abfrage,
/// NULL, Typkatalog, Einfügen/Ändern/Löschen, Fehler vor und nach Zeilen. Jede Verbindung läuft mit TLS in TDS 7.4
/// (Encrypt=Mandatory, Kette und Name geprüft); die Tabellen und Zahlen stehen in `KompatibilitaetsServer.kt`.
/// Nicht belegt: Cancel (steht in `SynchronitaetTests`), mehrteilige Antworten
/// und Rückgabewerte von Ausgabeparametern (das Simulations-Backend liefert sie nicht; zwei Tests messen die
/// Abweisung), ein echter SQL Server.
/// </summary>
[Trait("Kategorie", "Szenario")]
public class SzenarienTests
{
    // Kopplung: wie KOMPAT_BROKEN_AFTER, INSERTED_ROWS, UPDATED_ROWS und DELETED_ROWS in KompatibilitaetsServer.kt.
    private const int ZeilenVorDemFehler = 3;
    private const int Eingefuegt = 1;
    private const int Geaendert = 2;
    private const int Geloescht = 3;

    private readonly ITestOutputHelper _ausgabe;

    public SzenarienTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    [Fact]
    public void Anmeldung_mit_TLS_gelingt_und_die_Verbindung_ist_offen()
    {
        using var verbindung = Ziel.Oeffnen();

        Assert.Equal(ConnectionState.Open, verbindung.State);
        Ziel.Messung(_ausgabe, $"Anmeldung: ServerVersion '{verbindung.ServerVersion}', PacketSize {verbindung.PacketSize}");
        Assert.False(string.IsNullOrEmpty(verbindung.ServerVersion));
    }

    [Fact]
    public void Falsches_Kennwort_und_unbekannter_Benutzer_ergeben_dieselbe_Ablehnung()
    {
        var falsch = Ziel.Tds74();
        falsch.Password = "falsch";
        var unbekannt = Ziel.Tds74();
        unbekannt.UserID = "niemand";

        var ersteAusnahme = Assert.Throws<SqlException>(() => Ziel.Oeffnen(falsch));
        var zweiteAusnahme = Assert.Throws<SqlException>(() => Ziel.Oeffnen(unbekannt));

        Ziel.Messung(_ausgabe, "Ablehnung falsches Kennwort: " + Ziel.Beschreibung(ersteAusnahme));
        Ziel.Messung(_ausgabe, "Ablehnung unbekannter Benutzer: " + Ziel.Beschreibung(zweiteAusnahme));
        Assert.Equal(18456, ersteAusnahme.Number);
        Assert.Equal(ersteAusnahme.Number, zweiteAusnahme.Number);
        Assert.Equal(ersteAusnahme.Class, zweiteAusnahme.Class);
        Assert.Equal(ersteAusnahme.State, zweiteAusnahme.State);
        Assert.Equal(ersteAusnahme.Message, zweiteAusnahme.Message);
        Assert.DoesNotContain("falsch", ersteAusnahme.Message);
        Assert.DoesNotContain("niemand", zweiteAusnahme.Message);
    }

    [Fact]
    public void Auswahl_im_Batch_liefert_Metadaten_Zeilen_und_NULL()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id, Name, Age, Score FROM person WHERE Age = 36", verbindung);

        using var leser = befehl.ExecuteReader();

        Assert.Equal(new[] { "Id", "Name", "Age", "Score" }, Enumerable.Range(0, 4).Select(leser.GetName));
        Assert.True(leser.Read());
        Assert.Equal((1, "Ada", 36, 10.50m), (leser.GetInt32(0), leser.GetString(1), leser.GetInt32(2), leser.GetDecimal(3)));
        Assert.True(leser.Read());
        Assert.Equal(3, leser.GetInt32(0));
        Assert.True(leser.IsDBNull(3));
        Assert.False(leser.Read());
        Assert.False(leser.NextResult());
    }

    [Fact]
    public void Auswahl_mit_Stern_liefert_alle_Spalten_und_NULL_in_Age()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT * FROM person WHERE Id = 2", verbindung);

        using var leser = befehl.ExecuteReader();

        Assert.Equal(4, leser.FieldCount);
        Assert.True(leser.Read());
        Assert.Equal("Bob", leser.GetString(1));
        Assert.True(leser.IsDBNull(2));
        Assert.Equal(7.25m, leser.GetDecimal(3));
    }

    [Fact]
    public void Typkatalog_kommt_in_den_Typen_des_Clients_an_und_NULL_in_jeder_Spalte()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id, Gross, Flag, Text, Preis, Tag, Uhr, Zeit FROM typen", verbindung);

        using var leser = befehl.ExecuteReader();

        var namen = Enumerable.Range(0, leser.FieldCount)
            .Select(i => $"{leser.GetName(i)}={leser.GetDataTypeName(i)}/{leser.GetFieldType(i).Name}");
        Ziel.Messung(_ausgabe, "Typkatalog: " + string.Join(", ", namen));
        Assert.True(leser.Read());
        Assert.Equal(1, leser.GetInt32(0));
        Assert.Equal(9_000_000_000L, leser.GetInt64(1));
        Assert.True(leser.GetBoolean(2));
        Assert.Equal("ü€😀", leser.GetString(3));
        Assert.Equal(12345678.90m, leser.GetDecimal(4));
        Assert.Equal(new DateTime(2026, 10, 31), leser.GetDateTime(5));
        Assert.Equal(new TimeSpan(0, 13, 45, 59, 123), leser.GetFieldValue<TimeSpan>(6));
        Assert.Equal(new DateTime(2026, 10, 31, 13, 45, 59, 123), leser.GetDateTime(7));
        Assert.True(leser.Read());
        Assert.Equal(2, leser.GetInt32(0));
        for (var spalte = 1; spalte < leser.FieldCount; spalte++)
        {
            Assert.True(leser.IsDBNull(spalte), $"Spalte {leser.GetName(spalte)} ist nicht NULL");
        }

        Assert.False(leser.Read());
    }

    [Fact]
    public void Parametrisierte_Abfrage_mit_Int_und_Text_liefert_die_passenden_Zeilen()
    {
        using var verbindung = Ziel.Oeffnen();

        using (var nachAlter = new SqlCommand("SELECT Id, Name FROM person WHERE Age = @alter", verbindung))
        {
            nachAlter.Parameters.Add("@alter", SqlDbType.Int).Value = 36;
            using var leser = nachAlter.ExecuteReader();
            var ids = new List<int>();
            while (leser.Read()) ids.Add(leser.GetInt32(0));
            Assert.Equal(new[] { 1, 3 }, ids);
        }

        using var nachName = new SqlCommand("SELECT Id FROM person WHERE Name = @name", verbindung);
        nachName.Parameters.Add("@name", SqlDbType.NVarChar, 40).Value = "Bob";
        using var leserName = nachName.ExecuteReader();
        Assert.True(leserName.Read());
        Assert.Equal(2, leserName.GetInt32(0));
        Assert.False(leserName.Read());
    }

    [Fact]
    public void Parametrisiertes_Einfuegen_mit_NULL_meldet_die_Zeilenzahl_des_Callbacks()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("INSERT INTO person (Id, Name) VALUES (@id, @name)", verbindung);
        befehl.Parameters.Add("@id", SqlDbType.Int).Value = 10;
        befehl.Parameters.Add("@name", SqlDbType.NVarChar, 40).Value = DBNull.Value;

        var zeilen = befehl.ExecuteNonQuery();

        Ziel.Messung(_ausgabe, $"Einfuegen mit NULL-Parameter: ExecuteNonQuery {zeilen}");
        Assert.Equal(Eingefuegt, zeilen);
    }

    [Fact]
    public void Einfuegen_Aendern_und_Loeschen_im_Batch_melden_die_Zeilenzahlen_der_Callbacks()
    {
        using var verbindung = Ziel.Oeffnen();

        using var einfuegen = new SqlCommand("INSERT INTO person (Id, Name) VALUES (11, N'Di')", verbindung);
        using var aendern = new SqlCommand("UPDATE person SET Age = 40 WHERE Id = 1", verbindung);
        using var loeschen = new SqlCommand("DELETE FROM person WHERE Id = 3", verbindung);

        Assert.Equal(Eingefuegt, einfuegen.ExecuteNonQuery());
        Assert.Equal(Geaendert, aendern.ExecuteNonQuery());
        Assert.Equal(Geloescht, loeschen.ExecuteNonQuery());
    }

    [Fact]
    public void Unbekannte_Tabelle_ist_ein_Fehler_vor_den_Zeilen_und_die_Verbindung_bleibt_offen()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id FROM gibtesnicht", verbindung);

        var ausnahme = Assert.Throws<SqlException>(() => { befehl.ExecuteReader().Dispose(); });

        Ziel.Messung(_ausgabe, "Unbekannte Tabelle: " + Ziel.Beschreibung(ausnahme) + $"; Verbindung danach {verbindung.State}");
        Assert.Equal(208, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, folge.ExecuteScalar());
    }

    [Fact]
    public void Anweisung_ausserhalb_der_erkannten_Formen_ist_ein_Anfragefehler_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT 2", verbindung);

        var ausnahme = Assert.Throws<SqlException>(() => { befehl.ExecuteScalar(); });

        Ziel.Messung(_ausgabe, "Anweisung ausserhalb der Formen: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(50001, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, folge.ExecuteScalar());
    }

    [Fact]
    public void Mehrere_Anweisungen_in_einem_Text_liefern_keine_mehrteilige_Antwort()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand(
            "SELECT Id FROM person WHERE Id = 1; SELECT Id FROM person WHERE Id = 2", verbindung);

        var ausnahme = Assert.Throws<SqlException>(() => { befehl.ExecuteReader().Dispose(); });

        Ziel.Messung(_ausgabe, "Zwei Anweisungen in einem Text: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(50001, ausnahme.Number);
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }

    [Fact]
    public void InputOutput_Parameter_wird_angenommen_und_die_Antwort_traegt_keinen_Rueckgabewert()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @id", verbindung);
        var ausgabe = befehl.Parameters.Add("@id", SqlDbType.Int);
        ausgabe.Direction = ParameterDirection.InputOutput;
        ausgabe.Value = 7;

        var ergebnis = "keine Ausnahme";
        try
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) { }
        }
        catch (SqlException ausnahme)
        {
            ergebnis = Ziel.Beschreibung(ausnahme);
        }

        Ziel.Messung(_ausgabe, $"InputOutput-Parameter @id: {ergebnis}; Wert danach '{ausgabe.Value}'");
        Assert.Equal("keine Ausnahme", ergebnis);
        Assert.Equal(7, ausgabe.Value);
    }

    [Fact]
    public void Fehler_der_Zeilenquelle_kommt_nach_den_Zeilen_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id, Text FROM kaputt", verbindung);
        var gelesen = 0;

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) gelesen++;
        });

        Ziel.Messung(_ausgabe, $"Fehler nach {gelesen} Zeilen: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(ZeilenVorDemFehler, gelesen);
        Assert.Equal(50004, ausnahme.Number);
        Assert.Equal(16, ausnahme.Class);
        Assert.DoesNotContain("Geheimnis", ausnahme.Message);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, folge.ExecuteScalar());
    }
}
