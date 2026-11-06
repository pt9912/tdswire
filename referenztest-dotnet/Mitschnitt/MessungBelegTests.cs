using System.Text;
using System.Text.RegularExpressions;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Belege der Messreihe am Draht (slice-qa-vergleichslauf-d-messungen, SPEC-045): die abgelegten Messungen unter
/// <c>Mitschnitt/messung/&lt;2022|2025&gt;</c> (aufgenommen mit <c>make vergleichslauf-d LAUF=wire</c>, Einzelmessung je Szenario und
/// Instanz) gehen durch dieselbe Auswertung wie die Aufnahme (<see cref="MessAuswertung"/>); jede Aussage des Messprotokolls
/// (<c>docs/messungen/referenzabgleich.md</c>, Abschnitt Messreihe am Draht) hat hier eine Zeile. Die Tests laufen ohne Referenzinstanz
/// in der Dockerfile-Stage `test`: sie belegen, dass die abgelegte Messung die Aussage trägt, nicht dass der Server sie in einem
/// anderen Lauf wiederholt. Zuordnung `@spec SPEC-045` je Test als Kommentar.
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class MessungBelegTests
{
    private static string Verzeichnis(string stufe) => Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "messung", stufe);

    private static readonly Dictionary<string, Dictionary<string, string>> Auswertungen = new();

    private static Dictionary<string, string> Z(string stufe)
    {
        lock (Auswertungen)
        {
            if (Auswertungen.TryGetValue(stufe, out var vorhanden)) return vorhanden;
            var zeilen = new Dictionary<string, string>();
            foreach (var pfad in Directory.GetFiles(Verzeichnis(stufe), "*.messung").Order(StringComparer.Ordinal))
            {
                var datei = MessDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
                foreach (var (kennung, text) in MessAuswertung.Zeilen(datei)) zeilen.Add(kennung, text);
            }

            Auswertungen[stufe] = zeilen;
            return zeilen;
        }
    }

    private static string F(string stufe, string kennung) =>
        Z(stufe).TryGetValue(kennung, out var wert) ? wert : throw new KeyNotFoundException($"{stufe}: {kennung}");

    public static IEnumerable<object[]> Stufen => new[] { new object[] { "2022" }, new object[] { "2025" } };

    // ---- Ablage -----------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Ablage_hat_22_Messungen_mit_Anmeldung_und_alle_Auswertungen_ohne_Zerlegungsproblem(string stufe)
    {
        var dateien = Directory.GetFiles(Verzeichnis(stufe), "*.messung");
        Assert.Equal(22, dateien.Length);
        Assert.DoesNotContain(Z(stufe), z => z.Value.Contains("PROBLEM", StringComparison.Ordinal) || z.Value.Contains("ZERLEGUNG-ABGEBROCHEN", StringComparison.Ordinal));
        foreach (var pfad in dateien)
        {
            var datei = MessDatei.Lesen(File.ReadAllText(pfad, Encoding.UTF8));
            Assert.Equal(TdsPaket.Prelogin, datei.Nachrichten[0].Typ);
            Assert.Equal(TdsPaket.Login7, datei.Nachrichten[2].Typ);
            Assert.Contains(datei.Herkunft, h => h.Key == "image" && h.Value.Contains("@sha256:", StringComparison.Ordinal));
            Assert.Contains(datei.Herkunft, h => h.Key == "methode" && h.Value.Contains("ConnectRetryCount=", StringComparison.Ordinal));
            Assert.Equal(Path.GetFileNameWithoutExtension(pfad), datei.Szenario);
        }
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Ablage_enthaelt_in_LOGIN7_nur_x_an_Stelle_von_Rechner_Benutzer_Kennwort_Server_und_keine_ClientID(string stufe)
    {
        foreach (var pfad in Directory.GetFiles(Verzeichnis(stufe), "*.messung"))
        {
            var text = File.ReadAllText(pfad, Encoding.UTF8);
            var datei = MessDatei.Lesen(text);
            var login = datei.Nachrichten.Single(n => n.Typ == TdsPaket.Login7).Daten;
            Assert.True(login.Length > Login7Redaktion.FesterTeil, pfad);
            foreach (var (angabe, verschleiert) in new[] { (36, false), (40, false), (44, true), (52, false) })
            {
                var offset = login[angabe] | (login[angabe + 1] << 8);
                var zeichen = login[angabe + 2] | (login[angabe + 3] << 8);
                Assert.True(zeichen > 0, $"{pfad}: Feld {angabe} leer");
                var feld = login[offset..(offset + (zeichen * 2))];
                if (verschleiert) feld = feld.Select(b => (byte)(((b ^ 0xA5) << 4) | ((b ^ 0xA5) >> 4))).ToArray();
                Assert.Equal(new string('x', zeichen), Encoding.Unicode.GetString(feld));
            }

            Assert.All(login[72..78], b => Assert.Equal(0, b));
            Assert.All(login[16..24], b => Assert.Equal(0, b));
            // Namen der Umgebung des Laufs: Kennwort-Vorspann und Namen der Container stehen in keiner Form in der Datei.
            foreach (var muster in new[] { "Tdswire-Ref", "tdswire-vgl" })
            {
                Assert.False(Redaktion.Enthaelt(Encoding.UTF8.GetBytes(text), muster), pfad);
                foreach (var n in datei.Nachrichten)
                {
                    Assert.False(Redaktion.Enthaelt(n.Daten, muster), $"{pfad}: {muster}");
                    Assert.DoesNotContain(muster, Encoding.Unicode.GetString(n.Daten), StringComparison.OrdinalIgnoreCase);
                    Assert.DoesNotContain(muster, Encoding.UTF8.GetString(n.Daten), StringComparison.OrdinalIgnoreCase);
                }
            }

            // Der Name des Servers und des Rechners ist eine Kennung aus 12 Hex-Zeichen (Container); kein Text einer Nachricht trägt eine.
            foreach (var n in datei.Nachrichten)
                Assert.DoesNotMatch(new Regex("(?<![0-9a-f])[0-9a-f]{12}(?![0-9a-f])", RegexOptions.IgnoreCase), Encoding.Unicode.GetString(n.Daten));
        }
    }

    // ---- Punkt 1 (a): Fehler im RPC ---------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Fehler_vor_Zeilen_und_batch_abbrechender_Fehler_im_sp_executesql_enden_mit_ERROR_RETURNSTATUS_DONEPROC_mit_Fehlerbit(string stufe)
    {
        foreach (var szenario in new[] { "rpc-fehler-vor-zeilen", "rpc-fehler-batch-abbrechend" })
        {
            Assert.Equal("ERROR RETURNSTATUS DONEPROC", F(stufe, szenario + "/01/tokenfolge"));
            Assert.Equal("DONEPROC Status ERROR, Befehl 0x00e0, Zeilen -", F(stufe, szenario + "/01/done"));
            Assert.Equal("RETURNSTATUS 208", F(stufe, szenario + "/01/rueckgabe"));
            // Die Folgeabfrage auf derselben Verbindung gelingt.
            Assert.Equal("COLMETADATA ROW+ DONE", F(stufe, szenario + "/02/tokenfolge"));
        }
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Fehler_nach_Zeilen_im_sp_executesql_endet_mit_DONEINPROC_MORE_ERROR_und_DONEPROC_ohne_Fehlerbit(string stufe)
    {
        Assert.Equal("COLMETADATA ROW+ ERROR DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "rpc-fehler-nach-zeilen/01/tokenfolge"));
        Assert.Equal("DONEINPROC Status MORE|ERROR, Befehl 0x00c1, Zeilen - ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "rpc-fehler-nach-zeilen/01/done"));
        Assert.Equal("RETURNSTATUS 8134", F(stufe, "rpc-fehler-nach-zeilen/01/rueckgabe"));
        Assert.Equal("anzahl=3 [(1,'zeile');(2,'zeile');(3,'zeile')]", F(stufe, "rpc-fehler-nach-zeilen/01/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Statement_abbrechender_Fehler_im_sp_executesql_laeuft_weiter_und_endet_mit_RETURNSTATUS_0(string stufe)
    {
        Assert.Equal("COLMETADATA ERROR DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "rpc-fehler-statement-abbrechend/01/tokenfolge"));
        Assert.Equal("DONEINPROC Status MORE|ERROR, Befehl 0x00c1, Zeilen - ; DONEINPROC Status MORE|COUNT, Befehl 0x00c1, Zeilen 1 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "rpc-fehler-statement-abbrechend/01/done"));
        Assert.Equal("RETURNSTATUS 0", F(stufe, "rpc-fehler-statement-abbrechend/01/rueckgabe"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Fehler_nach_dem_Ausgabewert_liefert_RETURNSTATUS_und_RETURNVALUE_trotz_Fehler(string stufe)
    {
        Assert.Equal("DONEINPROC COLMETADATA ERROR DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC", F(stufe, "rpc-fehler-ausgabewert/01/tokenfolge"));
        Assert.Equal("RETURNSTATUS 8134 ; RETURNVALUE Ordinal 2, Name '@o', Status 1, Typ intn, Wert 1", F(stufe, "rpc-fehler-ausgabewert/01/rueckgabe"));
        Assert.EndsWith("DONEINPROC Status MORE|ERROR, Befehl 0x00c1, Zeilen - ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "rpc-fehler-ausgabewert/01/done"), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Fehler_in_einer_Prozedur_traegt_den_Prozedurnamen_und_RETURNSTATUS_minus_6(string stufe)
    {
        Assert.Contains("Prozedur 'wire_proc_fehler1'", F(stufe, "proc-fehler-statement-abbrechend/01/fehler"), StringComparison.Ordinal);
        Assert.Equal("RETURNSTATUS -6", F(stufe, "proc-fehler-statement-abbrechend/01/rueckgabe"));
        Assert.Equal("COLMETADATA ROW+ DONEINPROC ERROR DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "proc-fehler-raiserror/01/tokenfolge"));
        Assert.Contains("DONEINPROC Status MORE|ERROR, Befehl 0x00f6", F(stufe, "proc-fehler-raiserror/01/done"), StringComparison.Ordinal);
        Assert.Equal("RETURNSTATUS -6 ; RETURNVALUE Ordinal 0, Name '@o', Status 1, Typ intn, Wert 5", F(stufe, "proc-fehler-ausgabewert/01/rueckgabe"));
    }

    // ---- Punkt 1 (b): COLMETADATA hinter RETURNVALUE -----------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Kein_Szenario_stellt_COLMETADATA_hinter_RETURNVALUE_und_RETURNVALUE_steht_vor_DONEPROC(string stufe)
    {
        var folgen = Z(stufe).Where(z => z.Key.EndsWith("/tokenfolge", StringComparison.Ordinal)).ToList();
        Assert.True(folgen.Count(z => z.Value.Contains("RETURNVALUE", StringComparison.Ordinal)) >= 12);
        foreach (var (kennung, folge) in folgen.Where(z => z.Value.Contains("RETURNVALUE", StringComparison.Ordinal)))
        {
            var nach = folge[(folge.LastIndexOf("RETURNVALUE", StringComparison.Ordinal) + "RETURNVALUE".Length)..];
            Assert.True(nach.Split("||")[0].TrimEnd() == " DONEPROC", kennung + ": " + folge);
            Assert.DoesNotContain("COLMETADATA", nach, StringComparison.Ordinal);
        }

        // Das Ergebnis nach der Zuweisung steht vor RETURNSTATUS, in sp_executesql, in der Prozedur und in der geschachtelten Prozedur.
        Assert.Equal("COLMETADATA ROW+ DONEINPROC DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC", F(stufe, "colmetadata-hinter-returnvalue/01/tokenfolge"));
        Assert.Equal("COLMETADATA ROW+ DONEINPROC DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC", F(stufe, "colmetadata-hinter-returnvalue/02/tokenfolge"));
        Assert.Equal("DONEINPROC COLMETADATA ROW+ DONEINPROC DONEINPROC COLMETADATA ROW+ DONEINPROC RETURNSTATUS RETURNVALUE DONEPROC", F(stufe, "colmetadata-hinter-returnvalue/03/tokenfolge"));
        Assert.Equal("RETURNSTATUS 0 ; RETURNVALUE Ordinal 0, Name '@o', Status 1, Typ intn, Wert 7", F(stufe, "colmetadata-hinter-returnvalue/03/rueckgabe"));
    }

    // ---- Punkt 1 (c): Ordinal der RETURNVALUE -------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Das_Ordinal_der_RETURNVALUE_ist_die_Position_in_der_Aufrufliste_und_die_Reihenfolge_die_der_Liste(string stufe)
    {
        var erwartet = new (string Kennung, string Aufruf, string Rueckgabe)[]
        {
            ("ordinal-executesql/01", "[2]@a OUTPUT int NULL | [3]@w int 9 | [4]@b OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 1 ; RETURNVALUE Ordinal 4, Name '@b', Status 1, Typ intn, Wert 2"),
            ("ordinal-executesql/02", "[2]@a OUTPUT int NULL | [3]@w int 9", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 1"),
            ("ordinal-executesql/03", "[2]@w int 9 | [3]@a OUTPUT int NULL | [4]@b OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 3, Name '@a', Status 1, Typ intn, Wert 1 ; RETURNVALUE Ordinal 4, Name '@b', Status 1, Typ intn, Wert 2"),
            ("ordinal-executesql/04", "[2]@a OUTPUT int NULL | [3]@b OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 1 ; RETURNVALUE Ordinal 3, Name '@b', Status 1, Typ intn, Wert 2"),
            ("ordinal-executesql/05", "[2]@a OUTPUT int NULL | [3]@w int 9", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 3"),
            ("ordinal-executesql/06", "[2]@a OUTPUT int 4 | [3]@b OUTPUT int NULL | [4]@w int 9", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 1 ; RETURNVALUE Ordinal 3, Name '@b', Status 1, Typ intn, Wert 2"),
            ("ordinal-prepexec/01", "[3]@w int 9 | [4]@a OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 0, Name '', Status 1, Typ intn, Wert <vorhanden> ; RETURNVALUE Ordinal 4, Name '@a', Status 1, Typ intn, Wert 1"),
            ("ordinal-prepexec/02", "[1]@w int 9 | [2]@a OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 2, Name '@a', Status 1, Typ intn, Wert 1"),
            ("ordinal-prozedur/01", "[0]@a OUTPUT int NULL | [1]@w int 9 | [2]@b OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 0, Name '@a', Status 1, Typ intn, Wert 1 ; RETURNVALUE Ordinal 2, Name '@b', Status 1, Typ intn, Wert 2"),
            ("ordinal-prozedur/02", "[0]@a OUTPUT int NULL | [1]@b OUTPUT int NULL", "RETURNSTATUS 0 ; RETURNVALUE Ordinal 0, Name '@a', Status 1, Typ intn, Wert 10 ; RETURNVALUE Ordinal 1, Name '@b', Status 1, Typ intn, Wert 20"),
        };
        foreach (var (kennung, aufruf, rueckgabe) in erwartet)
        {
            Assert.EndsWith(aufruf, F(stufe, kennung + "/anfrage"), StringComparison.Ordinal);
            Assert.Equal(rueckgabe, F(stufe, kennung + "/rueckgabe"));
        }
    }

    // ---- Punkt 1 (c): curCmd ------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Befehlskennung_der_DONE_Familie_ist_je_Anweisungsart_gemessen_und_nie_0_im_Betrieb(string stufe)
    {
        // Batch
        Assert.Equal("DONE Status COUNT, Befehl 0x00c3, Zeilen 2", F(stufe, "dml-batch/01/done"));
        Assert.Equal("DONE Status COUNT, Befehl 0x00c5, Zeilen 2", F(stufe, "dml-batch/02/done"));
        Assert.Equal("DONE Status COUNT, Befehl 0x00c4, Zeilen 2", F(stufe, "dml-batch/03/done"));
        Assert.Equal("DONE Status MORE|COUNT, Befehl 0x00c3, Zeilen 1 ; DONE Status COUNT, Befehl 0x00c1, Zeilen 1", F(stufe, "dml-batch/04/done"));
        Assert.Equal("DONE Status COUNT, Befehl 0x00c4, Zeilen 0", F(stufe, "dml-batch/05/done"));
        Assert.Equal("DONE Status MORE, Befehl 0x00b9, Zeilen - ; DONE Status MORE, Befehl 0x00c3, Zeilen - ; DONE Status -, Befehl 0x00ba, Zeilen -", F(stufe, "dml-batch/06/done"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x00c3, Zeilen 1 ; DONEINPROC Status MORE|COUNT, Befehl 0x00c5, Zeilen 1 ; DONEINPROC Status MORE|COUNT, Befehl 0x00c4, Zeilen 1 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "dml-batch/07/done"));
        Assert.Equal("DONE Status MORE, Befehl 0x00c6, Zeilen - ; DONE Status -, Befehl 0x00c7, Zeilen -", F(stufe, "dml-batch/09/done"));
        Assert.Equal("DONE Status MORE|COUNT, Befehl 0x00c2, Zeilen 3 ; DONE Status -, Befehl 0x00c7, Zeilen -", F(stufe, "dml-batch/10/done"));
        // RPC
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x00c3, Zeilen 2 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "dml-rpc/01/done"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x00c5, Zeilen 3 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "dml-rpc/02/done"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x00c4, Zeilen 3 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "dml-rpc/03/done"));
        Assert.Equal("DONEINPROC Status MORE|COUNT, Befehl 0x00c3, Zeilen 1 ; DONEINPROC Status MORE|COUNT, Befehl 0x00c5, Zeilen 1 ; DONEINPROC Status MORE|COUNT, Befehl 0x00c4, Zeilen 1 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -", F(stufe, "dml-rpc/04/done"));

        // Allgemein: jedes DONE/DONEINPROC/DONEPROC nach der Anmeldung trägt eine Kennung ungleich 0.
        var nachAnmeldung = Z(stufe).Where(z => z.Key.EndsWith("/done", StringComparison.Ordinal) && !z.Key.Contains("/00/", StringComparison.Ordinal));
        foreach (var (kennung, wert) in nachAnmeldung) Assert.DoesNotContain("Befehl 0x0000", wert, StringComparison.Ordinal);
        // Die Anmeldeantwort selbst trägt 0.
        Assert.Equal("DONE Status -, Befehl 0x0000, Zeilen -", F(stufe, "dml-batch/00/done"));
    }

    // ---- Punkt 2: ENVCHANGE bei RESETCONNECTION -----------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_erste_Antwort_nach_RESETCONNECTION_beginnt_mit_ENVCHANGE_Typ_18_ohne_Werte(string stufe)
    {
        var zweite = new[] { "reset-ohne-zustand/02", "reset-datenbank/02", "reset-sprache/02", "reset-mehrfach/02", "reset-mehrfach/03" };
        foreach (var kennung in zweite)
        {
            Assert.Contains("RESETCONNECTION", F(stufe, kennung + "/anfrage"), StringComparison.Ordinal);
            Assert.StartsWith("ENVCHANGE COLMETADATA ROW+ DONE", F(stufe, kennung + "/tokenfolge"), StringComparison.Ordinal);
            Assert.Equal("ENVCHANGE Typ 18, neuer Wert leer, alter Wert leer", F(stufe, kennung + "/envchange"));
            Assert.Equal("ENVCHANGE Typ 18, Rest 0x0000", F(stufe, kennung + "/einzelheiten"));
            Assert.Equal("-", F(stufe, kennung + "/info"));
        }

        // Das Bit steht nur in der ersten Anfrage nach der Wiederverwendung, nicht in der ersten Nutzung.
        foreach (var kennung in new[] { "reset-ohne-zustand/01", "reset-datenbank/01", "reset-sprache/01", "reset-mehrfach/01" })
            Assert.DoesNotContain("RESETCONNECTION", F(stufe, kennung + "/anfrage"), StringComparison.Ordinal);
        Assert.StartsWith("Art 1 Status 0x09 RESETCONNECTION", F(stufe, "reset-mehrfach/03/anfrage"), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Nach_dem_Zuruecksetzen_sind_Datenbank_und_Sprache_die_der_Anmeldung_ohne_weiteren_ENVCHANGE(string stufe)
    {
        Assert.Equal("anzahl=1 [('tdswire_live')]", F(stufe, "reset-datenbank/02/zeilen"));
        Assert.Equal("anzahl=1 [('us_english')]", F(stufe, "reset-sprache/02/zeilen"));
        Assert.Equal("anzahl=1 [('us_english')]", F(stufe, "reset-mehrfach/02/zeilen"));
        Assert.Equal("anzahl=1 [('tdswire_live')]", F(stufe, "reset-mehrfach/03/zeilen"));
        // Vor dem Zurücksetzen hatte der Client Datenbank und Sprache geändert (ENVCHANGE Typ 1 und Typ 2 in der Antwort darauf).
        Assert.StartsWith("ENVCHANGE Typ 1,", F(stufe, "reset-datenbank/01/einzelheiten"), StringComparison.Ordinal);
        Assert.StartsWith("ENVCHANGE Typ 2,", F(stufe, "reset-sprache/01/einzelheiten"), StringComparison.Ordinal);
    }

    // ---- Punkt 3: Anmeldung und SESSIONSTATE --------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Die_Anmeldeantwort_hat_bei_beiden_Einstellungen_dieselbe_Tokenfolge_und_die_Verhandlung_steht_nur_mit_ConnectRetryCount_1_in_LOGIN7(string stufe)
    {
        foreach (var crc in new[] { "crc0", "crc1" })
        {
            Assert.Equal("ENVCHANGE INFO ENVCHANGE ENVCHANGE INFO LOGINACK ENVCHANGE FEATUREEXTACK DONE", F(stufe, $"anmeldung-{crc}/00/tokenfolge"));
            Assert.Equal("DONE Status -, Befehl 0x0000, Zeilen -", F(stufe, $"anmeldung-{crc}/00/done"));
        }

        Assert.DoesNotContain("0x01:0=", F(stufe, "anmeldung-crc0/00/login7"), StringComparison.Ordinal);
        Assert.Contains("0x01:0=", F(stufe, "anmeldung-crc1/00/login7"), StringComparison.Ordinal);
        Assert.DoesNotContain("FEATUREEXTACK Merkmal 0x01", F(stufe, "anmeldung-crc0/00/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("FEATUREEXTACK Merkmal 0x01", F(stufe, "anmeldung-crc1/00/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("ENVCHANGE Typ 1,", F(stufe, "anmeldung-crc1/00/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("Text neu 'tdswire_live', alt 'master'", F(stufe, "anmeldung-crc1/00/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("Info Nummer 5701", F(stufe, "anmeldung-crc1/00/info"), StringComparison.Ordinal);
        Assert.Contains("FEATUREEXTACK Merkmal 0x01, Daten 0x0009", F(stufe, "anmeldung-crc1/00/einzelheiten"), StringComparison.Ordinal);
        foreach (var id in new[] { "0x04", "0x09", "0x0a", "0x0b" })
            Assert.Contains($"FEATUREEXTACK Merkmal {id},", F(stufe, "anmeldung-crc0/00/einzelheiten"), StringComparison.Ordinal);
        // Nur die Instanz 2025 bestätigt zusätzlich die Merkmale 0x0d und 0x0e.
        Assert.Equal(stufe == "2025", F(stufe, "anmeldung-crc0/00/einzelheiten").Contains("FEATUREEXTACK Merkmal 0x0d,", StringComparison.Ordinal));
        Assert.Equal(stufe == "2025", F(stufe, "anmeldung-crc0/00/einzelheiten").Contains("FEATUREEXTACK Merkmal 0x0e,", StringComparison.Ordinal));
        Assert.Equal("1=0x01", string.Join(" ", F(stufe, "anmeldung-crc1/00/prelogin-server").Split(' ').Where(o => o.StartsWith("1=", StringComparison.Ordinal))));
        Assert.Equal("1=0x01", string.Join(" ", F(stufe, "anmeldung-crc1/00/prelogin-client").Split(' ').Where(o => o.StartsWith("1=", StringComparison.Ordinal))));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Ohne_Verhandlung_sendet_keine_Instanz_SESSIONSTATE_mit_Verhandlung_beide_nach_SET(string stufe)
    {
        var ohne = Z(stufe).Where(z => z.Key.StartsWith("sitzung-crc0/", StringComparison.Ordinal) || z.Key.StartsWith("anmeldung-crc0/", StringComparison.Ordinal));
        Assert.All(ohne.Where(z => z.Key.EndsWith("/tokenfolge", StringComparison.Ordinal)), z => Assert.DoesNotContain("SESSIONSTATE", z.Value, StringComparison.Ordinal));
        Assert.Equal("SESSIONSTATE DONE", F(stufe, "sitzung-crc1/01/tokenfolge"));
        Assert.Equal("SESSIONSTATE Laenge 9, Folge 1, wiederherstellbar 1, Zustaende [2:2=0x0702]", F(stufe, "sitzung-crc1/01/einzelheiten"));
        Assert.Equal("ENVCHANGE INFO SESSIONSTATE DONE", F(stufe, "sitzung-crc1/02/tokenfolge"));
        Assert.EndsWith("SESSIONSTATE Laenge 9, Folge 2, wiederherstellbar 1, Zustaende [2:2=0x0102]", F(stufe, "sitzung-crc1/02/einzelheiten"), StringComparison.Ordinal);
        // USE und SELECT lösen keinen SESSIONSTATE aus.
        foreach (var nr in new[] { "03", "04", "05" }) Assert.DoesNotContain("SESSIONSTATE", F(stufe, $"sitzung-crc1/{nr}/tokenfolge"), StringComparison.Ordinal);
    }

    // @spec SPEC-045
    [Fact]
    public void Nach_Cancel_und_in_der_Folgeabfrage_sendet_nur_die_Instanz_2022_SESSIONSTATE()
    {
        Assert.Equal("COLMETADATA ROW+ SESSIONSTATE DONE || DONE", F("2022", "sitzung-crc1/06/tokenfolge"));
        Assert.Equal("SESSIONSTATE Laenge 11, Folge 3, wiederherstellbar 0, Zustaende [254:4=0x03000000]", F("2022", "sitzung-crc1/06/einzelheiten"));
        Assert.Equal("COLMETADATA ROW+ SESSIONSTATE DONE", F("2022", "sitzung-crc1/07/tokenfolge"));
        Assert.Equal("SESSIONSTATE Laenge 11, Folge 4, wiederherstellbar 1, Zustaende [254:4=0x00000000]", F("2022", "sitzung-crc1/07/einzelheiten"));
        Assert.Equal("COLMETADATA ROW+ DONE || DONE", F("2025", "sitzung-crc1/06/tokenfolge"));
        Assert.Equal("-", F("2025", "sitzung-crc1/06/einzelheiten"));
        Assert.Equal("COLMETADATA ROW+ DONE", F("2025", "sitzung-crc1/07/tokenfolge"));
        Assert.Equal("-", F("2025", "sitzung-crc1/07/einzelheiten"));
        // Beide Instanzen: zwei Nachrichten auf das Cancel, DONE[ERROR] und DONE[ATTN].
        foreach (var stufe in new[] { "2022", "2025" })
            Assert.Equal("DONE Status ERROR, Befehl 0x00c1, Zeilen - ; DONE Status ATTN, Befehl 0x00fd, Zeilen -", F(stufe, "sitzung-crc1/06/done"));
    }

    // @spec SPEC-045
    [Fact]
    public void Ausser_SESSIONSTATE_nach_Cancel_sind_alle_Felder_beider_Instanzen_gleich_bis_auf_Version_und_Zufallswerte()
    {
        var a = Z("2022");
        var b = Z("2025");
        Assert.Equal(a.Keys.Order(StringComparer.Ordinal), b.Keys.Order(StringComparer.Ordinal));
        var abweichend = a.Where(x => x.Value != b[x.Key]).Select(x => x.Key)
            .Where(k => !k.Contains("/00/prelogin-", StringComparison.Ordinal) && !k.EndsWith("/00/login7", StringComparison.Ordinal) && !k.EndsWith("/00/einzelheiten", StringComparison.Ordinal))
            .Order(StringComparer.Ordinal).ToList();
        Assert.Equal(new[] { "sitzung-crc1/06/einzelheiten", "sitzung-crc1/06/tokenfolge", "sitzung-crc1/07/einzelheiten", "sitzung-crc1/07/tokenfolge" }, abweichend);
    }
}
