using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Anmeldemessungen und RPC-Messungen des Referenzclients (Microsoft.Data.SqlClient 7.1.1) am Serverprozess
/// (`make kompatibilitaet KATEGORIE=Anmeldung`, Stufe 5 von `make e2e`, ADR-0019, TDS-QA-01, TDS-QA-03): TDS 8.0
/// (`Encrypt=Strict`) mit SQL-Authentifizierung, Anmeldung mit den längsten Feldern und kleiner Paketgröße, Pooling,
/// `Prepare` (sp_prepexec und Wiederholung, Grenze der Handles), die `SET`-Texte von `SchemaOnly` und `KeyInfo`, Transaktionen und die Größen der RPC-Parametertypen.
/// Die Zeilen `messung: ...` im Bericht sind die Werte, die die Matrix zitiert. Dass beide Pooling-Anfragen auf
/// derselben Sitzung liefen, die SET-Texte und dass keine unbekannte Prozedur (`sp_unprepare`) ankam, stehen nur im Log des Servers; ihre Prüfung liegt in
/// `tools/kompatibilitaetstest.sh`, ein `dotnet test` allein belegt sie nicht. Nicht belegt: Windows-SSPI (nicht
/// lieferbar, ADR-0019); ob ein LOGIN7 in mehreren Paketen ankam (kein Mitschnitt, die Paketzahl ist nicht beobachtet);
/// das RESETCONNECTION-Bit selbst (der Server wertet es nicht aus, beobachtet ist die zweite Nutzung der gepoolten
/// Verbindung); die Paketgröße der Antworten des Servers (der Client nimmt auch Pakete über der ausgehandelten Größe
/// an: der Server mit fester Größe 8000 ließ den Test grün); TLS 1.2 und andere Suiten (die ausgehandelte Version ist
/// nicht beobachtet); ein echter SQL Server.
/// </summary>
[Trait("Kategorie", "Anmeldung")]
public class AnmeldungTests
{
    private readonly ITestOutputHelper _ausgabe;

