import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Mitschneidender TCP-Durchlauf zwischen einem JDBC-Client (mssql-jdbc, auch ueber d-migrate) und einem SQL Server
 * (Aufruf: tools/vergleichslauf-d.sh, Lauf `jdbc-*`; kein Gate, ADR-0022). Der Durchlauf aendert keine Bytes. Er liest
 * Nachrichten (TDS-Pakete bis zum EOM-Bit) und schreibt je Nachricht eine Zeile `aufnahme: <Verbindung> <Richtung> ...`
 * auf die Standardausgabe:
 * - Client zum Server: SQL-Batch (Text), RPC (ProcID oder Name, Parameter mit Typ und Wert), Massenladen 0x07 (Spalten
 *   mit Typ, Zahl der Zeilen, Hex der Nachricht), Anmeldung und Vorspann (nur Typ und Laenge, nichts aus LOGIN7).
 * - Server zum Client: die Tokenfolge (COLMETADATA mit Spalten, ROW-Zahl und die ersten Zeilenwerte, DONE-Arten mit
 *   Status, Befehl und Zaehler, ERROR/INFO mit Nummer, State, Class, Text, RETURNSTATUS, RETURNVALUE).
 * Dekodiert wird nach der Beschreibung von MS-TDS (Tokens und Typinformationen), soweit die Typen unten vorkommen;
 * ein unbekannter Typ oder Token bricht die Dekodierung der Nachricht mit `unlesbar` und dem Hex-Anfang ab.
 * Die Verbindung muss ohne TLS nach dem Anmeldeschritt laufen (`encrypt=false` im Client, Server ohne erzwungene
 * Verschluesselung); nach dem Anmeldeschritt wird entschluesselt gelesen, davor sind die Pakete TLS-Handschlag.
 * Argumente: <Listenport> <Zielhost> <Zielport>.
 */
public class Aufnahme {
    static final AtomicInteger VERBINDUNGEN = new AtomicInteger();
    static final int MAX_ZEILEN_ANZEIGE = 6;
    static final boolean PAKETE = System.getenv("AUFNAHME_PAKETE") != null && !System.getenv("AUFNAHME_PAKETE").isEmpty();

    static synchronized void zeile(String text) {
        System.out.println("aufnahme: " + text);
        System.out.flush();
    }

    static int le16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    static int le32(byte[] b, int at) {
        return le16(b, at) | le16(b, at + 2) << 16;
    }

    static long le64(byte[] b, int at) {
        return (le32(b, at) & 0xffffffffL) | ((long) le32(b, at + 4)) << 32;
    }

    static String hex(byte[] b, int von, int bis) {
        StringBuilder s = new StringBuilder();
        for (int i = von; i < Math.min(bis, b.length); i++) {
            s.append(String.format("%02x", b[i] & 0xff));
        }
        return s.toString();
    }

    static class Ende extends RuntimeException {
        Ende(String m) {
            super(m, null, false, false);
        }
    }

    /** Lesezeiger auf einer Nachricht. */
    static class Puffer {
        final byte[] b;
        int p;

        Puffer(byte[] b, int p) {
            this.b = b;
            this.p = p;
        }

        boolean ende() {
            return p >= b.length;
        }

        void pruefe(int n) {
            if (p + n > b.length) {
                throw new Ende("Nachricht endet bei " + p + " (+" + n + " von " + b.length + ")");
            }
        }

        int u8() {
            pruefe(1);
            return b[p++] & 0xff;
        }

        int u16() {
            pruefe(2);
            int v = le16(b, p);
            p += 2;
            return v;
        }

        int i32() {
            pruefe(4);
            int v = le32(b, p);
            p += 4;
            return v;
        }

        long i64() {
            pruefe(8);
            long v = le64(b, p);
            p += 8;
            return v;
        }

        byte[] bytes(int n) {
            pruefe(n);
            byte[] r = new byte[n];
            System.arraycopy(b, p, r, 0, n);
            p += n;
            return r;
        }

        String text16(int zeichen) {
            byte[] r = bytes(zeichen * 2);
            return new String(r, StandardCharsets.UTF_16LE);
        }

        String bVarchar() {
            return text16(u8());
        }

        String usVarchar() {
            return text16(u16());
        }
    }

    /** Typinformation einer Spalte oder eines Parameters. */
    static class Typ {
        int id;
        int laenge; // Hoechstlaenge in Byte oder Laengenfeld
        int praezision;
        int skala;
        boolean plp;

