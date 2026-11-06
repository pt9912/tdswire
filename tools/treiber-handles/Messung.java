import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Messung der Prepared-Handles von mssql-jdbc gegen den Testserver (Aufruf: tools/treiber-handles.sh; kein Gate).
 * Zwischen Treiber und Testserver liegt ein TCP-Durchlauf, der die Nachrichten des Clients nach Art zaehlt: RPC nach
 * ProcID (aus dem Kopf der Nachricht), SQL-Batch mit und ohne `sp_unprepare`. Der Durchlauf aendert keine Bytes.
 * Jede Reihe oeffnet eine Verbindung mit `encrypt=false`; der Benutzer ist `alice` mit Kennwort `secret` (Testwerte).
 * Gemessen wird der Treiber allein gegen den Server dieses Repos, nicht d-migrate und kein echter SQL Server.
 *
 * Reihen: `eine` = ein PreparedStatement, n-mal ausgefuehrt; `neu` = je Ausfuehrung ein neues PreparedStatement mit
 * demselben Text, danach geschlossen. `Erstes-Mal` = enablePrepareOnFirstPreparedStatementCall=true.
 * Ausgabe je Reihe eine Zeile `reihe: ...`; Exit 1, wenn eine Erwartung der Reihe nicht zutrifft.
 */
public class Messung {
    static final Map<String, AtomicInteger> ZAEHLER = new TreeMap<>();
    static String ersterUnprepareBatch = null;

    static synchronized void zaehle(String schluessel) {
        ZAEHLER.computeIfAbsent(schluessel, k -> new AtomicInteger()).incrementAndGet();
    }

    static int le16(byte[] b, int at) {
        return (b[at] & 0xff) | (b[at + 1] & 0xff) << 8;
    }

    static int le32(byte[] b, int at) {
        return le16(b, at) | le16(b, at + 2) << 16;
    }

    static void auswerten(int typ, byte[] nachricht) {
        if (typ == 0x03) {
            int at = le32(nachricht, 0); // ALL_HEADERS: Gesamtlaenge schliesst sich ein
            if (le16(nachricht, at) == 0xFFFF) {
                int id = le16(nachricht, at + 2);
                String name = switch (id) {
                    case 10 -> "(sp_executesql)";
                    case 11 -> "(sp_prepare)";
                    case 12 -> "(sp_execute)";
                    case 13 -> "(sp_prepexec)";
                    case 15 -> "(sp_unprepare)";
                    default -> "";
                };
                zaehle("RPC-ProcID-" + id + name);
            } else {
                zaehle("RPC-Name");
            }
        } else if (typ == 0x01) {
            int at = le32(nachricht, 0);
            String text = new String(nachricht, at, nachricht.length - at, StandardCharsets.UTF_16LE);
            if (text.contains("sp_unprepare")) {
                zaehle("Batch-mit-sp_unprepare");
                synchronized (Messung.class) {
                    if (ersterUnprepareBatch == null) {
                        ersterUnprepareBatch = text;
                    }
                }
            } else {
                zaehle("Batch");
            }
        }
    }

    static void leiten(InputStream in, OutputStream out, boolean zaehlen) throws IOException {
        byte[] kopf = new byte[8];
        ByteArrayOutputStream nachricht = new ByteArrayOutputStream();
        int typ = -1;
        while (true) {
            if (!lesen(in, kopf)) {
                return;
            }
            int laenge = (kopf[2] & 0xff) << 8 | (kopf[3] & 0xff);
            byte[] nutzlast = new byte[laenge - 8];
            if (!lesen(in, nutzlast)) {
                return;
            }
            out.write(kopf);
            out.write(nutzlast);
            out.flush();
            if (zaehlen) {
                if (nachricht.size() == 0) {
                    typ = kopf[0] & 0xff;
                }
                nachricht.write(nutzlast);
                if ((kopf[1] & 1) != 0) {
                    try {
                        auswerten(typ, nachricht.toByteArray());
                    } catch (RuntimeException e) {
                        zaehle("unlesbar");
                    }
                    nachricht.reset();
                }
            }
        }
    }

    static boolean lesen(InputStream in, byte[] ziel) throws IOException {
        int gelesen = 0;
        while (gelesen < ziel.length) {
            int k = in.read(ziel, gelesen, ziel.length - gelesen);
            if (k < 0) {
                return false;
            }
            gelesen += k;
        }
        return true;
    }

    static int zahl(String schluessel) {
        AtomicInteger z = ZAEHLER.get(schluessel);
        return z == null ? 0 : z.get();
    }

