using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Kompatibilitaet;

/// <summary>
/// Die Fehler-Matrix mit Wiederverwendung der Verbindung am Referenzclient (Microsoft.Data.SqlClient 7.1.1) gegen den
/// Serverprozess mit Simulations-Backend (`make kompatibilitaet KATEGORIE=Synchronitaet`, Stufe 7 von `make e2e`,
/// TDS-QA-07, ADR-0007): nach einem Fehler erhält die unmittelbar folgende Anfrage derselben Verbindung nur ihre
/// eigene Antwort, oder die Verbindung ist geschlossen. Jede Antwort der Folgeanfrage ist an ihrem Inhalt erkennbar
/// (Id, Name, Zeilenzahl), nicht nur an der Abwesenheit einer Ausnahme. Jede Verbindung läuft mit TLS in TDS 7.4,
/// ohne Pool, und stellt höchstens eine Anfrage zurzeit; die Tabellen stehen in `KompatibilitaetsServer.kt`.
/// Der Test des verworfenen Lesers und der Zustand `Closed` nach Class 20 beschreiben das Verhalten des Clients in
/// der gepinnten Version (7.1.1: `Dispose` meldet den Fehler der Quelle nicht, der Client schließt bei Class 20 selbst),
/// nicht eine Eigenschaft des Servers.
/// Nicht belegt: Cancel, ATTENTION und Timeout (der Server beantwortet ATTENTION während einer Anfrage nicht,
/// CO-004), mehrere Anfragen in einem Schreibvorgang (das prüft der Rohbytes-Test `SynchronitaetWiringTest`, nicht
/// der Referenzclient), MARS, asynchrone APIs, Pooling mit einer vom Server geschlossenen Verbindung, ein Fehler
/// in einer Folge von Prepared-Handles, ein echter SQL Server.
/// </summary>
[Trait("Kategorie", "Synchronitaet")]
public class SynchronitaetTests
{
    // Kopplung: wie KOMPAT_BROKEN_AFTER und die Zeilen von `person` in KompatibilitaetsServer.kt.
    private const int ZeilenVorDemFehler = 3;
    private static readonly string[] Namen = { "Ada", "Bob", "Cy" };

    private readonly ITestOutputHelper _ausgabe;

    public SynchronitaetTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static void ErwarteEigeneAntwort(SqlConnection verbindung, int id)
    {
        using var befehl = new SqlCommand("SELECT Id, Name FROM person WHERE Id = @id", verbindung);
        befehl.Parameters.Add("@id", SqlDbType.Int).Value = id;
        using var leser = befehl.ExecuteReader();
        Assert.Equal(2, leser.FieldCount);
        Assert.True(leser.Read());
        Assert.Equal((id, Namen[id - 1]), (leser.GetInt32(0), leser.GetString(1)));
        Assert.False(leser.Read());
        Assert.False(leser.NextResult());
    }

    private static int LiesBisZumFehler(SqlConnection verbindung, out SqlException ausnahme)
    {
        var gelesen = 0;
        using var befehl = new SqlCommand("SELECT Id, Text FROM kaputt", verbindung);
        try
        {
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) gelesen++;
            ausnahme = null!;
        }
        catch (SqlException e)
        {
            ausnahme = e;
        }

