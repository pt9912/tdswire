using System.Text;
using Xunit;

namespace TdsWire.Referenztests.Mitschnitt;

/// <summary>
/// Belege der Messung von `SchemaOnly`, `KeyInfo` und `GetSchemaTable()` (Slice slice-simulation-schemaonly-keyinfo, Lücke A-8, TDS-FA-11,
/// SPEC-045): die abgelegten Messungen `schemaonly-*`, `keyinfo-*`, `schemaonly-und-keyinfo` und `getschematable-ohne-verhalten` unter
/// <c>Mitschnitt/messung/&lt;2022|2025&gt;</c> (Einzelmessung je Szenario und Instanz vom 2026-11-22, `make vergleichslauf-d LAUF=wire`) gehen
/// durch dieselbe Auswertung wie die Aufnahme (<see cref="MessAuswertung"/>, mit `mitSpaltenform` für Text, Spaltenflags, TABNAME und COLINFO).
/// Die Asserts führen die Aussagen W-26 bis W-36 des Messprotokolls zu A-8 (<c>docs/messungen/referenzabgleich.md</c>): gesendeter Text, Tokenfolge,
/// DONE-Befehle und Zähler, Spaltenflags, TABNAME- und COLINFO-Bytes, SESSIONSTATE, die Schemazeilen von `person` und `typen` (gleich der
/// Abschrift der Referenz-Ergebnisse der Live-Schritte `schema-person`, `schema-typen` und `schema-person-schluessel`, siehe <see cref="Katalog"/>), die Ausnahmen des Clients, die
/// Zahl der Anfragen je Szenario und die Gleichheit beider Instanzen. Nicht Gegenstand der Asserts: Laufzeit, Messsperre und Host der Aufnahme
/// (Angaben des Messprotokolls), die Grenzen des Protokolls (nicht gemessene Formen). Die Tests laufen ohne Referenzinstanz und belegen, dass die
/// abgelegte Messung die Aussage trägt, nicht dass ein anderer Lauf sie wiederholt. Grenze: der Referenzclient ist `Microsoft.Data.SqlClient` 7.1.1;
/// andere Clients senden andere Texte. Zuordnung `@spec SPEC-045` je Test als Kommentar.
/// </summary>
[Trait("Kategorie", "MitschnittEinheit")]
public class MessungSchemaBelegTests
{
    private const string Person = "[Id:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein; Name:nvarchar(laenge=8000,genauigkeit=0,skala=0) nullbar=ja; " +
        "Age:intn(laenge=4,genauigkeit=0,skala=0) nullbar=ja; Score:decimaln(laenge=17,genauigkeit=10,skala=2) nullbar=ja]";

    private const string DoneSchemaOnly =
        "DONE Status MORE, Befehl 0x00ba, Zeilen - ; DONE Status MORE, Befehl 0x00b9, Zeilen - ; DONE Status MORE|COUNT, Befehl 0x00c1, Zeilen 0 ; DONE Status -, Befehl 0x00ba, Zeilen -";

    private static string Verzeichnis(string stufe) => Path.Combine(AppContext.BaseDirectory, "Mitschnitt", "messung", stufe);

    private static MessDatei Datei(string stufe, string szenario) =>
        MessDatei.Lesen(File.ReadAllText(Path.Combine(Verzeichnis(stufe), szenario + ".messung"), Encoding.UTF8));

    private static readonly Dictionary<string, Dictionary<string, string>> Auswertungen = new();

    /// <summary>Die Zeilen der Auswertung mit Text und Spaltenform für ein Szenario einer Instanz.</summary>
    private static Dictionary<string, string> Zeilen(string stufe, string szenario)
    {
        lock (Auswertungen)
        {
            var schluessel = stufe + "/" + szenario;
            if (!Auswertungen.TryGetValue(schluessel, out var zeilen))
                Auswertungen[schluessel] = zeilen = MessAuswertung.Zeilen(Datei(stufe, szenario), mitSpaltenform: true).ToDictionary(z => z.Kennung, z => z.Text);
            return zeilen;
        }
    }

    private static string F(string stufe, string kennung) =>
        Zeilen(stufe, kennung[..kennung.IndexOf('/', StringComparison.Ordinal)]).TryGetValue(kennung, out var wert)
            ? wert
            : throw new KeyNotFoundException($"{stufe}: {kennung}");

    /// <summary>Was der Client sah: je Spalte der Schemazeile `Feld=Wert`, für die Beschreibung des Aufrufs (Zeilen `# beobachtung:`).</summary>
    private static List<Dictionary<string, string>> Schemazeilen(string stufe, string szenario, string beschreibung)
    {
        var vorspann = $"{szenario} {beschreibung}: Spalte ";
        return Datei(stufe, szenario).Herkunft.Where(h => h.Key == "beobachtung" && h.Value.StartsWith(vorspann, StringComparison.Ordinal))
            .Select(h => h.Value[vorspann.Length..].Split("; ").Select(p => p.Split('=', 2)).ToDictionary(p => p[0], p => p[1])).ToList();
    }

