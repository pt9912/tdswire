using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// `Encrypt=Strict` (TDS 8.0) am echten SQL Server (`make vergleichslauf-d`, ADR-0008): die Erwartung dieser
/// Konfiguration (Image, Einstellung `network.forcestrict`) kommt aus TDSWIRE_ERWARTE_STRICT (`gelingt` oder
/// `scheitert`) und steht im Skript `tools/vergleichslauf-d.sh` neben der Messung, die sie begründet. Der Test ist
/// rot, sobald der Client etwas anderes beobachtet. Das Zertifikat des Servers ist der Vertrauensanker
/// (SSL_CERT_FILE, Name localhost); ein Scheitern liegt damit nicht an einem unbekannten Zertifikat, sondern am
/// Verbindungsaufbau vor der Anmeldung. Nicht belegt: ausgehandelte TLS-Version, Verhalten mit anderen Clients.
/// </summary>
[Trait("Kategorie", "VergleichTds8")]
public class ReferenzTds8Tests
{
    private readonly ITestOutputHelper _ausgabe;

    public ReferenzTds8Tests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    [Fact]
    public void Strict_verhaelt_sich_wie_erwartet() =>
        PruefeVerhalten("Encrypt=Strict", "TDSWIRE_ERWARTE_STRICT", ReferenzZiel.Tds80());

    /// <summary>
    /// Gegenprobe zur Einstellung `network.forcestrict`: ohne sie nimmt der Server `Encrypt=Mandatory` (TDS 7.4) an,
    /// mit ihr lehnt er es ab. Dieser Test belegt, dass die Einstellung wirkt.
    /// </summary>
    [Fact]
    public void Mandatory_verhaelt_sich_wie_erwartet() =>
        PruefeVerhalten("Encrypt=Mandatory", "TDSWIRE_ERWARTE_MANDATORY",
            ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));

    private void PruefeVerhalten(string bezeichnung, string erwartungsVariable, SqlConnectionStringBuilder zeichenfolge)
    {
        var erwartung = ReferenzZiel.Umgebung(erwartungsVariable);
        Assert.Contains(erwartung, new[] { "gelingt", "scheitert" });

        SqlConnection? verbindung = null;
        try
        {
            verbindung = ReferenzZiel.Oeffnen(zeichenfolge);
            ReferenzZiel.Messung(_ausgabe, $"{bezeichnung} gelingt: ServerVersion '{verbindung.ServerVersion}', Zustand {verbindung.State}");
            Assert.Equal("gelingt", erwartung);
            Assert.Equal(ConnectionState.Open, verbindung.State);
        }
        catch (SqlException ausnahme)
        {
            ReferenzZiel.Messung(_ausgabe, $"{bezeichnung} scheitert: " + ReferenzZiel.Beschreibung(ausnahme));
            Assert.Equal("scheitert", erwartung);
        }
        finally
        {
            verbindung?.Dispose();
        }
    }
}