    /** Eine Reihe; Rueckgabe: Zahl der gelungenen Ausfuehrungen. Die Zaehler gelten nur fuer diese Reihe. */
    static int reihe(int port, ServerSocket durchlauf, String modus, String optionen, int ausfuehrungen)
            throws Exception {
        ZAEHLER.clear();
        ersterUnprepareBatch = null;
        String url = "jdbc:sqlserver://127.0.0.1:" + durchlauf.getLocalPort()
                + ";user=alice;password=secret;encrypt=false;" + optionen;
        String sql = "SELECT * FROM bestellungen WHERE KundenId = ?";
        int gelungen = 0;
        String fehler = "-";
        try (Connection c = DriverManager.getConnection(url)) {
            ZAEHLER.clear();
            PreparedStatement behalten = modus.equals("eine") ? c.prepareStatement(sql) : null;
            for (int i = 0; i < ausfuehrungen; i++) {
                try {
                    if (behalten != null) {
                        behalten.setInt(1, i);
                        try (ResultSet rs = behalten.executeQuery()) {
                            while (rs.next()) {
                            }
                        }
                    } else {
                        try (PreparedStatement ps = c.prepareStatement(sql)) {
                            ps.setInt(1, i);
                            try (ResultSet rs = ps.executeQuery()) {
                                while (rs.next()) {
                                }
                            }
                        }
                    }
                    gelungen++;
                } catch (SQLException e) {
                    fehler = "bei Ausfuehrung " + (i + 1) + ": " + e.getMessage();
                    break;
                }
            }
        }
        Thread.sleep(300);
        System.out.println("reihe: " + modus + " | " + (optionen.isEmpty() ? "Voreinstellung" : optionen)
                + " | versucht=" + ausfuehrungen + " gelungen=" + gelungen + " | Fehler: " + fehler
                + " | Nachrichten=" + ZAEHLER);
        return gelungen;
    }

    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        ServerSocket durchlauf = new ServerSocket(0, 50, InetAddress.getByName("127.0.0.1"));
        Thread annehmen = new Thread(() -> {
            try {
                while (true) {
                    Socket client = durchlauf.accept();
                    Socket server = new Socket("127.0.0.1", port);
                    new Thread(() -> {
                        try {
                            leiten(client.getInputStream(), server.getOutputStream(), true);
                        } catch (IOException e) {
                            // Verbindung beendet
                        }
                    }).start();
                    new Thread(() -> {
                        try {
                            leiten(server.getInputStream(), client.getOutputStream(), false);
                        } catch (IOException e) {
                            // Verbindung beendet
                        }
                    }).start();
                }
            } catch (IOException e) {
                // Durchlauf geschlossen
            }
        });
        annehmen.setDaemon(true);
        annehmen.start();

        boolean ok = true;

        // m1: ein Anweisungsobjekt, 200 Ausfuehrungen: ein Handle fuer alle (Erwartung: 1 sp_prepexec, 198 sp_execute).
        int g1 = reihe(port, durchlauf, "eine", "", 200);
        boolean m1 = g1 == 200 && zahl("RPC-ProcID-13(sp_prepexec)") == 1 && zahl("RPC-ProcID-12(sp_execute)") == 198;
        System.out.println("erwartung m1 (200 gelungen, 1 sp_prepexec, 198 sp_execute): " + (m1 ? "erfuellt" : "VERFEHLT"));
        ok &= m1;

        // m2: 1100 neue Anweisungen mit Erstes-Mal-Prepare: die Grenze von 1000 gleichzeitig gehaltenen Handles
        // bricht nicht ab, weil der Treiber geschlossene Anweisungen als Batch freigibt.
        int g2 = reihe(port, durchlauf, "neu", "enablePrepareOnFirstPreparedStatementCall=true", 1100);
        boolean m2 = g2 == 1100 && zahl("Batch-mit-sp_unprepare") > 0;
        System.out.println("erwartung m2 (1100 gelungen, mindestens ein Batch mit sp_unprepare): " + (m2 ? "erfuellt" : "VERFEHLT"));
        if (ersterUnprepareBatch != null) {
            String text = ersterUnprepareBatch;
            System.out.println("text des ersten Batches mit sp_unprepare (" + text.length() + " Zeichen): "
                    + (text.length() > 600 ? text.substring(0, 600) + "..." : text));
        }
        ok &= m2;

        System.exit(ok ? 0 : 1);
    }
}