    public AnmeldungTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static SqlConnectionStringBuilder MitPool()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.Pooling = true;
        zeichenfolge.MaxPoolSize = 1;
        return zeichenfolge;
    }

    [Fact]
    public void Tds80_Strict_mit_SQL_Authentifizierung_meldet_an_und_beantwortet_Batch_und_RPC()
    {
        using var verbindung = Ziel.Oeffnen(Ziel.Tds80());

        Ziel.Messung(_ausgabe, $"TDS 8.0 Strict: ServerVersion '{verbindung.ServerVersion}', PacketSize {verbindung.PacketSize}");
        using var batch = new SqlCommand("SELECT Id, Name FROM person WHERE Id = 1", verbindung);
        using (var leser = batch.ExecuteReader())
        {
            Assert.True(leser.Read());
            Assert.Equal("Ada", leser.GetString(1));
        }

        using var rpc = new SqlCommand("SELECT Id FROM person WHERE Name = @name", verbindung);
        rpc.Parameters.Add("@name", SqlDbType.NVarChar, 40).Value = "Bob";
        Assert.Equal(2, rpc.ExecuteScalar());
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }

    [Fact]
    public void Tds80_Strict_prueft_den_Namen_im_Zertifikat()
    {
        using (Ziel.Oeffnen(Ziel.Tds80()))
        {
            // Gegenprobe: derselbe Aufbau mit dem Namen des Zertifikats gelingt.
        }

        var zeichenfolge = Ziel.Tds80();
        zeichenfolge.HostNameInCertificate = "anders.invalid";

        var ausnahme = Assert.Throws<SqlException>(() => Ziel.Oeffnen(zeichenfolge));

        Ziel.Messung(_ausgabe, "TDS 8.0 Strict mit falschem Namen: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(20, ausnahme.Class);
    }

    [Fact]
    public void Tds74_Mandatory_prueft_den_Namen_im_Zertifikat()
    {
        using (Ziel.Oeffnen(Ziel.Tds74()))
        {
            // Gegenprobe: derselbe Aufbau mit dem Namen des Zertifikats gelingt.
        }

        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.HostNameInCertificate = "anders.invalid";

        var ausnahme = Assert.Throws<SqlException>(() => Ziel.Oeffnen(zeichenfolge));

        Ziel.Messung(_ausgabe, "TDS 7.4 Mandatory mit falschem Namen: " + Ziel.Beschreibung(ausnahme));
        Assert.Equal(20, ausnahme.Class);
    }

    [Fact]
    public void Anmeldung_mit_den_Standardeinstellungen_des_Clients_gelingt()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.ConnectRetryCount = 1;

        using var verbindung = Ziel.Oeffnen(zeichenfolge);

        Ziel.Messung(_ausgabe, "ConnectRetryCount 1 (Standard des Clients): Open gelingt");
        using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, befehl.ExecuteScalar());
    }

    [Fact]
    public void Anmeldung_mit_den_laengsten_Feldern_und_Paketgroesse_512_nimmt_der_Client_an_und_lange_Zeilen_kommen_vollstaendig_an()
    {
        var zeichenfolge = Ziel.Tds74();
        zeichenfolge.ApplicationName = new string('a', 128);
        zeichenfolge.WorkstationID = new string('w', 128);
        zeichenfolge.PacketSize = 512;

        using var verbindung = Ziel.Oeffnen(zeichenfolge);

        Ziel.Messung(_ausgabe, $"PacketSize 512, Felder zu 128 Zeichen: Open gelingt, PacketSize {verbindung.PacketSize}");
        using var befehl = new SqlCommand("SELECT Id, Text FROM breit", verbindung);
        using var leser = befehl.ExecuteReader();
        var zeilen = 0;
        while (leser.Read())
        {
            Assert.Equal(600, leser.GetString(1).Length);
            zeilen++;
        }

        Ziel.Messung(_ausgabe, $"PacketSize 512: {zeilen} Zeilen zu 600 Zeichen gelesen");
        Assert.Equal(10, zeilen);
        Assert.Equal(512, verbindung.PacketSize);
    }

    [Fact]
    public void Pooling_zweite_Nutzung_der_gepoolten_Verbindung_gelingt()
    {
        SqlConnection.ClearAllPools();
        var zeichenfolge = MitPool();

        using (var erste = Ziel.Oeffnen(zeichenfolge))
        using (var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = 9101", erste))
        {
            Assert.Null(befehl.ExecuteScalar());
        }

        using (var zweite = Ziel.Oeffnen(zeichenfolge))
        using (var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = 9102", zweite))
        {
            Assert.Null(befehl.ExecuteScalar());
        }

        Ziel.Messung(_ausgabe, "Pooling: zweite Nutzung der gepoolten Verbindung gelingt (Sitzung: Log des Servers)");
        SqlConnection.ClearAllPools();
    }

    [Fact]
    public void Prepare_fuehrt_wiederholt_mit_anderen_Werten_aus_und_Dispose_laesst_die_Verbindung_nutzbar()
    {
        SqlConnection.ClearAllPools();
        var zeichenfolge = MitPool();
        using (var verbindung = Ziel.Oeffnen(zeichenfolge))
        {
            var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @id", verbindung);
            var parameter = befehl.Parameters.Add("@id", SqlDbType.Int);
            parameter.Value = 1;
            befehl.Prepare();
            var erste = befehl.ExecuteScalar();
            parameter.Value = 2;
            var zweite = befehl.ExecuteScalar();
            parameter.Value = 1;
            var dritte = befehl.ExecuteScalar();
            Ziel.Messung(_ausgabe, $"Prepare, drei Ausfuehrungen mit den Werten 1, 2, 1: Ergebnisse {erste}, {zweite}, {dritte}");
            Assert.Equal(1, erste);
            Assert.Equal(2, zweite);
            Assert.Equal(1, dritte);

            befehl.Dispose();
            Ziel.Messung(_ausgabe, "Dispose des vorbereiteten Befehls: keine Ausnahme, Verbindung " + verbindung.State);
            Assert.Equal(ConnectionState.Open, verbindung.State);
            using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
            Assert.Equal(1, folge.ExecuteScalar());
        }

        using var wieder = Ziel.Oeffnen(zeichenfolge);
        using var danach = new SqlCommand("SELECT Id FROM person WHERE Id = 1", wieder);
        Assert.Equal(1, danach.ExecuteScalar());
        SqlConnection.ClearAllPools();
    }

    [Fact]
    public void Prepare_wiederholte_Ausfuehrung_desselben_Befehls_bleibt_ueber_der_Grenze_der_Handles()
    {
        SqlConnection.ClearAllPools();
        using (var verbindung = Ziel.Oeffnen(MitPool()))
        {
            using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @wiederholung", verbindung);
            var parameter = befehl.Parameters.Add("@wiederholung", SqlDbType.Int);
            befehl.Prepare();
            for (var i = 1; i <= 1100; i++)
            {
                parameter.Value = i % 2 + 1;
                Assert.Equal(i % 2 + 1, befehl.ExecuteScalar());
            }

            Ziel.Messung(_ausgabe, "Prepare, 1100 Ausfuehrungen desselben Befehls mit wechselnden Werten: alle beantwortet, Verbindung " + verbindung.State);
        }

        SqlConnection.ClearAllPools();
    }

    [Fact]
    public void Prepare_je_neuem_Befehl_trifft_auf_der_gepoolten_Sitzung_die_Grenze_der_Handles()
    {
        SqlConnection.ClearAllPools();
        var zeichenfolge = MitPool();
        using (var verbindung = Ziel.Oeffnen(zeichenfolge))
        {
            SqlException? abgewiesen = null;
            var beantwortet = 0;
            while (abgewiesen is null && beantwortet < 1100)
            {
                using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @einzeln", verbindung);
                befehl.Parameters.Add("@einzeln", SqlDbType.Int).Value = 1;
                befehl.Prepare();
                try
                {
                    Assert.Equal(1, befehl.ExecuteScalar());
                    beantwortet++;
                }
                catch (SqlException ausnahme)
                {
                    abgewiesen = ausnahme;
                }
            }

            Ziel.Messung(_ausgabe, $"Prepare je neuem Befehl, danach Dispose: {beantwortet} beantwortet, dann " +
                (abgewiesen is null ? "keine Abweisung" : Ziel.Beschreibung(abgewiesen)) + "; Verbindung " + verbindung.State);
            Assert.Equal(1000, beantwortet);
            Assert.NotNull(abgewiesen);
            Assert.Equal(50010, abgewiesen!.Number);
            Assert.Equal(ConnectionState.Open, verbindung.State);
            using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
            Assert.Equal(1, folge.ExecuteScalar());
        }

        SqlConnection.ClearAllPools();
    }

    [Fact]
    public void SchemaOnly_und_KeyInfo_senden_SET_Texte_die_der_Server_nicht_erkennt()
    {
        using var verbindung = Ziel.Oeffnen();

        using (var befehl = new SqlCommand("SELECT Id, Name FROM person", verbindung))
        {
            var schema = Assert.Throws<SqlException>(() => { befehl.ExecuteReader(CommandBehavior.SchemaOnly).Dispose(); });
            Ziel.Messung(_ausgabe, "SchemaOnly: " + Ziel.Beschreibung(schema));
            Assert.Equal(50001, schema.Number);
        }

        using (var befehl = new SqlCommand("SELECT Id, Name FROM person", verbindung))
        {
            var schluessel = Assert.Throws<SqlException>(() => { befehl.ExecuteReader(CommandBehavior.KeyInfo).Dispose(); });
            Ziel.Messung(_ausgabe, "KeyInfo: " + Ziel.Beschreibung(schluessel));
            Assert.Equal(50001, schluessel.Number);
        }

        Assert.Equal(ConnectionState.Open, verbindung.State);
    }

    [Fact]
    public void Transaktion_beginnen_schliesst_die_Verbindung()
    {
        using var verbindung = Ziel.Oeffnen();

        var ausnahme = Assert.Throws<SqlException>(() => { verbindung.BeginTransaction().Dispose(); });

        Ziel.Messung(_ausgabe, "BeginTransaction: " + Ziel.Beschreibung(ausnahme) + $"; Verbindung danach {verbindung.State}");
        Assert.Equal(0, ausnahme.Number);
        Assert.Equal(20, ausnahme.Class);
        Assert.Equal(ConnectionState.Closed, verbindung.State);
    }

    public static IEnumerable<object[]> Parametertypen() => new[]
    {
        new object[] { SqlDbType.Float, 1.5d, "FLTN 8" },
        new object[] { SqlDbType.Real, 1.5f, "FLTN 4" },
        new object[] { SqlDbType.Money, 1.25m, "MONEYN 8" },
        new object[] { SqlDbType.SmallMoney, 1.25m, "MONEYN 4" },
        new object[] { SqlDbType.DateTime, new DateTime(2026, 10, 31, 13, 45, 59), "DATETIMN 8" },
        new object[] { SqlDbType.SmallDateTime, new DateTime(2026, 10, 31, 13, 45, 0), "DATETIMN 4" },
        new object[] { SqlDbType.UniqueIdentifier, Guid.Parse("00112233-4455-6677-8899-aabbccddeeff"), "GUID 16" },
        new object[] { SqlDbType.DateTimeOffset, new DateTimeOffset(2026, 10, 31, 13, 45, 59, TimeSpan.FromHours(2)), "DATETIMEOFFSETN" },
    };

    [Theory]
    [MemberData(nameof(Parametertypen))]
    public void Parametertypen_ausserhalb_des_Katalogs_werden_gelesen_und_als_RPC_abgewiesen(SqlDbType typ, object wert, string form)
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = @id", verbindung);
        befehl.Parameters.Add("@x", typ).Value = wert;
        befehl.Parameters.Add("@id", SqlDbType.Int).Value = 1;

        var ausnahme = Assert.Throws<SqlException>(() => { befehl.ExecuteScalar(); });

        Ziel.Messung(_ausgabe, $"Parameter {typ} ({form}) vor einem Int-Parameter: " + Ziel.Beschreibung(ausnahme) + $"; Verbindung danach {verbindung.State}");
        Assert.Equal(50002, ausnahme.Number);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, folge.ExecuteScalar());
    }
}
