using System.Text;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Belege der Handvektoren der Antwort auf ATTENTION (ADR-0054, SPEC-029, Messprotokoll des Referenzabgleichs, A-13): die
/// abgelegten Aufnahmen beider Referenzinstanzen (<c>Mitschnitt/aufnahmen/&lt;2022|2025&gt;</c>, ein Lauf je Szenario und
/// Instanz) tragen für ATTENTION im Stream und bei wartendem Befehl zwei Nachrichten, deren DONE-Token Byte für Byte die
/// Konstanten <c>BatchVectors.DONE_ERROR_OPEN</c>, <c>DONE_ERROR</c> und <c>DONE_ATTN</c> der Kotlin-Tests sind
/// (<c>DispatchSupport.kt</c>). Die Tests laufen ohne Referenzinstanz in der Dockerfile-Stage `test`: sie belegen, dass die
/// abgelegte Aufnahme die Aussage trägt, nicht dass die Instanz sie in einem anderen Lauf wiederholt. Zuordnung
/// `@spec` je Test als Kommentar.
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class AbbruchAntwortBelegTests
{
    // DONE (0xFD), Status, Befehl, Zeilenzahl (8 Byte); Handvektoren wie in BatchVectors (Kotlin-Tests).
    private const string DoneErrorOffen = "fd0200c1000000000000000000";
    private const string DoneError = "fd0200fd000000000000000000";
    private const string DoneAttn = "fd2000fd000000000000000000";

    public static IEnumerable<object[]> Instanzen => new[] { new object[] { "2022" }, new object[] { "2025" } };

    private static IReadOnlyList<byte[]> Antworten(string stufe, string szenario)
    {
        var pfad = Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "aufnahmen", stufe, szenario + ".mitschnitt");
        var datei = MitschnittDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
        Assert.Equal(szenario, datei.Szenario);
        var austausch = datei.Austausche()[0];
        Assert.Single(austausch.AttentionNach);
        return austausch.Antworten;
    }

    private static string Hex(byte[] bytes) => Convert.ToHexString(bytes).ToLowerInvariant();

    // @spec SPEC-029
    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Instanzen))]
    public void ATTENTION_im_Stream_ergibt_zwei_Nachrichten_DONE_ERROR_und_DONE_ATTN(string stufe)
    {
        var antworten = Antworten(stufe, "cancel-streaming");
        Assert.Equal(2, antworten.Count);
        Assert.EndsWith(DoneErrorOffen, Hex(antworten[0]), StringComparison.Ordinal);
        Assert.Equal(DoneAttn, Hex(antworten[1]));
        // Die erste Nachricht beginnt mit COLMETADATA (0x81), die zweite besteht aus dem DONE allein (13 Byte).
        Assert.Equal(0x81, antworten[0][0]);
        Assert.Equal(13, antworten[1].Length);
    }

    // @spec SPEC-029
    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Instanzen))]
    public void ATTENTION_bei_wartendem_Befehl_ergibt_zwei_Nachrichten_ohne_ERROR_Token(string stufe)
    {
        foreach (var szenario in new[] { "cancel-wartend", "timeout-wartend", "cancel-kurz-vor-timeout", "cancel-kurz-nach-timeout" })
        {
            var antworten = Antworten(stufe, szenario);
            Assert.Equal(2, antworten.Count);
            // Die erste Nachricht besteht aus DONE[ERROR] (13 Byte, Befehl 0x00fd) und nichts sonst; nur die Instanz 2022 stellt
            // ein SESSIONSTATE (0xE4, 16 Byte, A-18) davor. Die Länge schließt jeden ERROR-Token (0xAA) aus.
            Assert.EndsWith(DoneError, Hex(antworten[0]), StringComparison.Ordinal);
            Assert.Equal(stufe == "2022" ? 16 + 13 : 13, antworten[0].Length);
            if (stufe == "2022") Assert.Equal(0xE4, antworten[0][0]);
            Assert.Equal(DoneAttn, Hex(antworten[1]));
        }
    }

    // @spec SPEC-029
    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Instanzen))]
    public void Die_Felder_des_Vergleichs_nennen_zwei_Nachrichten_mit_DONE_ERROR_und_DONE_ATTN(string stufe)
    {
        var datei = MitschnittDatei.Lesen(File.ReadAllText(
            Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "aufnahmen", stufe, "cancel-streaming.mitschnitt"), Encoding.UTF8));
        var felder = Ergebnis.AusMitschnitt(datei).ToDictionary(z => z.Kennung, z => z.Text);
        Assert.Equal("2", felder["cancel-streaming/01/nachrichten"]);
        Assert.Equal(
            "DONE Status ERROR, Befehl 0x00c1, Zeilen - ; DONE Status ATTN, Befehl 0x00fd, Zeilen -",
            felder["cancel-streaming/01/done"]);
    }
}