        String name() {
            return switch (id) {
                case 0x1f -> "NULL";
                case 0x30 -> "INT1";
                case 0x32 -> "BIT";
                case 0x34 -> "INT2";
                case 0x38 -> "INT4";
                case 0x3a -> "DATETIM4";
                case 0x3b -> "REAL";
                case 0x3c -> "MONEY";
                case 0x3d -> "DATETIME";
                case 0x3e -> "FLOAT";
                case 0x7a -> "MONEY4";
                case 0x7f -> "INT8";
                case 0x24 -> "GUID(" + laenge + ")";
                case 0x26 -> "INTN(" + laenge + ")";
                case 0x68 -> "BITN";
                case 0x6d -> "FLTN(" + laenge + ")";
                case 0x6f -> "DATETIMN(" + laenge + ")";
                case 0x6e -> "MONEYN(" + laenge + ")";
                case 0x6a -> "DECIMALN(" + praezision + "," + skala + ")";
                case 0x6c -> "NUMERICN(" + praezision + "," + skala + ")";
                case 0x28 -> "DATEN";
                case 0x29 -> "TIMEN(" + skala + ")";
                case 0x2a -> "DATETIME2N(" + skala + ")";
                case 0x2b -> "DATETIMEOFFSETN(" + skala + ")";
                case 0xa5 -> "BIGVARBINARY(" + (plp ? "max" : laenge) + ")";
                case 0xa7 -> "BIGVARCHAR(" + (plp ? "max" : laenge) + ")";
                case 0xad -> "BIGBINARY(" + laenge + ")";
                case 0xaf -> "BIGCHAR(" + laenge + ")";
                case 0xe7 -> "NVARCHAR(" + (plp ? "max" : laenge / 2) + ")";
                case 0xef -> "NCHAR(" + laenge / 2 + ")";
                default -> "TYP0x" + Integer.toHexString(id);
            };
        }
    }

    static Typ typInfo(Puffer p) {
        Typ t = new Typ();
        t.id = p.u8();
        switch (t.id) {
            case 0x1f, 0x30, 0x32, 0x34, 0x38, 0x3a, 0x3b, 0x3c, 0x3d, 0x3e, 0x7a, 0x7f, 0x28, 0x68 -> {
                if (t.id == 0x68) {
                    t.laenge = p.u8();
                }
            }
            case 0x24, 0x26, 0x6d, 0x6f, 0x6e -> t.laenge = p.u8();
            case 0x6a, 0x6c -> {
                t.laenge = p.u8();
                t.praezision = p.u8();
                t.skala = p.u8();
            }
            case 0x29, 0x2a, 0x2b -> t.skala = p.u8();
            case 0xa5, 0xad -> {
                t.laenge = p.u16();
                t.plp = t.laenge == 0xffff;
            }
            case 0xa7, 0xaf, 0xe7, 0xef -> {
                t.laenge = p.u16();
                t.plp = t.laenge == 0xffff;
                p.bytes(5); // Kollation
            }
            default -> throw new Ende("Typ 0x" + Integer.toHexString(t.id) + " nicht gelesen");
        }
        return t;
    }

    static int festeLaenge(int id) {
        return switch (id) {
            case 0x1f -> 0;
            case 0x30, 0x32 -> 1;
            case 0x34 -> 2;
            case 0x38, 0x3a, 0x3b, 0x7a -> 4;
            case 0x3c, 0x3d, 0x3e, 0x7f -> 8;
            default -> -1;
        };
    }

    /** Liest einen Wert; Rueckgabe: lesbare Form (NULL als `NULL`). */
    static String wert(Puffer p, Typ t) {
        int fest = festeLaenge(t.id);
        if (fest >= 0) {
            return darstellen(t, p.bytes(fest));
        }
        switch (t.id) {
            case 0x24, 0x26, 0x68, 0x6d, 0x6f, 0x6e, 0x6a, 0x6c, 0x28, 0x29, 0x2a, 0x2b -> {
                int n = p.u8();
                return n == 0 ? "NULL" : darstellen(t, p.bytes(n));
            }
            default -> {
                if (t.plp) {
                    long gesamt = p.i64();
                    if (gesamt == -1L) {
                        return "NULL";
                    }
                    ByteArrayOutputStream o = new ByteArrayOutputStream();
                    while (true) {
                        int n = p.i32();
                        if (n == 0) {
                            break;
                        }
                        o.writeBytes(p.bytes(n));
                    }
                    return darstellen(t, o.toByteArray());
                }
                int n = p.u16();
                return n == 0xffff ? "NULL" : darstellen(t, p.bytes(n));
            }
        }
    }

