using System.Globalization;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Bildet die Antworten eines Austauschs auf die Felder des Vergleichs ab (SPEC-045): eine Zeile `Kennung TAB Ergebnis` je
/// Feld, Kennung `Szenario/NN/Feld`. Beide Seiten (die aufgenommene Antwort der Referenzinstanz und die Antwort des abgespielten
/// Servers) laufen durch dieselbe Abbildung; das Skript `tools/vergleichslauf-d.sh` vergleicht die Dateien mit der Ausnahmeliste.
/// Setzung: bei einem Austausch mit ATTENTION ist die Zahl der Zeilen vor dem Abbruch Sache des Zeitpunkts und steht nur als
/// „ja“ oder „nein“; die Werte der Zeilen stehen dort nicht.
/// </summary>
internal static class Ergebnis
{
    public static IReadOnlyList<(string Kennung, string Text)> Zeilen(string szenario, int nummer, IReadOnlyList<byte[]> antworten, int attention, string? problem = null)
    {
        var teile = antworten.Select(TokenLeser.Lesen).ToList();
        string Gesamt(Func<Antwort, IEnumerable<string>> feld, string trenner)
        {
            var alle = teile.SelectMany(feld).ToList();
            return alle.Count == 0 ? "-" : string.Join(trenner, alle);
        }

        var folge = string.Join(" || ", teile.Select(t => string.Join(" ", t.Folge)));
        var probleme = teile.Where(t => t.Problem != null).Select(t => t.Problem).ToList();
        if (probleme.Count > 0) folge += " PROBLEM: " + string.Join("; ", probleme);
        var anzahl = teile.Sum(t => t.ZeilenAnzahl);
        var zeilen = attention > 0
            ? "vorhanden=" + (anzahl > 0 ? "ja" : "nein")
            : $"anzahl={anzahl.ToString(CultureInfo.InvariantCulture)} [{string.Join(";", teile.SelectMany(t => t.ZeilenWerte))}]";
        string K(string feld) => $"{szenario}/{nummer:D2}/{feld}";
        return new List<(string, string)>
        {
            (K("nachrichten"), antworten.Count.ToString(CultureInfo.InvariantCulture)),
            (K("attention-gesendet"), attention.ToString(CultureInfo.InvariantCulture)),
            (K("attention-quittiert"), teile.Count(t => t.AttnQuittung).ToString(CultureInfo.InvariantCulture)),
            (K("tokenfolge"), folge.Length == 0 ? "-" : folge),
            (K("colmetadata"), Gesamt(t => t.Metadaten, " ; ")),
            (K("zeilen"), zeilen),
            (K("done"), Gesamt(t => t.Fertig, " ; ")),
            (K("fehler"), Gesamt(t => t.Fehler, " ; ")),
            (K("info"), Gesamt(t => t.Info, " ; ")),
            (K("envchange"), Gesamt(t => t.Umgebung, " ; ")),
            (K("rueckgabe"), Gesamt(t => t.Rueckgabe, " ; ")),
            (K("abspielen"), problem ?? "-"),
        };
    }

    /// <summary>Die Zeilen aller Austausche einer Mitschnittdatei, aus den aufgenommenen Antworten der Referenzinstanz.</summary>
    public static IReadOnlyList<(string Kennung, string Text)> AusMitschnitt(MitschnittDatei datei)
    {
        var zeilen = new List<(string, string)>();
        var nummer = 0;
        foreach (var austausch in datei.Austausche())
            zeilen.AddRange(Zeilen(datei.Szenario, ++nummer, austausch.Antworten, austausch.AttentionNach.Count));
        return zeilen;
    }

    public static string Ausgabe(IEnumerable<(string Kennung, string Text)> zeilen) =>
        string.Concat(zeilen.Select(z => z.Kennung + "\t" + z.Text.Replace("\t", " ", StringComparison.Ordinal).Replace("\n", " ", StringComparison.Ordinal) + "\n"));
}