    private static string Ausgang(string stufe, string szenario, string beschreibung) =>
        Datei(stufe, szenario).Herkunft.Single(h => h.Key == "beobachtung" && h.Value.StartsWith($"{szenario} {beschreibung}: Mengen ", StringComparison.Ordinal)).Value;

    /// <summary>
    /// Die Schemazeilen im Wortlaut der Ergebnisse der Live-Schritte `schema-*` (`Name:Typ(ColumnSize,NumericPrecision,NumericScale) NULL=AllowDBNull`,
    /// mit Schlüssel dahinter ` Schluessel=IsKey Tabelle=BaseTableName`). Die erwarteten Texte in den Tests sind Abschriften dieser Ergebnisse der Referenz; die Tests
    /// halten sie gegen die gemessene Ablage beider Stufen. Ihre Gleichheit mit den Zeilen `schema-*` in `Vergleich/live-ausnahmen.tsv` ist am 2026-11-22 von Hand
    /// geprüft, vor dem Entfernen der Regeln `schema-person` und `schema-typen`; `schema-person-schluessel` steht dort weiter. Die Datei liegt nicht im
    /// Build-Kontext der Stage `test`, kein Test vergleicht beide.
    /// </summary>
    private static string Katalog(List<Dictionary<string, string>> zeilen, bool mitSchluessel) =>
        string.Join("; ", zeilen.Select(z => $"{z["ColumnName"]}:{z["DataTypeName"]}({z["ColumnSize"]},{z["NumericPrecision"]},{z["NumericScale"]}) NULL={z["AllowDBNull"]}"
            + (mitSchluessel ? $" Schluessel={z["IsKey"]} Tabelle={z["BaseTableName"]}" : string.Empty)));

    private const string KatalogPerson = "Id:int(4,10,255) NULL=False; Name:nvarchar(4000,255,255) NULL=True; Age:int(4,10,255) NULL=True; Score:decimal(17,10,2) NULL=True";

    private const string KatalogTypen =
        "Id:int(4,10,255) NULL=False; Gross:bigint(8,19,255) NULL=True; Flag:bit(1,255,255) NULL=True; Text:nvarchar(4000,255,255) NULL=True; Preis:decimal(17,10,2) NULL=True; " +
        "Tag:date(3,255,255) NULL=True; Uhr:time(5,255,7) NULL=True; Zeit:datetime2(8,255,7) NULL=True";

    private const string KatalogPersonSchluessel =
        "Id:int(4,10,255) NULL=False Schluessel=True Tabelle=person; Name:nvarchar(4000,255,255) NULL=True Schluessel=False Tabelle=person; " +
        "Age:int(4,10,255) NULL=True Schluessel=False Tabelle=person; Score:decimal(17,10,2) NULL=True Schluessel=False Tabelle=person";

    private static readonly string[] Browsefelder = { "IsKey", "BaseTableName", "IsAliased", "IsExpression", "IsHidden" };

    public static IEnumerable<object[]> Stufen => new[] { new object[] { "2022" }, new object[] { "2025" } };