    static String darstellen(Typ t, byte[] b) {
        switch (t.id) {
            case 0x30, 0x34, 0x38, 0x7f, 0x26 -> {
                long v = 0;
                for (int i = b.length - 1; i >= 0; i--) {
                    v = v << 8 | (b[i] & 0xff);
                }
                int bits = b.length * 8;
                if (bits < 64) {
                    long vorzeichen = 1L << (bits - 1);
                    v = (v ^ vorzeichen) - vorzeichen;
                }
                return Long.toString(v);
            }
            case 0x32, 0x68 -> {
                return b[0] != 0 ? "1" : "0";
            }
            case 0xe7, 0xef -> {
                return "'" + new String(b, StandardCharsets.UTF_16LE) + "'";
            }
            case 0x28 -> {
                long tage = (b[0] & 0xff) | (b[1] & 0xff) << 8 | (b[2] & 0xff) << 16;
                return java.time.LocalDate.of(1, 1, 1).plusDays(tage).toString();
            }
            case 0xa7, 0xaf -> {
                return "'" + new String(b, StandardCharsets.ISO_8859_1) + "'";
            }
            default -> {
                return "0x" + hex(b, 0, 32) + (b.length > 32 ? "..." : "");
            }
        }
    }

    // ---- Client zum Server ----

    static String clientNachricht(int typ, byte[] n, boolean[] cekTabelle) {
        switch (typ) {
            case 0x01 -> {
                int at = le32(n, 0);
                return "Batch: " + new String(n, at, n.length - at, StandardCharsets.UTF_16LE).replace("\r", "\\r").replace("\n", "\\n");
            }
            case 0x03 -> {
                try {
                    return rpc(n);
                } catch (RuntimeException e) {
                    return "RPC unlesbar (" + e.getMessage() + ") hex=" + hex(n, 0, 120);
                }
            }
            case 0x07 -> {
                try {
                    return bulk(n, cekTabelle[0]);
                } catch (RuntimeException e) {
                    return "Massenladen unlesbar (" + e.getMessage() + ") hex=" + hex(n, 0, 200);
                }
            }
            case 0x06 -> {
                return "Attention";
            }
            default -> {
                return "Typ 0x" + Integer.toHexString(typ) + " (" + n.length + " Byte)";
            }
        }
    }

    static String rpc(byte[] n) {
        Puffer p = new Puffer(n, 0);
        int allHeaders = p.i32();
        p.p = allHeaders;
        StringBuilder s = new StringBuilder("RPC ");
        int name = p.u16();
        if (name == 0xffff) {
            int id = p.u16();
            s.append(switch (id) {
                case 10 -> "sp_executesql";
                case 11 -> "sp_prepare";
                case 12 -> "sp_execute";
                case 13 -> "sp_prepexec";
                case 14 -> "sp_prepexecrpc";
                case 15 -> "sp_unprepare";
                default -> "ProcID" + id;
            });
        } else {
            s.append(p.text16(name));
        }
        int optionen = p.u16();
        s.append(" optionen=0x").append(Integer.toHexString(optionen));
        int index = 0;
        while (!p.ende()) {
            int namenLaenge = p.u8();
            String pn = p.text16(namenLaenge);
            int status = p.u8();
            int von = p.p;
            Typ t = typInfo(p);
            String w = wert(p, t);
            s.append(" | ").append(pn.isEmpty() ? "#" + index : pn).append(status != 0 ? "(out)" : "").append(' ')
                .append(t.name()).append(" = ").append(w.length() > 2500 ? w.substring(0, 2500) + "..." : w);
            if (t.id != 0xe7 && t.id != 0xef) {
                s.append(" [hex=").append(hex(n, von, p.p)).append(']');
            }
            index++;
        }
        return s.toString().replace("\r", "\\r").replace("\n", "\\n");
    }

