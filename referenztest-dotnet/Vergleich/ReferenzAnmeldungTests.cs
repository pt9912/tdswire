using System.Data;
using Microsoft.Data.SqlClient;
using Xunit;
using Xunit.Abstractions;

namespace TdsWire.Referenztests.Vergleich;

/// <summary>
/// Anmeldung am echten SQL Server (`make vergleichslauf-d`, ADR-0008, ADR-0009): falsches Kennwort und unbekannter
/// Benutzer. Der Referenzclient (Microsoft.Data.SqlClient 7.1.1) beobachtet Number, Class, State und Meldung; die Zeilen
/// `messung: ...` im Bericht sind die Werte, die die Ausnahmeliste (`docs/plan/referenzabgleich-ausnahmen.md`)
/// zitiert. Der Server dieses Repos antwortet auf beide Fälle mit derselben festen Meldung ohne Kennung (SPEC-012);
/// dieser Test misst die Gegenseite. Nicht belegt: der interne State des Servers (am Client nicht sichtbar), das
/// Verhalten bei Drosselung oder Sperre nach mehreren Fehlversuchen, die Protokollfolge auf dem Draht (kein Mitschnitt).
/// </summary>
[Trait("Kategorie", "VergleichAnmeldung")]
public class ReferenzAnmeldungTests
{
    private const string FalschesKennwort = "Falsch-0815-nicht-das-Kennwort";

    private readonly ITestOutputHelper _ausgabe;

    public ReferenzAnmeldungTests(ITestOutputHelper ausgabe) => _ausgabe = ausgabe;

    private static SqlException Abgelehnt(string benutzer, string kennwort) =>
        Assert.Throws<SqlException>(() => ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(benutzer, kennwort)));

    [Fact]
    public void Richtiges_Kennwort_meldet_an()
    {
        using var verbindung = ReferenzZiel.Oeffnen(ReferenzZiel.Tds74(ReferenzZiel.Benutzer, ReferenzZiel.Umgebung("TDSWIRE_REFERENZ_KENNWORT")));

        ReferenzZiel.Messung(_ausgabe, $"Anmeldung sa: ServerVersion '{verbindung.ServerVersion}', PacketSize {verbindung.PacketSize}");
        Assert.Equal(ConnectionState.Open, verbindung.State);
    }

    [Fact]
    public void Falsches_Kennwort_endet_mit_18456_Class_14_State_1_und_nennt_den_Benutzer()
    {
        var ausnahme = Abgelehnt(ReferenzZiel.Benutzer, FalschesKennwort);

        ReferenzZiel.Messung(_ausgabe, "falsches Kennwort für sa: " + ReferenzZiel.Beschreibung(ausnahme));
        Assert.Equal(18456, ausnahme.Number);
        Assert.Equal(14, ausnahme.Class);
        Assert.Equal(1, ausnahme.State);
        Assert.Contains($"'{ReferenzZiel.Benutzer}'", ausnahme.Message);
        Assert.DoesNotContain(FalschesKennwort, ausnahme.Message);
    }

    [Fact]
    public void Unbekannter_Benutzer_endet_mit_18456_Class_14_State_1_und_nennt_den_Benutzer()
    {
        var ausnahme = Abgelehnt(ReferenzZiel.UnbekannterBenutzer, FalschesKennwort);

        ReferenzZiel.Messung(_ausgabe, $"unbekannter Benutzer {ReferenzZiel.UnbekannterBenutzer}: " + ReferenzZiel.Beschreibung(ausnahme));
        Assert.Equal(18456, ausnahme.Number);
        Assert.Equal(14, ausnahme.Class);
        Assert.Equal(1, ausnahme.State);
        Assert.Contains($"'{ReferenzZiel.UnbekannterBenutzer}'", ausnahme.Message);
    }

    [Fact]
    public void Beide_Faelle_unterscheiden_sich_am_Client_nur_im_Benutzernamen_der_Meldung()
    {
        var falsch = Abgelehnt(ReferenzZiel.Benutzer, FalschesKennwort);
        var unbekannt = Abgelehnt(ReferenzZiel.UnbekannterBenutzer, FalschesKennwort);

        var angeglichen = unbekannt.Message.Replace(ReferenzZiel.UnbekannterBenutzer, ReferenzZiel.Benutzer);

        ReferenzZiel.Messung(_ausgabe, $"Vergleich: Number {falsch.Number}/{unbekannt.Number}, Class {falsch.Class}/{unbekannt.Class}, State {falsch.State}/{unbekannt.State}, Meldung gleich nach Ersetzen des Namens: {angeglichen == falsch.Message}");
        Assert.Equal(falsch.Number, unbekannt.Number);
        Assert.Equal(falsch.Class, unbekannt.Class);
        Assert.Equal(falsch.State, unbekannt.State);
        Assert.Equal(falsch.Message, angeglichen);
    }
}