    // ---- SchemaOnly ---------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_sendet_FMTONLY_um_den_Text_und_die_Antwort_ist_COLMETADATA_ohne_Zeilen_mit_DONE_Zaehler_0(string stufe)
    {
        Assert.Equal(" SET FMTONLY OFF; SET FMTONLY ON;SELECT Id, Name, Age, Score FROM person SET FMTONLY OFF;", F(stufe, "schemaonly-spalten/01/anfragetext"));
        Assert.Equal("DONE DONE COLMETADATA DONE DONE", F(stufe, "schemaonly-spalten/01/tokenfolge"));
        Assert.Equal(Person, F(stufe, "schemaonly-spalten/01/colmetadata"));
        Assert.Equal(DoneSchemaOnly, F(stufe, "schemaonly-spalten/01/done"));
        Assert.Equal("anzahl=0 []", F(stufe, "schemaonly-spalten/01/zeilen"));
        Assert.Equal("COLMETADATA Flags [Id=0x0008; Name=0x0009; Age=0x0009; Score=0x0009]", F(stufe, "schemaonly-spalten/01/spaltenform"));

        // Der Stern ergibt dieselbe Antwort wie die Spaltenliste.
        Assert.Equal(" SET FMTONLY OFF; SET FMTONLY ON;SELECT * FROM person SET FMTONLY OFF;", F(stufe, "schemaonly-spalten/03/anfragetext"));
        Assert.Equal(Person, F(stufe, "schemaonly-spalten/03/colmetadata"));
        Assert.Equal(DoneSchemaOnly, F(stufe, "schemaonly-spalten/03/done"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_auf_typen_liefert_acht_Spalten_und_die_Typen_der_Tabelle(string stufe)
    {
        Assert.Equal(
            "[Id:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein; Gross:intn(laenge=8,genauigkeit=0,skala=0) nullbar=ja; Flag:bitn(laenge=1,genauigkeit=0,skala=0) nullbar=ja; " +
            "Text:nvarchar(laenge=8000,genauigkeit=0,skala=0) nullbar=ja; Preis:decimaln(laenge=17,genauigkeit=10,skala=2) nullbar=ja; Tag:date(laenge=3,genauigkeit=0,skala=0) nullbar=ja; " +
            "Uhr:time(laenge=0,genauigkeit=0,skala=7) nullbar=ja; Zeit:datetime2(laenge=0,genauigkeit=0,skala=7) nullbar=ja]",
            F(stufe, "schemaonly-spalten/02/colmetadata"));
        Assert.Equal(DoneSchemaOnly, F(stufe, "schemaonly-spalten/02/done"));
        Assert.Equal(
            "COLMETADATA Flags [Id=0x0008; Gross=0x0009; Flag=0x0009; Text=0x0009; Preis=0x0009; Tag=0x0009; Uhr=0x0009; Zeit=0x0009]",
            F(stufe, "schemaonly-spalten/02/spaltenform"));
        Assert.Equal(KatalogTypen, Katalog(Schemazeilen(stufe, "schemaonly-spalten", "typen, acht Spalten (SchemaOnly)"), mitSchluessel: false));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_mit_Parameter_geht_als_sp_executesql_ohne_abschliessendes_SET_und_antwortet_in_DONEINPROC(string stufe)
    {
        Assert.Equal("= SET FMTONLY OFF; SET FMTONLY ON;SELECT Id, Name FROM person WHERE Id = @i | =@i int", F(stufe, "schemaonly-sonderfaelle/04/anfragetext"));
        Assert.StartsWith("Art 3 Status 0x01: sp_ExecuteSql(#10)", F(stufe, "schemaonly-sonderfaelle/04/anfrage"), StringComparison.Ordinal);
        Assert.Equal("DONEINPROC DONEINPROC COLMETADATA DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "schemaonly-sonderfaelle/04/tokenfolge"));
        Assert.Equal(
            "DONEINPROC Status MORE, Befehl 0x00ba, Zeilen - ; DONEINPROC Status MORE, Befehl 0x00b9, Zeilen - ; DONEINPROC Status MORE|COUNT, Befehl 0x00c1, Zeilen 0 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -",
            F(stufe, "schemaonly-sonderfaelle/04/done"));
        Assert.Equal("anzahl=0 []", F(stufe, "schemaonly-sonderfaelle/04/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_auf_eine_unbekannte_Tabelle_endet_mit_dem_Fehler_208_ohne_abschliessendes_SET_und_ohne_SESSIONSTATE(string stufe)
    {
        Assert.Equal(" SET FMTONLY OFF; SET FMTONLY ON;SELECT Id FROM gibtesnicht SET FMTONLY OFF;", F(stufe, "schemaonly-sonderfaelle/05/anfragetext"));
        Assert.Equal("DONE DONE ERROR DONE", F(stufe, "schemaonly-sonderfaelle/05/tokenfolge"));
        Assert.Equal(
            "Fehler Nummer 208, Class 16, State 1, Meldung 'Invalid object name 'gibtesnicht'.', Server vorhanden, Prozedur '', Zeile 1",
            F(stufe, "schemaonly-sonderfaelle/05/fehler"));
        Assert.Equal(
            "DONE Status MORE, Befehl 0x00ba, Zeilen - ; DONE Status MORE, Befehl 0x00b9, Zeilen - ; DONE Status ERROR, Befehl 0x00fd, Zeilen -",
            F(stufe, "schemaonly-sonderfaelle/05/done"));
        Assert.Contains("Ausnahme bei ExecuteReader: Number 208, Class 16, State 1", Ausgang(stufe, "schemaonly-sonderfaelle", "Tabelle fehlt (SchemaOnly)"), StringComparison.Ordinal);

        // Die Folgeabfrage liefert ihre Zeile, ohne TABNAME und COLINFO: FMTONLY wirkt nicht über den fehlgeschlagenen Stapel hinaus.
        Assert.Equal("COLMETADATA ROW+ DONE", F(stufe, "schemaonly-sonderfaelle/06/tokenfolge"));
        Assert.Equal("anzahl=1 [(1)]", F(stufe, "schemaonly-sonderfaelle/06/zeilen"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_auf_eine_Tabelle_ohne_Schluessel_liefert_dieselbe_Antwortform_wie_mit_Schluessel(string stufe)
    {
        Assert.Equal(
            "[A:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein; B:nvarchar(laenge=20,genauigkeit=0,skala=0) nullbar=ja]",
            F(stufe, "schemaonly-sonderfaelle/03/colmetadata"));
        Assert.Equal("DONE DONE COLMETADATA DONE DONE", F(stufe, "schemaonly-sonderfaelle/03/tokenfolge"));
        Assert.Equal(DoneSchemaOnly, F(stufe, "schemaonly-sonderfaelle/03/done"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Der_Client_fuellt_bei_SchemaOnly_die_Browse_Felder_der_Schemazeile_nicht(string stufe)
    {
        var zeilen = Schemazeilen(stufe, "schemaonly-spalten", "person, vier Spalten (SchemaOnly)");
        Assert.Equal(new[] { "Id", "Name", "Age", "Score" }, zeilen.Select(z => z["ColumnName"]));
        Assert.Equal(new[] { "4", "4000", "4", "17" }, zeilen.Select(z => z["ColumnSize"]));
        Assert.Equal(new[] { "10", "255", "10", "10" }, zeilen.Select(z => z["NumericPrecision"]));
        Assert.Equal(new[] { "255", "255", "255", "2" }, zeilen.Select(z => z["NumericScale"]));
        Assert.Equal(new[] { "False", "True", "True", "True" }, zeilen.Select(z => z["AllowDBNull"]));
        Assert.Equal(new[] { "int", "nvarchar", "int", "decimal" }, zeilen.Select(z => z["DataTypeName"]));
        Assert.Equal(KatalogPerson, Katalog(zeilen, mitSchluessel: false));
        Assert.All(zeilen, z =>
        {
            Assert.Equal("NULL", z["IsKey"]);
            Assert.Equal("NULL", z["BaseTableName"]);
            Assert.Equal("NULL", z["IsHidden"]);
        });
    }

    // ---- KeyInfo ------------------------------------------------------------------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_sendet_NO_BROWSETABLE_um_den_Text_fuehrt_die_Auswahl_aus_und_ergaenzt_TABNAME_und_COLINFO(string stufe)
    {
        Assert.Equal(" SET FMTONLY OFF; SET NO_BROWSETABLE ON;SELECT Id, Name, Age, Score FROM person SET NO_BROWSETABLE OFF;", F(stufe, "keyinfo-spalten/01/anfragetext"));
        Assert.Equal("DONE DONE COLMETADATA TABNAME COLINFO ROW+ DONE DONE", F(stufe, "keyinfo-spalten/01/tokenfolge"));
        Assert.Equal(Person, F(stufe, "keyinfo-spalten/01/colmetadata"));
        Assert.Equal(
            "DONE Status MORE, Befehl 0x00ba, Zeilen - ; DONE Status MORE, Befehl 0x00b9, Zeilen - ; DONE Status MORE|COUNT, Befehl 0x00c1, Zeilen 3 ; DONE Status -, Befehl 0x00ba, Zeilen -",
            F(stufe, "keyinfo-spalten/01/done"));
        Assert.Equal("anzahl=3 [(1,'Ada',36,10.50);(2,'Bob',NULL,7.25);(3,'Cy',36,NULL)]", F(stufe, "keyinfo-spalten/01/zeilen"));
        Assert.Equal(
            "COLMETADATA Flags [Id=0x0008; Name=0x0009; Age=0x0009; Score=0x0009] ; TABNAME Tabellen [person] Bytes 0x01060070006500720073006f006e00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x08 Schluessel; Spalte 2 Tabelle 1 Status 0x00; Spalte 3 Tabelle 1 Status 0x00; Spalte 4 Tabelle 1 Status 0x00] Bytes 0x010108020100030100040100",
            F(stufe, "keyinfo-spalten/01/spaltenform"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_hat_dieselben_Spaltenflags_und_dieselbe_Beschreibung_wie_SchemaOnly_und_wie_ein_Lesen_ohne_Verhalten(string stufe)
    {
        foreach (var nr in new[] { "01", "02", "03" })
            Assert.Equal(F(stufe, $"schemaonly-spalten/{nr}/colmetadata"), F(stufe, $"keyinfo-spalten/{nr}/colmetadata"));
        Assert.Equal(F(stufe, "schemaonly-spalten/01/colmetadata"), F(stufe, "getschematable-ohne-verhalten/01/colmetadata"));
        Assert.Equal(F(stufe, "schemaonly-spalten/01/colmetadata"), F(stufe, "schemaonly-und-keyinfo/01/colmetadata"));
        Assert.Equal(F(stufe, "schemaonly-spalten/01/spaltenform"), F(stufe, "getschematable-ohne-verhalten/01/spaltenform"));
        Assert.Equal("COLMETADATA ROW+ DONE", F(stufe, "getschematable-ohne-verhalten/01/tokenfolge"));
        Assert.Equal("SELECT Id, Name, Age, Score FROM person", F(stufe, "getschematable-ohne-verhalten/01/anfragetext"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_auf_typen_liefert_je_Spalte_einen_COLINFO_Eintrag_und_Zeilen_als_ROW_und_NBCROW(string stufe)
    {
        Assert.Equal("DONE DONE COLMETADATA TABNAME COLINFO ROW+ NBCROW+ DONE DONE", F(stufe, "keyinfo-spalten/02/tokenfolge"));
        Assert.Contains("DONE Status MORE|COUNT, Befehl 0x00c1, Zeilen 2 ;", F(stufe, "keyinfo-spalten/02/done"), StringComparison.Ordinal);
        Assert.Equal(KatalogTypen, Katalog(Schemazeilen(stufe, "keyinfo-spalten", "typen, acht Spalten (KeyInfo)"), mitSchluessel: false));
        Assert.Equal(
            "COLMETADATA Flags [Id=0x0008; Gross=0x0009; Flag=0x0009; Text=0x0009; Preis=0x0009; Tag=0x0009; Uhr=0x0009; Zeit=0x0009] ; TABNAME Tabellen [typen] Bytes 0x01050074007900700065006e00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x08 Schluessel; Spalte 2 Tabelle 1 Status 0x00; Spalte 3 Tabelle 1 Status 0x00; Spalte 4 Tabelle 1 Status 0x00; Spalte 5 Tabelle 1 Status 0x00; " +
            "Spalte 6 Tabelle 1 Status 0x00; Spalte 7 Tabelle 1 Status 0x00; Spalte 8 Tabelle 1 Status 0x00] Bytes 0x010108020100030100040100050100060100070100080100",
            F(stufe, "keyinfo-spalten/02/spaltenform"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_haengt_eine_nicht_ausgewaehlte_Schluesselspalte_als_versteckte_Spalte_ans_Ende(string stufe)
    {
        Assert.Equal(
            "[Name:nvarchar(laenge=8000,genauigkeit=0,skala=0) nullbar=ja; Age:intn(laenge=4,genauigkeit=0,skala=0) nullbar=ja; Id:int(laenge=4,genauigkeit=0,skala=0) nullbar=nein]",
            F(stufe, "keyinfo-spalten/04/colmetadata"));
        Assert.Equal("anzahl=3 [('Ada',36,1);('Bob',NULL,2);('Cy',36,3)]", F(stufe, "keyinfo-spalten/04/zeilen"));
        Assert.Equal(
            "COLMETADATA Flags [Name=0x0009; Age=0x0009; Id=0x0008] ; TABNAME Tabellen [person] Bytes 0x01060070006500720073006f006e00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x00; Spalte 2 Tabelle 1 Status 0x00; Spalte 3 Tabelle 1 Status 0x18 Schluessel|versteckt] Bytes 0x010100020100030118",
            F(stufe, "keyinfo-spalten/04/spaltenform"));
        var zeilen = Schemazeilen(stufe, "keyinfo-spalten", "person, Schluesselspalte nicht ausgewaehlt (KeyInfo)");
        Assert.Equal(new[] { "Name", "Age", "Id" }, zeilen.Select(z => z["ColumnName"]));
        Assert.Equal(new[] { "False", "False", "True" }, zeilen.Select(z => z["IsKey"]));
        Assert.Equal(new[] { "False", "False", "True" }, zeilen.Select(z => z["IsHidden"]));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_kennzeichnet_ohne_Schluessel_keine_Spalte_und_beim_zusammengesetzten_Schluessel_beide_Spalten(string stufe)
    {
        Assert.Equal(
            "COLMETADATA Flags [A=0x0008; B=0x0009] ; TABNAME Tabellen [wire_schema_ohne_k] Bytes 0x01120077006900720065005f0073006300680065006d0061005f006f0068006e0065005f006b00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x00; Spalte 2 Tabelle 1 Status 0x00] Bytes 0x010100020100",
            F(stufe, "keyinfo-sonderfaelle/04/spaltenform"));
        Assert.Equal(
            "COLMETADATA Flags [A=0x0008; B=0x0008; C=0x0009] ; TABNAME Tabellen [wire_schema_zwei_k] Bytes 0x01120077006900720065005f0073006300680065006d0061005f007a007700650069005f006b00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x08 Schluessel; Spalte 2 Tabelle 1 Status 0x08 Schluessel; Spalte 3 Tabelle 1 Status 0x00] Bytes 0x010108020108030100",
            F(stufe, "keyinfo-sonderfaelle/05/spaltenform"));
        Assert.Equal("DONE DONE COLMETADATA TABNAME COLINFO DONE DONE", F(stufe, "keyinfo-sonderfaelle/05/tokenfolge"));
        Assert.All(Schemazeilen(stufe, "keyinfo-sonderfaelle", "Tabelle ohne Schluessel (KeyInfo)"), z => Assert.Equal("False", z["IsKey"]));
        Assert.Equal(new[] { "True", "True", "False" }, Schemazeilen(stufe, "keyinfo-sonderfaelle", "zusammengesetzter Schluessel (KeyInfo)").Select(z => z["IsKey"]));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_traegt_bei_einem_Alias_den_Spaltennamen_im_COLINFO_und_bei_dbo_person_zwei_Namensteile_im_TABNAME(string stufe)
    {
        Assert.Equal(
            "COLMETADATA Flags [Kennung=0x0008; Name=0x0009] ; TABNAME Tabellen [person] Bytes 0x01060070006500720073006f006e00 ; " +
            "COLINFO [Spalte 1 Tabelle 1 Status 0x28 Schluessel|anderer-Name Name 'Id'; Spalte 2 Tabelle 1 Status 0x00] Bytes 0x0101280249006400020100",
            F(stufe, "keyinfo-sonderfaelle/07/spaltenform"));
        var alias = Schemazeilen(stufe, "keyinfo-sonderfaelle", "Spalte mit Alias (KeyInfo)")[0];
        Assert.Equal("Kennung", alias["ColumnName"]);
        Assert.Equal("Id", alias["BaseColumnName"]);
        Assert.Equal("True", alias["IsAliased"]);
        Assert.Equal(
            "COLMETADATA Flags [Id=0x0008] ; TABNAME Tabellen [dbo.person] Bytes 0x020300640062006f00060070006500720073006f006e00 ; COLINFO [Spalte 1 Tabelle 1 Status 0x08 Schluessel] Bytes 0x010108",
            F(stufe, "keyinfo-sonderfaelle/08/spaltenform"));
        Assert.Equal("person", Schemazeilen(stufe, "keyinfo-sonderfaelle", "Tabelle mit Schemaname (KeyInfo)")[0]["BaseTableName"]);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void KeyInfo_ohne_Tabelle_sendet_weder_TABNAME_noch_COLINFO_und_mit_Parameter_gehen_die_SET_in_DONEINPROC(string stufe)
    {
        Assert.Equal("DONE DONE COLMETADATA ROW+ DONE DONE", F(stufe, "keyinfo-sonderfaelle/09/tokenfolge"));
        Assert.Equal("COLMETADATA Flags [eins=0x0020]", F(stufe, "keyinfo-sonderfaelle/09/spaltenform"));
        Assert.Equal("= SET FMTONLY OFF; SET NO_BROWSETABLE ON;SELECT Id, Name FROM person WHERE Id = @i | =@i int", F(stufe, "keyinfo-sonderfaelle/06/anfragetext"));
        Assert.Equal("DONEINPROC DONEINPROC COLMETADATA TABNAME COLINFO ROW+ DONEINPROC RETURNSTATUS DONEPROC", F(stufe, "keyinfo-sonderfaelle/06/tokenfolge"));
        Assert.Equal(
            "DONEINPROC Status MORE, Befehl 0x00ba, Zeilen - ; DONEINPROC Status MORE, Befehl 0x00b9, Zeilen - ; DONEINPROC Status MORE|COUNT, Befehl 0x00c1, Zeilen 1 ; DONEPROC Status -, Befehl 0x00e0, Zeilen -",
            F(stufe, "keyinfo-sonderfaelle/06/done"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Nach_einem_Fehler_in_KeyInfo_bleibt_NO_BROWSETABLE_in_der_Sitzung_und_jede_Folgeabfrage_traegt_TABNAME_und_COLINFO(string stufe)
    {
        Assert.Equal("DONE DONE ERROR SESSIONSTATE DONE", F(stufe, "keyinfo-sonderfaelle/10/tokenfolge"));
        Assert.StartsWith("Fehler Nummer 208, Class 16, State 1,", F(stufe, "keyinfo-sonderfaelle/10/fehler"), StringComparison.Ordinal);
        Assert.Contains("SESSIONSTATE Laenge 16, Folge 1, wiederherstellbar 1, Zustaende [0:9=0x20608114ffe7ffff00]", F(stufe, "keyinfo-sonderfaelle/10/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("Ausnahme bei ExecuteReader: Number 208, Class 16, State 1", Ausgang(stufe, "keyinfo-sonderfaelle", "Tabelle fehlt (KeyInfo)"), StringComparison.Ordinal);
        foreach (var nr in new[] { "11", "12" })
        {
            Assert.Equal("SELECT Id FROM person WHERE Id = 1", F(stufe, $"keyinfo-sonderfaelle/{nr}/anfragetext"));
            Assert.Equal("COLMETADATA TABNAME COLINFO ROW+ DONE", F(stufe, $"keyinfo-sonderfaelle/{nr}/tokenfolge"));
        }
    }

    // ---- SchemaOnly und KeyInfo zusammen, GetSchemaTable ohne Verhalten ----------------------------------------------

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_und_KeyInfo_zusammen_senden_beide_SET_Paare_und_antworten_mit_den_Browse_Tokens_ohne_Zeilen(string stufe)
    {
        Assert.Equal(
            " SET FMTONLY OFF; SET NO_BROWSETABLE ON; SET FMTONLY ON;SELECT Id, Name, Age, Score FROM person SET FMTONLY OFF; SET NO_BROWSETABLE OFF;",
            F(stufe, "schemaonly-und-keyinfo/01/anfragetext"));
        Assert.Equal("DONE DONE DONE COLMETADATA TABNAME COLINFO DONE DONE DONE", F(stufe, "schemaonly-und-keyinfo/01/tokenfolge"));
        Assert.Equal(
            "DONE Status MORE, Befehl 0x00ba, Zeilen - ; DONE Status MORE, Befehl 0x00b9, Zeilen - ; DONE Status MORE, Befehl 0x00b9, Zeilen - ; " +
            "DONE Status MORE|COUNT, Befehl 0x00c1, Zeilen 0 ; DONE Status MORE, Befehl 0x00ba, Zeilen - ; DONE Status -, Befehl 0x00ba, Zeilen -",
            F(stufe, "schemaonly-und-keyinfo/01/done"));
        Assert.Equal("anzahl=0 []", F(stufe, "schemaonly-und-keyinfo/01/zeilen"));
        Assert.Equal("DONE DONE DONE ERROR SESSIONSTATE DONE", F(stufe, "schemaonly-und-keyinfo/02/tokenfolge"));
        Assert.Contains("SESSIONSTATE Laenge 16, Folge 1, wiederherstellbar 1, Zustaende [0:9=0x20608114ffe7ffff00]", F(stufe, "schemaonly-und-keyinfo/02/einzelheiten"), StringComparison.Ordinal);
        Assert.Contains("Ausnahme bei ExecuteReader: Number 208, Class 16, State 1", Ausgang(stufe, "schemaonly-und-keyinfo", "Tabelle fehlt (SchemaOnly, KeyInfo)"), StringComparison.Ordinal);
        Assert.Equal("COLMETADATA TABNAME COLINFO ROW+ DONE", F(stufe, "schemaonly-und-keyinfo/03/tokenfolge"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void Ein_Schluesselstatus_im_COLINFO_und_IsKey_der_Schemazeile_gehoeren_zusammen(string stufe)
    {
        var zeilen = Schemazeilen(stufe, "keyinfo-spalten", "person, vier Spalten (KeyInfo)");
        Assert.Equal(KatalogPersonSchluessel, Katalog(zeilen, mitSchluessel: true));
        Assert.Equal(new[] { "True", "False", "False", "False" }, zeilen.Select(z => z["IsKey"]));
        Assert.All(zeilen, z => Assert.Equal("person", z["BaseTableName"]));
        Assert.All(zeilen, z => Assert.Equal("False", z["IsUnique"]));
        Assert.Equal(new[] { "Id", "Name", "Age", "Score" }, zeilen.Select(z => z["BaseColumnName"]));
        Assert.Equal(Schemazeilen(stufe, "schemaonly-und-keyinfo", "person, vier Spalten (SchemaOnly, KeyInfo)").Select(z => z["IsKey"]), zeilen.Select(z => z["IsKey"]));

        // Ohne Browse-Tokens bleiben die Felder der Schemazeile leer, auch beim Ausdruck ohne Tabelle.
        var ausdruck = Schemazeilen(stufe, "keyinfo-sonderfaelle", "Ausdruck ohne Tabelle (KeyInfo)").Single();
        Assert.Equal("NULL", ausdruck["IsKey"]);
        Assert.Equal("NULL", ausdruck["BaseTableName"]);
        Assert.Equal("NULL", ausdruck["IsExpression"]);
        Assert.Equal("True", ausdruck["IsReadOnly"]);
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void SchemaOnly_und_KeyInfo_unterscheiden_sich_in_der_Schemazeile_allein_in_fuenf_Feldern_und_ohne_Verhalten_ist_sie_gleich_SchemaOnly(string stufe)
    {
        var paare = new[]
        {
            ("schemaonly-spalten", "person, vier Spalten (SchemaOnly)", "keyinfo-spalten", "person, vier Spalten (KeyInfo)"),
            ("schemaonly-spalten", "typen, acht Spalten (SchemaOnly)", "keyinfo-spalten", "typen, acht Spalten (KeyInfo)"),
            ("schemaonly-spalten", "person, Stern (SchemaOnly)", "keyinfo-spalten", "person, Stern (KeyInfo)"),
        };
        foreach (var (szenarioA, beschreibungA, szenarioB, beschreibungB) in paare)
        {
            var schemaOnly = Schemazeilen(stufe, szenarioA, beschreibungA);
            var keyInfo = Schemazeilen(stufe, szenarioB, beschreibungB);
            Assert.NotEmpty(schemaOnly);
            Assert.Equal(schemaOnly.Count, keyInfo.Count);
            Assert.Equal(31, schemaOnly[0].Count);
            var unterschiede = new SortedSet<string>(StringComparer.Ordinal);
            for (var i = 0; i < schemaOnly.Count; i++)
            {
                Assert.Equal(schemaOnly[i].Keys, keyInfo[i].Keys);
                foreach (var feld in schemaOnly[i].Keys.Where(f => schemaOnly[i][f] != keyInfo[i][f])) unterschiede.Add(feld);
                Assert.All(Browsefelder, f => Assert.Equal("NULL", schemaOnly[i][f]));
                Assert.All(Browsefelder, f => Assert.NotEqual("NULL", keyInfo[i][f]));
            }

            Assert.Equal(Browsefelder.OrderBy(f => f, StringComparer.Ordinal), unterschiede);
        }

        // Ohne Verhalten (Default) ist die Schemazeile gleich der von SchemaOnly, in allen Feldern.
        Assert.Equal(
            Schemazeilen(stufe, "schemaonly-spalten", "person, vier Spalten (SchemaOnly)"),
            Schemazeilen(stufe, "getschematable-ohne-verhalten", "person, vier Spalten (Default)"));
    }

    // @spec SPEC-045
    [Theory]
    [MemberData(nameof(Stufen))]
    public void GetSchemaTable_loest_keine_Anfrage_aus_jede_Anfrage_nach_der_Anmeldung_gehoert_zu_einem_Befehl_des_Szenarios(string stufe)
    {
        foreach (var szenario in new[] { "schemaonly-spalten", "schemaonly-sonderfaelle", "keyinfo-spalten", "keyinfo-sonderfaelle", "schemaonly-und-keyinfo", "getschematable-ohne-verhalten" })
        {
            var datei = Datei(stufe, szenario);
            var anfragen = datei.Nachrichten.Skip(4).Count(n => n.Richtung == 'c');
            var befehle = datei.Herkunft.Count(h => h.Key == "beobachtung" && h.Value.Contains(": Mengen ", StringComparison.Ordinal));
            Assert.True(anfragen > 0, szenario);
            Assert.Equal(befehle, anfragen);
        }
    }

    // @spec SPEC-045
    [Fact]
    public void Beide_Instanzen_haben_in_allen_sechs_Szenarien_fuer_jeden_Austausch_nach_der_Anmeldung_dieselben_Auswertungszeilen()
    {
        var auswertungszeilen = 0;
        var beobachtungen = 0;
        foreach (var szenario in new[] { "schemaonly-spalten", "schemaonly-sonderfaelle", "keyinfo-spalten", "keyinfo-sonderfaelle", "schemaonly-und-keyinfo", "getschematable-ohne-verhalten" })
        {
            var a = Zeilen("2022", szenario).Where(z => !z.Key.StartsWith(szenario + "/00/", StringComparison.Ordinal)).OrderBy(z => z.Key, StringComparer.Ordinal).ToList();
            var b = Zeilen("2025", szenario).Where(z => !z.Key.StartsWith(szenario + "/00/", StringComparison.Ordinal)).OrderBy(z => z.Key, StringComparer.Ordinal).ToList();
            Assert.True(a.Count > 20, $"{szenario}: zu wenige Zeilen");
            auswertungszeilen += a.Count;
            beobachtungen += Datei("2022", szenario).Herkunft.Count(h => h.Key == "beobachtung");
            Assert.Equal(a, b);
            Assert.Equal(
                Datei("2022", szenario).Herkunft.Where(h => h.Key == "beobachtung").Select(h => h.Value).ToList(),
                Datei("2025", szenario).Herkunft.Where(h => h.Key == "beobachtung").Select(h => h.Value).ToList());
            foreach (var stufe in new[] { "2022", "2025" })
            {
                var datei = Datei(stufe, szenario);
                Assert.Equal(szenario, datei.Szenario);
                Assert.Equal(TdsPaket.Login7, datei.Nachrichten[2].Typ);
                Assert.Contains(datei.Herkunft, h => h is { Key: "aufnahme", Value: "2026-11-22" });
            }
        }

        // Die Zahlen des Messprotokolls (W-26 bis W-36, Kopf der Spalte „Befund“): je Instanz verglichen.
        Assert.Equal(472, auswertungszeilen);
        Assert.Equal(107, beobachtungen);
    }
}