    static String bulk(byte[] n, boolean cekTabelle) {
        Puffer p = new Puffer(n, 0);
        StringBuilder s = new StringBuilder("Massenladen 0x07 (" + n.length + " Byte) ");
        int marke = p.u8();
        if (marke != 0x81) {
            return s + "beginnt nicht mit COLMETADATA: " + hex(n, 0, 40);
        }
        int spalten = p.u16();
        if (cekTabelle && p.u16() != 0) {
            return s + "CekTable mit Eintraegen nicht gelesen";
        }
        List<Typ> typen = new ArrayList<>();
        s.append("COLMETADATA[");
        for (int i = 0; i < spalten; i++) {
            p.i32(); // UserType
            int flags = p.u16();
            Typ t = typInfo(p);
            String name = p.bVarchar();
            typen.add(t);
            s.append(i > 0 ? ", " : "").append(name).append(':').append(t.name()).append("/flags=0x").append(Integer.toHexString(flags));
        }
        s.append("] ");
        int zeilen = 0;
        List<String> beispiel = new ArrayList<>();
        String ende = "";
        while (!p.ende()) {
            int tok = p.u8();
            if (tok == 0xd1 || tok == 0xd2) {
                byte[] nullMaske = null;
                if (tok == 0xd2) {
                    nullMaske = p.bytes((spalten + 7) / 8);
                }
                StringBuilder z = new StringBuilder();
                for (int i = 0; i < spalten; i++) {
                    String w;
                    if (nullMaske != null && (nullMaske[i / 8] & (1 << (i % 8))) != 0) {
                        w = "NULL";
                    } else {
                        w = wert(p, typen.get(i));
                    }
                    z.append(i > 0 ? ", " : "").append(w);
                }
                zeilen++;
                if (beispiel.size() < MAX_ZEILEN_ANZEIGE) {
                    beispiel.add((tok == 0xd2 ? "NBC(" : "ROW(") + z + ")");
                }
            } else if (tok == 0xfd) {
                ende = "DONE status=0x" + Integer.toHexString(p.u16()) + " cmd=0x" + Integer.toHexString(p.u16()) + " zaehler=" + p.i64();
            } else {
                return s + "unbekanntes Token 0x" + Integer.toHexString(tok) + " nach " + zeilen + " Zeilen";
            }
        }
        s.append("Zeilen=").append(zeilen).append(' ').append(beispiel).append(' ').append(ende);
        s.append(" | anfang_hex=").append(hex(n, 0, 60));
        return s.toString();
    }

    // ---- Server zum Client ----

