using System.Data;
using System.Diagnostics;
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
/// Abbruch und Frist (TDS-FA-07, ADR-0027): die Tests an der Tabelle `laufend` (Zeilenquelle ohne Ende, die Anfrage
/// endet nur durch einen Abbruch) brechen eine laufende Anfrage ab, mit `Cancel()`, `CancellationToken`, dem
/// Command-Timeout des Clients (2 s) und dem Ablauf von `QUERY_TIMEOUT` am Server (5 s, `tools/kompatibilitaetstest.sh`
/// startet den Server dieser Kategorie damit); die unmittelbar folgende Anfrage derselben Verbindung erhält nur ihre
/// eigene Antwort. `tools/kompatibilitaetstest.sh` prüft zusätzlich, dass das Backend je Anfrage genau einen Cancel erhält.
/// Nicht belegt: ob der Client bei `Cancel()` und Command-Timeout zusammen zwei ATTENTION sendet (der Draht ist
/// TLS-verschlüsselt, es gibt keinen Mitschnitt), ATTENTION im Leerlauf, mehrere Anfragen in einem Schreibvorgang (das
/// prüft der Rohbytes-Test `SynchronitaetWiringTest`, nicht der Referenzclient), MARS, Pooling mit einer vom Server
/// geschlossenen Verbindung, Cancel unter TDS 8.0, ein Fehler in einer Folge von Prepared-Handles, ein echter
/// SQL Server.
/// </summary>
[Trait("Kategorie", "Synchronitaet")]
public class SynchronitaetTests
{
    // Kopplung: wie KOMPAT_BROKEN_AFTER und die Zeilen von `person` in KompatibilitaetsServer.kt.
    private const int ZeilenVorDemFehler = 3;
    private const int ZeilenVorDemAbbruch = 50;

    // Kopplung: QUERY_TIMEOUT, mit dem `tools/kompatibilitaetstest.sh` den Server dieser Kategorie startet; der
    // Command-Timeout des Clients liegt darunter, damit sein ATTENTION vor der Frist des Servers kommt.
    private const int FristDesServersSekunden = 5;
    private const int ClientTimeoutSekunden = 2;
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