        return gelesen;
    }

    [Fact]
    public void Eine_Folge_von_Fehlern_nach_Zeilen_und_Erfolgen_ordnet_jede_Antwort_ihrer_Anfrage_zu()
    {
        using var verbindung = Ziel.Oeffnen();

        for (var runde = 0; runde < 6; runde++)
        {
            var gelesen = LiesBisZumFehler(verbindung, out var ausnahme);
            Assert.NotNull(ausnahme);
            Assert.Equal(ZeilenVorDemFehler, gelesen);
            Assert.Equal(50004, ausnahme.Number);
            Assert.Equal(ConnectionState.Open, verbindung.State);

            ErwarteEigeneAntwort(verbindung, runde % 3 + 1);
        }

        Ziel.Messung(_ausgabe, "Sechs Runden Fehler nach 3 Zeilen (50004, Class 16) und Folgeabfrage mit wechselnder Id auf einer Verbindung: jede Antwort die ihrer Anfrage");
    }

    [Fact]
    public void Anfragefehler_im_Batch_und_in_der_RPC_lassen_die_naechste_Anfrage_nur_ihre_Antwort_erhalten()
    {
        using var verbindung = Ziel.Oeffnen();

        using (var batch = new SqlCommand("SELECT 1", verbindung))
        {
            var imBatch = Assert.Throws<SqlException>(() => { batch.ExecuteScalar(); });
            Ziel.Messung(_ausgabe, "Anfragefehler im Batch: " + Ziel.Beschreibung(imBatch));
            Assert.Equal(50001, imBatch.Number);
            Assert.Equal(16, imBatch.Class);
        }

        ErwarteEigeneAntwort(verbindung, 2);

        using (var rpc = new SqlCommand("SELECT 1 + @p", verbindung))
        {
            rpc.Parameters.Add("@p", SqlDbType.Int).Value = 5;
            var inDerRpc = Assert.Throws<SqlException>(() => { rpc.ExecuteScalar(); });
            Ziel.Messung(_ausgabe, "Anfragefehler in der RPC: " + Ziel.Beschreibung(inDerRpc));
            Assert.Equal(50001, inDerRpc.Number);
            Assert.Equal(16, inDerRpc.Class);
            Assert.Equal(ConnectionState.Open, verbindung.State);
        }

        ErwarteEigeneAntwort(verbindung, 3);
        using var batchDanach = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, batchDanach.ExecuteScalar());
    }

    // Der Client schließt bei Class 20 und höher selbst (ADR-0007, Messung S03/S04 am Testserver); dass der Server die
    // Verbindung ebenfalls schließt, sieht dieser Test nicht, das prüft `SynchronitaetWiringTest` am Socket.
    [Fact]
    public void Ein_Fehler_der_Class_20_laesst_die_naechste_Anfrage_derselben_Verbindung_ohne_Antwort()
    {
        using var verbindung = Ziel.Oeffnen();
        using (var fehler = new SqlCommand("SELECT Id FROM gibtesnicht", verbindung))
        {
            var ausnahme = Assert.Throws<SqlException>(() => { fehler.ExecuteReader().Dispose(); });
            Assert.Equal(20, ausnahme.Class);
        }

        object? antwort = null;
        string ergebnis;
        try
        {
            using var folge = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
            antwort = folge.ExecuteScalar();
            ergebnis = "Antwort " + antwort;
        }
        catch (Exception e) when (e is SqlException or InvalidOperationException)
        {
            ergebnis = e.GetType().Name + ": " + e.Message;
        }

        Ziel.Messung(_ausgabe, $"Folgeabfrage nach Class 20 auf derselben SqlConnection: {ergebnis}; Zustand {verbindung.State}");
        Assert.Null(antwort);
        Assert.NotEqual(ConnectionState.Open, verbindung.State);

        using var neu = Ziel.Oeffnen();
        ErwarteEigeneAntwort(neu, 1);
    }

    [Fact]
    public void Ein_vorzeitig_verworfener_Leser_hinter_einem_Zeilenstrom_mit_Fehler_vermischt_keine_Antworten()
    {
        using var verbindung = Ziel.Oeffnen();

        string beimVerwerfen = "keine Ausnahme";
        using (var befehl = new SqlCommand("SELECT Id, Text FROM kaputt", verbindung))
        {
            var leser = befehl.ExecuteReader();
            Assert.True(leser.Read());
            Assert.Equal(1, leser.GetInt32(0));
            try
            {
                leser.Dispose();
            }
            catch (SqlException e)
            {
                beimVerwerfen = Ziel.Beschreibung(e);
            }
        }

        string folge;
        try
        {
            ErwarteEigeneAntwort(verbindung, 2);
            folge = "eigene Antwort";
        }
        catch (Exception e) when (e is SqlException or InvalidOperationException)
        {
            folge = e.GetType().Name + ": " + e.Message;
        }

        Ziel.Messung(_ausgabe, $"Leser nach 1 von 3 Zeilen verworfen: {beimVerwerfen}; Folgeabfrage: {folge}; Zustand {verbindung.State}");
        Assert.Equal("keine Ausnahme", beimVerwerfen);
        Assert.Equal("eigene Antwort", folge);
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }
}