    static String serverNachricht(int typ, byte[] n, boolean[] cekTabelle) {
        if (typ != 0x04) {
            return "Typ 0x" + Integer.toHexString(typ) + " (" + n.length + " Byte)";
        }
        StringBuilder s = new StringBuilder();
        Puffer p = new Puffer(n, 0);
        List<Typ> typen = new ArrayList<>();
        String[] namen = new String[0];
        int zeilen = 0;
        List<String> beispiel = new ArrayList<>();
        try {
            while (!p.ende()) {
                int tok = p.u8();
                switch (tok) {
                    case 0x81 -> {
                        zeilenAbschluss(s, zeilen, beispiel);
                        zeilen = 0;
                        beispiel = new ArrayList<>();
                        int spalten = p.u16();
                        typen = new ArrayList<>();
                        s.append("COLMETADATA[");
                        if (spalten == 0xffff) {
                            spalten = 0;
                        } else if (cekTabelle[0] && p.u16() != 0) {
                            throw new Ende("CekTable mit Eintraegen nicht gelesen");
                        }
                        for (int i = 0; i < spalten; i++) {
                            p.i32();
                            int flags = p.u16();
                            Typ t = typInfo(p);
                            String name = p.bVarchar();
                            typen.add(t);
                            s.append(i > 0 ? ", " : "").append(name).append(':').append(t.name()).append("/flags=0x")
                                .append(Integer.toHexString(flags));
                        }
                        s.append("] ");
                    }
                    case 0xd1, 0xd2 -> {
                        byte[] maske = null;
                        if (tok == 0xd2) {
                            maske = p.bytes((typen.size() + 7) / 8);
                        }
                        StringBuilder z = new StringBuilder();
                        for (int i = 0; i < typen.size(); i++) {
                            String w = maske != null && (maske[i / 8] & (1 << (i % 8))) != 0 ? "NULL" : wert(p, typen.get(i));
                            z.append(i > 0 ? ", " : "").append(w);
                        }
                        zeilen++;
                        if (beispiel.size() < MAX_ZEILEN_ANZEIGE) {
                            beispiel.add("(" + z + ")");
                        }
                    }
                    case 0xfd, 0xfe, 0xff -> {
                        zeilenAbschluss(s, zeilen, beispiel);
                        zeilen = 0;
                        beispiel = new ArrayList<>();
                        String art = tok == 0xfd ? "DONE" : tok == 0xfe ? "DONEPROC" : "DONEINPROC";
                        int status = p.u16();
                        int cmd = p.u16();
                        long zaehler = p.i64();
                        s.append(art).append("(status=0x").append(Integer.toHexString(status)).append(" cmd=0x")
                            .append(String.format("%04x", cmd)).append(" zaehler=").append(zaehler).append(") ");
                    }
                    case 0xaa, 0xab -> {
                        zeilenAbschluss(s, zeilen, beispiel);
                        zeilen = 0;
                        beispiel = new ArrayList<>();
                        p.u16();
                        int nummer = p.i32();
                        int state = p.u8();
                        int klasse = p.u8();
                        String msg = p.usVarchar();
                        p.bVarchar();
                        p.bVarchar();
                        int zeile = p.i32();
                        s.append(tok == 0xaa ? "ERROR" : "INFO").append("(nummer=").append(nummer).append(" state=").append(state)
                            .append(" class=").append(klasse).append(" zeile=").append(zeile).append(" text='").append(msg).append("') ");
                    }
                    case 0x79 -> s.append("RETURNSTATUS(").append(p.i32()).append(") ");
                    case 0xac -> {
                        int ordinal = p.u16();
                        String name = p.bVarchar();
                        int status = p.u8();
                        p.i32();
                        p.u16();
                        Typ t = typInfo(p);
                        s.append("RETURNVALUE(ordinal=").append(ordinal).append(" ").append(name).append(" status=").append(status)
                            .append(" ").append(t.name()).append(" = ").append(wert(p, t)).append(") ");
                    }
                    case 0xe3 -> {
                        int len = p.u16();
                        byte[] inhalt = p.bytes(len);
                        s.append("ENVCHANGE(typ=").append(inhalt[0] & 0xff).append(") ");
                    }
                    case 0xad -> {
                        int len = p.u16();
                        p.bytes(len);
                        s.append("LOGINACK ");
                    }
                    case 0xa9 -> {
                        int len = p.u16();
                        p.bytes(len);
                        s.append("ORDER ");
                    }
                    case 0xe4 -> {
                        int len = p.i32();
                        p.bytes(len);
                        s.append("SESSIONSTATE ");
                    }
                    case 0xae -> {
                        s.append("FEATUREEXTACK[");
                        while (true) {
                            int id = p.u8();
                            if (id == 0xff) {
                                break;
                            }
                            int len = p.i32();
                            p.bytes(len);
                            if (id == 0x04) {
                                cekTabelle[0] = true;
                            }
                            s.append("0x").append(Integer.toHexString(id)).append(' ');
                        }
                        s.append("] ");
                    }
                    default -> {
                        zeilenAbschluss(s, zeilen, beispiel);
                        return s + "unbekanntes Token 0x" + Integer.toHexString(tok) + " an Stelle " + (p.p - 1) + " hex=" + hex(n, Math.max(0, p.p - 1), p.p + 40);
                    }
                }
            }
            zeilenAbschluss(s, zeilen, beispiel);
        } catch (RuntimeException e) {
            return s + "unlesbar (" + e.getMessage() + ") hex=" + hex(n, 0, 80);
        }
        return s.toString().trim();
    }

    static void zeilenAbschluss(StringBuilder s, int zeilen, List<String> beispiel) {
        if (zeilen > 0) {
            s.append("ROW*").append(zeilen).append(' ').append(beispiel).append(' ');
        }
    }

    // ---- Durchlauf ----

    static boolean lesen(InputStream in, byte[] ziel) throws IOException {
        return lesen(in, ziel, 0, ziel.length);
    }

    /** Liest die Stellen [von, bis) von [ziel]. */
    static boolean lesen(InputStream in, byte[] ziel, int von, int bis) throws IOException {
        int gelesen = von;
        while (gelesen < bis) {
            int k = in.read(ziel, gelesen, bis - gelesen);
            if (k < 0) {
                return false;
            }
            gelesen += k;
        }
        return true;
    }