        using (var batch = new SqlCommand("SELECT 2", verbindung))
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
    // Verbindung ebenfalls schließt, sieht dieser Test nicht, das prüft `SynchronitaetWiringTest` am Socket. Den Fehler
    // der Class 20 liefert ein `xml`-Parameter (`TDS_UNSUPPORTED_TYPE`, Number 50003).
    [Fact]
    public void Ein_Fehler_der_Class_20_laesst_die_naechste_Anfrage_derselben_Verbindung_ohne_Antwort()
    {
        using var verbindung = Ziel.Oeffnen();
        using (var fehler = new SqlCommand("SELECT Id FROM person WHERE Name = @x", verbindung))
        {
            fehler.Parameters.Add("@x", SqlDbType.Xml).Value = "<a>1</a>";
            var ausnahme = Assert.Throws<SqlException>(() => { fehler.ExecuteScalar(); });
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

    // Die Tests zu Abbruch und Frist lesen `laufend`: eine Quelle ohne Ende (`LaufendeQuelle.kt`), die Anfrage läuft also,
    // bis sie abgebrochen wird. Dass sie beim Abbruch läuft, belegen die gelesenen Zeilen.

    /// <summary>Liest bis zu <paramref name="anzahl"/> Zeilen und gibt zurück, wie viele angekommen sind.</summary>
    private static int LiesZeilen(SqlDataReader leser, int anzahl)
    {
        var gelesen = 0;
        while (gelesen < anzahl && leser.Read()) gelesen++;
        return gelesen;
    }

    /// <summary>Liest den Rest eines abgebrochenen Stroms; gibt Zahl der Zeilen und die Ausnahme zurück (`null` ohne), begrenzt auf 30 s.</summary>
    private static (int Zeilen, SqlException? Ausnahme) LiesBisZumEnde(SqlDataReader leser)
    {
        var uhr = Stopwatch.StartNew();
        var zeilen = 0;
        try
        {
            while (leser.Read())
            {
                zeilen++;
                Assert.True(uhr.Elapsed < TimeSpan.FromSeconds(30), "der Strom endet nach dem Abbruch nicht");
            }

            return (zeilen, null);
        }
        catch (SqlException e)
        {
            return (zeilen, e);
        }
    }

    [Fact]
    public void Cancel_waehrend_des_Streamings_laesst_jede_Folgeabfrage_nur_ihre_Antwort_erhalten()
    {
        using var verbindung = Ziel.Oeffnen();

        for (var runde = 1; runde <= 3; runde++)
        {
            using (var befehl = new SqlCommand("SELECT Id, Text FROM laufend", verbindung))
            using (var leser = befehl.ExecuteReader())
            {
                Assert.Equal(ZeilenVorDemAbbruch, LiesZeilen(leser, ZeilenVorDemAbbruch));
                befehl.Cancel();
                var (zeilen, ausnahme) = LiesBisZumEnde(leser);
                Ziel.Messung(_ausgabe, $"Cancel() nach {ZeilenVorDemAbbruch} Zeilen, Runde {runde}: danach {zeilen} Zeilen gelesen, Ausgang: {(ausnahme == null ? "Strom endet ohne Ausnahme" : Ziel.Beschreibung(ausnahme))}");
                // Gemessen mit SqlClient 7.1.1: der Client beendet das Lesen selbst mit Number 0, Class 11.
                Assert.NotNull(ausnahme);
                Assert.Equal((0, (byte)11), (ausnahme!.Number, ausnahme.Class));
            }

            Assert.Equal(ConnectionState.Open, verbindung.State);
            ErwarteEigeneAntwort(verbindung, runde);
        }
    }

    [Fact]
    public async Task CancellationToken_bricht_die_laufende_Anfrage_ab_und_die_Folgeabfrage_erhaelt_nur_ihre_Antwort()
    {
        using var verbindung = Ziel.Oeffnen();
        using var quelle = new CancellationTokenSource();
        var gelesen = 0;
        Exception? ausnahme = null;
        var uhr = Stopwatch.StartNew();

        try
        {
            using var befehl = new SqlCommand("SELECT Id, Text FROM laufend", verbindung);
            using var leser = await befehl.ExecuteReaderAsync(quelle.Token);
            while (await leser.ReadAsync(quelle.Token))
            {
                gelesen++;
                if (gelesen == ZeilenVorDemAbbruch) quelle.Cancel();
                Assert.True(gelesen < ZeilenVorDemAbbruch + 100_000, "der Strom endet nach dem Abbruch nicht");
            }
        }
        catch (Exception e) when (e is SqlException or OperationCanceledException)
        {
            ausnahme = e;
        }

        var ausgang = ausnahme is SqlException s ? Ziel.Beschreibung(s) : ausnahme == null ? "Strom endet ohne Ausnahme" : ausnahme.GetType().Name + ": " + ausnahme.Message;
        Ziel.Messung(_ausgabe, $"CancellationToken nach {ZeilenVorDemAbbruch} Zeilen: {gelesen} Zeilen insgesamt, Ausgang: {ausgang}; Zustand {verbindung.State}");
        // Gemessen mit SqlClient 7.1.1: ReadAsync endet mit TaskCanceledException, ohne weitere Zeile.
        Assert.IsAssignableFrom<OperationCanceledException>(ausnahme);
        Assert.Equal(ZeilenVorDemAbbruch, gelesen);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteEigeneAntwort(verbindung, 2);
        // Die Folgeabfrage erhält ihre Antwort erst nach dem Abbruch am Server; vor der Frist des Servers heißt, dass das
        // ATTENTION des Clients ihn auslöste und nicht der Ablauf der Frist.
        Assert.True(uhr.Elapsed < TimeSpan.FromSeconds(FristDesServersSekunden));
    }

    [Fact]
    public void Command_Timeout_des_Clients_bricht_die_laufende_Anfrage_vor_der_Frist_des_Servers_ab()
    {
        using var verbindung = Ziel.Oeffnen();
        var gelesen = 0;
        SqlException? ausnahme = null;
        var uhr = Stopwatch.StartNew();

        using (var befehl = new SqlCommand("SELECT Id, Text FROM laufend", verbindung) { CommandTimeout = ClientTimeoutSekunden })
        {
            try
            {
                using var leser = befehl.ExecuteReader();
                while (leser.Read())
                {
                    gelesen++;
                    Assert.True(uhr.Elapsed < TimeSpan.FromSeconds(30), "der Strom endet durch keine Frist");
                }
            }
            catch (SqlException e)
            {
                ausnahme = e;
            }
        }

        var bisZumTimeout = uhr.ElapsedMilliseconds;
        Assert.NotNull(ausnahme);
        // Gemessen mit SqlClient 7.1.1: Number -2, Class 11; vor dem Ablauf kam keine Zeile an.
        Assert.Equal((-2, (byte)11), (ausnahme!.Number, ausnahme.Class));
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteEigeneAntwort(verbindung, 3);
        uhr.Stop();
        Ziel.Messung(_ausgabe, $"Command-Timeout {ClientTimeoutSekunden} s (Frist des Servers {FristDesServersSekunden} s): {gelesen} Zeilen in {bisZumTimeout} ms, Ausgang: {Ziel.Beschreibung(ausnahme)}; Folgeabfrage beantwortet nach {uhr.ElapsedMilliseconds} ms; Zustand {verbindung.State}");
        // Die Folgeabfrage erhält ihre Antwort erst, wenn der Server den Abbruch bestätigt hat: vor der Frist des Servers
        // heißt, dass ATTENTION des Clients ihn auslöste und nicht der Ablauf der Frist.
        Assert.True(uhr.Elapsed < TimeSpan.FromSeconds(FristDesServersSekunden));
    }

    [Fact]
    public void Ablauf_der_Frist_am_Server_kommt_als_SqlException_und_die_Verbindung_bleibt_nutzbar()
    {
        using var verbindung = Ziel.Oeffnen();
        var gelesen = 0;
        var uhr = Stopwatch.StartNew();

        var ausnahme = Assert.Throws<SqlException>(() =>
        {
            using var befehl = new SqlCommand("SELECT Id, Text FROM laufend", verbindung);
            using var leser = befehl.ExecuteReader();
            while (leser.Read()) gelesen++;
        });

        uhr.Stop();
        Ziel.Messung(_ausgabe, $"Frist des Servers {FristDesServersSekunden} s: {gelesen} Zeilen in {uhr.ElapsedMilliseconds} ms, " + Ziel.Beschreibung(ausnahme) + $"; Zustand {verbindung.State}");
        // Gemessen mit SqlClient 7.1.1: die Zeilen vor der Frist kommen an, dann Number 50005 (TDS_TIMEOUT), Class 16.
        Assert.True(gelesen > 0);
        Assert.Equal((50005, (byte)16), (ausnahme.Number, ausnahme.Class));
        Assert.InRange(uhr.Elapsed.TotalSeconds, FristDesServersSekunden - 1, FristDesServersSekunden + 5);
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteEigeneAntwort(verbindung, 1);
    }

    [Fact]
    public void Cancel_und_Command_Timeout_kurz_hintereinander_lassen_die_Folgeabfrage_nur_ihre_Antwort_erhalten()
    {
        using var verbindung = Ziel.Oeffnen();
        var runde = 0;

        // Der Command-Timeout von 1 s läuft ab, während `Cancel()` aus einem zweiten Thread um die Versätze davor und
        // danach kommt; ob der Client dabei zwei ATTENTION sendet, ist am Draht nicht beobachtet (TLS, kein Mitschnitt).
        foreach (var versatzMillis in new[] { -300, -100, 0, 100, 300 })
        {
            runde++;
            string ausgang;
            using var befehl = new SqlCommand("SELECT Id, Text FROM laufend", verbindung) { CommandTimeout = 1 };
            var abbrecher = new Thread(() =>
            {
                Thread.Sleep(1000 + versatzMillis);
                befehl.Cancel();
            });
            abbrecher.Start();

            try
            {
                using var leser = befehl.ExecuteReader();
                var zeilen = 0;
                while (leser.Read()) zeilen++;
                ausgang = $"Strom endet ohne Ausnahme nach {zeilen} Zeilen";
            }
            catch (SqlException e)
            {
                ausgang = Ziel.Beschreibung(e);
            }

            abbrecher.Join();
            Ziel.Messung(_ausgabe, $"Cancel() {versatzMillis} ms um den Command-Timeout von 1 s, Runde {runde}: {ausgang}; Zustand {verbindung.State}");
            Assert.Equal(ConnectionState.Open, verbindung.State);
            ErwarteEigeneAntwort(verbindung, runde % 3 + 1);
        }
    }

    [Fact]
    public void Cancel_eines_beendeten_Befehls_aendert_die_naechste_Antwort_nicht()
    {
        using var verbindung = Ziel.Oeffnen();
        using var befehl = new SqlCommand("SELECT Id FROM person WHERE Id = 1", verbindung);
        Assert.Equal(1, befehl.ExecuteScalar());

        befehl.Cancel();

        Ziel.Messung(_ausgabe, "Cancel() eines beendeten Befehls: Zustand " + verbindung.State + "; ob der Client dabei ATTENTION sendet, ist nicht beobachtet");
        Assert.Equal(ConnectionState.Open, verbindung.State);
        ErwarteEigeneAntwort(verbindung, 2);
        Assert.Equal(1, befehl.ExecuteScalar());
    }
}