    static void leiten(InputStream in, OutputStream out, int nr, boolean ausClient, boolean[] cekTabelle) throws IOException {
        byte[] kopf = new byte[8];
        ByteArrayOutputStream nachricht = new ByteArrayOutputStream();
        int typ = -1;
        int anzahl = 0;
        String richtung = ausClient ? "C->S" : "S->C";
        while (true) {
            if (!lesen(in, kopf, 0, 1)) {
                zeile("c" + nr + " " + richtung + " Ende des Stroms");
                return;
            }
            // TLS-Records (Typ 0x14 bis 0x17) ohne TDS-Kopf: die Anmeldung und ihre Antwort laufen nach dem Handschlag
            // als TLS-Records mit dem ganzen TDS-Paket als Nutzlast (MS-TDS, Login-only-Verschluesselung).
            if ((kopf[0] & 0xff) >= 0x14 && (kopf[0] & 0xff) <= 0x17) {
                if (!lesen(in, kopf, 1, 5)) {
                    return;
                }
                int recordLaenge = (kopf[3] & 0xff) << 8 | (kopf[4] & 0xff);
                byte[] record = new byte[5 + recordLaenge];
                System.arraycopy(kopf, 0, record, 0, 5);
                if (!lesen(in, record, 5, record.length)) {
                    return;
                }
                out.write(record);
                out.flush();
                anzahl++;
                zeile("c" + nr + " " + richtung + " #" + anzahl + " " + (ausClient ? "LOGIN7" : "Anmeldeantwort")
                    + " als TLS-Record (" + record.length + " Byte, verschluesselt)");
                continue;
            }
            if (!lesen(in, kopf, 1, 8)) {
                return;
            }
            int laenge = (kopf[2] & 0xff) << 8 | (kopf[3] & 0xff);
            byte[] nutzlast = new byte[laenge - 8];
            if (!lesen(in, nutzlast)) {
                return;
            }
            byte[] paket = new byte[laenge];
            System.arraycopy(kopf, 0, paket, 0, 8);
            System.arraycopy(nutzlast, 0, paket, 8, nutzlast.length);
            out.write(paket);
            out.flush();
            if (PAKETE) {
                zeile("c" + nr + " " + richtung + " Paket typ=0x" + Integer.toHexString(kopf[0] & 0xff)
                    + " status=0x" + Integer.toHexString(kopf[1] & 0xff) + " laenge=" + laenge);
            }
            if (nachricht.size() == 0) {
                typ = kopf[0] & 0xff;
            }
            nachricht.write(nutzlast);
            if ((kopf[1] & 1) == 0) {
                continue;
            }
            byte[] n = nachricht.toByteArray();
            nachricht.reset();
            anzahl++;
            // Vorspann (0x12) und die Antwort darauf (Typ 0x04 als erste Nachricht des Servers): nur Kopf.
            boolean vorspannAntwort = !ausClient && typ == 0x04 && anzahl == 1;
            if (typ == 0x12 || vorspannAntwort) {
                zeile("c" + nr + " " + richtung + " #" + anzahl + " PRELOGIN (" + n.length + " Byte)");
                continue;
            }
            String text;
            try {
                text = ausClient ? clientNachricht(typ, n, cekTabelle) : serverNachricht(typ, n, cekTabelle);
            } catch (RuntimeException e) {
                text = "Fehler beim Lesen: " + e;
            }
            zeile("c" + nr + " " + richtung + " #" + anzahl + " " + text);
        }
    }

    public static void main(String[] args) throws Exception {
        int listen = Integer.parseInt(args[0]);
        String host = args[1];
        int port = Integer.parseInt(args[2]);
        ServerSocket durchlauf = new ServerSocket(listen);
        zeile("bereit port=" + listen + " ziel=" + host + ":" + port);
        while (true) {
            Socket client = durchlauf.accept();
            client.setTcpNoDelay(true);
            Socket server = new Socket(host, port);
            server.setTcpNoDelay(true);
            int nr = VERBINDUNGEN.incrementAndGet();
            boolean[] cek = new boolean[1];
            zeile("c" + nr + " verbunden");
            new Thread(() -> {
                try {
                    leiten(client.getInputStream(), server.getOutputStream(), nr, true, cek);
                } catch (IOException e) {
                    zeile("c" + nr + " C->S beendet: " + e);
                } finally {
                    schliessen(server);
                }
            }).start();
            new Thread(() -> {
                try {
                    leiten(server.getInputStream(), client.getOutputStream(), nr, false, cek);
                } catch (IOException e) {
                    zeile("c" + nr + " S->C beendet: " + e);
                } finally {
                    schliessen(client);
                }
            }).start();
        }
    }

    static void schliessen(Socket s) {
        try {
            s.close();
        } catch (IOException e) {
            // schon zu
        }
    }
}
